package org.dobi.manager;

import org.dobi.api.IDriver;
import org.dobi.dto.TagData;
import org.dobi.entities.Machine;
import org.dobi.kafka.producer.KafkaProducerService;

public class MachineCollector implements Runnable {
    private final Machine machine;
    private final IDriver driver;
    private volatile boolean running = true;
    private final KafkaProducerService kafkaProducerService;
    private volatile String currentStatus = "Initialisation...";
    private long tagsReadCount = 0;

    public MachineCollector(Machine machine, IDriver driver, KafkaProducerService kps) {
        this.machine = machine;
        this.driver = driver;
        this.kafkaProducerService = kps;
    }

    @Override
    public void run() {
        driver.configure(machine);
        while (running) {
            try {
                // Étape 1 : Assurer la connexion au début de chaque cycle
                if (!driver.isConnected()) {
                    updateStatus("Connexion...");
                    if (!driver.connect()) {
                        updateStatus("Erreur Connexion");
                        Thread.sleep(10000); // Attendre avant de réessayer
                        continue;
                    }
                    updateStatus("Connecté");
                }

                // Étape 2 : Boucle de lecture des tags
                int tagsInCycle = 0;
                boolean readOk = true;
                if (machine.getTags() != null && !machine.getTags().isEmpty()) {
                    for (org.dobi.entities.Tag tag : machine.getTags()) {
                        if (!running) break; // Sortir si un arrêt est demandé
                        
                        if (tag.isActive()) {
                           try {
                                Object value = driver.read(tag);
                                if (value != null) {
                                   // Si la lecture réussit, on envoie à Kafka
                                   TagData tagData = new TagData(tag.getId(), tag.getName(), value, System.currentTimeMillis());
                                   kafkaProducerService.sendTagData(tagData);
                                   tagsInCycle++;
                               } else {
                                   // La lecture a échoué (ex: tag inexistant), mais la connexion est peut-être OK
                                   // On ne fait rien pour ne pas spammer les logs
                               }
                           } catch (Exception e) {
                               // Une exception pendant la lecture indique une perte de connexion !
                               System.err.println("Exception de lecture pour " + tag.getName() + ", perte de connexion suspectée. Tentative de reconnexion au prochain cycle.");
                               readOk = false;
                               driver.disconnect(); // Forcer la déconnexion pour réinitialiser l'état
                               break; // Sortir de la boucle for des tags
                           }
                        }
                    }
                }
                
                // Étape 3 : Mise à jour du statut et attente
                if (readOk) {
                    tagsReadCount += tagsInCycle;
                    updateStatus("Connecté (lus: " + tagsReadCount + ")");
                    Thread.sleep(5000); // Attente normale entre les cycles
                } else {
                    // Si une erreur de lecture a eu lieu, on attend un peu avant de retenter une connexion complète
                    updateStatus("Reconnexion...");
                    Thread.sleep(5000);
                }

            } catch (InterruptedException e) {
                running = false;
                Thread.currentThread().interrupt();
            }
        }
        driver.disconnect();
        updateStatus("Déconnecté");
    }
    
    public void stop() { this.running = false; }
    public long getMachineId() { return machine.getId(); }
    public String getMachineName() { return machine.getName(); }
    public long getTagsReadCount() { return tagsReadCount; }
    public String getCurrentStatus() { return currentStatus; }
    private void updateStatus(String status) { this.currentStatus = status; }
}
