package org.dobi.app.controller;

import org.dobi.app.service.ActiveAlarmQueryService;
import org.dobi.dto.ActiveAlarmDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST final pour la gestion des alarmes. Expose les endpoints pour
 * lister et acquitter les alarmes actives.
 */
@RestController
@RequestMapping("/api/v1/alarms")
public class AlarmController {

    private final ActiveAlarmQueryService alarmQueryService;

    public AlarmController(ActiveAlarmQueryService alarmQueryService) {
        this.alarmQueryService = alarmQueryService;
    }

    /**
     * Récupère la liste de toutes les alarmes actuellement actives dans le
     * système.
     *
     * @return Une liste de DTOs contenant les détails de chaque alarme active.
     */
    @GetMapping("/active")
    public ResponseEntity<List<ActiveAlarmDto>> getActiveAlarms() {
        List<ActiveAlarmDto> activeAlarms = alarmQueryService.getActiveAlarms();
        return ResponseEntity.ok(activeAlarms);
    }

    /**
     * Acquitte une alarme active spécifique.
     */
    @PostMapping("/{alarmId}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeAlarm(@PathVariable Long alarmId) {
        boolean success = alarmQueryService.acknowledgeAlarm(alarmId);
        if (success) {
            return ResponseEntity.ok(Map.of("success", true, "alarmId", alarmId));
        } else {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Alarme non trouvée ou déjà acquittée."));
        }
    }

    /**
     * Acquitte en masse toutes les alarmes actives non acquittées.
     */
    @PostMapping("/acknowledge-all")
    public ResponseEntity<Map<String, Object>> acknowledgeAllAlarms() {
        int acknowledgedCount = alarmQueryService.acknowledgeAllAlarms();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "acknowledgedCount", acknowledgedCount
        ));
    }
}
