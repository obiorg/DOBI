package org.dobi.manager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.dobi.api.IDriver;
import org.dobi.dto.MachineStatusDto;
import org.dobi.entities.Machine;
import org.dobi.kafka.producer.KafkaProducerService;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MachineManagerService {
    private final EntityManagerFactory emf;
    private final Map<Long, MachineCollector> activeCollectors = new HashMap<>();
    private ExecutorService executorService;
    private final Properties driverProperties = new Properties();
    private KafkaProducerService kafkaProducerService;
    private final Properties appProperties = new Properties();
    
    // Auto-reload des tags
    private Timer tagReloadTimer;
    private boolean autoReloadEnabled = true;
    private int autoReloadIntervalSeconds = 30;
    private final Map<Long, Integer> lastKnownTagCounts = new HashMap<>();
    private final Map<Long, Long> lastTagReloadTime = new HashMap<>();

    public MachineManagerService() {
        this.emf = Persistence.createEntityManagerFactory("DOBI-PU");
        loadAppProperties();
        loadDriverProperties();
    }

    public void initializeKafka() {
        this.kafkaProducerService = new KafkaProducerService(appProperties.getProperty("kafka.bootstrap.servers"), appProperties.getProperty("kafka.topic.tags.data"));
    }

    private void loadAppProperties() { 
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) { 
            if (input == null) { 
                System.err.println("ATTENTION: application.properties introuvable!"); 
                return; 
            } 
            appProperties.load(input); 
        } catch (Exception ex) { 
            ex.printStackTrace(); 
        } 
    }
    
    private void loadDriverProperties() { 
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("drivers.properties")) { 
            if (input == null) { 
                System.err.println("ERREUR: drivers.properties introuvable!"); 
                return; 
            } 
            driverProperties.load(input); 
        } catch (Exception ex) { 
            ex.printStackTrace(); 
        } 
    }
    
    private IDriver createDriverForMachine(Machine machine) { 
        String driverName = machine.getDriver().getDriver(); 
        String driverClassName = driverProperties.getProperty(driverName); 
        if (driverClassName == null) { 
            System.err.println("Aucune classe pour driver '" + driverName + "'"); 
            return null; 
        } 
        try { 
            return (IDriver) Class.forName(driverClassName).getConstructor().newInstance(); 
        } catch (Exception e) { 
            System.err.println("Erreur instanciation driver '" + driverClassName + "'"); 
            e.printStackTrace(); 
            return null; 
        } 
    }

    public void start() {
        List<Machine> machines = getMachinesFromDb();
        executorService = Executors.newFixedThreadPool(Math.max(1, machines.size()));
        
        for (Machine machine : machines) {
            IDriver driver = createDriverForMachine(machine);
            if (driver != null) {
                MachineCollector collector = new MachineCollector(machine, driver, kafkaProducerService);
                activeCollectors.put(machine.getId(), collector);
                
                // Initialiser le compteur de tags
                lastKnownTagCounts.put(machine.getId(), 
                    machine.getTags() != null ? machine.getTags().size() : 0);
                lastTagReloadTime.put(machine.getId(), System.currentTimeMillis());
                
                executorService.submit(collector);
            }
        }
        
        // Démarrer le monitoring automatique
        if (autoReloadEnabled) {
            startTagReloadMonitoring();
        }
    }
    
    /**
     * Démarre le monitoring automatique des tags
     */
    public void startTagReloadMonitoring() {
        if (tagReloadTimer != null) {
            tagReloadTimer.cancel();
        }
        
        tagReloadTimer = new Timer("TagAutoReloader", true);
        tagReloadTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (autoReloadEnabled) {
                    checkForTagUpdates();
                }
            }
        }, autoReloadIntervalSeconds * 1000L, autoReloadIntervalSeconds * 1000L);
        
        System.out.println("[AUTO-RELOAD] Monitoring des tags démarré (intervalle: " + autoReloadIntervalSeconds + "s)");
    }
    
    /**
     * Arrête le monitoring automatique des tags
     */
    public void stopTagReloadMonitoring() {
        autoReloadEnabled = false;
        if (tagReloadTimer != null) {
            tagReloadTimer.cancel();
            tagReloadTimer = null;
        }
        System.out.println("[AUTO-RELOAD] Monitoring des tags arrêté");
    }
    
    /**
     * Configure l'intervalle de monitoring automatique
     */
    public void setAutoReloadInterval(int intervalSeconds) {
        this.autoReloadIntervalSeconds = Math.max(10, intervalSeconds); // Minimum 10 secondes
        if (autoReloadEnabled && tagReloadTimer != null) {
            startTagReloadMonitoring(); // Redémarrer avec le nouvel intervalle
        }
        System.out.println("[AUTO-RELOAD] Intervalle configuré à " + this.autoReloadIntervalSeconds + " secondes");
    }
    
    /**
     * Active/désactive le monitoring automatique
     */
    public void setAutoReloadEnabled(boolean enabled) {
        this.autoReloadEnabled = enabled;
        if (enabled) {
            startTagReloadMonitoring();
        } else {
            stopTagReloadMonitoring();
        }
    }
    
    /**
     * Force la vérification immédiate des mises à jour de tags
     */
    public Map<String, Object> forceTagReloadCheck() {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", System.currentTimeMillis());
        result.put("machinesChecked", activeCollectors.size());
        
        List<String> updatedMachines = new ArrayList<>();
        
        for (Map.Entry<Long, MachineCollector> entry : activeCollectors.entrySet()) {
            Long machineId = entry.getKey();
            MachineCollector collector = entry.getValue();
            
            try {
                Machine currentMachine = getMachineFromDb(machineId);
                if (currentMachine != null) {
                    int currentTagCount = currentMachine.getTags() != null ? currentMachine.getTags().size() : 0;
                    int lastKnownCount = lastKnownTagCounts.getOrDefault(machineId, 0);
                    
                    if (currentTagCount != lastKnownCount) {
                        System.out.println("[FORCE-RELOAD] Tags mis à jour pour " + currentMachine.getName() + 
                                         " (" + lastKnownCount + " → " + currentTagCount + " tags)");
                        
                        collector.updateMachine(currentMachine);
                        lastKnownTagCounts.put(machineId, currentTagCount);
                        lastTagReloadTime.put(machineId, System.currentTimeMillis());
                        updatedMachines.add(currentMachine.getName());
                    }
                }
            } catch (Exception e) {
                System.err.println("[FORCE-RELOAD] Erreur pour machine " + machineId + ": " + e.getMessage());
            }
        }
        
        result.put("updatedMachines", updatedMachines);
        result.put("updatedCount", updatedMachines.size());
        
        return result;
    }
    
    /**
     * Vérification automatique des mises à jour de tags
     */
    private void checkForTagUpdates() {
        for (Map.Entry<Long, MachineCollector> entry : activeCollectors.entrySet()) {
            Long machineId = entry.getKey();
            MachineCollector collector = entry.getValue();
            
            try {
                Machine currentMachine = getMachineFromDb(machineId);
                if (currentMachine != null) {
                    int currentTagCount = currentMachine.getTags() != null ? currentMachine.getTags().size() : 0;
                    int lastKnownCount = lastKnownTagCounts.getOrDefault(machineId, 0);
                    
                    if (currentTagCount != lastKnownCount) {
                        System.out.println("[AUTO-RELOAD] Nouveaux tags détectés pour " + currentMachine.getName() + 
                                         " (" + lastKnownCount + " → " + currentTagCount + " tags)");
                        
                        collector.updateMachine(currentMachine);
                        lastKnownTagCounts.put(machineId, currentTagCount);
                        lastTagReloadTime.put(machineId, System.currentTimeMillis());
                    }
                }
            } catch (Exception e) {
                System.err.println("[AUTO-RELOAD] Erreur pour machine " + machineId + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Obtient le statut du monitoring automatique
     */
    public Map<String, Object> getAutoReloadStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", autoReloadEnabled);
        status.put("intervalSeconds", autoReloadIntervalSeconds);
        status.put("machinesMonitored", activeCollectors.size());
        status.put("timerActive", tagReloadTimer != null);
        
        // Dernières vérifications par machine
        Map<String, Object> lastChecks = new HashMap<>();
        for (Map.Entry<Long, MachineCollector> entry : activeCollectors.entrySet()) {
            Long machineId = entry.getKey();
            MachineCollector collector = entry.getValue();
            
            Map<String, Object> machineInfo = new HashMap<>();
            machineInfo.put("machineName", collector.getMachineName());
            machineInfo.put("lastReloadTime", lastTagReloadTime.getOrDefault(machineId, 0L));
            machineInfo.put("currentTagCount", lastKnownTagCounts.getOrDefault(machineId, 0));
            
            lastChecks.put(machineId.toString(), machineInfo);
        }
        status.put("machines", lastChecks);
        
        return status;
    }

    public List<Machine> getMachinesFromDb() {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery(
                "SELECT DISTINCT m FROM Machine m " +
                "JOIN FETCH m.driver " +
                "LEFT JOIN FETCH m.tags t " +
                "LEFT JOIN FETCH t.type " +
                "LEFT JOIN FETCH t.memory " +
                "WHERE t.active = true OR t IS NULL", 
                Machine.class
            ).getResultList();
        } finally {
            em.close();
        }
    }
    
    public Machine getMachineFromDb(long machineId) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery(
                "SELECT m FROM Machine m LEFT JOIN FETCH m.tags t LEFT JOIN FETCH t.type ty LEFT JOIN FETCH t.memory WHERE m.id = :id", Machine.class)
                .setParameter("id", machineId)
                .getSingleResult();
        } catch (Exception e) {
            System.err.println("Impossible de trouver la machine avec l'ID: " + machineId);
            return null;
        } finally {
            em.close();
        }
    }

    public org.dobi.entities.Tag getTagFromDb(long tagId) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.find(org.dobi.entities.Tag.class, tagId);
        } catch (Exception e) {
            System.err.println("Impossible de trouver le tag avec l'ID: " + tagId);
            return null;
        } finally {
            em.close();
        }
    }

    public List<org.dobi.entities.PersStandard> getTagHistory(long tagId, int page, int size) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery(
                "SELECT h FROM PersStandard h WHERE h.tag = :tagId ORDER BY h.vStamp DESC", org.dobi.entities.PersStandard.class)
                .setParameter("tagId", tagId)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
        } catch (Exception e) {
            System.err.println("Impossible de récupérer l'historique pour le tag ID: " + tagId);
            return java.util.Collections.emptyList();
        } finally {
            em.close();
        }
    }

    public EntityManagerFactory getEmf() { return emf; }
    public String getAppProperty(String key) { return appProperties.getProperty(key); }

    public void stop() {
        // Arrêter le monitoring automatique
        stopTagReloadMonitoring();
        
        if (kafkaProducerService != null) kafkaProducerService.close();
        if (executorService != null) {
            activeCollectors.values().forEach(MachineCollector::stop);
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) executorService.shutdownNow();
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
        if (emf != null) emf.close();
    }

    public void restartCollector(long machineId) {
        MachineCollector oldCollector = activeCollectors.get(machineId);
        if (oldCollector != null) oldCollector.stop();
        
        EntityManager em = emf.createEntityManager();
        try {
            Machine machineToRestart = em.createQuery(
                "SELECT m FROM Machine m " +
                "JOIN FETCH m.driver " +
                "LEFT JOIN FETCH m.tags t " +
                "LEFT JOIN FETCH t.type " +
                "LEFT JOIN FETCH t.memory " +
                "WHERE m.id = :id", Machine.class)
                .setParameter("id", machineId)
                .getSingleResult();
                
            IDriver driver = createDriverForMachine(machineToRestart);
            if (driver != null) {
                MachineCollector newCollector = new MachineCollector(machineToRestart, driver, kafkaProducerService);
                activeCollectors.put(machineToRestart.getId(), newCollector);
                
                // Mettre à jour les compteurs
                lastKnownTagCounts.put(machineId, 
                    machineToRestart.getTags() != null ? machineToRestart.getTags().size() : 0);
                lastTagReloadTime.put(machineId, System.currentTimeMillis());
                
                executorService.submit(newCollector);
            }
        } catch(Exception e) {
            System.err.println("Impossible de redemarrer machine " + machineId);
        } finally {
            em.close();
        }
    }

    public List<MachineStatusDto> getActiveCollectorDetails() {
        return activeCollectors.values().stream()
            .map(c -> new MachineStatusDto(c.getMachineId(), c.getMachineName(), c.getCurrentStatus(), c.getTagsReadCount()))
            .collect(Collectors.toList());
    }
}