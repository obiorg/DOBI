package org.dobi.app.config;

import org.dobi.kafka.consumer.KafkaConsumerService;
import org.dobi.manager.MachineManagerService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
// L'annotation @ComponentScan indique à Spring de scanner les packages spécifiés
// pour trouver des composants (@Service, @Component, etc.) à gérer.
@ComponentScan(basePackages = {"org.dobi.manager", "org.dobi.app.service"})
public class DobiServiceConfiguration {

    @Bean
    public MachineManagerService machineManagerService() {
        return new MachineManagerService();
    }

    @Bean
    public KafkaConsumerService kafkaConsumerService(MachineManagerService machineManagerService) {
        // Initialiser Kafka après que la config ait été chargée par le machineManager
        machineManagerService.initializeKafka();
        
        return new KafkaConsumerService(
            machineManagerService.getAppProperty("kafka.bootstrap.servers"),
            "dobi-persistence-group",
            machineManagerService.getAppProperty("kafka.topic.tags.data"),
            machineManagerService.getEmf()
        );
    }
}
