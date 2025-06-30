package org.dobi.app.controller;

import org.dobi.manager.MachineManagerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auto-reload")
public class AutoReloadController {
    
    private final MachineManagerService machineManagerService;
    
    public AutoReloadController(MachineManagerService machineManagerService) {
        this.machineManagerService = machineManagerService;
    }
    
    /**
     * Obtient le statut du monitoring automatique
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAutoReloadStatus() {
        try {
            Map<String, Object> status = machineManagerService.getAutoReloadStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Active/désactive le monitoring automatique
     */
    @PostMapping("/enable")
    public ResponseEntity<Map<String, Object>> setAutoReloadEnabled(@RequestParam boolean enabled) {
        try {
            machineManagerService.setAutoReloadEnabled(enabled);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "enabled", enabled,
                "message", enabled ? "Auto-reload activé" : "Auto-reload désactivé"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Configure l'intervalle de monitoring
     */
    @PostMapping("/interval")
    public ResponseEntity<Map<String, Object>> setAutoReloadInterval(@RequestParam int intervalSeconds) {
        try {
            if (intervalSeconds < 10) {
                return ResponseEntity.badRequest().body(Map.of("error", "L'intervalle minimum est de 10 secondes"));
            }
            
            machineManagerService.setAutoReloadInterval(intervalSeconds);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "intervalSeconds", intervalSeconds,
                "message", "Intervalle configuré à " + intervalSeconds + " secondes"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Force une vérification immédiate des tags
     */
    @PostMapping("/check-now")
    public ResponseEntity<Map<String, Object>> forceTagReloadCheck() {
        try {
            Map<String, Object> result = machineManagerService.forceTagReloadCheck();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Démarre le monitoring automatique
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startAutoReload() {
        try {
            machineManagerService.startTagReloadMonitoring();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Monitoring automatique démarré"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Arrête le monitoring automatique
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopAutoReload() {
        try {
            machineManagerService.stopTagReloadMonitoring();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Monitoring automatique arrêté"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}