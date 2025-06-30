package org.dobi.app.controller;

import org.dobi.logging.LogLevelManager;
import org.dobi.logging.LogLevelManager.LogLevel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/logs")
public class LogController {
    
    /**
     * Obtient l'état actuel des niveaux de log
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getLogStatus() {
        try {
            Map<String, Object> status = LogLevelManager.getLogStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Configure le niveau de log global
     */
    @PostMapping("/global")
    public ResponseEntity<Map<String, Object>> setGlobalLogLevel(@RequestParam String level) {
        try {
            LogLevel logLevel = LogLevel.valueOf(level.toUpperCase());
            LogLevelManager.setGlobalLogLevel(logLevel);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "globalLevel", logLevel.toString(),
                "message", "Niveau global configuré à " + logLevel
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Niveau invalide. Valeurs possibles: NONE, ERROR, WARN, INFO, DEBUG, TRACE"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Configure le niveau de log pour un driver spécifique
     */
    @PostMapping("/driver/{driverName}")
    public ResponseEntity<Map<String, Object>> setDriverLogLevel(
            @PathVariable String driverName, 
            @RequestParam String level) {
        try {
            LogLevel logLevel = LogLevel.valueOf(level.toUpperCase());
            LogLevelManager.setDriverLogLevel(driverName, logLevel);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "driver", driverName,
                "level", logLevel.toString(),
                "message", "Niveau " + logLevel + " configuré pour " + driverName
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Niveau invalide. Valeurs possibles: NONE, ERROR, WARN, INFO, DEBUG, TRACE"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Remet tous les niveaux par défaut
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetLogLevels() {
        try {
            LogLevelManager.resetToDefaults();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Niveaux de log remis par défaut"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Configurations rapides prédéfinies
     */
    @PostMapping("/preset/{preset}")
    public ResponseEntity<Map<String, Object>> applyLogPreset(@PathVariable String preset) {
        try {
            switch (preset.toLowerCase()) {
                case "silent":
                    LogLevelManager.setGlobalLogLevel(LogLevel.ERROR);
                    break;
                case "minimal":
                    LogLevelManager.setGlobalLogLevel(LogLevel.WARN);
                    break;
                case "normal":
                    LogLevelManager.setGlobalLogLevel(LogLevel.INFO);
                    break;
                case "verbose":
                    LogLevelManager.setGlobalLogLevel(LogLevel.DEBUG);
                    break;
                case "debug":
                    LogLevelManager.setGlobalLogLevel(LogLevel.TRACE);
                    break;
                default:
                    return ResponseEntity.badRequest().body(Map.of(
                        "error", "Preset invalide. Valeurs possibles: silent, minimal, normal, verbose, debug"
                    ));
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "preset", preset,
                "message", "Preset '" + preset + "' appliqué"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Obtient la liste des niveaux disponibles
     */
    @GetMapping("/levels")
    public ResponseEntity<Map<String, Object>> getAvailableLevels() {
        return ResponseEntity.ok(Map.of(
            "levels", Map.of(
                "NONE", "Aucun log",
                "ERROR", "Seulement les erreurs",
                "WARN", "Erreurs + warnings", 
                "INFO", "Erreurs + warnings + infos importantes",
                "DEBUG", "Tout (mode développement)",
                "TRACE", "Tout + traces détaillées"
            ),
            "presets", Map.of(
                "silent", "Seulement les erreurs critiques",
                "minimal", "Erreurs et warnings",
                "normal", "Mode standard (par défaut)",
                "verbose", "Mode développement",
                "debug", "Mode debug complet"
            )
        ));
    }
}