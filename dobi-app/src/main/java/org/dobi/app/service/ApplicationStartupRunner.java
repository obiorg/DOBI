package org.dobi.app.service;

import org.dobi.kafka.consumer.KafkaConsumerService;
import org.dobi.manager.MachineManagerService;
import org.dobi.kafka.manager.KafkaManagerService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ApplicationStartupRunner implements CommandLineRunner {

    private final MachineManagerService machineManagerService;
    private final KafkaConsumerService kafkaConsumerService;
    private final KafkaManagerService kafkaManagerService;

    public ApplicationStartupRunner(MachineManagerService machineManagerService, KafkaConsumerService kafkaConsumerService, KafkaManagerService kafkaManagerService) {
        this.machineManagerService = machineManagerService;
        this.kafkaConsumerService = kafkaConsumerService;
        this.kafkaManagerService = kafkaManagerService;
    }

    @Override
    public void run(String... args) {
        System.out.println("--- DEMARRAGE DES SERVICES DOBI EN ARRIERE-PLAN ---");
        new Thread(kafkaConsumerService).start();
        machineManagerService.start();
    }
}
