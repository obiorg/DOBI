package org.dobi.manager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.dobi.entities.Machine;
import java.util.List;

public class MachineManagerService {

    private EntityManagerFactory emf;

    public MachineManagerService() {
        // Le nom "DOBI-PU" doit correspondre � celui dans persistence.xml
        this.emf = Persistence.createEntityManagerFactory("DOBI-PU");
    }

    public void start() {
        System.out.println("D�marrage du Machine Manager Service...");
        List<Machine> machines = getMachinesFromDb();
        System.out.println(machines.size() + " machine(s) trouv�e(s) dans la base de donn�es.");

        for (Machine machine : machines) {
            // TODO: Lancer un thread pour chaque machine
            System.out.println(" -> Pr�paration de la machine: " + machine.getName() + " (Driver: " + machine.getDriver().getDriver() + ")");
        }
    }

    public List<Machine> getMachinesFromDb() {
        EntityManager em = emf.createEntityManager();
        try {
            // Requ�te JPQL pour r�cup�rer toutes les machines avec leur driver
            return em.createQuery("SELECT m FROM Machine m JOIN FETCH m.driver", Machine.class).getResultList();
        } finally {
            em.close();
        }
    }

    public void stop() {
        if (emf != null) {
            emf.close();
        }
        System.out.println("Machine Manager Service arr�t�.");
    }
}
