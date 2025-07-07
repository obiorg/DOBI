package org.dobi.app.config;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.dobi.app.service.AlarmService;
import org.dobi.core.ports.AlarmNotifier;
import org.dobi.core.websocket.TagWebSocketController;
import org.dobi.influxdb.InfluxDBReaderService;
import org.dobi.influxdb.InfluxDBWriterService;
import org.dobi.kafka.consumer.KafkaConsumerService;
import org.dobi.kafka.manager.KafkaManagerService;
import org.dobi.logging.LogLevelManager;
import org.dobi.manager.MachineManagerService;
import org.dobi.services.alarm.AlarmEngineService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = {
    "org.dobi.manager",
    "org.dobi.app.service",
    "org.dobi.app.controller",
    "org.dobi.influxdb",
    "org.dobi.core.websocket",
    "org.dobi.services.alarm"
})
public class DobiServiceConfiguration {

    private static final String COMPONENT_NAME = "SPRING-CONFIG";

    /**
     * NOUVEAU BEAN CENTRALISÉ : Crée une instance unique de
     * l'EntityManagerFactory pour toute l'application. Ce bean sera maintenant
     * injectable dans tous les autres services.
     */
    @Bean
    public EntityManagerFactory entityManagerFactory() {
        // La création se fait ici une seule fois.
        return Persistence.createEntityManagerFactory("DOBI-PU");
    }

    @Bean
    public MachineManagerService machineManagerService(EntityManagerFactory emf) {
        return new MachineManagerService(emf);
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

    /**
     * NOUVEAU BEAN : Crée l'instance du moteur d'alarmes. Spring injectera
     * automatiquement l'EntityManagerFactory et l'implémentation de
     * AlarmNotifier (qui est notre AlarmService).
     */
    @Bean
    public AlarmEngineService alarmEngineService(EntityManagerFactory emf, AlarmNotifier alarmNotifier) {
        return new AlarmEngineService(emf, alarmNotifier);
    }

    /**
     * BEAN MIS À JOUR : Le constructeur de KafkaConsumerService a changé.Nous
     * ajoutons AlarmEngineService à la liste des dépendances.
     *
     * @param machineManagerService
     * @param influxDBWriterService
     * @param tagWebSocketController
     * @param alarmEngineService
     * @return
     */
    @Bean
    public KafkaConsumerService kafkaConsumerService(
            MachineManagerService machineManagerService,
            InfluxDBWriterService influxDBWriterService,
            TagWebSocketController tagWebSocketController,
            AlarmEngineService alarmEngineService,
            EntityManagerFactory emf) { // L'EMF est aussi injecté ici

        machineManagerService.initializeKafka();
        influxDBWriterService.initialize();

        return new KafkaConsumerService(
                machineManagerService.getAppProperty("kafka.bootstrap.servers"),
                "dobi-persistence-group",
                machineManagerService.getAppProperty("kafka.topic.tags.data"),
                emf, // On passe le bean EMF partagé
                influxDBWriterService,
                tagWebSocketController,
                alarmEngineService
        );
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
