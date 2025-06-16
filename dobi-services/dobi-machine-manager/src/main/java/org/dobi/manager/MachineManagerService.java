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
    private final Properties appProperties = new Properties();

    public MachineManagerService() {
        // 1. Charger la configuration de l'application (Kafka, etc.)
        loadAppProperties();
        
        // 2. Initialiser les services avec cette configuration
        this.emf = Persistence.createEntityManagerFactory("DOBI-PU");
        this.kafkaProducerService = new KafkaProducerService(
            appProperties.getProperty("kafka.bootstrap.servers", "localhost:9092"),
            appProperties.getProperty("kafka.topic.tags.data", "dobi.tags.data")
        );
        
        // 3. Charger la configuration des drivers
        loadDriverProperties();
    }
    
    private void loadAppProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                System.err.println("ATTENTION: Le fichier application.properties est introuvable ! Utilisation des valeurs par dÃƒÂ©faut.");
                return;
            }
            appProperties.load(input);
            System.out.println("Fichier de configuration de l'application chargÃƒÂ©.");
        } catch (Exception ex) {
            System.err.println("Erreur lors du chargement de application.properties");
            ex.printStackTrace();
        }
    }

    private void loadDriverProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("drivers.properties")) {
            if (input == null) {
                System.err.println("ERREUR: Le fichier drivers.properties est introuvable !");
                return;
            }
            driverProperties.load(input);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private IDriver createDriverForMachine(Machine machine) {
        if (machine.getDriver() == null || machine.getDriver().getDriver() == null) {
            System.err.println("Driver non dÃƒÂ©fini pour la machine " + machine.getName());
            return null;
        }

        String driverName = machine.getDriver().getDriver();
        String driverClassName = driverProperties.getProperty(driverName);

        if (driverClassName == null || driverClassName.trim().isEmpty()) {
            System.err.println("Aucune classe Java n'est associee au driver '" + driverName + "' dans drivers.properties.");
            return null;
        }

        try {
            // Utilisation de la rÃƒÂ©flexion pour instancier la classe de driver
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
                System.out.println(" -> Lancement du collecteur pour la machine: " + machine.getName());
                MachineCollector collector = new MachineCollector(machine, driver, kafkaProducerService);
                activeCollectors.put(machine.getId(), collector);
                executorService.submit(collector);
            }
        }
    }

    public List<Machine> getMachinesFromDb() {
        EntityManager em = emf.createEntityManager();
        try {
            // RequÃƒÂªte pour charger les machines avec leurs tags, types et mÃƒÂ©moires
            return em.createQuery(("SELECT m FROM Machine m JOIN FETCH m.driver JOIN FETCH m.company LEFT JOIN FETCH m.tags t LEFT JOIN FETCH t.type ty LEFT JOIN FETCH t.memory"), Machine.class).getResultList();
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
                    System.err.println("Des taches n'ont pas pu se terminer, forÃƒÂ§age de l'arret.");
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

    public EntityManagerFactory getEmf() { return emf; }
    public String getAppProperty(String key) { return appProperties.getProperty(key); }
}


