package org.dobi.manager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.dobi.entities.Machine;
import java.util.List;

public class MachineManagerService {

    private EntityManagerFactory emf;

    public MachineManagerService() {
        this.emf = Persistence.createEntityManagerFactory("DOBI-PU");
    }

    public void start() {
        System.out.println("Demarrage du Machine Manager Service...");
        List<Machine> machines = getMachinesFromDb();
        System.out.println(machines.size() + " machine(s) trouvee(s) dans la base de donnees.");

        for (Machine machine : machines) {
            System.out.println(" -> Preparation de la machine: " + machine.getName() + " (Driver: " + machine.getDriver().getDriver() + ")");
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
        if (emf != null) {
            emf.close();
        }
        System.out.println("Machine Manager Service arrete.");
    }
}
