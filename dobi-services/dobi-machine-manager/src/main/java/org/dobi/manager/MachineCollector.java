package org.dobi.manager;
import org.dobi.api.IDriver;
import org.dobi.dto.TagData;
import org.dobi.entities.Machine;
import org.dobi.kafka.producer.KafkaProducerService;
public class MachineCollector implements Runnable {
    private final Machine machine;
    private final IDriver driver;
    private volatile boolean running = true;
    private final KafkaProducerService kafkaProducerService;
    private volatile String currentStatus = "Initialisation...";
    private long tagsReadCount = 0;
    public MachineCollector(Machine machine, IDriver driver, KafkaProducerService kps) {
        this.machine = machine; this.driver = driver; this.kafkaProducerService = kps;
    }
    @Override
    public void run() {
        driver.configure(machine);
        while (running) {
            try {
                if (!driver.isConnected()) {
                    updateStatus("Connexion...");
                    if (driver.connect()) { updateStatus("Connecté"); }
                    else { updateStatus("Erreur Connexion"); Thread.sleep(10000); continue; }
                }
                int tagsInCycle = 0;
                if (machine.getTags() != null && !machine.getTags().isEmpty()) {
                    for (org.dobi.entities.Tag tag : machine.getTags()) {
                        if (tag.isActive() && running) {
                           Object value = driver.read(tag);
                           if (value != null) {
                               TagData tagData = new TagData(tag.getId(), tag.getName(), value, System.currentTimeMillis());
                               kafkaProducerService.sendTagData(tagData);
                               tagsInCycle++;
                           }
                        }
                    }
                }
                tagsReadCount += tagsInCycle;
                updateStatus("Connecté (lus: " + tagsReadCount + ")");
                Thread.sleep(5000);
            } catch (InterruptedException e) { running = false; Thread.currentThread().interrupt(); }
        }
        driver.disconnect();
        updateStatus("Déconnecté");
    }
    public void stop() { this.running = false; }
    public long getMachineId() { return machine.getId(); }
    public String getMachineName() { return machine.getName(); }
    public long getTagsReadCount() { return tagsReadCount; }
    public String getCurrentStatus() { return currentStatus; }
    private void updateStatus(String status) { this.currentStatus = status; }
}
