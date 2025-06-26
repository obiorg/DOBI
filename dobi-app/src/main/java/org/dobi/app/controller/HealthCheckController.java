package org.dobi.app.controller;

import org.dobi.app.service.KafkaHealthCheckService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthCheckController {

    private final KafkaHealthCheckService healthCheckService;

    public HealthCheckController(KafkaHealthCheckService healthCheckService) {
        this.healthCheckService = healthCheckService;
    }

    @GetMapping("/kafka")
    public ResponseEntity<Map<String, Object>> getKafkaHealth() {
        Map<String, Object> health = healthCheckService.checkKafkaConnection();
        HttpStatus status = "UP".equals(health.get("status")) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return new ResponseEntity<>(health, status);
    }
}
