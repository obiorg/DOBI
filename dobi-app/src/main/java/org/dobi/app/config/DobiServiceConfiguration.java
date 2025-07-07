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
import org.dobi.core.websocket.TagWebSocketController; // Import correct
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Configuration
// L'annotation @ComponentScan indique a Spring de scanner les packages specifies
// pour trouver des composants (@Service, @Component, etc.) a gerer.
// S'assurer que 'org.dobi.core.websocket' est bien inclus.
@ComponentScan(basePackages = {"org.dobi.manager", "org.dobi.app.service", "org.dobi.app.controller", "org.dobi.influxdb", "org.dobi.core.websocket"})
public class DobiServiceConfiguration {

    private static final String COMPONENT_NAME = "SPRING-CONFIG"; // Pour les logs dans ce fichier

    @Bean
    public MachineManagerService machineManagerService() {
        return new MachineManagerService();
    }

    @Bean
    public KafkaConsumerService kafkaConsumerService(
            MachineManagerService machineManagerService,
            InfluxDBWriterService influxDBWriterService,
            TagWebSocketController tagWebSocketController,
            SimpMessagingTemplate messagingTemplate // <-- AJOUTÉ : Spring va injecter ce bean automatiquement
    ) {
        machineManagerService.initializeKafka();
        influxDBWriterService.initialize();

        if (influxDBWriterService.getWriteApiBlocking() == null) {
            LogLevelManager.logError(COMPONENT_NAME, "InfluxDBWriterService n'a pas pu initialiser WriteApiBlocking.");
        } else {
            LogLevelManager.logInfo(COMPONENT_NAME, "InfluxDBWriterService a initialisé WriteApiBlocking avec succès.");
        }

        // CORRIGÉ : On passe le messagingTemplate au constructeur
        return new KafkaConsumerService(
                machineManagerService.getAppProperty("kafka.bootstrap.servers"),
                "dobi-persistence-group",
                machineManagerService.getAppProperty("kafka.topic.tags.data"),
                machineManagerService.getEmf(),
                influxDBWriterService,
                tagWebSocketController,
                messagingTemplate // <-- AJOUTÉ
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

            LogLevelManager.logError(COMPONENT_NAME, "Propriétés InfluxDB manquantes ou vides dans application.properties. Le service InfluxDBWriterService ne sera pas opérationnel.");

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
            LogLevelManager.logError(COMPONENT_NAME, "InfluxDBReaderService n'a pas pu initialiser son client. Les lectures InfluxDB échoueront.");
        } else {
            LogLevelManager.logInfo(COMPONENT_NAME, "InfluxDBReaderService a initialisé son client avec succès.");
        }

        return readerService;
    }

    // AJOUTÉ : Assurez-vous que WebSocketConfig est bien un bean géré par Spring.
    // Normalement, @Configuration et @EnableWebSocketMessageBroker suffisent,
    // mais si elle n'est pas dans un package scanné par l'application principale,
    // on peut la déclarer explicitement ici ou s'assurer que le ComponentScan
    // de l'application principale (DobiApplication.java) la couvre.
    // Puisque nous avons déplacé WebSocketConfig dans org.dobi.core.websocket,
    // et que dobi-app scanne déjà org.dobi.core.websocket, cela devrait être bon.
    // Si cette erreur persiste, c'est que le problème est plus subtil.
}
