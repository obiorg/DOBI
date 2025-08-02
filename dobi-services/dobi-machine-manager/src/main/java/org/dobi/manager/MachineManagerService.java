package org.dobi.manager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.dobi.api.IDriver;
import org.dobi.dto.MachineStatusDto;
import org.dobi.entities.Machine;
import org.dobi.kafka.producer.KafkaProducerService;
import org.dobi.profinet.ProfinetDriver;
import org.dobi.profinet.diagnostic.AlarmHandler;
import org.dobi.profinet.diagnostic.AlarmHandler.ProfinetAlarm;
import org.dobi.logging.LogLevelManager;
import org.springframework.stereotype.Service; // <-- AJOUT IMPORTANT

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service // <-- CETTE LIGNE EST LA SOLUTION
public class MachineManagerService {

    private static final String COMPONENT_NAME = "MACHINE-MANAGER";

    private final EntityManagerFactory emf;
    private final Map<Long, MachineCollector> activeCollectors = new HashMap<>();
    private ExecutorService executorService;
    private final Properties driverProperties = new Properties();
    private KafkaProducerService kafkaProducerService;
    private final Properties appProperties = new Properties();

    // Auto-reload des tags
    private Timer tagReloadTimer;
    private boolean autoReloadEnabled = true;
    private int autoReloadIntervalSeconds = 30;
    private final Map<Long, Integer> lastKnownTagCounts = new HashMap<>();
    private final Map<Long, Long> lastTagReloadTime = new HashMap<>();

    // Gestion des alarmes Profinet
    private final Map<Long, AlarmHandler> machineAlarmHandlers = new HashMap<>();
    private Timer alarmMonitoringTimer;
    private boolean alarmMonitoringEnabled = true;
    private int alarmCheckIntervalSeconds = 15; // Vérification alarmes toutes les 15 secondes

    /**
     * CONSTRUCTEUR MIS À JOUR : Ne crée plus l'EntityManagerFactory, mais le
     * reçoit par injection de dépendances.
     */
    public MachineManagerService(EntityManagerFactory emf) {
        this.emf = emf; // Reçoit l'instance partagée
        loadAppProperties();
        loadDriverProperties();
        LogLevelManager.logInfo(COMPONENT_NAME, "MachineManagerService initialisé.");
    }

    public void initializeKafka() {
        this.kafkaProducerService = new KafkaProducerService(
                appProperties.getProperty("kafka.bootstrap.servers"),
                appProperties.getProperty("kafka.topic.tags.data")
        );
        LogLevelManager.logInfo(COMPONENT_NAME, "Services Kafka initialisés");
    }

    private void loadAppProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                LogLevelManager.logError(COMPONENT_NAME, "ATTENTION: application.properties introuvable!");
                return;
            }
            appProperties.load(input);
            LogLevelManager.logDebug(COMPONENT_NAME, "Propriétés application chargées");
        } catch (Exception ex) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur chargement application.properties: " + ex.getMessage());
        }
    }

    private void loadDriverProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("drivers.properties")) {
            if (input == null) {
                LogLevelManager.logError(COMPONENT_NAME, "ERREUR: drivers.properties introuvable!");
                return;
            }
            driverProperties.load(input);
            LogLevelManager.logDebug(COMPONENT_NAME, "Propriétés drivers chargées");
        } catch (Exception ex) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur chargement drivers.properties: " + ex.getMessage());
        }
    }

    private IDriver createDriverForMachine(Machine machine) {
        String driverName = machine.getDriver().getDriver();
        String driverClassName = driverProperties.getProperty(driverName);

        if (driverClassName == null) {
            LogLevelManager.logError(COMPONENT_NAME, "Aucune classe pour driver '" + driverName + "'");
            return null;
        }

        try {
            IDriver driver = (IDriver) Class.forName(driverClassName).getConstructor().newInstance();
            LogLevelManager.logDebug(COMPONENT_NAME, "Driver créé: " + driverClassName + " pour machine: " + machine.getName());
            return driver;
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur instanciation driver '" + driverClassName + "': " + e.getMessage());
            return null;
        }
    }

    public void start() {
        List<Machine> machines = getMachinesFromDb();
        executorService = Executors.newFixedThreadPool(Math.max(1, machines.size()));

        LogLevelManager.logInfo(COMPONENT_NAME, "Démarrage de " + machines.size() + " collecteur(s)");

        for (Machine machine : machines) {
            IDriver driver = createDriverForMachine(machine);
            if (driver != null) {
                MachineCollector collector = new MachineCollector(machine, driver, kafkaProducerService);
                activeCollectors.put(machine.getId(), collector);

                // Initialiser le compteur de tags
                lastKnownTagCounts.put(machine.getId(),
                        machine.getTags() != null ? machine.getTags().size() : 0);
                lastTagReloadTime.put(machine.getId(), System.currentTimeMillis());

                // Si c'est un driver Profinet, enregistrer son gestionnaire d'alarmes
                if (driver instanceof ProfinetDriver) {
                    ProfinetDriver profinetDriver = (ProfinetDriver) driver;
                    AlarmHandler alarmHandler = profinetDriver.getAlarmHandler();
                    if (alarmHandler != null) {
                        machineAlarmHandlers.put(machine.getId(), alarmHandler);
                        LogLevelManager.logInfo(COMPONENT_NAME, "Gestionnaire d'alarmes enregistré pour machine Profinet: " + machine.getName());
                    }
                }

                executorService.submit(collector);
            }
        }

        // Démarrer le monitoring automatique
        if (autoReloadEnabled) {
            startTagReloadMonitoring();
        }

        // Démarrer le monitoring des alarmes
        if (alarmMonitoringEnabled) {
            startAlarmMonitoring();
        }

        LogLevelManager.logInfo(COMPONENT_NAME, "Tous les collecteurs démarrés avec monitoring activé");
    }

    /**
     * Démarre le monitoring automatique des tags
     */
    public void startTagReloadMonitoring() {
        if (tagReloadTimer != null) {
            tagReloadTimer.cancel();
        }

        tagReloadTimer = new Timer("TagAutoReloader", true);
        tagReloadTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (autoReloadEnabled) {
                    checkForTagUpdates();
                }
            }
        }, autoReloadIntervalSeconds * 1000L, autoReloadIntervalSeconds * 1000L);

        LogLevelManager.logInfo(COMPONENT_NAME, "Monitoring des tags démarré (intervalle: " + autoReloadIntervalSeconds + "s)");
    }

    /**
     * Démarre le monitoring des alarmes Profinet
     */
    public void startAlarmMonitoring() {
        if (alarmMonitoringTimer != null) {
            alarmMonitoringTimer.cancel();
        }

        alarmMonitoringTimer = new Timer("AlarmMonitor", true);
        alarmMonitoringTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (alarmMonitoringEnabled) {
                    performAlarmMonitoring();
                }
            }
        }, alarmCheckIntervalSeconds * 1000L, alarmCheckIntervalSeconds * 1000L);

        LogLevelManager.logInfo(COMPONENT_NAME, "Monitoring des alarmes démarré (intervalle: " + alarmCheckIntervalSeconds + "s)");
    }

    /**
     * Arrête le monitoring automatique des tags
     */
    public void stopTagReloadMonitoring() {
        autoReloadEnabled = false;
        if (tagReloadTimer != null) {
            tagReloadTimer.cancel();
            tagReloadTimer = null;
        }
        LogLevelManager.logInfo(COMPONENT_NAME, "Monitoring des tags arrêté");
    }

    /**
     * Arrête le monitoring des alarmes
     */
    public void stopAlarmMonitoring() {
        alarmMonitoringEnabled = false;
        if (alarmMonitoringTimer != null) {
            alarmMonitoringTimer.cancel();
            alarmMonitoringTimer = null;
        }
        LogLevelManager.logInfo(COMPONENT_NAME, "Monitoring des alarmes arrêté");
    }

    /**
     * Monitoring périodique des alarmes
     */
    private void performAlarmMonitoring() {
        try {
            LogLevelManager.logTrace(COMPONENT_NAME, "Vérification des alarmes Profinet...");

            int totalActiveAlarms = 0;
            int totalCriticalAlarms = 0;

            for (Map.Entry<Long, AlarmHandler> entry : machineAlarmHandlers.entrySet()) {
                Long machineId = entry.getKey();
                AlarmHandler alarmHandler = entry.getValue();

                MachineCollector collector = activeCollectors.get(machineId);
                if (collector == null) {
                    continue;
                }

                String machineName = collector.getMachineName();

                try {
                    // Statistiques des alarmes
                    Map<String, Object> alarmStats = alarmHandler.getAlarmStatistics();
                    long activeAlarms = (Long) alarmStats.get("activeCount");
                    long criticalAlarms = (Long) alarmStats.get("criticalUnacknowledged");

                    totalActiveAlarms += activeAlarms;
                    totalCriticalAlarms += criticalAlarms;

                    // Log si alarmes critiques
                    if (criticalAlarms > 0) {
                        LogLevelManager.logError(COMPONENT_NAME,
                                "ALARMES CRITIQUES sur " + machineName + ": " + criticalAlarms + " alarme(s)");

                        // Détail des alarmes critiques
                        List<ProfinetAlarm> criticalAlarmsList = alarmHandler.getActiveAlarmsByPriority(
                                AlarmHandler.AlarmPriority.CRITICAL);

                        for (ProfinetAlarm alarm : criticalAlarmsList) {
                            LogLevelManager.logError(COMPONENT_NAME,
                                    "  🚨 " + alarm.getType().getDescription() + ": " + alarm.getMessage());
                        }
                    }

                    // Log périodique si alarmes actives
                    if (activeAlarms > 0) {
                        LogLevelManager.logDebug(COMPONENT_NAME,
                                "Machine " + machineName + ": " + activeAlarms + " alarme(s) active(s)");
                    }

                } catch (Exception e) {
                    LogLevelManager.logError(COMPONENT_NAME,
                            "Erreur monitoring alarmes pour " + machineName + ": " + e.getMessage());
                }
            }

            // Log global si alarmes
            if (totalCriticalAlarms > 0) {
                LogLevelManager.logError(COMPONENT_NAME,
                        "🚨 TOTAL ALARMES CRITIQUES: " + totalCriticalAlarms + " sur " + machineAlarmHandlers.size() + " machine(s) Profinet");
            } else if (totalActiveAlarms > 0) {
                LogLevelManager.logInfo(COMPONENT_NAME,
                        "Alarmes actives totales: " + totalActiveAlarms + " sur " + machineAlarmHandlers.size() + " machine(s) Profinet");
            }

        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur lors du monitoring des alarmes: " + e.getMessage());
        }
    }

    /**
     * Configure l'intervalle de monitoring automatique
     */
    public void setAutoReloadInterval(int intervalSeconds) {
        this.autoReloadIntervalSeconds = Math.max(10, intervalSeconds); // Minimum 10 secondes
        if (autoReloadEnabled && tagReloadTimer != null) {
            startTagReloadMonitoring(); // Redémarrer avec le nouvel intervalle
        }
        LogLevelManager.logInfo(COMPONENT_NAME, "Intervalle auto-reload configuré à " + this.autoReloadIntervalSeconds + " secondes");
    }

    /**
     * Configure l'intervalle de monitoring des alarmes
     */
    public void setAlarmMonitoringInterval(int intervalSeconds) {
        this.alarmCheckIntervalSeconds = Math.max(5, intervalSeconds); // Minimum 5 secondes
        if (alarmMonitoringEnabled && alarmMonitoringTimer != null) {
            startAlarmMonitoring(); // Redémarrer avec le nouvel intervalle
        }
        LogLevelManager.logInfo(COMPONENT_NAME, "Intervalle monitoring alarmes configuré à " + this.alarmCheckIntervalSeconds + " secondes");
    }

    /**
     * Active/désactive le monitoring automatique
     */
    public void setAutoReloadEnabled(boolean enabled) {
        this.autoReloadEnabled = enabled;
        if (enabled) {
            startTagReloadMonitoring();
        } else {
            stopTagReloadMonitoring();
        }
        LogLevelManager.logInfo(COMPONENT_NAME, "Auto-reload " + (enabled ? "activé" : "désactivé"));
    }

    /**
     * Active/désactive le monitoring des alarmes
     */
    public void setAlarmMonitoringEnabled(boolean enabled) {
        this.alarmMonitoringEnabled = enabled;
        if (enabled) {
            startAlarmMonitoring();
        } else {
            stopAlarmMonitoring();
        }
        LogLevelManager.logInfo(COMPONENT_NAME, "Monitoring alarmes " + (enabled ? "activé" : "désactivé"));
    }

    /**
     * Force la vérification immédiate des mises à jour de tags
     */
    public Map<String, Object> forceTagReloadCheck() {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", System.currentTimeMillis());
        result.put("machinesChecked", activeCollectors.size());

        List<String> updatedMachines = new ArrayList<>();

        for (Map.Entry<Long, MachineCollector> entry : activeCollectors.entrySet()) {
            Long machineId = entry.getKey();
            MachineCollector collector = entry.getValue();

            try {
                Machine currentMachine = getMachineFromDb(machineId);
                if (currentMachine != null) {
                    int currentTagCount = currentMachine.getTags() != null ? currentMachine.getTags().size() : 0;
                    int lastKnownCount = lastKnownTagCounts.getOrDefault(machineId, 0);

                    if (currentTagCount != lastKnownCount) {
                        LogLevelManager.logInfo(COMPONENT_NAME, "Tags mis à jour pour " + currentMachine.getName()
                                + " (" + lastKnownCount + " → " + currentTagCount + " tags)");

                        collector.updateMachine(currentMachine);
                        lastKnownTagCounts.put(machineId, currentTagCount);
                        lastTagReloadTime.put(machineId, System.currentTimeMillis());
                        updatedMachines.add(currentMachine.getName());
                    }
                }
            } catch (Exception e) {
                LogLevelManager.logError(COMPONENT_NAME, "Erreur force reload pour machine " + machineId + ": " + e.getMessage());
            }
        }

        result.put("updatedMachines", updatedMachines);
        result.put("updatedCount", updatedMachines.size());

        return result;
    }

    /**
     * Vérification automatique des mises à jour de tags
     */
    private void checkForTagUpdates() {
        for (Map.Entry<Long, MachineCollector> entry : activeCollectors.entrySet()) {
            Long machineId = entry.getKey();
            MachineCollector collector = entry.getValue();

            try {
                Machine currentMachine = getMachineFromDb(machineId);
                if (currentMachine != null) {
                    int currentTagCount = currentMachine.getTags() != null ? currentMachine.getTags().size() : 0;
                    int lastKnownCount = lastKnownTagCounts.getOrDefault(machineId, 0);

                    if (currentTagCount != lastKnownCount) {
                        LogLevelManager.logInfo(COMPONENT_NAME, "Nouveaux tags détectés pour " + currentMachine.getName()
                                + " (" + lastKnownCount + " → " + currentTagCount + " tags)");

                        collector.updateMachine(currentMachine);
                        lastKnownTagCounts.put(machineId, currentTagCount);
                        lastTagReloadTime.put(machineId, System.currentTimeMillis());
                    }
                }
            } catch (Exception e) {
                LogLevelManager.logError(COMPONENT_NAME, "Erreur auto-reload pour machine " + machineId + ": " + e.getMessage());
            }
        }
    }

    /**
     * Obtient le statut du monitoring automatique
     */
    public Map<String, Object> getAutoReloadStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", autoReloadEnabled);
        status.put("intervalSeconds", autoReloadIntervalSeconds);
        status.put("machinesMonitored", activeCollectors.size());
        status.put("timerActive", tagReloadTimer != null);

        // Statut monitoring alarmes
        status.put("alarmMonitoringEnabled", alarmMonitoringEnabled);
        status.put("alarmCheckInterval", alarmCheckIntervalSeconds);
        status.put("profinetMachinesWithAlarms", machineAlarmHandlers.size());

        // Dernières vérifications par machine
        Map<String, Object> lastChecks = new HashMap<>();
        for (Map.Entry<Long, MachineCollector> entry : activeCollectors.entrySet()) {
            Long machineId = entry.getKey();
            MachineCollector collector = entry.getValue();

            Map<String, Object> machineInfo = new HashMap<>();
            machineInfo.put("machineName", collector.getMachineName());
            machineInfo.put("lastReloadTime", lastTagReloadTime.getOrDefault(machineId, 0L));
            machineInfo.put("currentTagCount", lastKnownTagCounts.getOrDefault(machineId, 0));

            // Informations alarmes si Profinet
            if (machineAlarmHandlers.containsKey(machineId)) {
                AlarmHandler alarmHandler = machineAlarmHandlers.get(machineId);
                Map<String, Object> alarmStats = alarmHandler.getAlarmStatistics();
                machineInfo.put("hasAlarmHandler", true);
                machineInfo.put("activeAlarms", alarmStats.get("activeCount"));
                machineInfo.put("criticalAlarms", alarmStats.get("criticalUnacknowledged"));
            } else {
                machineInfo.put("hasAlarmHandler", false);
            }

            lastChecks.put(machineId.toString(), machineInfo);
        }
        status.put("machines", lastChecks);

        return status;
    }

    // === NOUVELLES MÉTHODES POUR GESTION ALARMES ===
    /**
     * Obtient toutes les alarmes actives de toutes les machines Profinet
     */
    public List<ProfinetAlarm> getAllActiveAlarms() {
        List<ProfinetAlarm> allAlarms = new ArrayList<>();

        for (AlarmHandler alarmHandler : machineAlarmHandlers.values()) {
            allAlarms.addAll(alarmHandler.getActiveAlarms());
        }

        // Tri par priorité puis par timestamp
        allAlarms.sort((a1, a2) -> {
            int priorityCompare = Integer.compare(a1.getPriority().getLevel(), a2.getPriority().getLevel());
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return a2.getTimestamp().compareTo(a1.getTimestamp()); // Plus récent en premier
        });

        return allAlarms;
    }

    /**
     * Obtient toutes les alarmes critiques actives
     */
    public List<ProfinetAlarm> getAllCriticalAlarms() {
        List<ProfinetAlarm> criticalAlarms = new ArrayList<>();

        for (AlarmHandler alarmHandler : machineAlarmHandlers.values()) {
            criticalAlarms.addAll(alarmHandler.getActiveAlarmsByPriority(AlarmHandler.AlarmPriority.CRITICAL));
        }

        // Tri par timestamp (plus récent en premier)
        criticalAlarms.sort((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()));

        return criticalAlarms;
    }

    /**
     * Obtient le gestionnaire d'alarmes pour une machine spécifique
     */
    public AlarmHandler getAlarmHandlerForMachine(Long machineId) {
        return machineAlarmHandlers.get(machineId);
    }

    /**
     * Obtient toutes les alarmes d'une machine spécifique
     */
    public List<ProfinetAlarm> getMachineAlarms(Long machineId) {
        AlarmHandler alarmHandler = machineAlarmHandlers.get(machineId);
        if (alarmHandler != null) {
            MachineCollector collector = activeCollectors.get(machineId);
            if (collector != null) {
                return alarmHandler.getDeviceAlarms(collector.getMachineName());
            }
        }
        return new ArrayList<>();
    }

    /**
     * Acquitte une alarme spécifique
     */
    public boolean acknowledgeAlarm(String alarmId) {
        for (AlarmHandler alarmHandler : machineAlarmHandlers.values()) {
            if (alarmHandler.acknowledgeAlarm(alarmId)) {
                LogLevelManager.logInfo(COMPONENT_NAME, "Alarme acquittée via MachineManager: " + alarmId);
                return true;
            }
        }
        return false;
    }

    /**
     * Efface une alarme spécifique
     */
    public boolean clearAlarm(String alarmId) {
        for (AlarmHandler alarmHandler : machineAlarmHandlers.values()) {
            if (alarmHandler.clearAlarm(alarmId)) {
                LogLevelManager.logInfo(COMPONENT_NAME, "Alarme effacée via MachineManager: " + alarmId);
                return true;
            }
        }
        return false;
    }

    /**
     * Acquitte toutes les alarmes actives de toutes les machines
     */
    public int acknowledgeAllAlarms() {
        int totalAcknowledged = 0;

        for (AlarmHandler alarmHandler : machineAlarmHandlers.values()) {
            totalAcknowledged += alarmHandler.acknowledgeAllAlarms();
        }

        if (totalAcknowledged > 0) {
            LogLevelManager.logInfo(COMPONENT_NAME, "Total alarmes acquittées: " + totalAcknowledged);
        }

        return totalAcknowledged;
    }

    /**
     * Efface toutes les alarmes d'une machine spécifique
     */
    public int clearMachineAlarms(Long machineId) {
        AlarmHandler alarmHandler = machineAlarmHandlers.get(machineId);
        if (alarmHandler != null) {
            MachineCollector collector = activeCollectors.get(machineId);
            if (collector != null) {
                int clearedCount = alarmHandler.clearDeviceAlarms(collector.getMachineName());
                if (clearedCount > 0) {
                    LogLevelManager.logInfo(COMPONENT_NAME,
                            clearedCount + " alarme(s) effacée(s) pour machine: " + collector.getMachineName());
                }
                return clearedCount;
            }
        }
        return 0;
    }

    /**
     * Vérifie s'il y a des alarmes critiques dans le système
     */
    public boolean hasSystemCriticalAlarms() {
        for (AlarmHandler alarmHandler : machineAlarmHandlers.values()) {
            if (alarmHandler.hasCriticalAlarms()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Obtient le nombre total d'alarmes critiques dans le système
     */
    public long getSystemCriticalAlarmsCount() {
        return machineAlarmHandlers.values().stream()
                .mapToLong(handler -> handler.getActiveAlarmsByPriority(AlarmHandler.AlarmPriority.CRITICAL).size())
                .sum();
    }

    /**
     * Obtient un rapport global d'alarmes du système
     */
    public String getSystemAlarmReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Rapport Global des Alarmes DOBI ===\n");

        if (machineAlarmHandlers.isEmpty()) {
            report.append("Aucune machine Profinet avec gestionnaire d'alarmes.\n");
            return report.toString();
        }

        // Statistiques globales
        long totalActive = 0;
        long totalCritical = 0;
        long totalGenerated = 0;

        for (AlarmHandler alarmHandler : machineAlarmHandlers.values()) {
            Map<String, Object> stats = alarmHandler.getAlarmStatistics();
            totalActive += (Long) stats.get("activeCount");
            totalCritical += (Long) stats.get("criticalUnacknowledged");
            totalGenerated += (Long) stats.get("totalGenerated");
        }

        report.append("Machines Profinet surveillées: ").append(machineAlarmHandlers.size()).append("\n");
        report.append("Total alarmes actives: ").append(totalActive).append("\n");
        report.append("Total alarmes critiques: ").append(totalCritical).append("\n");
        report.append("Total alarmes générées: ").append(totalGenerated).append("\n");

        // Détail par machine
        report.append("\n=== Détail par Machine ===\n");
        for (Map.Entry<Long, AlarmHandler> entry : machineAlarmHandlers.entrySet()) {
            Long machineId = entry.getKey();
            AlarmHandler alarmHandler = entry.getValue();
            MachineCollector collector = activeCollectors.get(machineId);

            if (collector != null) {
                String machineName = collector.getMachineName();
                Map<String, Object> stats = alarmHandler.getAlarmStatistics();

                report.append("Machine: ").append(machineName).append("\n");
                report.append("  Alarmes actives: ").append(stats.get("activeCount")).append("\n");
                report.append("  Alarmes critiques: ").append(stats.get("criticalUnacknowledged")).append("\n");

                // Alarmes critiques détaillées
                List<ProfinetAlarm> criticalAlarms = alarmHandler.getActiveAlarmsByPriority(AlarmHandler.AlarmPriority.CRITICAL);
                if (!criticalAlarms.isEmpty()) {
                    report.append("  🚨 Alarmes critiques:\n");
                    for (ProfinetAlarm alarm : criticalAlarms) {
                        report.append("    - ").append(alarm.getMessage()).append("\n");
                    }
                }
            }
        }

        // Recommandations
        if (totalCritical > 0) {
            report.append("\n⚠️ ACTIONS REQUISES:\n");
            report.append("- Traiter immédiatement les ").append(totalCritical).append(" alarme(s) critique(s)\n");
            report.append("- Vérifier la connectivité des équipements défaillants\n");
            report.append("- Consulter les logs détaillés pour diagnostic\n");
        } else if (totalActive > 0) {
            report.append("\n📊 SURVEILLANCE:\n");
            report.append("- ").append(totalActive).append(" alarme(s) active(s) en surveillance\n");
            report.append("- Acquitter les alarmes traitées si nécessaire\n");
        } else {
            report.append("\n✅ SYSTÈME NOMINAL:\n");
            report.append("- Aucune alarme active détectée\n");
            report.append("- Tous les équipements Profinet opérationnels\n");
        }

        return report.toString();
    }

    // === MÉTHODES EXISTANTES MAINTENUES ===
    public List<Machine> getMachinesFromDb() {
        EntityManager em = emf.createEntityManager();
        try {
            // Modification de la requête : retirer le filtre t.active = true
            // pour que MachineCollector puisse gérer la logique de cycle et d'activité.
            return em.createQuery(
                    "SELECT DISTINCT m FROM Machine m "
                    + "JOIN FETCH m.driver "
                    + "LEFT JOIN FETCH m.tags t " // Retiré la condition WHERE t.active = true OR t IS NULL
                    + "LEFT JOIN FETCH t.type "
                    + "LEFT JOIN FETCH t.memory ", // Retiré la condition WHERE t.active = true OR t IS NULL
                    Machine.class
            ).getResultList();
        } finally {
            em.close();
        }
    }

    public Machine getMachineFromDb(long machineId) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery(
                    "SELECT m FROM Machine m LEFT JOIN FETCH m.tags t LEFT JOIN FETCH t.type ty LEFT JOIN FETCH t.memory WHERE m.id = :id", Machine.class)
                    .setParameter("id", machineId)
                    .getSingleResult();
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Impossible de trouver la machine avec l'ID: " + machineId);
            return null;
        } finally {
            em.close();
        }
    }

    public org.dobi.entities.Tag getTagFromDb(long tagId) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.find(org.dobi.entities.Tag.class, tagId);
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Impossible de trouver le tag avec l'ID: " + tagId);
            return null;
        } finally {
            em.close();
        }
    }

    public List<org.dobi.entities.PersStandard> getTagHistory(long tagId, int page, int size) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery(
                    "SELECT h FROM PersStandard h WHERE h.tag = :tagId ORDER BY h.vStamp DESC", org.dobi.entities.PersStandard.class)
                    .setParameter("tagId", tagId)
                    .setFirstResult(page * size)
                    .setMaxResults(size)
                    .getResultList();
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Impossible de récupérer l'historique pour le tag ID: " + tagId);
            return java.util.Collections.emptyList();
        } finally {
            em.close();
        }
    }

    public EntityManagerFactory getEmf() {
        return emf;
    }

    public String getAppProperty(String key) {
        return appProperties.getProperty(key);
    }

    public void stop() {
        LogLevelManager.logInfo(COMPONENT_NAME, "Arrêt du MachineManagerService...");

        // Arrêt du monitoring automatique
        stopTagReloadMonitoring();
        stopAlarmMonitoring();

        if (kafkaProducerService != null) {
            kafkaProducerService.close();
        }

        if (executorService != null) {
            activeCollectors.values().forEach(MachineCollector::stop);
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }

        // Nettoyage des gestionnaires d'alarmes
        machineAlarmHandlers.clear();

        if (emf != null) {
            emf.close();
        }

        LogLevelManager.logInfo(COMPONENT_NAME, "MachineManagerService arrêté");
    }

    public void restartCollector(long machineId) {
        LogLevelManager.logInfo(COMPONENT_NAME, "Redémarrage collecteur pour machine ID: " + machineId);

        MachineCollector oldCollector = activeCollectors.get(machineId);
        if (oldCollector != null) {
            oldCollector.stop();
        }

        // Suppression du gestionnaire d'alarmes si existant
        AlarmHandler oldAlarmHandler = machineAlarmHandlers.remove(machineId);
        if (oldAlarmHandler != null) {
            LogLevelManager.logDebug(COMPONENT_NAME, "Gestionnaire d'alarmes supprimé pour machine ID: " + machineId);
        }

        EntityManager em = emf.createEntityManager();
        try {
            Machine machineToRestart = em.createQuery(
                    "SELECT m FROM Machine m "
                    + "JOIN FETCH m.driver "
                    + "LEFT JOIN FETCH m.tags t "
                    + "LEFT JOIN FETCH t.type "
                    + "LEFT JOIN FETCH t.memory "
                    + "WHERE m.id = :id", Machine.class)
                    .setParameter("id", machineId)
                    .getSingleResult();

            IDriver driver = createDriverForMachine(machineToRestart);
            if (driver != null) {
                MachineCollector newCollector = new MachineCollector(machineToRestart, driver, kafkaProducerService);
                activeCollectors.put(machineToRestart.getId(), newCollector);

                // Mettre à jour les compteurs
                lastKnownTagCounts.put(machineId,
                        machineToRestart.getTags() != null ? machineToRestart.getTags().size() : 0);
                lastTagReloadTime.put(machineId, System.currentTimeMillis());

                // Si c'est un driver Profinet, réenregistrer son gestionnaire d'alarmes
                if (driver instanceof ProfinetDriver) {
                    ProfinetDriver profinetDriver = (ProfinetDriver) driver;
                    AlarmHandler alarmHandler = profinetDriver.getAlarmHandler();
                    if (alarmHandler != null) {
                        machineAlarmHandlers.put(machineToRestart.getId(), alarmHandler);
                        LogLevelManager.logInfo(COMPONENT_NAME, "Gestionnaire d'alarmes réenregistré pour machine Profinet: " + machineToRestart.getName());
                    }
                }

                executorService.submit(newCollector);
                LogLevelManager.logInfo(COMPONENT_NAME, "Collecteur redémarré avec succès pour: " + machineToRestart.getName());
            }
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Impossible de redémarrer machine " + machineId + ": " + e.getMessage());
        } finally {
            em.close();
        }
    }

    public List<MachineStatusDto> getActiveCollectorDetails() {
        return activeCollectors.values().stream()
                .map(collector -> {
                    long machineId = collector.getMachineId();
                    String machineName = collector.getMachineName();
                    String status = collector.getCurrentStatus();
                    long tagsReadCount = collector.getTagsReadCount();

                    // Ajout d'informations d'alarme si disponible
                    if (machineAlarmHandlers.containsKey(machineId)) {
                        AlarmHandler alarmHandler = machineAlarmHandlers.get(machineId);
                        long criticalAlarms = alarmHandler.getActiveAlarmsByPriority(AlarmHandler.AlarmPriority.CRITICAL).size();

                        if (criticalAlarms > 0) {
                            status = status + " (🚨" + criticalAlarms + " critiques)";
                        }
                    }

                    return new MachineStatusDto(machineId, machineName, status, tagsReadCount);
                })
                .collect(Collectors.toList());
    }

    /**
     * Obtient des statistiques détaillées du système avec alarmes
     */
    public Map<String, Object> getSystemStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // Statistiques générales
        stats.put("totalMachines", activeCollectors.size());
        stats.put("profinetMachinesWithAlarms", machineAlarmHandlers.size());
        stats.put("autoReloadEnabled", autoReloadEnabled);
        stats.put("alarmMonitoringEnabled", alarmMonitoringEnabled);

        // Statistiques des collecteurs
        long totalTagsRead = activeCollectors.values().stream()
                .mapToLong(MachineCollector::getTagsReadCount)
                .sum();
        stats.put("totalTagsRead", totalTagsRead);

        // Statistiques des alarmes
        if (!machineAlarmHandlers.isEmpty()) {
            long totalActiveAlarms = 0;
            long totalCriticalAlarms = 0;
            long totalGenerated = 0;
            long totalAcknowledged = 0;

            for (AlarmHandler alarmHandler : machineAlarmHandlers.values()) {
                Map<String, Object> alarmStats = alarmHandler.getAlarmStatistics();
                totalActiveAlarms += (Long) alarmStats.get("activeCount");
                totalCriticalAlarms += (Long) alarmStats.get("criticalUnacknowledged");
                totalGenerated += (Long) alarmStats.get("totalGenerated");
                totalAcknowledged += (Long) alarmStats.get("totalAcknowledged");
            }

            Map<String, Object> alarmSummary = new HashMap<>();
            alarmSummary.put("totalActive", totalActiveAlarms);
            alarmSummary.put("totalCritical", totalCriticalAlarms);
            alarmSummary.put("totalGenerated", totalGenerated);
            alarmSummary.put("totalAcknowledged", totalAcknowledged);

            double acknowledgmentRate = totalGenerated > 0 ? (double) totalAcknowledged / totalGenerated : 0.0;
            alarmSummary.put("acknowledgmentRate", acknowledgmentRate);

            stats.put("alarms", alarmSummary);
        } else {
            stats.put("alarms", Map.of("totalActive", 0, "totalCritical", 0));
        }

        // État du système
        String systemHealth = "NOMINAL";
        if (getSystemCriticalAlarmsCount() > 0) {
            systemHealth = "CRITIQUE";
        } else if (getAllActiveAlarms().size() > 0) {
            systemHealth = "ATTENTION";
        }
        stats.put("systemHealth", systemHealth);

        return stats;
    }

    /**
     * Obtient un collecteur par ID de machine
     *
     * @param machineId L'ID de la machine
     * @return Le collecteur correspondant ou null si non trouvé
     */
    public MachineCollector getCollectorByMachineId(long machineId) {
        return activeCollectors.get(machineId);
    }

    /**
     * Obtient tous les collecteurs actifs
     *
     * @return Map des collecteurs actifs (ID machine -> Collecteur)
     */
    public Map<Long, MachineCollector> getActiveCollectors() {
        return new HashMap<>(activeCollectors);
    }

    /**
     * Vérifie si un collecteur utilise un driver spécifique
     *
     * @param collector La collecteur à vérifier
     * @param driverClass La classe du driver recherché
     * @return true si le collecteur utilise ce type de driver
     */
    public boolean isCollectorOfType(MachineCollector collector, Class<?> driverClass) {
        if (collector == null || driverClass == null) {
            return false;
        }

        try {
            // Accès au driver via réflexion (temporaire)
            java.lang.reflect.Field driverField = MachineCollector.class.getDeclaredField("driver");
            driverField.setAccessible(true);
            Object driver = driverField.get(collector);

            return driverClass.isInstance(driver);

        } catch (Exception e) {
            System.err.println("Erreur lors de la vérification du type de driver: " + e.getMessage());
            return false;
        }
    }

    /**
     * Obtient le driver d'un collecteur
     *
     * @param collector Le collecteur
     * @return Le driver ou null si erreur
     */
    public Object getDriverFromCollector(MachineCollector collector) {
        if (collector == null) {
            return null;
        }

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
     * Obtient tous les collecteurs d'un type de driver spécifique
     *
     * @param driverClass La classe du driver recherché
     * @return Liste des collecteurs utilisant ce type de driver
     */
    public List<MachineCollector> getCollectorsByDriverType(Class<?> driverClass) {
        List<MachineCollector> filteredCollectors = new ArrayList<>();

        for (MachineCollector collector : activeCollectors.values()) {
            if (isCollectorOfType(collector, driverClass)) {
                filteredCollectors.add(collector);
            }
        }

        return filteredCollectors;
    }

    /**
     * Obtient tous les drivers Profinet actifs
     *
     * @return Liste des drivers Profinet
     */
    public List<org.dobi.profinet.ProfinetDriver> getActiveProfinetDrivers() {
        List<org.dobi.profinet.ProfinetDriver> profinetDrivers = new ArrayList<>();

        try {
            List<MachineCollector> profinetCollectors = getCollectorsByDriverType(
                    org.dobi.profinet.ProfinetDriver.class);

            for (MachineCollector collector : profinetCollectors) {
                Object driver = getDriverFromCollector(collector);
                if (driver instanceof org.dobi.profinet.ProfinetDriver) {
                    profinetDrivers.add((org.dobi.profinet.ProfinetDriver) driver);
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de la récupération des drivers Profinet: " + e.getMessage());
        }

        return profinetDrivers;
    }

    /**
     * Obtient tous les gestionnaires d'alarmes Profinet
     *
     * @return Liste des gestionnaires d'alarmes
     */
    public List<org.dobi.profinet.diagnostic.AlarmHandler> getProfinetAlarmHandlers() {
        List<org.dobi.profinet.diagnostic.AlarmHandler> alarmHandlers = new ArrayList<>();

        List<org.dobi.profinet.ProfinetDriver> profinetDrivers = getActiveProfinetDrivers();

        for (org.dobi.profinet.ProfinetDriver driver : profinetDrivers) {
            if (driver.getAlarmHandler() != null) {
                alarmHandlers.add(driver.getAlarmHandler());
            }
        }

        return alarmHandlers;
    }

    /**
     * Obtient des statistiques globales sur les alarmes Profinet
     *
     * @return Map contenant les statistiques
     */
    /**
     * Obtient des statistiques globales sur les alarmes Profinet
     *
     * @return Map contenant les statistiques
     */
    public Map<String, Object> getGlobalProfinetAlarmStatistics() {
        Map<String, Object> globalStats = new HashMap<>();

        try {
            List<org.dobi.profinet.diagnostic.AlarmHandler> alarmHandlers = getProfinetAlarmHandlers();

            long totalActiveAlarms = 0;
            long totalCriticalAlarms = 0;

            for (org.dobi.profinet.diagnostic.AlarmHandler handler : alarmHandlers) {
                Map<String, Object> handlerStats = handler.getAlarmStatistics();
                totalActiveAlarms += (Long) handlerStats.get("activeCount");
                totalCriticalAlarms += (Long) handlerStats.get("criticalUnacknowledged");
            }

            globalStats.put("profinetDriversCount", getActiveProfinetDrivers().size());
            globalStats.put("alarmHandlersCount", alarmHandlers.size());
            globalStats.put("totalActiveAlarms", totalActiveAlarms);
            globalStats.put("totalCriticalAlarms", totalCriticalAlarms);
            globalStats.put("hasCriticalAlarms", totalCriticalAlarms > 0);

        } catch (Exception e) {
            System.err.println("Erreur lors du calcul des statistiques d'alarmes: " + e.getMessage());
            globalStats.put("error", e.getMessage());
        }

        return globalStats;
    }
}
