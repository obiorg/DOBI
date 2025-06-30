package org.dobi.logging;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class LogLevelManager {
    
    public enum LogLevel {
        NONE(0),     // Aucun log
        ERROR(1),    // Seulement les erreurs
        WARN(2),     // Erreurs + warnings
        INFO(3),     // Erreurs + warnings + infos importantes
        DEBUG(4),    // Tout (mode développement)
        TRACE(5);    // Tout + traces détaillées
        
        private final int level;
        
        LogLevel(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
        
        public boolean shouldLog(LogLevel targetLevel) {
            return this.level >= targetLevel.level;
        }
    }
    
    private static final Map<String, LogLevel> driverLogLevels = new ConcurrentHashMap<>();
    private static LogLevel globalLogLevel = LogLevel.INFO; // Par défaut
    
    /**
     * Configure le niveau de log pour un driver spécifique
     */
    public static void setDriverLogLevel(String driverName, LogLevel level) {
        driverLogLevels.put(driverName.toUpperCase(), level);
        System.out.println("[LOG-MANAGER] Niveau " + level + " configuré pour le driver " + driverName);
    }
    
    /**
     * Configure le niveau de log global (tous les drivers)
     */
    public static void setGlobalLogLevel(LogLevel level) {
        globalLogLevel = level;
        System.out.println("[LOG-MANAGER] Niveau global configuré à " + level);
    }
    
    /**
     * Obtient le niveau de log pour un driver
     */
    public static LogLevel getDriverLogLevel(String driverName) {
        return driverLogLevels.getOrDefault(driverName.toUpperCase(), globalLogLevel);
    }
    
    /**
     * Vérifie si un message doit être loggé
     */
    public static boolean shouldLog(String driverName, LogLevel messageLevel) {
        LogLevel currentLevel = getDriverLogLevel(driverName);
        return currentLevel.shouldLog(messageLevel);
    }
    
    /**
     * Log conditionnel pour un driver
     */
    public static void log(String driverName, LogLevel level, String message) {
        if (shouldLog(driverName, level)) {
            String prefix = getLogPrefix(driverName, level);
            System.out.println(prefix + message);
        }
    }
    
    /**
     * Log d'erreur (toujours affiché sauf si NONE)
     */
    public static void logError(String driverName, String message) {
        log(driverName, LogLevel.ERROR, message);
    }
    
    /**
     * Log d'information importante
     */
    public static void logInfo(String driverName, String message) {
        log(driverName, LogLevel.INFO, message);
    }
    
    /**
     * Log de debug (détaillé)
     */
    public static void logDebug(String driverName, String message) {
        log(driverName, LogLevel.DEBUG, message);
    }
    
    /**
     * Log de trace (très détaillé)
     */
    public static void logTrace(String driverName, String message) {
        log(driverName, LogLevel.TRACE, message);
    }
    
    private static String getLogPrefix(String driverName, LogLevel level) {
        return "[" + driverName + "-" + level + "] ";
    }
    
    /**
     * Obtient l'état actuel des niveaux de log
     */
    public static Map<String, Object> getLogStatus() {
        Map<String, Object> status = new ConcurrentHashMap<>();
        status.put("globalLevel", globalLogLevel.toString());
        status.put("driverLevels", new ConcurrentHashMap<>(driverLogLevels));
        return status;
    }
    
    /**
     * Remet tous les niveaux par défaut
     */
    public static void resetToDefaults() {
        driverLogLevels.clear();
        globalLogLevel = LogLevel.INFO;
        System.out.println("[LOG-MANAGER] Niveaux de log remis par défaut");
    }
}