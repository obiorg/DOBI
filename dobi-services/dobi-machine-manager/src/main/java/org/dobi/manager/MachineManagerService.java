package org.dobi.manager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.dobi.api.IDriver;
import org.dobi.entities.Machine;
import org.dobi.kafka.producer.KafkaProducerService;
import org.dobi.ui.MachineStatusPanel; // Correction de l'import
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
        loadDriverProperties(); // Chargement des drivers
    }
    
    // Constructeur sans argument pour le premier chargement
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
            
            // --- AJOUT DE LOG POUR LE DEBUG ---
            System.out.println("--- Contenu de drivers.properties chargÃƒÂ© ---");
            driverProperties.forEach((key, value) -> System.out.println("  -> ClÃƒÂ© lue: '" + key + "' | Classe: '" + value + "'"));
            System.out.println("------------------------------------------");
            
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private IDriver createDriverForMachine(Machine machine) {
        if (machine.getDriver() == null || machine.getDriver().getDriver() == null) {
            System.err.println("Driver non dÃƒÂ©fini pour la machine " + machine.getName());
            return null;
        }

        String driverName = machine.getDriver().getDriver();
        
        // --- AJOUT DE LOG POUR LE DEBUG ---
        System.out.println("Machine '" + machine.getName() + "': Recherche du driver pour la clÃƒÂ©: '" + driverName + "'");
        
        String driverClassName = driverProperties.getProperty(driverName);
        if (driverClassName == null) { 
            System.err.println("--> ECHEC: Aucune classe associÃƒÂ©e au driver '" + driverName + "'"); 
            return null; 
        }
        
        System.out.println("--> SUCCES: Classe trouvÃƒÂ©e: '" + driverClassName + "'");
        
        try {
            return (IDriver) Class.forName(driverClassName).getConstructor().newInstance();
        } catch (Exception e) { 
            System.err.println("--> ERREUR: Erreur d'instanciation du driver '" + driverClassName + "'"); 
            e.printStackTrace();
            return null;
        }
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
            return em.createQuery(("SELECT m FROM Machine m JOIN FETCH m.driver JOIN FETCH m.company LEFT JOIN FETCH m.tags t LEFT JOIN FETCH t.type ty LEFT JOIN FETCH t.memory"), Machine.class).getResultList();
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


    public void restartCollector(long machineId) {
        System.out.println("Demande de redémarrage pour la machine ID: " + machineId);
        
        // Arrêter l'ancien collecteur s'il existe
        MachineCollector oldCollector = activeCollectors.get(machineId);
        if (oldCollector != null) {
            oldCollector.stop();
        }

        // Retrouver l'objet Machine
        EntityManager em = emf.createEntityManager();
        Machine machineToRestart;
        try {
            machineToRestart = em.createQuery(
                "SELECT m FROM Machine m JOIN FETCH m.driver WHERE m.id = :id", Machine.class)
                .setParameter("id", machineId)
                .getSingleResult();
        } catch (Exception e) {
            System.err.println("Impossible de retrouver la machine avec l'ID " + machineId + " pour la redémarrer.");
            return;
        } finally {
            em.close();
        }
        
        // Lancer un nouveau collecteur
        IDriver driver = createDriverForMachine(machineToRestart);
        if (driver != null) {
            System.out.println(" -> Relance du collecteur pour la machine: " + machineToRestart.getName());
            MachineCollector newCollector = new MachineCollector(machineToRestart, driver, kafkaProducerService, this.statusPanel);
            activeCollectors.put(machineToRestart.getId(), newCollector);
            executorService.submit(newCollector);
        }
    }
}


