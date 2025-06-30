package org.dobi.manager;

import org.dobi.api.IDriver;
import org.dobi.dto.TagData;
import org.dobi.entities.Machine;
import org.dobi.kafka.producer.KafkaProducerService;
import org.dobi.logging.LogLevelManager;
import org.dobi.logging.LogLevelManager.LogLevel;

public class MachineCollector implements Runnable {

    private static final String COMPONENT_NAME = "COLLECTOR";
    
    private Machine machine;
    private final IDriver driver;
    private volatile boolean running = true;
    private final KafkaProducerService kafkaProducerService;
    private volatile String currentStatus = "Initialisation...";
    private long tagsReadCount = 0;

    public MachineCollector(Machine machine, IDriver driver, KafkaProducerService kps) {
        this.machine = machine;
        this.driver = driver;
        this.kafkaProducerService = kps;
        LogLevelManager.logInfo(COMPONENT_NAME, "Nouveau collecteur créé pour machine: " + machine.getName());
    }

    @Override
    public void run() {
        String driverType = getDriverType();
        LogLevelManager.logInfo(COMPONENT_NAME, "Démarrage collecteur pour " + machine.getName() + " (Driver: " + driverType + ")");
        
        driver.configure(machine);
        
        while (running) {
            try {
                // Étape 1 : Assurer la connexion au début de chaque cycle
                if (!driver.isConnected()) {
                    updateStatus("Connexion...");
                    LogLevelManager.logInfo(COMPONENT_NAME, "Tentative de connexion pour " + machine.getName());
                    
                    if (!driver.connect()) {
                        updateStatus("Erreur Connexion");
                        LogLevelManager.logError(COMPONENT_NAME, "Échec de connexion pour " + machine.getName() + " - Nouvelle tentative dans 10s");
                        Thread.sleep(10000); // Attendre avant de réessayer
                        continue;
                    }
                    updateStatus("Connecté");
                    LogLevelManager.logInfo(COMPONENT_NAME, "Connexion établie pour " + machine.getName());
                    
                    // === DIAGNOSTIC OPC UA ===
                    if (driver instanceof org.dobi.opcua.OpcUaDriver) {
                        performOpcUaDiagnostic();
                    }
                }

                // Étape 2 : Boucle de lecture des tags
                int tagsInCycle = 0;
                boolean readOk = true;
                
                if (machine.getTags() != null && !machine.getTags().isEmpty()) {
                    LogLevelManager.logDebug(COMPONENT_NAME, "Début cycle de lecture pour " + machine.getName() + 
                                         " (" + machine.getTags().size() + " tags actifs)");
                    
                    for (org.dobi.entities.Tag tag : machine.getTags()) {
                        if (!running) {
                            LogLevelManager.logInfo(COMPONENT_NAME, "Arrêt demandé pendant la lecture des tags");
                            break; // Sortir si un arrêt est demandé
                        }
                        
                        if (tag.isActive()) {
                            try {
                                LogLevelManager.logTrace(COMPONENT_NAME, "Lecture tag: " + tag.getName() + " (machine: " + machine.getName() + ")");
                                
                                Object value = driver.read(tag);
                                if (value != null) {
                                    // Si la lecture réussit, on envoie à Kafka
                                    TagData tagData = new TagData(tag.getId(), tag.getName(), value, System.currentTimeMillis());
                                    kafkaProducerService.sendTagData(tagData);
                                    tagsInCycle++;
                                    
                                    LogLevelManager.logTrace(COMPONENT_NAME, "Lecture réussie: " + tag.getName() + 
                                                         " = " + value + " (machine: " + machine.getName() + ")");
                                } else {
                                    // La lecture a échoué (ex: tag inexistant), mais la connexion est peut-être OK
                                    LogLevelManager.logDebug(COMPONENT_NAME, "Lecture échouée pour tag: " + tag.getName() + 
                                                         " (machine: " + machine.getName() + ") - valeur null retournée");
                                }
                            } catch (Exception e) {
                                // Une exception pendant la lecture indique une perte de connexion !
                                LogLevelManager.logError(COMPONENT_NAME, "Exception de lecture pour tag " + tag.getName() + 
                                                     " (machine: " + machine.getName() + "): " + e.getMessage() + 
                                                     " - Perte de connexion suspectée");
                                readOk = false;
                                driver.disconnect(); // Forcer la déconnexion pour réinitialiser l'état
                                break; // Sortir de la boucle for des tags
                            }
                        } else {
                            LogLevelManager.logTrace(COMPONENT_NAME, "Tag inactif ignoré: " + tag.getName());
                        }
                    }
                } else {
                    LogLevelManager.logError(COMPONENT_NAME, "Aucun tag configuré pour la machine: " + machine.getName());
                }

                // Étape 3 : Mise à jour du statut et attente
                if (readOk) {
                    tagsReadCount += tagsInCycle;
                    updateStatus("Connecté (lus: " + tagsReadCount + ")");
                    
                    if (tagsInCycle > 0) {
                        LogLevelManager.logDebug(COMPONENT_NAME, "Cycle terminé pour " + machine.getName() + 
                                             " - " + tagsInCycle + " tags lus avec succès");
                    }
                    
                    Thread.sleep(5000); // Attente normale entre les cycles
                } else {
                    // Si une erreur de lecture a eu lieu, on attend un peu avant de retenter une connexion complète
                    updateStatus("Reconnexion...");
                    LogLevelManager.logInfo(COMPONENT_NAME, "Attente avant tentative de reconnexion pour " + machine.getName());
                    Thread.sleep(5000);
                }

            } catch (InterruptedException e) {
                LogLevelManager.logInfo(COMPONENT_NAME, "Collecteur interrompu pour " + machine.getName());
                running = false;
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LogLevelManager.logError(COMPONENT_NAME, "Erreur inattendue dans le collecteur pour " + machine.getName() + ": " + e.getMessage());
                updateStatus("Erreur");
                try {
                    Thread.sleep(5000); // Attendre avant de continuer
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    running = false;
                }
            }
        }
        
        // Nettoyage final
        try {
            driver.disconnect();
            updateStatus("Déconnecté");
            LogLevelManager.logInfo(COMPONENT_NAME, "Collecteur arrêté et déconnecté pour " + machine.getName());
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de la déconnexion finale pour " + machine.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Diagnostic spécifique pour OPC UA
     */
    private void performOpcUaDiagnostic() {
        try {
            org.dobi.opcua.OpcUaDriver opcDriver = (org.dobi.opcua.OpcUaDriver) driver;
            
            LogLevelManager.logInfo(COMPONENT_NAME, "=== DIAGNOSTIC OPC UA POUR " + machine.getName() + " ===");
            
            // Test direct avec les identifiants UaExpert
            LogLevelManager.logDebug(COMPONENT_NAME, "--- Test Identifiants UaExpert ---");
            
            // Test ENERGY_2 avec le bon identifiant
            if (machine.getName().contains("ENERGIE 2")) {
                LogLevelManager.logDebug(COMPONENT_NAME, "Test spécifique ENERGY_2 détecté");
                
                String testResult = opcDriver.testSpecificNodeId("|var|ENERGY_2.Application.GVL.DMG[6].phase_voltage.a");
                LogLevelManager.logInfo(COMPONENT_NAME, "Test ENERGY_2 phase_voltage.a: " + 
                                     (testResult.contains("✅ SUCCÈS") ? "RÉUSSI" : "ÉCHOUÉ"));
                LogLevelManager.logTrace(COMPONENT_NAME, "Détail test ENERGY_2 phase_voltage.a:\n" + testResult);
                
                // Test avec frequency aussi
                String testResult2 = opcDriver.testSpecificNodeId("|var|ENERGY_2.Application.GVL.DMG[6].frequency");
                LogLevelManager.logInfo(COMPONENT_NAME, "Test ENERGY_2 frequency: " + 
                                     (testResult2.contains("✅ SUCCÈS") ? "RÉUSSI" : "ÉCHOUÉ"));
                LogLevelManager.logTrace(COMPONENT_NAME, "Détail test ENERGY_2 frequency:\n" + testResult2);
            }
            
            // Test ENERGY_1 - À CORRIGER avec le vrai identifiant UaExpert
            if (machine.getName().contains("ENERGIE 1")) {
                LogLevelManager.logDebug(COMPONENT_NAME, "Test spécifique ENERGY_1 détecté");
                
                // Test plusieurs variantes possibles pour ENERGY_1
                String[] energy1Tests = {
                    "|var|ENERGY_1.Application.GVL.tfos[0].stdset.frequency.value",
                    "|var|ENERGY_1.Application.GlobalVars.GVL.tfos[0].stdset.frequency.value",
                    "|appo|ENERGY_1.Application.GVL.tfos[0].stdset.frequency.value"
                };
                
                boolean foundWorking = false;
                for (String testId : energy1Tests) {
                    LogLevelManager.logDebug(COMPONENT_NAME, "Test ENERGY_1 avec identifiant: " + testId);
                    
                    String testResult = opcDriver.testSpecificNodeId(testId);
                    boolean success = testResult.contains("✅ SUCCÈS");
                    
                    LogLevelManager.logInfo(COMPONENT_NAME, "Test ENERGY_1 (" + testId + "): " + 
                                         (success ? "RÉUSSI" : "ÉCHOUÉ"));
                    LogLevelManager.logTrace(COMPONENT_NAME, "Détail test:\n" + testResult);
                    
                    if (success) {
                        LogLevelManager.logInfo(COMPONENT_NAME, "🎉 IDENTIFIANT CORRECT TROUVÉ POUR ENERGY_1: " + testId);
                        foundWorking = true;
                        break;
                    }
                }
                
                if (!foundWorking) {
                    LogLevelManager.logError(COMPONENT_NAME, "❌ Aucun identifiant fonctionnel trouvé pour ENERGY_1");
                }
            }
            
            LogLevelManager.logInfo(COMPONENT_NAME, "=== FIN DIAGNOSTIC OPC UA ===");
            
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur lors du diagnostic OPC UA pour " + machine.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Met à jour la machine avec de nouveaux tags
     */
    public void updateMachine(Machine updatedMachine) {
        synchronized (this) {
            int oldTagCount = this.machine.getTags() != null ? this.machine.getTags().size() : 0;
            int newTagCount = updatedMachine.getTags() != null ? updatedMachine.getTags().size() : 0;
            
            LogLevelManager.logInfo(COMPONENT_NAME, "Mise à jour machine " + machine.getName() + 
                                 " - Tags: " + oldTagCount + " → " + newTagCount);
            
            this.machine = updatedMachine;
            
            if (newTagCount > oldTagCount) {
                LogLevelManager.logInfo(COMPONENT_NAME, (newTagCount - oldTagCount) + 
                                     " nouveaux tags détectés pour " + machine.getName());
            } else if (newTagCount < oldTagCount) {
                LogLevelManager.logInfo(COMPONENT_NAME, (oldTagCount - newTagCount) + 
                                     " tags supprimés pour " + machine.getName());
            } else {
                LogLevelManager.logDebug(COMPONENT_NAME, "Nombre de tags inchangé pour " + machine.getName());
            }
        }
    }
    
    /**
     * Obtient le nombre actuel de tags
     */
    public int getCurrentTagCount() {
        int count = machine.getTags() != null ? machine.getTags().size() : 0;
        LogLevelManager.logTrace(COMPONENT_NAME, "Nombre actuel de tags pour " + machine.getName() + ": " + count);
        return count;
    }

    public void stop() {
        LogLevelManager.logInfo(COMPONENT_NAME, "Arrêt demandé pour collecteur " + machine.getName());
        this.running = false;
    }

    public long getMachineId() {
        return machine.getId();
    }

    public String getMachineName() {
        return machine.getName();
    }

    public long getTagsReadCount() {
        return tagsReadCount;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    private void updateStatus(String status) {
        String oldStatus = this.currentStatus;
        this.currentStatus = status;
        
        if (!status.equals(oldStatus)) {
            LogLevelManager.logDebug(COMPONENT_NAME, "Changement statut pour " + machine.getName() + 
                                 ": " + oldStatus + " → " + status);
        }
    }
    
    /**
     * Méthode utilitaire pour obtenir le type de driver
     */
    private String getDriverType() {
        if (driver == null) return "UNKNOWN";
        
        String className = driver.getClass().getSimpleName();
        
        if (className.contains("OpcUa")) {
            return "OPC-UA";
        } else if (className.contains("Siemens")) {
            return "SIEMENS-S7";
        } else if (className.contains("Modbus")) {
            return "MODBUS-TCP";
        } else {
            return className;
        }
    }
    
    /**
     * Méthode pour obtenir des informations de diagnostic
     */
    public String getDiagnosticInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== Diagnostic Collecteur ===\n");
        info.append("Machine: ").append(machine.getName()).append(" (ID: ").append(machine.getId()).append(")\n");
        info.append("Driver: ").append(getDriverType()).append("\n");
        info.append("Statut: ").append(currentStatus).append("\n");
        info.append("En cours d'exécution: ").append(running).append("\n");
        info.append("Tags lus (total): ").append(tagsReadCount).append("\n");
        info.append("Tags configurés: ").append(getCurrentTagCount()).append("\n");
        info.append("Driver connecté: ").append(driver != null ? driver.isConnected() : "N/A").append("\n");
        
        // Informations sur la machine
        if (machine != null) {
            info.append("\n=== Configuration Machine ===\n");
            info.append("Adresse: ").append(machine.getAddress()).append("\n");
            info.append("Port: ").append(machine.getPort()).append("\n");
            
            if (machine.getRack() != null || machine.getSlot() != null) {
                info.append("Rack/Slot: ").append(machine.getRack()).append("/").append(machine.getSlot()).append("\n");
            }
            
            if (machine.getBus() != null) {
                info.append("Bus/Unit ID: ").append(machine.getBus()).append("\n");
            }
        }
        
        LogLevelManager.logDebug(COMPONENT_NAME, "Diagnostic généré pour " + machine.getName());
        return info.toString();
    }
    
    /**
     * Réinitialise les compteurs
     */
    public void resetCounters() {
        long oldCount = tagsReadCount;
        tagsReadCount = 0;
        LogLevelManager.logInfo(COMPONENT_NAME, "Compteurs réinitialisés pour " + machine.getName() + 
                             " (ancien total: " + oldCount + ")");
    }
}