package org.dobi.app.config;

import org.dobi.kafka.consumer.KafkaConsumerService;
import org.dobi.manager.MachineManagerService;
import org.dobi.kafka.manager.KafkaManagerService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
// L'annotation @ComponentScan indique Ã  Spring de scanner les packages spÃ©cifiÃ©s
// pour trouver des composants (@Service, @Component, etc.) Ã  gÃ©rer.
@ComponentScan(basePackages = {"org.dobi.manager", "org.dobi.app.service", "org.dobi.app.controller"})
public class DobiServiceConfiguration {

    @Bean
    public MachineManagerService machineManagerService() {
        return new MachineManagerService();
    }

    @Bean
    public KafkaConsumerService kafkaConsumerService(MachineManagerService machineManagerService) {
        // Initialiser Kafka aprÃ¨s que la config ait Ã©tÃ© chargÃ©e par le machineManager
        machineManagerService.initializeKafka();
        
        return new KafkaConsumerService(
            machineManagerService.getAppProperty("kafka.bootstrap.servers"),
            "dobi-persistence-group",
            machineManagerService.getAppProperty("kafka.topic.tags.data"),
            machineManagerService.getEmf()
        );
    }

    @Bean
    public KafkaManagerService kafkaManagerService() {
        return new KafkaManagerService();
    }
}

