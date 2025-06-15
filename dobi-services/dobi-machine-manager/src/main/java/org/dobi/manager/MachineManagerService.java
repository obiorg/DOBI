package org.dobi.manager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.dobi.api.IDriver;
import org.dobi.entities.Machine;
import org.dobi.siemens.SiemensDriver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MachineManagerService {

    private final EntityManagerFactory emf;
    private final Map<Long, MachineCollector> activeCollectors = new HashMap<>();
    private ExecutorService executorService;

    public MachineManagerService() {
        this.emf = Persistence.createEntityManagerFactory("DOBI-PU");
    }

    /**
     * Crée une instance du driver approprié en fonction du nom du driver de la machine.
     * @param machine La machine pour laquelle créer le driver.
     * @return une instance de IDriver, ou null si le driver est inconnu.
     */
    private IDriver createDriverForMachine(Machine machine) {
        String driverName = machine.getDriver().getDriver();
        // C'est notre "Driver Factory" simple
        switch (driverName.toUpperCase()) {
            case "S7":
                return new SiemensDriver();
            // TODO: Ajouter d'autres cas pour "MODBUS", "OPCUA", etc.
            default:
                System.err.println("Driver inconnu : " + driverName + " pour la machine " + machine.getName());
                return null;
        }
    }

    public void start() {
        System.out.println("Démarrage du Machine Manager Service...");
        List<Machine> machines = getMachinesFromDb();
        System.out.println(machines.size() + " machine(s) trouvée(s) dans la base de données.");

        // Création d'un pool de threads pour gérer les collecteurs
        executorService = Executors.newFixedThreadPool(machines.size() > 0 ? machines.size() : 1);

        for (Machine machine : machines) {
            IDriver driver = createDriverForMachine(machine);
            if (driver != null) {
                System.out.println(" -> Lancement du collecteur pour la machine: " + machine.getName());
                MachineCollector collector = new MachineCollector(machine, driver);
                activeCollectors.put(machine.getId(), collector);
                executorService.submit(collector);
            }
        }
    }

    public List<Machine> getMachinesFromDb() {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("SELECT m FROM Machine m JOIN FETCH m.driver", Machine.class).getResultList();
        } finally {
            em.close();
        }
    }

    public void stop() {
        System.out.println("Arrêt du Machine Manager Service...");
        if (executorService != null) {
            // Demande à chaque collecteur de s'arrêter proprement
            activeCollectors.values().forEach(MachineCollector::stop);
            executorService.shutdown(); // N'accepte plus de nouvelles tâches
            try {
                // Attend jusqu'à 30 secondes que les tâches en cours se terminent
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    System.err.println("Des tâches n'ont pas pu se terminer, forçage de l'arrêt.");
                    executorService.shutdownNow(); // Force l'arrêt des tâches
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
        if (emf != null) {
            emf.close();
        }
        System.out.println("Machine Manager Service arrêté.");
    }
}
