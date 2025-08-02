package org.dobi.app.config;

import jakarta.persistence.EntityManagerFactory;
import org.dobi.influxdb.InfluxDBReaderService;
import org.dobi.influxdb.InfluxDBWriterService;
import org.dobi.kafka.consumer.KafkaConsumerService;
import org.dobi.manager.MachineManagerService;
import org.dobi.core.websocket.TagWebSocketController;
import org.dobi.services.alarm.AlarmEngineService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Configuration
public class DobiManagerConfiguration {

    @Value("${influxdb.url}")
    private String influxdbUrl;

    @Value("${influxdb.token}")
    private String influxdbToken;

    @Value("${influxdb.org}")
    private String influxdbOrg;

    @Value("${influxdb.bucket}")
    private String influxdbBucket;

    @Value("${kafka.bootstrap.servers}")
    private String kafkaBootstrapServers;

    @Value("${kafka.topic.tags.data}")
    private String kafkaTopicTagsData;

    /**
     * Bean pour InfluxDBWriterService
     */
    @Bean
    public InfluxDBWriterService influxDBWriterService() {
        InfluxDBWriterService service = new InfluxDBWriterService(
                influxdbUrl, influxdbToken, influxdbOrg, influxdbBucket);
        service.initialize();
        return service;
    }

    /**
     * Bean pour InfluxDBReaderService
     */
    @Bean
    public InfluxDBReaderService influxDBReaderService() {
        InfluxDBReaderService service = new InfluxDBReaderService(
                influxdbUrl, influxdbToken, influxdbOrg, influxdbBucket);
        service.initialize();
        return service;
    }

    /**
     * Bean pour MachineManagerService - CORRECTION PRINCIPALE
     */
    @Bean
    public MachineManagerService machineManagerService(EntityManagerFactory emf) {
        MachineManagerService service = new MachineManagerService(emf);
        service.initializeKafka(); // Initialisation Kafka
        return service;
    }

    /**
     * Bean pour KafkaManagerService
     */
    @Bean
    public org.dobi.kafka.manager.KafkaManagerService kafkaManagerService() {
        return new org.dobi.kafka.manager.KafkaManagerService();
    }

    /**
     * Bean pour KafkaConsumerService
     */
    @Bean
    public KafkaConsumerService kafkaConsumerService(
            EntityManagerFactory emf,
            InfluxDBWriterService influxDBWriterService,
            TagWebSocketController tagWebSocketController,
            AlarmEngineService alarmEngineService) {

        return new KafkaConsumerService(
                kafkaBootstrapServers,
                "dobi-consumer-group", // Group ID
                kafkaTopicTagsData,
                emf,
                influxDBWriterService,
                tagWebSocketController,
                alarmEngineService
        );
    }
}
