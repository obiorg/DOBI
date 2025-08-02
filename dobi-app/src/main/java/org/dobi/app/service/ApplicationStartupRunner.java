package org.dobi.app.service;

import org.dobi.kafka.consumer.KafkaConsumerService;
import org.dobi.manager.MachineManagerService;
import org.dobi.kafka.manager.KafkaManagerService;
import org.dobi.influxdb.InfluxDBReaderService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ApplicationStartupRunner implements CommandLineRunner {

    private final MachineManagerService machineManagerService;
    private final KafkaConsumerService kafkaConsumerService;
    private final KafkaManagerService kafkaManagerService;
    private final InfluxDBReaderService influxDBReaderService;

    public ApplicationStartupRunner(
            MachineManagerService machineManagerService,
            KafkaConsumerService kafkaConsumerService,
            KafkaManagerService kafkaManagerService,
            InfluxDBReaderService influxDBReaderService) {
        this.machineManagerService = machineManagerService;
        this.kafkaConsumerService = kafkaConsumerService;
        this.kafkaManagerService = kafkaManagerService;
        this.influxDBReaderService = influxDBReaderService;
    }

    @Override
    public void run(String... args) {
        System.out.println("--- DEMARRAGE DES SERVICES DOBI EN ARRIERE-PLAN ---");

        // Démarrage du consommateur Kafka dans un thread séparé
        new Thread(kafkaConsumerService).start();

        // Démarrage du gestionnaire de machines (maintenant déjà configuré)
        machineManagerService.start();

        // Ajout d'un hook d'arrêt pour fermer les services proprement
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("--- ARRET DES SERVICES DOBI EN ARRIERE-PLAN ---");
            kafkaConsumerService.stop(); // Le consommateur ferme déjà le writer
            machineManagerService.stop();
            influxDBReaderService.close(); // Fermer le reader InfluxDB
        }));
    }
}
