package org.dobi.profinet.diagnostic;

import org.dobi.logging.LogLevelManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AlarmHandler {

    private static final String COMPONENT_NAME = "PROFINET-ALARM";

    // Types d'alarmes Profinet
    public enum AlarmType {
        DEVICE_FAILURE("Défaillance équipement", 1),
        COMMUNICATION_ERROR("Erreur communication", 2),
        CONFIGURATION_ERROR("Erreur configuration", 3),
        NETWORK_ERROR("Erreur réseau", 4),
        CYCLIC_DATA_ERROR("Erreur données cycliques", 5),
        ACYCLIC_DATA_ERROR("Erreur données acycliques", 6),
        DIAGNOSTIC_ALARM("Alarme diagnostic", 7),
        PARAMETER_ERROR("Erreur paramètre", 8),
        MODULE_FAILURE("Défaillance module", 9),
        SLOT_ERROR("Erreur slot", 10);

        private final String description;
        private final int code;

        AlarmType(String description, int code) {
            this.description = description;
            this.code = code;
        }

        public String getDescription() {
            return description;
        }

        public int getCode() {
            return code;
        }
    }

    // Priorités d'alarmes
    public enum AlarmPriority {
        CRITICAL(1, "Critique"),
        HIGH(2, "Haute"),
        MEDIUM(3, "Moyenne"),
        LOW(4, "Basse"),
        INFO(5, "Information");

        private final int level;
        private final String description;

        AlarmPriority(int level, String description) {
            this.level = level;
            this.description = description;
        }

        public int getLevel() {
            return level;
        }

        public String getDescription() {
            return description;
        }
    }

    // États d'alarmes
    public enum AlarmState {
        ACTIVE("Active"),
        ACKNOWLEDGED("Acquittée"),
        CLEARED("Effacée"),
        ARCHIVED("Archivée");

        private final String description;

        AlarmState(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // Classe représentant une alarme Profinet
    public static class ProfinetAlarm {

        private final String id;
        private final String deviceName;
        private final String stationName;
        private final AlarmType type;
        private final AlarmPriority priority;
        private AlarmState state;
        private final LocalDateTime timestamp;
        private LocalDateTime acknowledgedTime;
        private LocalDateTime clearedTime;
        private final String message;
        private final String details;
        private final Map<String, Object> additionalData;

        public ProfinetAlarm(String deviceName, String stationName, AlarmType type,
                AlarmPriority priority, String message) {
            this.id = generateAlarmId();
            this.deviceName = deviceName;
            this.stationName = stationName;
            this.type = type;
            this.priority = priority;
            this.state = AlarmState.ACTIVE;
            this.timestamp = LocalDateTime.now();
            this.message = message;
            this.details = "";
            this.additionalData = new HashMap<>();
        }

        public ProfinetAlarm(String deviceName, String stationName, AlarmType type,
                AlarmPriority priority, String message, String details) {
            this.id = generateAlarmId();
            this.deviceName = deviceName;
            this.stationName = stationName;
            this.type = type;
            this.priority = priority;
            this.state = AlarmState.ACTIVE;
            this.timestamp = LocalDateTime.now();
            this.message = message;
            this.details = details;
            this.additionalData = new HashMap<>();
        }

        private String generateAlarmId() {
            return "ALM_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000);
        }

        // Getters
        public String getId() {
            return id;
        }

        public String getDeviceName() {
            return deviceName;
        }

        public String getStationName() {
            return stationName;
        }

        public AlarmType getType() {
            return type;
        }

        public AlarmPriority getPriority() {
            return priority;
        }

        public AlarmState getState() {
            return state;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public LocalDateTime getAcknowledgedTime() {
            return acknowledgedTime;
        }

        public LocalDateTime getClearedTime() {
            return clearedTime;
        }

        public String getMessage() {
            return message;
        }

        public String getDetails() {
            return details;
        }

        public Map<String, Object> getAdditionalData() {
            return additionalData;
        }

        // Setters pour gestion d'état
        public void acknowledge() {
            if (state == AlarmState.ACTIVE) {
                state = AlarmState.ACKNOWLEDGED;
                acknowledgedTime = LocalDateTime.now();
            }
        }

        public void clear() {
            state = AlarmState.CLEARED;
            clearedTime = LocalDateTime.now();
        }

        public void archive() {
            state = AlarmState.ARCHIVED;
        }

        public void addData(String key, Object value) {
            additionalData.put(key, value);
        }

        @Override
        public String toString() {
            return String.format("ProfinetAlarm{id='%s', device='%s', type=%s, priority=%s, state=%s, message='%s'}",
                    id, deviceName, type, priority, state, message);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ProfinetAlarm that = (ProfinetAlarm) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    // Stockage des alarmes
    private final Map<String, ProfinetAlarm> activeAlarms = new ConcurrentHashMap<>();
    private final Map<String, ProfinetAlarm> historicalAlarms = new ConcurrentHashMap<>();

    // Statistiques
    private long totalAlarmsGenerated = 0;
    private long totalAlarmsAcknowledged = 0;
    private long totalAlarmsCleared = 0;

    // Configuration
    private int maxActiveAlarms = 1000;
    private int maxHistoricalAlarms = 10000;
    private boolean autoAcknowledgeInfo = true;

    public AlarmHandler() {
        LogLevelManager.logInfo(COMPONENT_NAME, "Gestionnaire d'alarmes Profinet initialisé");
    }

    /**
     * Génère une nouvelle alarme
     */
    public ProfinetAlarm generateAlarm(String deviceName, String stationName,
            AlarmType type, AlarmPriority priority, String message) {

        ProfinetAlarm alarm = new ProfinetAlarm(deviceName, stationName, type, priority, message);

        // Ajout d'informations contextuelles
        alarm.addData("source", "DOBI_Profinet_Driver");
        alarm.addData("typeCode", type.getCode());
        alarm.addData("priorityLevel", priority.getLevel());

        // Stockage de l'alarme
        activeAlarms.put(alarm.getId(), alarm);
        totalAlarmsGenerated++;

        // Auto-acquittement des alarmes informationnelles si configuré
        if (autoAcknowledgeInfo && priority == AlarmPriority.INFO) {
            acknowledgeAlarm(alarm.getId());
        }

        // Limitation du nombre d'alarmes actives
        if (activeAlarms.size() > maxActiveAlarms) {
            cleanupOldAlarms();
        }

        LogLevelManager.logError(COMPONENT_NAME, String.format(
                "ALARME [%s] %s - %s: %s (Équipement: %s)",
                priority.name(), type.getDescription(), stationName, message, deviceName));

        return alarm;
    }

    /**
     * Génère une alarme avec détails supplémentaires
     */
    public ProfinetAlarm generateAlarm(String deviceName, String stationName,
            AlarmType type, AlarmPriority priority,
            String message, String details) {

        ProfinetAlarm alarm = new ProfinetAlarm(deviceName, stationName, type, priority, message, details);

        // Ajout d'informations contextuelles
        alarm.addData("source", "DOBI_Profinet_Driver");
        alarm.addData("typeCode", type.getCode());
        alarm.addData("priorityLevel", priority.getLevel());
        alarm.addData("hasDetails", true);

        // Stockage de l'alarme
        activeAlarms.put(alarm.getId(), alarm);
        totalAlarmsGenerated++;

        // Auto-acquittement des alarmes informationnelles si configuré
        if (autoAcknowledgeInfo && priority == AlarmPriority.INFO) {
            acknowledgeAlarm(alarm.getId());
        }

        // Limitation du nombre d'alarmes actives
        if (activeAlarms.size() > maxActiveAlarms) {
            cleanupOldAlarms();
        }

        LogLevelManager.logError(COMPONENT_NAME, String.format(
                "ALARME [%s] %s - %s: %s | Détails: %s (Équipement: %s)",
                priority.name(), type.getDescription(), stationName, message, details, deviceName));

        return alarm;
    }

    /**
     * Acquitte une alarme
     */
    public boolean acknowledgeAlarm(String alarmId) {
        ProfinetAlarm alarm = activeAlarms.get(alarmId);
        if (alarm != null && alarm.getState() == AlarmState.ACTIVE) {
            alarm.acknowledge();
            totalAlarmsAcknowledged++;

            LogLevelManager.logInfo(COMPONENT_NAME, "Alarme acquittée: " + alarmId
                    + " (" + alarm.getMessage() + ")");
            return true;
        }
        return false;
    }

    /**
     * Efface une alarme
     */
    public boolean clearAlarm(String alarmId) {
        ProfinetAlarm alarm = activeAlarms.get(alarmId);
        if (alarm != null) {
            alarm.clear();

            // Déplacement vers l'historique
            historicalAlarms.put(alarmId, alarm);
            activeAlarms.remove(alarmId);
            totalAlarmsCleared++;

            // Limitation de l'historique
            if (historicalAlarms.size() > maxHistoricalAlarms) {
                cleanupHistoricalAlarms();
            }

            LogLevelManager.logInfo(COMPONENT_NAME, "Alarme effacée: " + alarmId
                    + " (" + alarm.getMessage() + ")");
            return true;
        }
        return false;
    }

    /**
     * Efface toutes les alarmes d'un équipement
     */
    public int clearDeviceAlarms(String deviceName) {
        List<String> deviceAlarmIds = activeAlarms.values().stream()
                .filter(alarm -> deviceName.equals(alarm.getDeviceName()))
                .map(ProfinetAlarm::getId)
                .collect(Collectors.toList());

        int clearedCount = 0;
        for (String alarmId : deviceAlarmIds) {
            if (clearAlarm(alarmId)) {
                clearedCount++;
            }
        }

        if (clearedCount > 0) {
            LogLevelManager.logInfo(COMPONENT_NAME, clearedCount + " alarme(s) effacée(s) pour l'équipement: " + deviceName);
        }

        return clearedCount;
    }

    /**
     * Acquitte toutes les alarmes actives
     */
    public int acknowledgeAllAlarms() {
        int acknowledgedCount = 0;

        for (ProfinetAlarm alarm : activeAlarms.values()) {
            if (alarm.getState() == AlarmState.ACTIVE) {
                alarm.acknowledge();
                acknowledgedCount++;
            }
        }

        totalAlarmsAcknowledged += acknowledgedCount;

        if (acknowledgedCount > 0) {
            LogLevelManager.logInfo(COMPONENT_NAME, acknowledgedCount + " alarme(s) acquittée(s) en masse");
        }

        return acknowledgedCount;
    }

    /**
     * Obtient toutes les alarmes actives
     */
    public List<ProfinetAlarm> getActiveAlarms() {
        return new ArrayList<>(activeAlarms.values());
    }

    /**
     * Obtient les alarmes actives par priorité
     */
    public List<ProfinetAlarm> getActiveAlarmsByPriority(AlarmPriority priority) {
        return activeAlarms.values().stream()
                .filter(alarm -> alarm.getPriority() == priority)
                .collect(Collectors.toList());
    }

    /**
     * Obtient les alarmes actives par type
     */
    public List<ProfinetAlarm> getActiveAlarmsByType(AlarmType type) {
        return activeAlarms.values().stream()
                .filter(alarm -> alarm.getType() == type)
                .collect(Collectors.toList());
    }

    /**
     * Obtient les alarmes d'un équipement spécifique
     */
    public List<ProfinetAlarm> getDeviceAlarms(String deviceName) {
        return activeAlarms.values().stream()
                .filter(alarm -> deviceName.equals(alarm.getDeviceName()))
                .collect(Collectors.toList());
    }

    /**
     * Obtient l'historique des alarmes
     */
    public List<ProfinetAlarm> getHistoricalAlarms() {
        return new ArrayList<>(historicalAlarms.values());
    }

    /**
     * Obtient une alarme spécifique
     */
    public ProfinetAlarm getAlarm(String alarmId) {
        ProfinetAlarm alarm = activeAlarms.get(alarmId);
        if (alarm == null) {
            alarm = historicalAlarms.get(alarmId);
        }
        return alarm;
    }

    /**
     * Vérifie s'il y a des alarmes critiques actives
     */
    public boolean hasCriticalAlarms() {
        return activeAlarms.values().stream()
                .anyMatch(alarm -> alarm.getPriority() == AlarmPriority.CRITICAL
                && alarm.getState() == AlarmState.ACTIVE);
    }

    /**
     * Compte les alarmes par état
     */
    public Map<AlarmState, Long> getAlarmCountsByState() {
        return activeAlarms.values().stream()
                .collect(Collectors.groupingBy(
                        ProfinetAlarm::getState,
                        Collectors.counting()
                ));
    }

    /**
     * Obtient des statistiques détaillées
     */
    public Map<String, Object> getAlarmStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // Compteurs généraux
        stats.put("totalGenerated", totalAlarmsGenerated);
        stats.put("totalAcknowledged", totalAlarmsAcknowledged);
        stats.put("totalCleared", totalAlarmsCleared);
        stats.put("activeCount", activeAlarms.size());
        stats.put("historicalCount", historicalAlarms.size());

        // CORRECTION: Utilisation de groupingBy au lieu de collect manuel
        // Répartition par priorité
        Map<AlarmPriority, Long> priorityCount = activeAlarms.values().stream()
                .collect(Collectors.groupingBy(
                        ProfinetAlarm::getPriority,
                        Collectors.counting()
                ));
        stats.put("byPriority", priorityCount);

        // Répartition par type
        Map<AlarmType, Long> typeCount = activeAlarms.values().stream()
                .collect(Collectors.groupingBy(
                        ProfinetAlarm::getType,
                        Collectors.counting()
                ));
        stats.put("byType", typeCount);

        // Répartition par état
        Map<AlarmState, Long> stateCount = activeAlarms.values().stream()
                .collect(Collectors.groupingBy(
                        ProfinetAlarm::getState,
                        Collectors.counting()
                ));
        stats.put("byState", stateCount);

        // Répartition par équipement
        Map<String, Long> deviceCount = activeAlarms.values().stream()
                .collect(Collectors.groupingBy(
                        ProfinetAlarm::getDeviceName,
                        Collectors.counting()
                ));
        stats.put("byDevice", deviceCount);

        // Alarmes critiques non acquittées
        long criticalUnacknowledged = activeAlarms.values().stream()
                .filter(alarm -> alarm.getPriority() == AlarmPriority.CRITICAL
                && alarm.getState() == AlarmState.ACTIVE)
                .count();
        stats.put("criticalUnacknowledged", criticalUnacknowledged);

        // Taux d'acquittement
        double acknowledgmentRate = totalAlarmsGenerated > 0
                ? (double) totalAlarmsAcknowledged / totalAlarmsGenerated : 0.0;
        stats.put("acknowledgmentRate", acknowledgmentRate);

        // Taux de résolution
        double resolutionRate = totalAlarmsGenerated > 0
                ? (double) totalAlarmsCleared / totalAlarmsGenerated : 0.0;
        stats.put("resolutionRate", resolutionRate);

        return stats;
    }

    /**
     * Génère un rapport d'alarmes
     */
    public String generateAlarmReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Rapport d'Alarmes Profinet ===\n");

        Map<String, Object> stats = getAlarmStatistics();

        report.append("Statistiques générales:\n");
        report.append("  Total générées: ").append(stats.get("totalGenerated")).append("\n");
        report.append("  Total acquittées: ").append(stats.get("totalAcknowledged")).append("\n");
        report.append("  Total effacées: ").append(stats.get("totalCleared")).append("\n");
        report.append("  Actives: ").append(stats.get("activeCount")).append("\n");
        report.append("  Critiques non acquittées: ").append(stats.get("criticalUnacknowledged")).append("\n");

        // Taux de performance
        double ackRate = (Double) stats.get("acknowledgmentRate");
        double resRate = (Double) stats.get("resolutionRate");
        report.append("  Taux d'acquittement: ").append(String.format("%.1f%%", ackRate * 100)).append("\n");
        report.append("  Taux de résolution: ").append(String.format("%.1f%%", resRate * 100)).append("\n");

        // Répartition par priorité
        @SuppressWarnings("unchecked")
        Map<AlarmPriority, Long> priorityCount = (Map<AlarmPriority, Long>) stats.get("byPriority");
        if (!priorityCount.isEmpty()) {
            report.append("\nRépartition par priorité:\n");
            for (Map.Entry<AlarmPriority, Long> entry : priorityCount.entrySet()) {
                report.append("  ").append(entry.getKey().getDescription())
                        .append(": ").append(entry.getValue()).append("\n");
            }
        }

        // Répartition par type
        @SuppressWarnings("unchecked")
        Map<AlarmType, Long> typeCount = (Map<AlarmType, Long>) stats.get("byType");
        if (!typeCount.isEmpty()) {
            report.append("\nRépartition par type:\n");
            for (Map.Entry<AlarmType, Long> entry : typeCount.entrySet()) {
                report.append("  ").append(entry.getKey().getDescription())
                        .append(": ").append(entry.getValue()).append("\n");
            }
        }

        // Alarmes actives récentes
        List<ProfinetAlarm> recentAlarms = activeAlarms.values().stream()
                .sorted((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()))
                .limit(10)
                .collect(Collectors.toList());

        if (!recentAlarms.isEmpty()) {
            report.append("\nAlarmes actives récentes:\n");
            for (ProfinetAlarm alarm : recentAlarms) {
                report.append("  [").append(alarm.getPriority().name()).append("] ")
                        .append(alarm.getDeviceName()).append(" - ")
                        .append(alarm.getMessage()).append("\n");
            }
        }

        return report.toString();
    }

    /**
     * Nettoyage des anciennes alarmes actives
     */
    private void cleanupOldAlarms() {
        List<ProfinetAlarm> oldAlarms = activeAlarms.values().stream()
                .filter(alarm -> alarm.getState() == AlarmState.ACKNOWLEDGED)
                .sorted((a1, a2) -> a1.getTimestamp().compareTo(a2.getTimestamp()))
                .limit(100) // Nettoyer les 100 plus anciennes
                .collect(Collectors.toList());

        for (ProfinetAlarm alarm : oldAlarms) {
            alarm.archive();
            historicalAlarms.put(alarm.getId(), alarm);
            activeAlarms.remove(alarm.getId());
        }

        if (!oldAlarms.isEmpty()) {
            LogLevelManager.logDebug(COMPONENT_NAME, "Nettoyage: " + oldAlarms.size()
                    + " alarmes déplacées vers l'historique");
        }
    }

    /**
     * Nettoyage de l'historique des alarmes
     */
    private void cleanupHistoricalAlarms() {
        List<String> oldAlarmIds = historicalAlarms.values().stream()
                .sorted((a1, a2) -> a1.getTimestamp().compareTo(a2.getTimestamp()))
                .limit(1000) // Supprimer les 1000 plus anciennes
                .map(ProfinetAlarm::getId)
                .collect(Collectors.toList());

        for (String alarmId : oldAlarmIds) {
            historicalAlarms.remove(alarmId);
        }

        if (!oldAlarmIds.isEmpty()) {
            LogLevelManager.logDebug(COMPONENT_NAME, "Nettoyage historique: " + oldAlarmIds.size()
                    + " alarmes supprimées");
        }
    }

    /**
     * Configuration du gestionnaire d'alarmes
     */
    public void setMaxActiveAlarms(int maxActiveAlarms) {
        this.maxActiveAlarms = maxActiveAlarms;
        LogLevelManager.logInfo(COMPONENT_NAME, "Limite alarmes actives configurée: " + maxActiveAlarms);
    }

    public void setMaxHistoricalAlarms(int maxHistoricalAlarms) {
        this.maxHistoricalAlarms = maxHistoricalAlarms;
        LogLevelManager.logInfo(COMPONENT_NAME, "Limite historique configurée: " + maxHistoricalAlarms);
    }

    public void setAutoAcknowledgeInfo(boolean autoAcknowledgeInfo) {
        this.autoAcknowledgeInfo = autoAcknowledgeInfo;
        LogLevelManager.logInfo(COMPONENT_NAME, "Auto-acquittement INFO: "
                + (autoAcknowledgeInfo ? "activé" : "désactivé"));
    }

    /**
     * Réinitialise toutes les statistiques
     */
    public void resetStatistics() {
        totalAlarmsGenerated = 0;
        totalAlarmsAcknowledged = 0;
        totalAlarmsCleared = 0;
        LogLevelManager.logInfo(COMPONENT_NAME, "Statistiques d'alarmes réinitialisées");
    }

    /**
     * Efface toutes les alarmes (actives et historiques)
     */
    public void clearAllAlarms() {
        int activeCount = activeAlarms.size();
        int historicalCount = historicalAlarms.size();

        activeAlarms.clear();
        historicalAlarms.clear();

        LogLevelManager.logInfo(COMPONENT_NAME, "Toutes les alarmes effacées: "
                + activeCount + " actives, " + historicalCount + " historiques");
    }
}
