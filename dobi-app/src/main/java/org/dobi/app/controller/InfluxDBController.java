package org.dobi.app.controller;

import org.dobi.influxdb.InfluxDBReaderService;
import org.dobi.logging.LogLevelManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/influxdb")
public class InfluxDBController {

    private static final String COMPONENT_NAME = "INFLUXDB-CONTROLLER";
    private final InfluxDBReaderService influxDBReaderService;

    public InfluxDBController(InfluxDBReaderService influxDBReaderService) {
        this.influxDBReaderService = influxDBReaderService;
        LogLevelManager.logInfo(COMPONENT_NAME, "InfluxDBController initialisé.");
    }

    /**
     * Récupère l'historique des valeurs pour un tag donné. Exemple: GET
     * /api/v1/influxdb/history?tagName=my_tag&start=-1h Exemple: GET
     * /api/v1/influxdb/history?tagName=my_tag&start=2023-01-01T00:00:00Z&stop=2023-01-01T01:00:00Z
     *
     * @param tagName Le nom du tag.
     * @param start La période de début (ex: "-1h", "2023-01-01T00:00:00Z").
     * @param stop La période de fin (optionnel, par défaut "now()").
     * @return Une liste de points de données (timestamp, value).
     */
    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getTagHistory(
            @RequestParam String tagName,
            @RequestParam String start,
            @RequestParam(required = false) String stop) {
        try {
            LogLevelManager.logDebug(COMPONENT_NAME, "Requête historique pour tag: " + tagName + ", start: " + start + ", stop: " + (stop != null ? stop : "null"));
            List<Map<String, Object>> history = influxDBReaderService.getTagHistory(tagName, start, stop);
            if (history.isEmpty()) {
                LogLevelManager.logInfo(COMPONENT_NAME, "Aucun historique trouvé pour le tag: " + tagName);
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de la récupération de l'historique pour tag " + tagName + ": " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Récupère la dernière valeur connue pour un tag. Exemple: GET
     * /api/v1/influxdb/last?tagName=my_tag
     *
     * @param tagName Le nom du tag.
     * @return La dernière valeur et son timestamp.
     */
    @GetMapping("/last")
    public ResponseEntity<Map<String, Object>> getLastTagValue(@RequestParam String tagName) {
        try {
            LogLevelManager.logDebug(COMPONENT_NAME, "Requête dernière valeur pour tag: " + tagName);
            Map<String, Object> lastValue = influxDBReaderService.getLastTagValue(tagName);
            if (lastValue == null || lastValue.isEmpty()) {
                LogLevelManager.logInfo(COMPONENT_NAME, "Aucune dernière valeur trouvée pour le tag: " + tagName);
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(lastValue);
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de la récupération de la dernière valeur pour tag " + tagName + ": " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Récupère la liste de tous les noms de tags disponibles dans InfluxDB.
     * Exemple: GET /api/v1/influxdb/tags
     *
     * @return Une liste de noms de tags.
     */
    @GetMapping("/tags")
    public ResponseEntity<List<String>> getAvailableTagNames() {
        try {
            LogLevelManager.logDebug(COMPONENT_NAME, "Requête des noms de tags disponibles.");
            List<String> tagNames = influxDBReaderService.getAvailableTagNames();
            if (tagNames.isEmpty()) {
                LogLevelManager.logInfo(COMPONENT_NAME, "Aucun tag trouvé dans InfluxDB.");
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(tagNames);
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de la récupération des noms de tags disponibles: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
