package org.dobi.manager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.dobi.api.IDriver;
import org.dobi.entities.Machine;
import org.dobi.kafka.producer.KafkaProducerService;
import org.dobi.ui.MachineStatusPanel;
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
    private KafkaProducerService kafkaProducerService;
    private final Properties appProperties = new Properties();
    private MachineStatusPanel statusPanel;

    public MachineManagerService(MachineStatusPanel statusPanel) {
        this.emf = Persistence.createEntityManagerFactory("DOBI-PU");
        this.statusPanel = statusPanel;
        loadAppProperties();
        loadDriverProperties();
    }
    
    // Constructeur sans argument pour le premier chargement des machines
    public MachineManagerService() {
         this.emf = Persistence.createEntityManagerFactory("DOBI-PU");
    }
    
    public void initializeKafka() {
        this.kafkaProducerService = new KafkaProducerService(
            appProperties.getProperty("kafka.bootstrap.servers", "localhost:9092"),
            appProperties.getProperty("kafka.topic.tags.data", "dobi.tags.data")
        );
    }

    private void loadAppProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) { System.err.println("ATTENTION: Le fichier application.properties est introuvable !"); return; }
            appProperties.load(input);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void loadDriverProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("drivers.properties")) {
            if (input == null) { System.err.println("ERREUR: Le fichier drivers.properties est introuvable !"); return; }
            driverProperties.load(input);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private IDriver createDriverForMachine(Machine machine) {
        String driverName = machine.getDriver().getDriver();
        String driverClassName = driverProperties.getProperty(driverName);
        if (driverClassName == null) { System.err.println("Aucune classe associee au driver '" + driverName + "'"); return null; }
        try {
            return (IDriver) Class.forName(driverClassName).getConstructor().newInstance();
        } catch (Exception e) { System.err.println("Erreur instanciation driver '" + driverClassName + "'"); return null; }
    }
    
    public void start() {
        System.out.println("Demarrage du Machine Manager Service...");
        List<Machine> machines = getMachinesFromDb();
        System.out.println(machines.size() + " machine(s) trouvee(s).");
        executorService = Executors.newFixedThreadPool(Math.max(1, machines.size()));
        for (Machine machine : machines) {
            IDriver driver = createDriverForMachine(machine);
            if (driver != null) {
                MachineCollector collector = new MachineCollector(machine, driver, kafkaProducerService, this.statusPanel);
                activeCollectors.put(machine.getId(), collector);
                executorService.submit(collector);
            }
        }
    }

    public List<Machine> getMachinesFromDb() {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("SELECT m FROM Machine m JOIN FETCH m.driver LEFT JOIN FETCH m.tags t LEFT JOIN FETCH t.type ty LEFT JOIN FETCH t.memory", Machine.class).getResultList();
        } finally { em.close(); }
    }
    
    public EntityManagerFactory getEmf() { return emf; }
    public String getAppProperty(String key) { return appProperties.getProperty(key); }
    
    public void stop() {
        System.out.println("Arret du Machine Manager Service...");
        if (kafkaProducerService != null) kafkaProducerService.close();
        if (executorService != null) {
            activeCollectors.values().forEach(MachineCollector::stop);
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) executorService.shutdownNow();
            } catch (InterruptedException e) { executorService.shutdownNow(); }
        }
        if (emf != null) emf.close();
        System.out.println("Machine Manager Service arrete.");
    }
}
