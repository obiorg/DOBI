package org.dobi.manager;

import org.dobi.api.IDriver;
import org.dobi.dto.TagData;
import org.dobi.entities.Machine;
import org.dobi.kafka.producer.KafkaProducerService;
import org.dobi.app.ui.MachineStatusPanel;
import java.awt.Color;

public class MachineCollector implements Runnable {
    private final Machine machine;
    private final IDriver driver;
    private volatile boolean running = true;
    private final KafkaProducerService kafkaProducerService;
    private final MachineStatusPanel statusPanel;

    public MachineCollector(Machine machine, IDriver driver, KafkaProducerService kps, MachineStatusPanel sp) {
        this.machine = machine;
        this.driver = driver;
        this.kafkaProducerService = kps;
        this.statusPanel = sp;
    }

    @Override
    public void run() {
        driver.configure(machine);
        while (running) {
            try {
                if (!driver.isConnected()) {
                    statusPanel.updateMachineStatus(machine.getId(), "Connexion...", Color.ORANGE);
                    if (driver.connect()) {
                        statusPanel.updateMachineStatus(machine.getId(), "Connecte", Color.GREEN);
                    } else {
                        statusPanel.updateMachineStatus(machine.getId(), "Erreur Connexion", Color.RED);
                        Thread.sleep(10000);
                        continue;
                    }
                }
                int tagsRead = 0;
                if (machine.getTags() != null && !machine.getTags().isEmpty()) {
                    for (org.dobi.entities.Tag tag : machine.getTags()) {
                        if (tag.isActive() && running) {
                           Object value = driver.read(tag);
                           if (value != null) {
                               TagData tagData = new TagData(tag.getId(), tag.getName(), value, System.currentTimeMillis());
                               kafkaProducerService.sendTagData(tagData);
                               tagsRead++;
                           }
                        }
                    }
                }
                statusPanel.updateMachineStatus(machine.getId(), "Connecte (lus: " + tagsRead + ")", Color.GREEN);
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                running = false;
                Thread.currentThread().interrupt();
            }
        }
        driver.disconnect();
        statusPanel.updateMachineStatus(machine.getId(), "Deconnecte", Color.DARK_GRAY);
    }
    public void stop() { this.running = false; }
}
