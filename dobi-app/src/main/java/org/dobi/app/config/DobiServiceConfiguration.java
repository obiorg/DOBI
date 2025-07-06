package org.dobi.app.config;

import org.dobi.kafka.consumer.KafkaConsumerService;
import org.dobi.manager.MachineManagerService;
import org.dobi.kafka.manager.KafkaManagerService;
import org.dobi.influxdb.InfluxDBWriterService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
// L'annotation @ComponentScan indique a Spring de scanner les packages specifies
// pour trouver des composants (@Service, @Component, etc.) a gerer.
@ComponentScan(basePackages = {"org.dobi.manager", "org.dobi.app.service", "org.dobi.app.controller", "org.dobi.influxdb"})
public class DobiServiceConfiguration {

    @Bean
    public MachineManagerService machineManagerService() {
        return new MachineManagerService();
    }

    @Bean
    public KafkaConsumerService kafkaConsumerService(MachineManagerService machineManagerService, InfluxDBWriterService influxDBWriterService) {
        // Initialiser Kafka apres que la config ait ete chargee par le machineManager
        machineManagerService.initializeKafka();

        // Initialiser InfluxDBWriterService
        influxDBWriterService.initialize();

        return new KafkaConsumerService(
                machineManagerService.getAppProperty("kafka.bootstrap.servers"),
                "dobi-persistence-group",
                machineManagerService.getAppProperty("kafka.topic.tags.data"),
                machineManagerService.getEmf(),
                influxDBWriterService
        );
    }

    @Bean
    public KafkaManagerService kafkaManagerService() {
        return new KafkaManagerService();
    }

    @Bean
    public InfluxDBWriterService influxDBWriterService(MachineManagerService machineManagerService) {
        // Recuperer les proprietes InfluxDB depuis application.properties via MachineManagerService
        String influxdbUrl = machineManagerService.getAppProperty("influxdb.url");
        String influxdbToken = machineManagerService.getAppProperty("influxdb.token");
        String influxdbOrg = machineManagerService.getAppProperty("influxdb.org");
        String influxdbBucket = machineManagerService.getAppProperty("influxdb.bucket");

        return new InfluxDBWriterService(influxdbUrl, influxdbToken, influxdbOrg, influxdbBucket);
    }
}
