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
                System.err.println("ATTENTION: Le fichier application.properties est introuvable ! Utilisation des valeurs par dÃ©faut.");
                return;
            }
            appProperties.load(input);
            System.out.println("Fichier de configuration de l'application chargÃ©.");
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
        String driverName = machine.getDriver().getDriver();
        String driverClassName = driverProperties.getProperty(driverName);
        if (driverClassName == null) {
            System.err.println("Aucune classe Java n'est associee au driver '" + driverName + "' dans drivers.properties.");
            return null;
        }
        try {
            return (IDriver) Class.forName(driverClassName).getConstructor().newInstance();
        } catch (Exception e) {
            System.err.println("Erreur lors de l'instanciation de la classe driver '" + driverClassName + "'");
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
            return em.createQuery(("SELECT m FROM Machine m JOIN FETCH m.driver LEFT JOIN FETCH m.tags t LEFT JOIN FETCH t.type ty LEFT JOIN FETCH t.memory"), Machine.class).getResultList();
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

