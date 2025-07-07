package org.dobi.app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/alarms")
public class AlarmController {

    // DTO interne pour la simulation. Dans un cas réel, il viendrait de dobi-core.
    public record AlarmDto(
            String alarmId,
            String timestamp,
            String message,
            String priority,
            String deviceName,
            boolean acknowledged
    ) {}

    @GetMapping("/active-mock")
    public ResponseEntity<List<AlarmDto>> getActiveMockAlarms() {
        List<AlarmDto> mockAlarms = new ArrayList<>();

        mockAlarms.add(new AlarmDto(
                "uuid-1",
                Instant.now().minusSeconds(60).toString(),
                "Perte de communication avec le variateur principal",
                "CRITICAL",
                "VARIATEUR_POMPE",
                false
        ));

        mockAlarms.add(new AlarmDto(
                "uuid-2",
                Instant.now().minusSeconds(300).toString(),
                "Seuil de température haut atteint sur le moteur A",
                "HIGH",
                "AUTOMATE_PRINCIPAL",
                false
        ));
        
        mockAlarms.add(new AlarmDto(
                "uuid-3",
                Instant.now().minusSeconds(1200).toString(),
                "Maintenance préventive requise",
                "MEDIUM",
                "SUPERVISION_WAGO",
                true // Déjà acquittée pour tester l'affichage
        ));

        return ResponseEntity.ok(mockAlarms);
    }

    // Simule l'acquittement d'une alarme
    @PostMapping("/{alarmId}/acknowledge-mock")
    public ResponseEntity<Map<String, Object>> acknowledgeMockAlarm(@PathVariable String alarmId) {
        System.out.println("ACTION: Acquittement de l'alarme (simulation) : " + alarmId);
        return ResponseEntity.ok(Map.of("success", true, "alarmId", alarmId));
    }
}
