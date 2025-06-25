package org.dobi.manager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.dobi.api.IDriver;
import org.dobi.dto.MachineStatusDto; // Utilise le DTO depuis dobi-core
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
import java.util.stream.Collectors;

public class MachineManagerService {
    private final EntityManagerFactory emf;
    private final Map<Long, MachineCollector> activeCollectors = new HashMap<>();
    private ExecutorService executorService;
    private final Properties driverProperties = new Properties();
    private KafkaProducerService kafkaProducerService;
    private final Properties appProperties = new Properties();

    public MachineManagerService() {
        this.emf = Persistence.createEntityManagerFactory("DOBI-PU");
        loadAppProperties();
        loadDriverProperties();
    }
    
    public void initializeKafka() {
        this.kafkaProducerService = new KafkaProducerService(appProperties.getProperty("kafka.bootstrap.servers"), appProperties.getProperty("kafka.topic.tags.data"));
    }
    
    private void loadAppProperties() { try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) { if (input == null) { System.err.println("ATTENTION: application.properties introuvable!"); return; } appProperties.load(input); } catch (Exception ex) { ex.printStackTrace(); } }
    private void loadDriverProperties() { try (InputStream input = getClass().getClassLoader().getResourceAsStream("drivers.properties")) { if (input == null) { System.err.println("ERREUR: drivers.properties introuvable!"); return; } driverProperties.load(input); } catch (Exception ex) { ex.printStackTrace(); } }
    private IDriver createDriverForMachine(Machine machine) { String driverName = machine.getDriver().getDriver(); String driverClassName = driverProperties.getProperty(driverName); if (driverClassName == null) { System.err.println("Aucune classe pour driver '" + driverName + "'"); return null; } try { return (IDriver) Class.forName(driverClassName).getConstructor().newInstance(); } catch (Exception e) { System.err.println("Erreur instanciation driver '" + driverClassName + "'"); return null; } }
    
    public void start() {
        List<Machine> machines = getMachinesFromDb();
        executorService = Executors.newFixedThreadPool(Math.max(1, machines.size()));
        for (Machine machine : machines) {
            IDriver driver = createDriverForMachine(machine);
            if (driver != null) {
                MachineCollector collector = new MachineCollector(machine, driver, kafkaProducerService);
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
    
    // --- MÃƒÆ’Ã¢â‚¬Â°THODE AJOUTÃƒÆ’Ã¢â‚¬Â°E ---
    public Machine getMachineFromDb(long machineId) {
        EntityManager em = emf.createEntityManager();
        try {
            // RequÃƒÆ’Ã‚Âªte pour charger une seule machine avec tous ses tags et associations
            return em.createQuery(
                "SELECT m FROM Machine m LEFT JOIN FETCH m.tags t LEFT JOIN FETCH t.type ty LEFT JOIN FETCH t.memory WHERE m.id = :id", Machine.class)
                .setParameter("id", machineId)
                .getSingleResult();
        } catch (Exception e) {
            System.err.println("Impossible de trouver la machine avec l'ID: " + machineId);
            return null;
        } finally {
            em.close();
        }
    }

    public EntityManagerFactory getEmf() { return emf; }
    public String getAppProperty(String key) { return appProperties.getProperty(key); }
    
    public void stop() { 
        if (kafkaProducerService != null) kafkaProducerService.close();
        if (executorService != null) {
            activeCollectors.values().forEach(MachineCollector::stop);
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) executorService.shutdownNow();
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
        if (emf != null) emf.close();
    }
    
    public void restartCollector(long machineId) {
        MachineCollector oldCollector = activeCollectors.get(machineId);
        if (oldCollector != null) oldCollector.stop();
        
        EntityManager em = emf.createEntityManager();
        try {
            Machine machineToRestart = em.createQuery("SELECT m FROM Machine m JOIN FETCH m.driver WHERE m.id = :id", Machine.class)
                .setParameter("id", machineId).getSingleResult();
            IDriver driver = createDriverForMachine(machineToRestart);
            if (driver != null) {
                MachineCollector newCollector = new MachineCollector(machineToRestart, driver, kafkaProducerService);
                activeCollectors.put(machineToRestart.getId(), newCollector);
                executorService.submit(newCollector);
            }
        } catch(Exception e) {
            System.err.println("Impossible de redemarrer machine " + machineId);
        } finally {
            em.close();
        }
    }

    public List<MachineStatusDto> getActiveCollectorDetails() {
        return activeCollectors.values().stream()
            .map(c -> new MachineStatusDto(c.getMachineId(), c.getMachineName(), c.getCurrentStatus(), c.getTagsReadCount()))
            .collect(Collectors.toList());
    }

        public List<org.dobi.entities.PersStandard> getTagHistory(long tagId, int page, int size) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery(
                "SELECT h FROM PersStandard h WHERE h.tag = :tagId ORDER BY h.vStamp DESC", org.dobi.entities.PersStandard.class)
                .setParameter("tagId", tagId)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
        } catch (Exception e) {
            System.err.println("Impossible de récupérer l'historique pour le tag ID: " + tagId);
            return java.util.Collections.emptyList();
        } finally {
            em.close();
        }
    } catch (Exception e) {
            System.err.println("Impossible de rÃƒÂ©cupÃƒÂ©rer l'historique pour le tag ID: " + tagId);
            return java.util.Collections.emptyList();
        } finally {
            em.close();
        }
    }

//    public org.dobi.entities.Machine getMachineFromDb(long machineId) {
//        EntityManager em = emf.createEntityManager();
//        try {
//            return em.createQuery(
//                "SELECT m FROM Machine m LEFT JOIN FETCH m.tags t WHERE m.id = :id", org.dobi.entities.Machine.class)
//                .setParameter("id", machineId)
//                .getSingleResult();
//        } catch (Exception e) {
//            return null;
//        } finally {
//            em.close();
//        }
//    }

    public org.dobi.entities.Tag getTagFromDb(long tagId) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.find(org.dobi.entities.Tag.class, tagId);
        } finally {
            em.close();
        }
    }
}



