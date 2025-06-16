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

    public MachineCollector(Machine machine, IDriver driver, KafkaProducerService kafkaProducerService) {
        this.machine = machine;
        this.driver = driver;
        this.kafkaProducerService = kafkaProducerService;
    }

    @Override
    public void run() {
        System.out.println("[Thread " + Thread.currentThread().getId() + "] Demarrage du collecteur pour " + machine.getName());
        driver.configure(machine);

        while (running) {
            try {
                if (!driver.isConnected()) {
                    System.out.println("[Thread " + Thread.currentThread().getId() + "] Tentative de connexion a " + machine.getName() + "...");
                    if (driver.connect()) {
                        System.out.println("[Thread " + Thread.currentThread().getId() + "] Connecte a " + machine.getName());
                    } else {
                        System.out.println("[Thread " + Thread.currentThread().getId() + "] Echec de la connexion a " + machine.getName() + ". Nouvelle tentative dans 10s.");
                        Thread.sleep(10000);
                        continue;
                    }
                }

                if (machine.getTags() == null || machine.getTags().isEmpty()) {
                    System.out.println("[Thread " + Thread.currentThread().getId() + "] Aucun tag a lire pour " + machine.getName() + ".");
                } else {
                     for (org.dobi.entities.Tag tag : machine.getTags()) {
                         if (tag.isActive()) {
                            Object value = driver.read(tag);
                            if (value != null) {
                                System.out.println("    -> Tag: " + tag.getName() + " | Valeur: " + value);
                                TagData tagData = new TagData(tag.getId(), tag.getName(), value, System.currentTimeMillis());
                                kafkaProducerService.sendTagData(tagData);
                            }
                         }
                     }
                }
                
                Thread.sleep(5000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("[Thread " + Thread.currentThread().getId() + "] Le thread collecteur pour " + machine.getName() + " a ete interrompu.");
                running = false;
            }
        }
        
        driver.disconnect();
        System.out.println("[Thread " + Thread.currentThread().getId() + "] Collecteur pour " + machine.getName() + " arrete et deconnecte.");
    }

    public void stop() {
        this.running = false;
    }
}
