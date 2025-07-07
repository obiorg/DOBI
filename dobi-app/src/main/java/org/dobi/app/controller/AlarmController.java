package org.dobi.app.controller;

import org.dobi.app.service.AlarmService; // Importer notre nouveau service
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alarms")
public class AlarmController {

    private final AlarmService alarmService; // Injection du service

    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    public record AlarmDto(
            String alarmId, String timestamp, String message,
            String priority, String deviceName, boolean acknowledged
    ) {}

    @GetMapping("/active-mock")
    public ResponseEntity<List<AlarmDto>> getActiveMockAlarms() {
        // ... (le code existant reste le même)
        List<AlarmDto> mockAlarms = new ArrayList<>();
        mockAlarms.add(new AlarmDto("uuid-1", Instant.now().minusSeconds(60).toString(), "Perte de communication avec le variateur principal", "CRITICAL", "VARIATEUR_POMPE", false));
        mockAlarms.add(new AlarmDto("uuid-2", Instant.now().minusSeconds(300).toString(), "Seuil de température haut atteint sur le moteur A", "HIGH", "AUTOMATE_PRINCIPAL", false));
        return ResponseEntity.ok(mockAlarms);
    }

    // NOUVEL ENDPOINT POUR DÉCLENCHER UNE ALARME EN TEMPS RÉEL
    @PostMapping("/trigger-mock")
    public ResponseEntity<Map<String, Object>> triggerMockAlarm() {
        AlarmDto newAlarm = new AlarmDto(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                "Surchauffe détectée sur le convoyeur B-12",
                "CRITICAL",
                "AUTOMATE_PRINCIPAL",
                false
        );

        // Utilise le service pour diffuser l'alarme à tous les clients
        alarmService.broadcastAlarm(newAlarm);

        return ResponseEntity.ok(Map.of("success", true, "message", "Alarme de test diffusée via WebSocket."));
    }

    @PostMapping("/{alarmId}/acknowledge-mock")
    public ResponseEntity<Map<String, Object>> acknowledgeMockAlarm(@PathVariable String alarmId) {
        // ... (le code existant reste le même)
        return ResponseEntity.ok(Map.of("success", true, "alarmId", alarmId));
    }
}
