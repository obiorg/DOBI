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
     * CrÃ©e une instance du driver appropriÃ© en fonction du nom du driver de la machine.
     * @param machine La machine pour laquelle crÃ©er le driver.
     * @return une instance de IDriver, ou null si le driver est inconnu.
     */
        private IDriver createDriverForMachine(Machine machine) {
        if (machine.getDriver() == null || machine.getDriver().getDriver() == null) {
            System.err.println("Driver non défini pour la machine " + machine.getName());
            return null;
        }
        
        String driverName = machine.getDriver().getDriver().toUpperCase();
        
        // On vérifie si le nom du driver commence par "S7"
        if (driverName.startsWith("S7")) {
            return new SiemensDriver();
        }

        // TODO: Ajouter d'autres cas pour "MODBUS", "OPCUA", etc.
        
        System.err.println("Driver non supporté : " + driverName + " pour la machine " + machine.getName());
        return null;
    }

    public void start() {
        System.out.println("DÃ©marrage du Machine Manager Service...");
        List<Machine> machines = getMachinesFromDb();
        System.out.println(machines.size() + " machine(s) trouvÃ©e(s) dans la base de donnÃ©es.");

        // CrÃ©ation d'un pool de threads pour gÃ©rer les collecteurs
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
        System.out.println("ArrÃªt du Machine Manager Service...");
        if (executorService != null) {
            // Demande Ã  chaque collecteur de s'arrÃªter proprement
            activeCollectors.values().forEach(MachineCollector::stop);
            executorService.shutdown(); // N'accepte plus de nouvelles tÃ¢ches
            try {
                // Attend jusqu'Ã  30 secondes que les tÃ¢ches en cours se terminent
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    System.err.println("Des tÃ¢ches n'ont pas pu se terminer, forÃ§age de l'arrÃªt.");
                    executorService.shutdownNow(); // Force l'arrÃªt des tÃ¢ches
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
        if (emf != null) {
            emf.close();
        }
        System.out.println("Machine Manager Service arrÃªtÃ©.");
    }
}

