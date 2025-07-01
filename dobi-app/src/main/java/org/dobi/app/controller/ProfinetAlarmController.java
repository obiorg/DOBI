package org.dobi.app.controller;

import org.dobi.manager.MachineManagerService;
import org.dobi.manager.MachineCollector;
import org.dobi.profinet.ProfinetDriver;
import org.dobi.profinet.diagnostic.AlarmHandler;
import org.dobi.profinet.diagnostic.AlarmHandler.*;
import org.dobi.dto.MachineStatusDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/profinet/alarms")
public class ProfinetAlarmController {
    
    @Autowired
    private MachineManagerService machineManagerService;
    
    /**
     * Obtient tous les gestionnaires d'alarmes Profinet actifs
     */
    private List<AlarmHandler> getAllProfinetAlarmHandlers() {
        List<AlarmHandler> alarmHandlers = new ArrayList<>();
        
        try {
            // Récupération de tous les collecteurs actifs
            List<MachineStatusDto> activeStatuses = machineManagerService.getActiveCollectorDetails();
            
            for (MachineStatusDto status : activeStatuses) {
                try {
                    // Recherche du collecteur correspondant par ID de machine
                    MachineCollector collector = findCollectorByMachineId(status.id());
                    
                    if (collector != null && isProfinetCollector(collector)) {
                        ProfinetDriver profinetDriver = (ProfinetDriver) getDriverFromCollector(collector);
                        if (profinetDriver != null && profinetDriver.getAlarmHandler() != null) {
                            alarmHandlers.add(profinetDriver.getAlarmHandler());
                        }
                    }
                } catch (Exception e) {
                    // Log l'erreur mais continue avec les autres collecteurs
                    System.err.println("Erreur lors de la récupération du gestionnaire d'alarmes pour la machine " + 
                                     status.id() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de la récupération des gestionnaires d'alarmes Profinet: " + e.getMessage());
        }
        
        return alarmHandlers;
    }
    
    /**
     * Trouve un collecteur par ID de machine (méthode utilitaire)
     */
    private MachineCollector findCollectorByMachineId(long machineId) {
        try {
            // Utilisation de réflexion pour accéder aux collecteurs privés
            // En production, il serait mieux d'ajouter une méthode publique dans MachineManagerService
            
            java.lang.reflect.Field collectorsField = MachineManagerService.class.getDeclaredField("activeCollectors");
            collectorsField.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            Map<Long, MachineCollector> activeCollectors = (Map<Long, MachineCollector>) collectorsField.get(machineManagerService);
            
            return activeCollectors.get(machineId);
            
        } catch (Exception e) {
            System.err.println("Erreur lors de l'accès aux collecteurs: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Extrait le driver d'un collecteur (méthode utilitaire)
     */
    private Object getDriverFromCollector(MachineCollector collector) {
        try {
            java.lang.reflect.Field driverField = MachineCollector.class.getDeclaredField("driver");
            driverField.setAccessible(true);
            return driverField.get(collector);
        } catch (Exception e) {
            System.err.println("Erreur lors de l'accès au driver: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Vérifie si un collecteur utilise le driver Profinet
     */
    private boolean isProfinetCollector(MachineCollector collector) {
        try {
            Object driver = getDriverFromCollector(collector);
            return driver instanceof ProfinetDriver;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Obtient toutes les alarmes actives de tous les équipements Profinet
     */
    @GetMapping("/active")
    public ResponseEntity<List<ProfinetAlarm>> getActiveAlarms() {
        try {
            List<ProfinetAlarm> allActiveAlarms = new ArrayList<>();
            
            List<AlarmHandler> alarmHandlers = getAllProfinetAlarmHandlers();
            for (AlarmHandler handler : alarmHandlers) {
                allActiveAlarms.addAll(handler.getActiveAlarms());
            }
            
            // Tri par priorité puis par timestamp (plus récentes en premier)
            allActiveAlarms.sort((a1, a2) -> {
                int priorityCompare = Integer.compare(a1.getPriority().getLevel(), a2.getPriority().getLevel());
                if (priorityCompare != 0) {
                    return priorityCompare;
                }
                return a2.getTimestamp().compareTo(a1.getTimestamp());
            });
            
            return ResponseEntity.ok(allActiveAlarms);
            
        } catch (Exception e) {
            System.err.println("Erreur lors de la récupération des alarmes actives: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new ArrayList<>());
        }
    }
    
    /**
     * Obtient toutes les alarmes critiques
     */
    @GetMapping("/critical")
    public ResponseEntity<List<ProfinetAlarm>> getCriticalAlarms() {
        try {
            List<ProfinetAlarm> criticalAlarms = new ArrayList<>();
            
            List<AlarmHandler> alarmHandlers = getAllProfinetAlarmHandlers();
            for (AlarmHandler handler : alarmHandlers) {
                criticalAlarms.addAll(handler.getActiveAlarmsByPriority(AlarmPriority.CRITICAL));
            }
            
            // Tri par timestamp (plus récentes en premier)
            criticalAlarms.sort((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()));
            
            return ResponseEntity.ok(criticalAlarms);
            
        } catch (Exception e) {
            System.err.println("Erreur lors de la récupération des alarmes critiques: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new ArrayList<>());
        }
    }
    
    /**
     * Acquitte une alarme spécifique
     */
    @PostMapping("/{alarmId}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeAlarm(@PathVariable String alarmId) {
        Map<String, Object> response = new HashMap<>();
        boolean success = false;
        
        try {
            List<AlarmHandler> alarmHandlers = getAllProfinetAlarmHandlers();
            
            for (AlarmHandler handler : alarmHandlers) {
                if (handler.acknowledgeAlarm(alarmId)) {
                    success = true;
                    break;
                }
            }
            
            response.put("success", success);
            response.put("alarmId", alarmId);
            response.put("timestamp", System.currentTimeMillis());
            
            if (!success) {
                response.put("message", "Alarme non trouvée ou déjà acquittée");
            }
            
            return success ? ResponseEntity.ok(response) : ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Efface une alarme spécifique
     */
    @PostMapping("/{alarmId}/clear")
    public ResponseEntity<Map<String, Object>> clearAlarm(@PathVariable String alarmId) {
        Map<String, Object> response = new HashMap<>();
        boolean success = false;
        
        try {
            List<AlarmHandler> alarmHandlers = getAllProfinetAlarmHandlers();
            
            for (AlarmHandler handler : alarmHandlers) {
                if (handler.clearAlarm(alarmId)) {
                    success = true;
                    break;
                }
            }
            
            response.put("success", success);
            response.put("alarmId", alarmId);
            response.put("timestamp", System.currentTimeMillis());
            
            if (!success) {
                response.put("message", "Alarme non trouvée ou déjà effacée");
            }
            
            return success ? ResponseEntity.ok(response) : ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Acquitte toutes les alarmes actives de tous les équipements Profinet
     */
    @PostMapping("/acknowledge-all")
    public ResponseEntity<Map<String, Object>> acknowledgeAllAlarms() {
        Map<String, Object> response = new HashMap<>();
        int totalAcknowledged = 0;
        
        try {
            List<AlarmHandler> alarmHandlers = getAllProfinetAlarmHandlers();
            
            for (AlarmHandler handler : alarmHandlers) {
                totalAcknowledged += handler.acknowledgeAllAlarms();
            }
            
            response.put("acknowledgedCount", totalAcknowledged);
            response.put("timestamp", System.currentTimeMillis());
            response.put("handlersProcessed", alarmHandlers.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("acknowledgedCount", totalAcknowledged);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Obtient les statistiques globales d'alarmes Profinet
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getAlarmStatistics() {
        try {
            Map<String, Object> globalStats = new HashMap<>();
            List<AlarmHandler> alarmHandlers = getAllProfinetAlarmHandlers();
            
            // Agrégation des statistiques de tous les gestionnaires
            long totalGenerated = 0;
            long totalAcknowledged = 0;
            long totalCleared = 0;
            long totalActive = 0;
            long totalCritical = 0;
            
            Map<AlarmPriority, Long> globalPriorityCount = new HashMap<>();
            Map<AlarmType, Long> globalTypeCount = new HashMap<>();
            
            for (AlarmHandler handler : alarmHandlers) {
                Map<String, Object> handlerStats = handler.getAlarmStatistics();
                
                totalGenerated += (Long) handlerStats.get("totalGenerated");
                totalAcknowledged += (Long) handlerStats.get("totalAcknowledged");
                totalCleared += (Long) handlerStats.get("totalCleared");
                totalActive += (Long) handlerStats.get("activeCount");
                totalCritical += (Long) handlerStats.get("criticalUnacknowledged");
                
                // Agrégation par priorité
                @SuppressWarnings("unchecked")
                Map<AlarmPriority, Long> priorityCount = (Map<AlarmPriority, Long>) handlerStats.get("byPriority");
                for (Map.Entry<AlarmPriority, Long> entry : priorityCount.entrySet()) {
                    globalPriorityCount.merge(entry.getKey(), entry.getValue(), Long::sum);
                }
                
                // Agrégation par type
                @SuppressWarnings("unchecked")
                Map<AlarmType, Long> typeCount = (Map<AlarmType, Long>) handlerStats.get("byType");
                for (Map.Entry<AlarmType, Long> entry : typeCount.entrySet()) {
                    globalTypeCount.merge(entry.getKey(), entry.getValue(), Long::sum);
                }
            }
            
            // Construction des statistiques globales
            globalStats.put("totalGenerated", totalGenerated);
            globalStats.put("totalAcknowledged", totalAcknowledged);
            globalStats.put("totalCleared", totalCleared);
            globalStats.put("activeCount", totalActive);
            globalStats.put("criticalUnacknowledged", totalCritical);
            globalStats.put("handlersCount", alarmHandlers.size());
            
            // Taux de performance
            double acknowledgmentRate = totalGenerated > 0 ? (double) totalAcknowledged / totalGenerated : 0.0;
            double resolutionRate = totalGenerated > 0 ? (double) totalCleared / totalGenerated : 0.0;
            
            globalStats.put("acknowledgmentRate", acknowledgmentRate);
            globalStats.put("resolutionRate", resolutionRate);
            
            // Répartitions
            globalStats.put("byPriority", globalPriorityCount);
            globalStats.put("byType", globalTypeCount);
            
            return ResponseEntity.ok(globalStats);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Génère un rapport global d'alarmes Profinet
     */
    @GetMapping("/report")
    public ResponseEntity<String> getAlarmReport() {
        try {
            StringBuilder globalReport = new StringBuilder();
            globalReport.append("=== Rapport Global d'Alarmes Profinet ===\n");
            globalReport.append("Généré le: ").append(new java.util.Date()).append("\n\n");
            
            List<AlarmHandler> alarmHandlers = getAllProfinetAlarmHandlers();
            
            if (alarmHandlers.isEmpty()) {
                globalReport.append("Aucun gestionnaire d'alarmes Profinet actif.\n");
                return ResponseEntity.ok()
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .body(globalReport.toString());
            }
            
            // Statistiques globales
            Map<String, Object> globalStats = getAlarmStatistics().getBody();
            if (globalStats != null) {
                globalReport.append("Statistiques globales:\n");
                globalReport.append("  Gestionnaires actifs: ").append(alarmHandlers.size()).append("\n");
                globalReport.append("  Total généré: ").append(globalStats.get("totalGenerated")).append("\n");
                globalReport.append("  Total acquitté: ").append(globalStats.get("totalAcknowledged")).append("\n");
                globalReport.append("  Actives: ").append(globalStats.get("activeCount")).append("\n");
                globalReport.append("  Critiques non acquittées: ").append(globalStats.get("criticalUnacknowledged")).append("\n");
                
                double ackRate = (Double) globalStats.get("acknowledgmentRate");
                double resRate = (Double) globalStats.get("resolutionRate");
                globalReport.append("  Taux d'acquittement: ").append(String.format("%.1f%%", ackRate * 100)).append("\n");
                globalReport.append("  Taux de résolution: ").append(String.format("%.1f%%", resRate * 100)).append("\n\n");
            }
            
            // Rapport par gestionnaire (machine)
            int handlerIndex = 1;
            for (AlarmHandler handler : alarmHandlers) {
                globalReport.append("--- Gestionnaire ").append(handlerIndex++).append(" ---\n");
                globalReport.append(handler.generateAlarmReport());
                globalReport.append("\n");
            }
            
            return ResponseEntity.ok()
                .header("Content-Type", "text/plain; charset=utf-8")
                .body(globalReport.toString());
                
        } catch (Exception e) {
            String errorReport = "Erreur lors de la génération du rapport: " + e.getMessage();
            return ResponseEntity.internalServerError()
                .header("Content-Type", "text/plain; charset=utf-8")
                .body(errorReport);
        }
    }
    
    /**
     * Obtient les alarmes d'un équipement spécifique
     */
    @GetMapping("/device/{deviceName}")
    public ResponseEntity<List<ProfinetAlarm>> getDeviceAlarms(@PathVariable String deviceName) {
        try {
            List<ProfinetAlarm> deviceAlarms = new ArrayList<>();
            
            List<AlarmHandler> alarmHandlers = getAllProfinetAlarmHandlers();
            for (AlarmHandler handler : alarmHandlers) {
                deviceAlarms.addAll(handler.getDeviceAlarms(deviceName));
            }
            
            // Tri par timestamp (plus récentes en premier)
            deviceAlarms.sort((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()));
            
            return ResponseEntity.ok(deviceAlarms);
            
        } catch (Exception e) {
            System.err.println("Erreur lors de la récupération des alarmes de l'équipement " + deviceName + ": " + e.getMessage());
            return ResponseEntity.internalServerError().body(new ArrayList<>());
        }
    }
    
    /**
     * Efface toutes les alarmes d'un équipement spécifique
     */
    @PostMapping("/device/{deviceName}/clear-all")
    public ResponseEntity<Map<String, Object>> clearDeviceAlarms(@PathVariable String deviceName) {
        Map<String, Object> response = new HashMap<>();
        int totalCleared = 0;
        
        try {
            List<AlarmHandler> alarmHandlers = getAllProfinetAlarmHandlers();
            
            for (AlarmHandler handler : alarmHandlers) {
                totalCleared += handler.clearDeviceAlarms(deviceName);
            }
            
            response.put("clearedCount", totalCleared);
            response.put("deviceName", deviceName);
            response.put("timestamp", System.currentTimeMillis());
            response.put("handlersProcessed", alarmHandlers.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("clearedCount", totalCleared);
            response.put("deviceName", deviceName);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Obtient les alarmes par type
     */
    @GetMapping("/type/{alarmType}")
    public ResponseEntity<List<ProfinetAlarm>> getAlarmsByType(@PathVariable String alarmType) {
        try {
            AlarmType type = AlarmType.valueOf(alarmType.toUpperCase());
            List<ProfinetAlarm> typeAlarms = new ArrayList<>();
            
            List<AlarmHandler> alarmHandlers = getAllProfinetAlarmHandlers();
            for (AlarmHandler handler : alarmHandlers) {
                typeAlarms.addAll(handler.getActiveAlarmsByType(type));
            }
            
            // Tri par priorité puis par timestamp
            typeAlarms.sort((a1, a2) -> {
                int priorityCompare = Integer.compare(a1.getPriority().getLevel(), a2.getPriority().getLevel());
                if (priorityCompare != 0) {
                    return priorityCompare;
                }
                return a2.getTimestamp().compareTo(a1.getTimestamp());
            });
            
            return ResponseEntity.ok(typeAlarms);
            
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Type d'alarme invalide: " + alarmType);
            errorResponse.put("validTypes", Arrays.stream(AlarmType.values())
                .map(Enum::name).collect(Collectors.toList()));
            
            return ResponseEntity.badRequest().body(new ArrayList<>());
            
        } catch (Exception e) {
            System.err.println("Erreur lors de la récupération des alarmes par type: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new ArrayList<>());
        }
    }
    
    /**
     * Obtient les alarmes par priorité
     */
    @GetMapping("/priority/{priority}")
    public ResponseEntity<List<ProfinetAlarm>> getAlarmsByPriority(@PathVariable String priority) {
        try {
            AlarmPriority priorityLevel = AlarmPriority.valueOf(priority.toUpperCase());
            List<ProfinetAlarm> priorityAlarms = new ArrayList<>();
            
            List<AlarmHandler> alarmHandlers = getAllProfinetAlarmHandlers();
            for (AlarmHandler handler : alarmHandlers) {
                priorityAlarms.addAll(handler.getActiveAlarmsByPriority(priorityLevel));
            }
            
            // Tri par timestamp (plus récentes en premier)
            priorityAlarms.sort((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()));
            
            return ResponseEntity.ok(priorityAlarms);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ArrayList<>());
            
        } catch (Exception e) {
            System.err.println("Erreur lors de la récupération des alarmes par priorité: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new ArrayList<>());
        }
    }
    
    /**
     * Obtient le résumé des alarmes (dashboard)
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getAlarmsSummary() {
        try {
            Map<String, Object> summary = new HashMap<>();
            
            List<AlarmHandler> alarmHandlers = getAllProfinetAlarmHandlers();
            
            // Compteurs par priorité
            Map<String, Long> priorityCounts = new HashMap<>();
            for (AlarmPriority priority : AlarmPriority.values()) {
                priorityCounts.put(priority.name(), 0L);
            }
            
            // Comptage des alarmes actives
            long totalActive = 0;
            ProfinetAlarm lastAlarm = null;
            
            for (AlarmHandler handler : alarmHandlers) {
                List<ProfinetAlarm> activeAlarms = handler.getActiveAlarms();
                totalActive += activeAlarms.size();
                
                // Comptage par priorité
                for (ProfinetAlarm alarm : activeAlarms) {
                    String priorityKey = alarm.getPriority().name();
                    priorityCounts.merge(priorityKey, 1L, Long::sum);
                    
                    // Recherche de la dernière alarme
                    if (lastAlarm == null || alarm.getTimestamp().isAfter(lastAlarm.getTimestamp())) {
                        lastAlarm = alarm;
                    }
                }
            }
            
            summary.put("totalActive", totalActive);
            summary.put("handlersCount", alarmHandlers.size());
            summary.put("priorityCounts", priorityCounts);
            summary.put("hasCritical", priorityCounts.get("CRITICAL") > 0);
            
            if (lastAlarm != null) {
                Map<String, Object> lastAlarmInfo = new HashMap<>();
                lastAlarmInfo.put("type", lastAlarm.getType().name());
                lastAlarmInfo.put("priority", lastAlarm.getPriority().name());
                lastAlarmInfo.put("message", lastAlarm.getMessage());
                lastAlarmInfo.put("deviceName", lastAlarm.getDeviceName());
                lastAlarmInfo.put("timestamp", lastAlarm.getTimestamp().toString());
                summary.put("lastAlarm", lastAlarmInfo);
            }
            
            summary.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(summary);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}