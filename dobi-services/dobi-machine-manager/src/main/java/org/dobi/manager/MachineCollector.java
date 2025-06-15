package org.dobi.manager;

import org.dobi.api.IDriver;
import org.dobi.entities.Machine;

/**
 * TÃƒÆ’Ã‚Â¢che exÃƒÆ’Ã‚Â©cutable qui gÃƒÆ’Ã‚Â¨re le cycle de vie de la communication
 * pour une seule machine.
 */
public class MachineCollector implements Runnable {

    private final Machine machine;
    private final IDriver driver;
    // Volatile pour assurer la visibilitÃƒÆ’Ã‚Â© entre les threads
    private volatile boolean running = true; 

    public MachineCollector(Machine machine, IDriver driver) {
        this.machine = machine;
        this.driver = driver;
    }

    @Override
    public void run() {
        System.out.println("[Thread " + Thread.currentThread().getId() + "] DÃƒÆ’Ã‚Â©marrage du collecteur pour " + machine.getName());
        driver.configure(machine);

        while (running) {
            try {
                if (!driver.isConnected()) {
                    System.out.println("[Thread " + Thread.currentThread().getId() + "] Tentative de connexion ÃƒÆ’Ã‚Â  " + machine.getName() + "...");
                    if (driver.connect()) {
                        System.out.println("[Thread " + Thread.currentThread().getId() + "] ConnectÃƒÆ’Ã‚Â© ÃƒÆ’Ã‚Â  " + machine.getName());
                    } else {
                        System.out.println("[Thread " + Thread.currentThread().getId() + "] Echec de la connexion ÃƒÆ’Ã‚Â  " + machine.getName() + ". Nouvelle tentative dans 10s.");
                        Thread.sleep(10000); // Attendre 10 secondes avant de rÃƒÆ’Ã‚Â©essayer
                        continue; // Reboucle pour retenter la connexion
                    }
                }
                
                // --- Boucle de lecture des donnÃƒÆ’Ã‚Â©es ---
                System.out.println("[Thread " + Thread.currentThread().getId() + "] Lecture des donnÃƒÆ’Ã‚Â©es sur " + machine.getName() + "...");
                // TODO: Parcourir les tags de la machine et les lire avec driver.read(...)
                
                Thread.sleep(5000); // Attendre 5 secondes entre chaque cycle de lecture

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // RedÃƒÆ’Ã‚Â©finir le statut d'interruption
                System.out.println("[Thread " + Thread.currentThread().getId() + "] Le thread collecteur pour " + machine.getName() + " a ÃƒÆ’Ã‚Â©tÃƒÆ’Ã‚Â© interrompu.");
                running = false; // Sortir de la boucle
            }
        }
        
        driver.disconnect();
        System.out.println("[Thread " + Thread.currentThread().getId() + "] Collecteur pour " + machine.getName() + " arrÃƒÆ’Ã‚ÂªtÃƒÆ’Ã‚Â© et dÃƒÆ’Ã‚Â©connectÃƒÆ’Ã‚Â©.");
    }

    public void stop() {
        this.running = false;
    }
}



