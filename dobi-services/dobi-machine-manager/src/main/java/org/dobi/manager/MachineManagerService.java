package org.dobi.manager;
import jakarta.persistence.*;
import org.dobi.api.IDriver;
import org.dobi.dto.MachineStatusDto;
import org.dobi.entities.Machine;
import org.dobi.kafka.producer.KafkaProducerService;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
public class MachineManagerService {
    private final EntityManagerFactory emf;
    private final Map<Long, MachineCollector> activeCollectors = new HashMap<>();
    private ExecutorService executorService;
    private final Properties driverProperties = new Properties();
    private KafkaProducerService kafkaProducerService;
    private final Properties appProperties = new Properties();
    public MachineManagerService() { this.emf = Persistence.createEntityManagerFactory("DOBI-PU"); loadAppProperties(); loadDriverProperties(); }
    public void initializeKafka() { this.kafkaProducerService = new KafkaProducerService(appProperties.getProperty("kafka.bootstrap.servers", "localhost:9092"), appProperties.getProperty("kafka.topic.tags.data", "dobi.tags.data")); }
    private void loadAppProperties() { try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) { if (input == null) { System.err.println("ATTENTION: application.properties introuvable!"); return; } appProperties.load(input); } catch (Exception ex) { ex.printStackTrace(); } }
    private void loadDriverProperties() { try (InputStream input = getClass().getClassLoader().getResourceAsStream("drivers.properties")) { if (input == null) { System.err.println("ERREUR: drivers.properties introuvable!"); return; } driverProperties.load(input); } catch (Exception ex) { ex.printStackTrace(); } }
    private IDriver createDriverForMachine(Machine machine) { String driverName = machine.getDriver().getDriver(); String driverClassName = driverProperties.getProperty(driverName); if (driverClassName == null) { System.err.println("Aucune classe pour driver '" + driverName + "'"); return null; } try { return (IDriver) Class.forName(driverClassName).getConstructor().newInstance(); } catch (Exception e) { System.err.println("Erreur instanciation driver '" + driverClassName + "'"); return null; } }
    public void start() { List<Machine> machines = getMachinesFromDb(); executorService = Executors.newFixedThreadPool(Math.max(1, machines.size())); for (Machine machine : machines) { IDriver driver = createDriverForMachine(machine); if (driver != null) { MachineCollector collector = new MachineCollector(machine, driver, kafkaProducerService); activeCollectors.put(machine.getId(), collector); executorService.submit(collector); } } }
    public List<Machine> getMachinesFromDb() { EntityManager em = emf.createEntityManager(); try { return em.createQuery("SELECT m FROM Machine m JOIN FETCH m.driver LEFT JOIN FETCH m.tags t LEFT JOIN FETCH t.type ty LEFT JOIN FETCH t.memory", Machine.class).getResultList(); } finally { em.close(); } }
    public EntityManagerFactory getEmf() { return emf; }
    public String getAppProperty(String key) { return appProperties.getProperty(key); }
    public void stop() { if (kafkaProducerService != null) kafkaProducerService.close(); if (executorService != null) { activeCollectors.values().forEach(MachineCollector::stop); executorService.shutdown(); try { if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) executorService.shutdownNow(); } catch (InterruptedException e) { executorService.shutdownNow(); } } if (emf != null) emf.close(); }
    public void restartCollector(long machineId) { MachineCollector oldCollector = activeCollectors.get(machineId); if (oldCollector != null) { oldCollector.stop(); } EntityManager em = emf.createEntityManager(); try { Machine machineToRestart = em.createQuery("SELECT m FROM Machine m JOIN FETCH m.driver WHERE m.id = :id", Machine.class).setParameter("id", machineId).getSingleResult(); IDriver driver = createDriverForMachine(machineToRestart); if (driver != null) { MachineCollector newCollector = new MachineCollector(machineToRestart, driver, kafkaProducerService); activeCollectors.put(machineToRestart.getId(), newCollector); executorService.submit(newCollector); } } catch(Exception e) {System.err.println("Impossible de redemarrer machine " + machineId);} finally { em.close(); } }
    public List<MachineStatusDto> getActiveCollectorDetails() { return activeCollectors.values().stream().map(c -> new MachineStatusDto(c.getMachineId(), c.getMachineName(), c.getCurrentStatus(), c.getTagsReadCount())).collect(Collectors.toList()); }
}
