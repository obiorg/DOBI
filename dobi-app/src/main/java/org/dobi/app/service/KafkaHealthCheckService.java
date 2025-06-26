package org.dobi.app.service;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.dobi.manager.MachineManagerService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class KafkaHealthCheckService {

    private final MachineManagerService machineManagerService;

    public KafkaHealthCheckService(MachineManagerService machineManagerService) {
        this.machineManagerService = machineManagerService;
    }

    public Map<String, Object> checkKafkaConnection() {
        String bootstrapServers = machineManagerService.getAppProperty("kafka.bootstrap.servers");
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000"); // Timeout de 5 secondes

        try (AdminClient adminClient = AdminClient.create(props)) {
            ListTopicsResult topics = adminClient.listTopics();
            Set<String> topicNames = topics.names().get(5, TimeUnit.SECONDS);
            
            return Map.of(
                "status", "UP",
                "bootstrap_servers", bootstrapServers,
                "topics", topicNames
            );
        } catch (Exception e) {
            return Map.of(
                "status", "DOWN",
                "bootstrap_servers", bootstrapServers,
                "error", e.getClass().getSimpleName(),
                "message", e.getMessage()
            );
        }
    }
}
