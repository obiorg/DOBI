package org.dobi.manager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.dobi.api.IDriver;
import org.dobi.entities.Machine;
import org.dobi.kafka.producer.KafkaProducerService;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MachineManagerService {

    private final EntityManagerFactory emf;
    private final Map<Long, MachineCollector> activeCollectors = new HashMap<>();
    private ExecutorService executorService;
    private final Properties driverProperties = new Properties();
    private final KafkaProducerService kafkaProducerService;

    public MachineManagerService() {
        this.emf = Persistence.createEntityManagerFactory("DOBI-PU");
        loadDriverProperties();
        this.kafkaProducerService = new KafkaProducerService("localhost:9092"); // TODO: Mettre l'adresse de votre serveur Kafka
    }

    private void loadDriverProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("drivers.properties")) {
            if (input == null) {
                System.err.println("ERREUR: Le fichier drivers.properties est introuvable !");
                return;
            }
            driverProperties.load(input);
            System.out.println("Fichier de configuration des drivers charge avec " + driverProperties.size() + " entrees.");
        } catch (Exception ex) {
            System.err.println("Erreur lors du chargement de drivers.properties");
            ex.printStackTrace();
        }
    }

    private IDriver createDriverForMachine(Machine machine) {
        String driverName = machine.getDriver().getDriver();
        String driverClassName = driverProperties.getProperty(driverName);

        if (driverClassName == null || driverClassName.trim().isEmpty()) {
            System.err.println("Aucune classe Java n'est associee au driver '" + driverName + "' dans drivers.properties.");
            return null;
        }

        try {
            Class<?> driverClass = Class.forName(driverClassName);
            return (IDriver) driverClass.getConstructor().newInstance();
        } catch (Exception e) {
            System.err.println("Erreur lors de l'instanciation de la classe driver '" + driverClassName + "'");
            e.printStackTrace();
            return null;
        }
    }
    
    public void start() {
        System.out.println("Demarrage du Machine Manager Service...");
        List<Machine> machines = getMachinesFromDb();
        System.out.println(machines.size() + " machine(s) trouvee(s) dans la base de donnees.");

        executorService = Executors.newFixedThreadPool(Math.max(1, machines.size()));

        for (Machine machine : machines) {
            IDriver driver = createDriverForMachine(machine);
            if (driver != null) {
                System.out.println(" -> Lancement du collecteur pour la machine: " + machine.getName() + " (Classe: " + driver.getClass().getSimpleName() + ")");
                MachineCollector collector = new MachineCollector(machine, driver, kafkaProducerService);
                activeCollectors.put(machine.getId(), collector);
                executorService.submit(collector);
            }
        }
    }

    public List<Machine> getMachinesFromDb() {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("SELECT m FROM Machine m JOIN FETCH m.driver LEFT JOIN FETCH m.tags t LEFT JOIN FETCH t.type", Machine.class).getResultList();
        } finally {
            em.close();
        }
    }

    public void stop() {
        System.out.println("Arret du Machine Manager Service...");
        if (kafkaProducerService != null) {
            kafkaProducerService.close();
        }
        if (executorService != null) {
            activeCollectors.values().forEach(MachineCollector::stop);
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    System.err.println("Des taches n'ont pas pu se terminer, forçage de l'arret.");
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
        if (emf != null) {
            emf.close();
        }
        System.out.println("Machine Manager Service arrete.");
    }
}
