package org.dobi.app.config;

import org.dobi.kafka.consumer.KafkaConsumerService;
import org.dobi.manager.MachineManagerService;
import org.dobi.kafka.manager.KafkaManagerService;
import org.dobi.influxdb.InfluxDBWriterService;
import org.dobi.influxdb.InfluxDBReaderService;
import org.dobi.logging.LogLevelManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.dobi.core.websocket.TagWebSocketController; // Ajouté

@Configuration
@ComponentScan(basePackages = {"org.dobi.manager", "org.dobi.app.service", "org.dobi.app.controller", "org.dobi.influxdb"})
public class DobiServiceConfiguration {

    private static final String COMPONENT_NAME = "DOBI-SERVICE-CONFIGURATION";

    @Bean
    public MachineManagerService machineManagerService() {
        return new MachineManagerService();
    }

    @Bean
    public KafkaConsumerService kafkaConsumerService(MachineManagerService machineManagerService, InfluxDBWriterService influxDBWriterService, TagWebSocketController tagWebSocketController) { // Modifié pour injecter TagWebSocketController
        machineManagerService.initializeKafka();

        influxDBWriterService.initialize();

        if (influxDBWriterService.getWriteApiBlocking() == null) {
            LogLevelManager.logError("SPRING-CONFIG", "InfluxDBWriterService n'a pas pu initialiser WriteApiBlocking. Les écritures InfluxDB échoueront.");
        } else {
            LogLevelManager.logInfo("SPRING-CONFIG", "InfluxDBWriterService a initialisé WriteApiBlocking avec succès.");
        }

        return new KafkaConsumerService(
                machineManagerService.getAppProperty("kafka.bootstrap.servers"),
                "dobi-persistence-group",
                machineManagerService.getAppProperty("kafka.topic.tags.data"),
                machineManagerService.getEmf(),
                influxDBWriterService,
                tagWebSocketController // Passé le contrôleur WebSocket au consommateur Kafka
        );
    }

    @Bean
    public KafkaManagerService kafkaManagerService() {
        return new KafkaManagerService();
    }

    @Bean
    public InfluxDBWriterService influxDBWriterService(MachineManagerService machineManagerService) {
        String influxdbUrl = machineManagerService.getAppProperty("influxdb.url");
        String influxdbToken = machineManagerService.getAppProperty("influxdb.token");
        String influxdbOrg = machineManagerService.getAppProperty("influxdb.org");
        String influxdbBucket = machineManagerService.getAppProperty("influxdb.bucket");

        if (influxdbUrl == null || influxdbUrl.isEmpty()
                || influxdbToken == null || influxdbToken.isEmpty()
                || influxdbOrg == null || influxdbOrg.isEmpty()
                || influxdbBucket == null || influxdbBucket.isEmpty()) {

            LogLevelManager.logError("SPRING-CONFIG", "Propriétés InfluxDB manquantes ou vides dans application.properties. Le service InfluxDBWriterService ne sera pas opérationnel.");

            return new InfluxDBWriterService("invalid_url", "invalid_token", "invalid_org", "invalid_bucket") {
                @Override
                public void initialize() {
                    LogLevelManager.logWarn(COMPONENT_NAME, "InfluxDBWriterService (No-Op) : initialize() appelé, mais les propriétés sont manquantes. Pas d'initialisation réelle.");
                }

                @Override
                public void writeTagData(org.dobi.dto.TagData tagData) {
                    LogLevelManager.logWarn(COMPONENT_NAME, "Tentative d'écriture vers un InfluxDBWriterService non initialisé (propriétés manquantes). Données non écrites pour " + tagData.tagName() + ".");
                }

                @Override
                public void close() {
                    /* Ne fait rien */ }
            };
        }

        return new InfluxDBWriterService(influxdbUrl, influxdbToken, influxdbOrg, influxdbBucket);
    }

    @Bean
    public InfluxDBReaderService influxDBReaderService(MachineManagerService machineManagerService) {
        String influxdbUrl = machineManagerService.getAppProperty("influxdb.url");
        String influxdbToken = machineManagerService.getAppProperty("influxdb.token");
        String influxdbOrg = machineManagerService.getAppProperty("influxdb.org");
        String influxdbBucket = machineManagerService.getAppProperty("influxdb.bucket");

        InfluxDBReaderService readerService = new InfluxDBReaderService(influxdbUrl, influxdbToken, influxdbOrg, influxdbBucket);
        readerService.initialize();

        if (readerService.getInfluxDBClient() == null) {
            LogLevelManager.logError("SPRING-CONFIG", "InfluxDBReaderService n'a pas pu initialiser son client. Les lectures InfluxDB échoueront.");
        } else {
            LogLevelManager.logInfo("SPRING-CONFIG", "InfluxDBReaderService a initialisé son client avec succès.");
        }

        return readerService;
    }
}


