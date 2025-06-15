package org.dobi.manager;

import org.dobi.api.IDriver;
import org.dobi.entities.Machine;

/**
 * TÃƒÂ¢che exÃƒÂ©cutable qui gÃƒÂ¨re le cycle de vie de la communication
 * pour une seule machine.
 */
public class MachineCollector implements Runnable {

    private final Machine machine;
    private final IDriver driver;
    // Volatile pour assurer la visibilitÃƒÂ© entre les threads
    private volatile boolean running = true; 

    public MachineCollector(Machine machine, IDriver driver) {
        this.machine = machine;
        this.driver = driver;
    }

    @Override
    public void run() {
        System.out.println("[Thread " + Thread.currentThread().getId() + "] DÃƒÂ©marrage du collecteur pour " + machine.getName());
        driver.configure(machine);

        while (running) {
            try {
                if (!driver.isConnected()) {
                    System.out.println("[Thread " + Thread.currentThread().getId() + "] Tentative de connexion ÃƒÂ  " + machine.getName() + "...");
                    if (driver.connect()) {
                        System.out.println("[Thread " + Thread.currentThread().getId() + "] ConnectÃƒÂ© ÃƒÂ  " + machine.getName());
                    } else {
                        System.out.println("[Thread " + Thread.currentThread().getId() + "] Echec de la connexion ÃƒÂ  " + machine.getName() + ". Nouvelle tentative dans 10s.");
                        Thread.sleep(10000); // Attendre 10 secondes avant de rÃƒÂ©essayer
                        continue; // Reboucle pour retenter la connexion
                    }
                }
                
                // --- Boucle de lecture des donnÃƒÂ©es ---
                System.out.println("[Thread " + Thread.currentThread().getId() + "] Lecture des donnÃƒÂ©es sur " + machine.getName() + "...");
                // TODO: Parcourir les tags de la machine et les lire avec driver.read(...)
                
                Thread.sleep(5000); // Attendre 5 secondes entre chaque cycle de lecture

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // RedÃƒÂ©finir le statut d'interruption
                System.out.println("[Thread " + Thread.currentThread().getId() + "] Le thread collecteur pour " + machine.getName() + " a ÃƒÂ©tÃƒÂ© interrompu.");
                running = false; // Sortir de la boucle
            }
        }
        
        driver.disconnect();
        System.out.println("[Thread " + Thread.currentThread().getId() + "] Collecteur pour " + machine.getName() + " arrÃƒÂªtÃƒÂ© et dÃƒÂ©connectÃƒÂ©.");
    }

    public void stop() {
        this.running = false;
    }
}


