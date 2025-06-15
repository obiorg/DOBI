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
     * CrÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©e une instance du driver appropriÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© en fonction du nom du driver de la machine.
     * @param machine La machine pour laquelle crÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©er le driver.
     * @return une instance de IDriver, ou null si le driver est inconnu.
     */
            private IDriver createDriverForMachine(Machine machine) {
        if (machine.getDriver() == null || machine.getDriver().getDriver() == null) {
            System.err.println("Driver non dÃƒÂ©fini pour la machine " + machine.getName());
            return null;
        }
        
        String driverName = machine.getDriver().getDriver().toUpperCase();
        
        // On vÃƒÂ©rifie si le nom du driver commence par "S7"
        if (driverName.startsWith("S7")) {
            return new SiemensDriver();
        }

        // TODO: Ajouter d'autres cas pour "MODBUS", "OPCUA", etc.
        
        System.err.println("Driver non supportÃƒÂ© : " + driverName + " pour la machine " + machine.getName());
        return null;
    }

    public void start() {
        System.out.println("DÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©marrage du Machine Manager Service...");
        List<Machine> machines = getMachinesFromDb();
        System.out.println(machines.size() + " machine(s) trouvÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©e(s) dans la base de donnÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©es.");

        // CrÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©ation d'un pool de threads pour gÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©rer les collecteurs
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
            return em.createQuery(("SELECT m FROM Machine m JOIN FETCH m.driver LEFT JOIN FETCH m.tags t LEFT JOIN FETCH t.type"), Machine.class).getResultList();
        } finally {
            em.close();
        }
    }

    public void stop() {
        System.out.println("ArrÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Âªt du Machine Manager Service...");
        if (executorService != null) {
            // Demande ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â  chaque collecteur de s'arrÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Âªter proprement
            activeCollectors.values().forEach(MachineCollector::stop);
            executorService.shutdown(); // N'accepte plus de nouvelles tÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ches
            try {
                // Attend jusqu'ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â  30 secondes que les tÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ches en cours se terminent
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    System.err.println("Des tÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ches n'ont pas pu se terminer, forÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â§age de l'arrÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Âªt.");
                    executorService.shutdownNow(); // Force l'arrÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Âªt des tÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ches
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
        if (emf != null) {
            emf.close();
        }
        System.out.println("Machine Manager Service arrÃƒÆ’Ã†â€™Ãƒâ€šÃ‚ÂªtÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©.");
    }
}




