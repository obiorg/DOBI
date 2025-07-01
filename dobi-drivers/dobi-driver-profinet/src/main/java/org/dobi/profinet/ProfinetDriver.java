package org.dobi.profinet;

import org.dobi.api.IDriver;
import org.dobi.entities.Machine;
import org.dobi.entities.Tag;
import org.dobi.logging.LogLevelManager;
import org.dobi.logging.LogLevelManager.LogLevel;
import org.dobi.profinet.config.ProfinetConfig;
import org.dobi.profinet.device.DeviceManager;
import org.dobi.profinet.device.ProfinetDevice;
import org.dobi.profinet.protocol.DCPClient;
import org.dobi.profinet.protocol.RTDataHandler;
import org.dobi.profinet.io.DataMapper;
import org.dobi.profinet.diagnostic.NetworkAnalyzer;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.dobi.profinet.diagnostic.AlarmHandler;
import org.dobi.profinet.diagnostic.AlarmHandler.*;

public class ProfinetDriver implements IDriver {

    private static final String DRIVER_NAME = "PROFINET";

    // Configuration par défaut
    private static final int DEFAULT_CYCLIC_INTERVAL_MS = 10;
    private static final int DEFAULT_CONNECTION_TIMEOUT = 10000;
    private static final int RECONNECT_DELAY_MS = 5000;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int MAX_DEVICES = 50;

    // Codes d'erreur Profinet standardisés
    public static final class ErrorCodes {

        public static final int SUCCESS = 0;
        public static final int CONNECTION_LOST = 3001;
        public static final int INVALID_CONFIGURATION = 3002;
        public static final int DEVICE_NOT_FOUND = 3003;
        public static final int TAG_READ_ERROR = 3004;
        public static final int TAG_WRITE_ERROR = 3005;
        public static final int SLOT_CONFIGURATION_ERROR = 3006;
        public static final int GSDML_PARSING_ERROR = 3007;
        public static final int NETWORK_ERROR = 3008;
        public static final int CYCLIC_DATA_ERROR = 3009;
        public static final int DEVICE_ALARM = 3010;
    }

    private Machine machine;
    private ProfinetConfig config;
    private DeviceManager deviceManager;
    private DCPClient dcpClient;
    private RTDataHandler rtDataHandler;
    private DataMapper dataMapper;
    private NetworkAnalyzer networkAnalyzer;

    private volatile boolean connected = false;
    private volatile boolean shouldReconnect = true;
    private volatile int reconnectAttempts = 0;
    private final AtomicBoolean cyclicRunning = new AtomicBoolean(false);

    // Threading
    private ExecutorService executorService;
    private ScheduledExecutorService cyclicExecutor;
    private CompletableFuture<Void> cyclicTask;

    // Métriques de performance
    private long totalReads = 0;
    private long totalWrites = 0;
    private long totalErrors = 0;
    private long connectionTime = 0;
    private long cyclicCycleCount = 0;
    private final Map<String, Long> errorCounts = new ConcurrentHashMap<>();
    private final Map<String, ProfinetDevice> discoveredDevices = new ConcurrentHashMap<>();

    // Cache des données
    private final Map<String, Object> inputCache = new ConcurrentHashMap<>();
    private final Map<String, Object> outputCache = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastUpdateTimes = new ConcurrentHashMap<>();

    // Alarm
    private AlarmHandler alarmHandler;

    @Override
    public void configure(Machine machine) {
        this.machine = machine;
        loadConfiguration();
        initializeComponents();
        LogLevelManager.logInfo(DRIVER_NAME, "Configuration du driver pour la machine: " + machine.getName()
                + " (Station: " + machine.getAddress() + ")");
    }

    private void loadConfiguration() {
        this.config = new ProfinetConfig();

        // Configuration depuis les propriétés de la machine ou fichier
        config.setStationName(machine.getName());
        config.setNetworkInterface(machine.getAddress());
        config.setCyclicInterval(machine.getPort() != null ? machine.getPort() : DEFAULT_CYCLIC_INTERVAL_MS);
        config.setConnectionTimeout(DEFAULT_CONNECTION_TIMEOUT);

        LogLevelManager.logDebug(DRIVER_NAME, "Configuration chargée: " + config);
    }

    private void initializeComponents() {
        this.deviceManager = new DeviceManager(config);
        this.dcpClient = new DCPClient(config);
        this.rtDataHandler = new RTDataHandler(config);
        this.dataMapper = new DataMapper(config);
        this.networkAnalyzer = new NetworkAnalyzer(config);
        this.alarmHandler = new AlarmHandler();

        // Configuration de l'AlarmHandler
        alarmHandler.setMaxActiveAlarms(500);
        alarmHandler.setMaxHistoricalAlarms(5000);
        alarmHandler.setAutoAcknowledgeInfo(true);

        // Pool de threads pour opérations asynchrones
        this.executorService = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "Profinet-Worker");
            t.setDaemon(true);
            return t;
        });

        this.cyclicExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Profinet-Cyclic");
            t.setDaemon(true);
            return t;
        });

        LogLevelManager.logDebug(DRIVER_NAME, "Composants Profinet initialisés");
    }

    @Override
    public boolean connect() {
        if (machine == null || machine.getAddress() == null) {
            recordError(ErrorCodes.INVALID_CONFIGURATION, "Configuration machine invalide");
            return false;
        }

        try {
            shouldReconnect = true;
            reconnectAttempts = 0;
            return establishConnection();
        } catch (Exception e) {
            recordError(ErrorCodes.CONNECTION_LOST, "Erreur lors de la connexion initiale: " + e.getMessage());
            return false;
        }
    }

    private boolean establishConnection() {
        try {
            long startTime = System.currentTimeMillis();

            LogLevelManager.logInfo(DRIVER_NAME, "Tentative de connexion Profinet à " + machine.getAddress()
                    + " (Station: " + config.getStationName() + ")");

            // 1. Initialisation de l'interface réseau
            if (!networkAnalyzer.initializeInterface(config.getNetworkInterface())) {
                throw new Exception("Impossible d'initialiser l'interface réseau: " + config.getNetworkInterface());
            }

            // 2. Démarrage du client DCP pour découverte
            dcpClient.start();
            LogLevelManager.logDebug(DRIVER_NAME, "Client DCP démarré");

            // 3. Découverte des équipements Profinet
            performDeviceDiscovery();

            // 4. Configuration des équipements découverts
            if (!configureDiscoveredDevices()) {
                throw new Exception("Échec de la configuration des équipements");
            }

            // 5. Démarrage de la communication temps réel
            rtDataHandler.start();
            LogLevelManager.logDebug(DRIVER_NAME, "Handler RT démarré");

            // 6. Démarrage de la communication cyclique
            startCyclicCommunication();

            connected = true;
            connectionTime = System.currentTimeMillis() - startTime;
            reconnectAttempts = 0;

            LogLevelManager.logInfo(DRIVER_NAME, "Connexion Profinet établie avec " + machine.getName()
                    + " en " + connectionTime + "ms (" + discoveredDevices.size() + " équipements)");

            return true;

        } catch (Exception e) {
            connected = false;
            recordError(ErrorCodes.NETWORK_ERROR, "Erreur de connexion Profinet: " + e.getMessage());
            cleanup();
            return false;
        }
    }

    private void performDeviceDiscovery() throws Exception {
        LogLevelManager.logInfo(DRIVER_NAME, "Début de la découverte d'équipements Profinet...");

        try {
            // Envoi de requête DCP Identify All
            CompletableFuture<List<ProfinetDevice>> discoveryFuture = dcpClient.discoverDevices();

            List<ProfinetDevice> devices = discoveryFuture.get(config.getDiscoveryTimeout(), TimeUnit.MILLISECONDS);

            for (ProfinetDevice device : devices) {
                discoveredDevices.put(device.getStationName(), device);
                LogLevelManager.logDebug(DRIVER_NAME, "Équipement découvert: " + device.getStationName()
                        + " (" + device.getIpAddress() + ")");

                // Génération d'alarme informative pour nouvel équipement
                if (alarmHandler != null) {
                    alarmHandler.generateAlarm(
                            device.getStationName(),
                            device.getStationName(),
                            AlarmType.DIAGNOSTIC_ALARM,
                            AlarmPriority.INFO,
                            "Équipement découvert sur le réseau",
                            "IP: " + device.getIpAddress() + ", Type: " + device.getDeviceType()
                    );
                }
            }

            if (discoveredDevices.isEmpty()) {
                LogLevelManager.logError(DRIVER_NAME, "Aucun équipement Profinet découvert sur le réseau");

                // Alarme pour absence d'équipements
                if (alarmHandler != null) {
                    alarmHandler.generateAlarm(
                            machine.getName(),
                            config.getStationName(),
                            AlarmType.NETWORK_ERROR,
                            AlarmPriority.HIGH,
                            "Aucun équipement Profinet découvert",
                            "Vérifier la configuration réseau et la connectivité"
                    );
                }
            } else {
                LogLevelManager.logInfo(DRIVER_NAME, discoveredDevices.size() + " équipement(s) Profinet découvert(s)");

                // Effacement des alarmes précédentes de découverte si succès
                if (alarmHandler != null) {
                    alarmHandler.clearDeviceAlarms(machine.getName());
                }
            }

        } catch (Exception e) {
            // Alarme pour échec de découverte
            if (alarmHandler != null) {
                alarmHandler.generateAlarm(
                        machine.getName(),
                        config.getStationName(),
                        AlarmType.COMMUNICATION_ERROR,
                        AlarmPriority.HIGH,
                        "Échec de la découverte d'équipements",
                        "Exception: " + e.getMessage()
                );
            }
            throw e;
        }
    }

    private void monitorDeviceHealth() {
        if (alarmHandler == null) {
            return;
        }

        for (ProfinetDevice device : discoveredDevices.values()) {
            try {
                boolean wasHealthy = device.isConnected();
                boolean isHealthy = deviceManager.isDeviceHealthy(device);

                // Détection changement d'état
                if (wasHealthy && !isHealthy) {
                    // Équipement devient défaillant
                    alarmHandler.generateAlarm(
                            device.getStationName(),
                            device.getStationName(),
                            AlarmType.DEVICE_FAILURE,
                            AlarmPriority.CRITICAL,
                            "Équipement non accessible",
                            "IP: " + device.getIpAddress() + " | Dernière communication OK"
                    );

                } else if (!wasHealthy && isHealthy) {
                    // Équipement revient en ligne
                    alarmHandler.generateAlarm(
                            device.getStationName(),
                            device.getStationName(),
                            AlarmType.DIAGNOSTIC_ALARM,
                            AlarmPriority.INFO,
                            "Équipement de nouveau accessible",
                            "IP: " + device.getIpAddress() + " | Communication rétablie"
                    );

                    // Effacement des alarmes précédentes de cet équipement
                    alarmHandler.clearDeviceAlarms(device.getStationName());
                }

                device.setConnected(isHealthy);

            } catch (Exception e) {
                LogLevelManager.logError(DRIVER_NAME, "Erreur monitoring équipement "
                        + device.getStationName() + ": " + e.getMessage());
            }
        }
    }

    private boolean configureDiscoveredDevices() {
        boolean allConfigured = true;

        for (ProfinetDevice device : discoveredDevices.values()) {
            try {
                LogLevelManager.logDebug(DRIVER_NAME, "Configuration de l'équipement: " + device.getStationName());

                // Configuration des slots et modules
                deviceManager.configureDevice(device);

                // Établissement de la connexion AR (Application Relation)
                if (deviceManager.establishConnection(device)) {
                    LogLevelManager.logDebug(DRIVER_NAME, "Connexion AR établie avec: " + device.getStationName());
                } else {
                    LogLevelManager.logError(DRIVER_NAME, "Échec connexion AR avec: " + device.getStationName());
                    allConfigured = false;
                }

            } catch (Exception e) {
                recordError(ErrorCodes.SLOT_CONFIGURATION_ERROR,
                        "Erreur configuration équipement " + device.getStationName() + ": " + e.getMessage());
                allConfigured = false;
            }
        }

        return allConfigured;
    }

    private void startCyclicCommunication() {
        if (cyclicRunning.compareAndSet(false, true)) {
            LogLevelManager.logInfo(DRIVER_NAME, "Démarrage communication cyclique (intervalle: "
                    + config.getCyclicInterval() + "ms)");

            cyclicTask = CompletableFuture.runAsync(this::cyclicCommunicationLoop, cyclicExecutor);
        }
    }

    private void cyclicCommunicationLoop() {
        LogLevelManager.logDebug(DRIVER_NAME, "Boucle de communication cyclique démarrée");

        long lastHealthCheck = System.currentTimeMillis();
        final long HEALTH_CHECK_INTERVAL = 30000; // 30 secondes

        while (cyclicRunning.get() && connected && shouldReconnect) {
            try {
                long cycleStart = System.currentTimeMillis();

                // Lecture des données d'entrée de tous les équipements
                readCyclicInputData();

                // Écriture des données de sortie vers tous les équipements
                writeCyclicOutputData();

                cyclicCycleCount++;

                // Monitoring périodique de la santé des équipements
                if (cycleStart - lastHealthCheck > HEALTH_CHECK_INTERVAL) {
                    monitorDeviceHealth();
                    lastHealthCheck = cycleStart;
                }

                // Calcul du temps de cycle et attente
                long cycleTime = System.currentTimeMillis() - cycleStart;
                long sleepTime = Math.max(0, config.getCyclicInterval() - cycleTime);

                // Alarme pour cycles trop lents
                if (cycleTime > config.getCyclicInterval() * 2 && alarmHandler != null) {
                    alarmHandler.generateAlarm(
                            machine.getName(),
                            config.getStationName(),
                            AlarmType.CYCLIC_DATA_ERROR,
                            AlarmPriority.MEDIUM,
                            "Cycle cyclique lent détecté",
                            String.format("Temps cycle: %dms (limite: %dms)", cycleTime, config.getCyclicInterval())
                    );
                }

                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }

                // Log périodique avec effacement d'alarmes si tout va bien
                if (cyclicCycleCount % 1000 == 0) {
                    LogLevelManager.logTrace(DRIVER_NAME, "Cycles cycliques exécutés: " + cyclicCycleCount);

                    // Si 1000 cycles sans problème, effacer les alarmes de communication cyclique
                    if (alarmHandler != null && cycleTime <= config.getCyclicInterval()) {
                        // Recherche et effacement des alarmes cycliques résolues
                        List<ProfinetAlarm> cyclicAlarms = alarmHandler.getActiveAlarmsByType(AlarmType.CYCLIC_DATA_ERROR);
                        for (ProfinetAlarm alarm : cyclicAlarms) {
                            if (alarm.getMessage().contains("Cycle cyclique lent")) {
                                alarmHandler.clearAlarm(alarm.getId());
                            }
                        }
                    }
                }

            } catch (InterruptedException e) {
                LogLevelManager.logInfo(DRIVER_NAME, "Communication cyclique interrompue");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                recordError(ErrorCodes.CYCLIC_DATA_ERROR, "Erreur dans la communication cyclique: " + e.getMessage());

                // En cas d'erreur répétée, tenter une reconnexion
                if (!isConnected()) {
                    LogLevelManager.logError(DRIVER_NAME, "Perte de connexion détectée en communication cyclique");
                    connected = false;
                    attemptReconnection();
                    break;
                }
            }
        }

        LogLevelManager.logDebug(DRIVER_NAME, "Boucle de communication cyclique arrêtée");
    }

    private void readCyclicInputData() {
        for (ProfinetDevice device : discoveredDevices.values()) {
            try {
                // Lecture des données cycliques d'entrée via RT
                byte[] inputData = rtDataHandler.readCyclicData(device);

                if (inputData != null && inputData.length > 0) {
                    // Décodage des données selon la configuration GSDML
                    Map<String, Object> decodedData = dataMapper.decodeInputData(device, inputData);

                    // Mise à jour du cache
                    for (Map.Entry<String, Object> entry : decodedData.entrySet()) {
                        String tagKey = device.getStationName() + "." + entry.getKey();
                        inputCache.put(tagKey, entry.getValue());
                        lastUpdateTimes.put(tagKey, LocalDateTime.now());

                        LogLevelManager.logTrace(DRIVER_NAME, "Données lues: " + tagKey + " = " + entry.getValue());
                    }
                }

            } catch (Exception e) {
                recordError(ErrorCodes.TAG_READ_ERROR,
                        "Erreur lecture cyclique équipement " + device.getStationName() + ": " + e.getMessage());
            }
        }
    }

    private void writeCyclicOutputData() {
        for (ProfinetDevice device : discoveredDevices.values()) {
            try {
                // Récupération des données de sortie depuis le cache
                Map<String, Object> outputData = new HashMap<>();
                String devicePrefix = device.getStationName() + ".";

                for (Map.Entry<String, Object> entry : outputCache.entrySet()) {
                    if (entry.getKey().startsWith(devicePrefix)) {
                        String localTag = entry.getKey().substring(devicePrefix.length());
                        outputData.put(localTag, entry.getValue());
                    }
                }

                if (!outputData.isEmpty()) {
                    // Encodage des données selon la configuration GSDML
                    byte[] encodedData = dataMapper.encodeOutputData(device, outputData);

                    // Envoi des données cycliques de sortie via RT
                    rtDataHandler.writeCyclicData(device, encodedData);

                    LogLevelManager.logTrace(DRIVER_NAME, "Données écrites vers " + device.getStationName()
                            + ": " + outputData.size() + " tags");
                }

            } catch (Exception e) {
                recordError(ErrorCodes.TAG_WRITE_ERROR,
                        "Erreur écriture cyclique équipement " + device.getStationName() + ": " + e.getMessage());
            }
        }
    }

    @Override
    public Object read(Tag tag) {
        if (!isConnected()) {
            recordError(ErrorCodes.CONNECTION_LOST, "Driver non connecté pour la lecture du tag: " + tag.getName());
            return null;
        }

        try {
            // Construction de la clé du tag
            String tagKey = buildTagKey(tag);

            LogLevelManager.logDebug(DRIVER_NAME, "Lecture du tag Profinet: " + tag.getName() + " (clé: " + tagKey + ")");

            // Lecture depuis le cache (données cycliques)
            Object cachedValue = inputCache.get(tagKey);
            if (cachedValue != null) {
                totalReads++;
                LogLevelManager.logTrace(DRIVER_NAME, "Valeur lue depuis cache: " + tag.getName() + " = " + cachedValue);
                return cachedValue;
            }

            // Si pas dans le cache, lecture acyclique directe
            Object directValue = readAcyclicData(tag);
            if (directValue != null) {
                totalReads++;
                LogLevelManager.logTrace(DRIVER_NAME, "Valeur lue directement: " + tag.getName() + " = " + directValue);
                return directValue;
            }

            LogLevelManager.logDebug(DRIVER_NAME, "Aucune valeur trouvée pour le tag: " + tag.getName());
            return null;

        } catch (Exception e) {
            recordError(ErrorCodes.TAG_READ_ERROR, "Exception lors de la lecture du tag '" + tag.getName() + "': " + e.getMessage());
            return null;
        }
    }

    @Override
    public void write(Tag tag, Object value) {
        if (!isConnected()) {
            recordError(ErrorCodes.CONNECTION_LOST, "Driver non connecté pour l'écriture du tag: " + tag.getName());
            return;
        }

        try {
            String tagKey = buildTagKey(tag);

            LogLevelManager.logDebug(DRIVER_NAME, "Écriture du tag Profinet: " + tag.getName()
                    + " (clé: " + tagKey + ") avec valeur: " + value);

            // Mise à jour du cache de sortie (sera écrit au prochain cycle)
            outputCache.put(tagKey, value);
            lastUpdateTimes.put(tagKey, LocalDateTime.now());

            // Pour les écritures critiques, écriture acyclique immédiate
            if (isUrgentTag(tag)) {
                writeAcyclicData(tag, value);
            }

            totalWrites++;
            LogLevelManager.logTrace(DRIVER_NAME, "Valeur mise en cache pour écriture: " + tag.getName() + " = " + value);

        } catch (Exception e) {
            recordError(ErrorCodes.TAG_WRITE_ERROR, "Exception lors de l'écriture du tag '" + tag.getName() + "': " + e.getMessage());
        }
    }

    private String buildTagKey(Tag tag) {
        // Construction de la clé basée sur les métadonnées du tag
        // Format: StationName.SlotNumber.SubmoduleIndex.ParameterName

        if (tag.getByteAddress() != null && tag.getBitAddress() != null) {
            // Format avec adresse: Station.Slot.Submodule.Offset.Bit
            return String.format("%s.%d.%d.%d.%d",
                    machine.getName(),
                    tag.getDbNumber() != null ? tag.getDbNumber() : 0, // Slot
                    tag.getByteAddress() / 100, // Submodule (supposé)
                    tag.getByteAddress() % 100, // Offset
                    tag.getBitAddress());        // Bit
        } else if (tag.getByteAddress() != null) {
            // Format avec byte address: Station.Slot.Submodule.Offset
            return String.format("%s.%d.%d.%d",
                    machine.getName(),
                    tag.getDbNumber() != null ? tag.getDbNumber() : 0,
                    tag.getByteAddress() / 100,
                    tag.getByteAddress() % 100);
        } else {
            // Format simple: Station.TagName
            return machine.getName() + "." + tag.getName();
        }
    }

    private Object readAcyclicData(Tag tag) {
        try {
            // Implémentation de la lecture acyclique Profinet
            // Utilisation des services Read/Write pour données non-cycliques

            String stationName = extractStationName(tag);
            ProfinetDevice device = discoveredDevices.get(stationName);

            if (device == null) {
                LogLevelManager.logError(DRIVER_NAME, "Équipement non trouvé: " + stationName);
                return null;
            }

            // Lecture via services Profinet
            return deviceManager.readParameter(device, tag);

        } catch (Exception e) {
            LogLevelManager.logError(DRIVER_NAME, "Erreur lecture acyclique: " + e.getMessage());
            return null;
        }
    }

    private void writeAcyclicData(Tag tag, Object value) {
        try {
            String stationName = extractStationName(tag);
            ProfinetDevice device = discoveredDevices.get(stationName);

            if (device == null) {
                LogLevelManager.logError(DRIVER_NAME, "Équipement non trouvé pour écriture: " + stationName);
                return;
            }

            // Écriture via services Profinet
            deviceManager.writeParameter(device, tag, value);

        } catch (Exception e) {
            LogLevelManager.logError(DRIVER_NAME, "Erreur écriture acyclique: " + e.getMessage());
        }
    }

    private String extractStationName(Tag tag) {
        // Extraction du nom de station depuis la configuration du tag
        return machine.getName(); // Simplification pour l'exemple
    }

    private boolean isUrgentTag(Tag tag) {
        // Détermine si un tag nécessite une écriture immédiate (acyclique)
        // Par exemple, tags de commande, arrêts d'urgence, etc.
        return tag.getName().toLowerCase().contains("emergency")
                || tag.getName().toLowerCase().contains("stop")
                || tag.getName().toLowerCase().contains("cmd");
    }

    private void attemptReconnection() {
        if (!shouldReconnect || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            LogLevelManager.logError(DRIVER_NAME, "Abandon de la reconnexion après " + reconnectAttempts + " tentatives");
            return;
        }

        reconnectAttempts++;
        final int currentAttempt = reconnectAttempts;

        CompletableFuture.runAsync(() -> {
            try {
                LogLevelManager.logInfo(DRIVER_NAME, "Tentative de reconnexion Profinet "
                        + currentAttempt + "/" + MAX_RECONNECT_ATTEMPTS);

                // Attente progressive
                int delay = RECONNECT_DELAY_MS * Math.min(currentAttempt, 4);
                Thread.sleep(delay);

                // Nettoyage de l'ancienne connexion
                cleanup();

                if (establishConnection()) {
                    LogLevelManager.logInfo(DRIVER_NAME, "Reconnexion Profinet réussie");
                } else {
                    attemptReconnection();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LogLevelManager.logError(DRIVER_NAME, "Erreur lors de la reconnexion: " + e.getMessage());
                attemptReconnection();
            }
        }, executorService);
    }

    private void cleanup() {
        try {
            // Arrêt de la communication cyclique
            if (cyclicRunning.compareAndSet(true, false)) {
                LogLevelManager.logDebug(DRIVER_NAME, "Arrêt de la communication cyclique");
                if (cyclicTask != null) {
                    cyclicTask.cancel(true);
                }
            }

            // Fermeture des connexions avec les équipements
            for (ProfinetDevice device : discoveredDevices.values()) {
                try {
                    deviceManager.disconnectDevice(device);
                } catch (Exception e) {
                    LogLevelManager.logDebug(DRIVER_NAME, "Erreur déconnexion équipement " + device.getStationName() + ": " + e.getMessage());
                }
            }

            // Arrêt des services
            if (rtDataHandler != null) {
                rtDataHandler.stop();
            }

            if (dcpClient != null) {
                dcpClient.stop();
            }

            // Nettoyage des caches
            inputCache.clear();
            outputCache.clear();
            lastUpdateTimes.clear();
            discoveredDevices.clear();

        } catch (Exception e) {
            LogLevelManager.logError(DRIVER_NAME, "Erreur lors du nettoyage: " + e.getMessage());
        }
    }

    private void recordError(int errorCode, String message) {
        totalErrors++;
        String errorKey = String.valueOf(errorCode);
        errorCounts.merge(errorKey, 1L, Long::sum);

        // Génération d'alarme selon le type d'erreur
        generateAlarmFromError(errorCode, message);

        LogLevelManager.logError(DRIVER_NAME, "[" + errorCode + "] " + message);
    }

    private void generateAlarmFromError(int errorCode, String message) {
        if (alarmHandler == null || machine == null) {
            return;
        }

        AlarmType alarmType;
        AlarmPriority priority;
        String deviceName = machine.getName();
        String stationName = config != null ? config.getStationName() : "Unknown";

        switch (errorCode) {
            case ErrorCodes.CONNECTION_LOST:
                alarmType = AlarmType.COMMUNICATION_ERROR;
                priority = AlarmPriority.HIGH;
                break;

            case ErrorCodes.DEVICE_NOT_FOUND:
                alarmType = AlarmType.DEVICE_FAILURE;
                priority = AlarmPriority.CRITICAL;
                break;

            case ErrorCodes.NETWORK_ERROR:
                alarmType = AlarmType.NETWORK_ERROR;
                priority = AlarmPriority.HIGH;
                break;

            case ErrorCodes.CYCLIC_DATA_ERROR:
                alarmType = AlarmType.CYCLIC_DATA_ERROR;
                priority = AlarmPriority.MEDIUM;
                break;

            case ErrorCodes.SLOT_CONFIGURATION_ERROR:
                alarmType = AlarmType.CONFIGURATION_ERROR;
                priority = AlarmPriority.MEDIUM;
                break;

            case ErrorCodes.GSDML_PARSING_ERROR:
                alarmType = AlarmType.CONFIGURATION_ERROR;
                priority = AlarmPriority.LOW;
                break;

            case ErrorCodes.TAG_READ_ERROR:
            case ErrorCodes.TAG_WRITE_ERROR:
                alarmType = AlarmType.COMMUNICATION_ERROR;
                priority = AlarmPriority.MEDIUM;
                break;

            case ErrorCodes.DEVICE_ALARM:
                alarmType = AlarmType.DIAGNOSTIC_ALARM;
                priority = AlarmPriority.HIGH;
                break;

            default:
                alarmType = AlarmType.DIAGNOSTIC_ALARM;
                priority = AlarmPriority.LOW;
        }

        // Génération de l'alarme avec contexte
        String detailedMessage = String.format("Code erreur: %d | Contexte: %s", errorCode, getErrorContext());

        alarmHandler.generateAlarm(
                deviceName,
                stationName,
                alarmType,
                priority,
                message,
                detailedMessage
        );
    }

    private String getErrorContext() {
        StringBuilder context = new StringBuilder();

        context.append("Connecté: ").append(isConnected());
        context.append(", Équipements: ").append(discoveredDevices.size());
        context.append(", Cycles: ").append(cyclicCycleCount);
        context.append(", Comm. cyclique: ").append(cyclicRunning.get());

        if (config != null) {
            context.append(", Interface: ").append(config.getNetworkInterface());
            context.append(", Intervalle: ").append(config.getCyclicInterval()).append("ms");
        }

        return context.toString();
    }

    @Override
    public void disconnect() {
        shouldReconnect = false;

        LogLevelManager.logInfo(DRIVER_NAME, "Déconnexion du driver Profinet...");

        // Génération d'alarme informative pour déconnexion volontaire
        if (alarmHandler != null && machine != null) {
            alarmHandler.generateAlarm(
                    machine.getName(),
                    config != null ? config.getStationName() : "Unknown",
                    AlarmType.DIAGNOSTIC_ALARM,
                    AlarmPriority.INFO,
                    "Déconnexion volontaire du driver Profinet",
                    "Arrêt demandé par l'utilisateur ou le système"
            );
        }

        cleanup();

        // Arrêt des threads
        if (executorService != null && !executorService.isShutdown()) {
            try {
                executorService.shutdown();
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (cyclicExecutor != null && !cyclicExecutor.isShutdown()) {
            try {
                cyclicExecutor.shutdown();
                if (!cyclicExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cyclicExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cyclicExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Effacement des alarmes actives lors de la déconnexion
        if (alarmHandler != null) {
            alarmHandler.clearDeviceAlarms(machine.getName());
        }

        connected = false;

        LogLevelManager.logInfo(DRIVER_NAME, "Déconnexion Profinet complète de "
                + (machine != null ? machine.getName() : "machine inconnue"));
    }

    @Override
    public boolean isConnected() {
        return connected && cyclicRunning.get() && !discoveredDevices.isEmpty();
    }

    // === MÉTHODES DE DIAGNOSTIC ET UTILITAIRES ===
    /**
     * Découverte manuelle des équipements Profinet sur le réseau
     */
    public Map<String, ProfinetDevice> discoverNetworkDevices() {
        LogLevelManager.logInfo(DRIVER_NAME, "Début de la découverte manuelle d'équipements Profinet");

        Map<String, ProfinetDevice> networkDevices = new HashMap<>();

        try {
            if (dcpClient == null) {
                dcpClient = new DCPClient(config);
                dcpClient.start();
            }

            CompletableFuture<List<ProfinetDevice>> discoveryFuture = dcpClient.discoverDevices();
            List<ProfinetDevice> devices = discoveryFuture.get(config.getDiscoveryTimeout(), TimeUnit.MILLISECONDS);

            for (ProfinetDevice device : devices) {
                networkDevices.put(device.getStationName(), device);
                LogLevelManager.logInfo(DRIVER_NAME, "Équipement découvert: " + device.getStationName()
                        + " (" + device.getIpAddress() + ") - " + device.getDeviceType());
            }

            LogLevelManager.logInfo(DRIVER_NAME, "Découverte terminée: " + networkDevices.size() + " équipement(s) trouvé(s)");

        } catch (Exception e) {
            LogLevelManager.logError(DRIVER_NAME, "Erreur lors de la découverte: " + e.getMessage());
        }

        return networkDevices;
    }

    /**
     * Test de connectivité avec un équipement spécifique
     */
    public String testDeviceConnection(String stationName) {
        StringBuilder result = new StringBuilder();
        result.append("=== Test Connexion Profinet: ").append(stationName).append(" ===\n");

        LogLevelManager.logDebug(DRIVER_NAME, "Test de connexion pour équipement: " + stationName);

        try {
            ProfinetDevice device = discoveredDevices.get(stationName);
            if (device == null) {
                result.append("ÉCHEC: Équipement non trouvé dans les équipements découverts\n");
                LogLevelManager.logError(DRIVER_NAME, "Équipement non trouvé: " + stationName);
                return result.toString();
            }

            result.append("Informations équipement:\n");
            result.append("  Nom de station: ").append(device.getStationName()).append("\n");
            result.append("  Adresse IP: ").append(device.getIpAddress()).append("\n");
            result.append("  Type d'équipement: ").append(device.getDeviceType()).append("\n");
            result.append("  Version: ").append(device.getVersion()).append("\n");

            // Test de ping
            long startTime = System.currentTimeMillis();
            boolean pingResult = networkAnalyzer.pingDevice(device.getIpAddress());
            long pingTime = System.currentTimeMillis() - startTime;

            result.append("Test Ping: ").append(pingResult ? "SUCCÈS" : "ÉCHEC").append(" (").append(pingTime).append("ms)\n");

            if (!pingResult) {
                result.append("L'équipement n'est pas accessible via ping\n");
                return result.toString();
            }

            // Test de connexion Profinet
            startTime = System.currentTimeMillis();
            boolean connectionTest = deviceManager.testConnection(device);
            long connectionTime = System.currentTimeMillis() - startTime;

            result.append("Test Connexion Profinet: ").append(connectionTest ? "SUCCÈS" : "ÉCHEC")
                    .append(" (").append(connectionTime).append("ms)\n");

            if (connectionTest) {
                result.append("✅ Équipement accessible et opérationnel\n");

                // Test de lecture d'identification
                try {
                    Map<String, Object> deviceInfo = deviceManager.readDeviceIdentification(device);
                    result.append("Informations détaillées:\n");
                    for (Map.Entry<String, Object> info : deviceInfo.entrySet()) {
                        result.append("  ").append(info.getKey()).append(": ").append(info.getValue()).append("\n");
                    }
                } catch (Exception e) {
                    result.append("⚠️ Erreur lecture identification: ").append(e.getMessage()).append("\n");
                }
            } else {
                result.append("❌ Équipement non accessible via Profinet\n");
            }

        } catch (Exception e) {
            result.append("EXCEPTION: ").append(e.getMessage()).append("\n");
            LogLevelManager.logError(DRIVER_NAME, "Exception lors du test de connexion: " + e.getMessage());
        }

        return result.toString();
    }

    /**
     * Analyse du réseau Profinet
     */
    public String performNetworkAnalysis() {
        StringBuilder analysis = new StringBuilder();
        analysis.append("=== Analyse Réseau Profinet ===\n");

        LogLevelManager.logDebug(DRIVER_NAME, "Début de l'analyse réseau");

        try {
            // Analyse de l'interface réseau
            Map<String, Object> interfaceInfo = networkAnalyzer.analyzeInterface();
            analysis.append("Interface réseau:\n");
            for (Map.Entry<String, Object> info : interfaceInfo.entrySet()) {
                analysis.append("  ").append(info.getKey()).append(": ").append(info.getValue()).append("\n");
            }

            // Statistiques du trafic
            Map<String, Long> trafficStats = networkAnalyzer.getTrafficStatistics();
            analysis.append("\nStatistiques trafic:\n");
            for (Map.Entry<String, Long> stat : trafficStats.entrySet()) {
                analysis.append("  ").append(stat.getKey()).append(": ").append(stat.getValue()).append("\n");
            }

            // Qualité de la communication cyclique
            if (cyclicRunning.get()) {
                analysis.append("\nCommunication cyclique:\n");
                analysis.append("  Cycles exécutés: ").append(cyclicCycleCount).append("\n");
                analysis.append("  Intervalle configuré: ").append(config.getCyclicInterval()).append("ms\n");

                // Calcul de la charge réseau
                double networkLoad = networkAnalyzer.calculateNetworkLoad();
                analysis.append("  Charge réseau: ").append(String.format("%.2f%%", networkLoad)).append("\n");
            }

            // État des équipements
            analysis.append("\nÉquipements connectés: ").append(discoveredDevices.size()).append("\n");
            for (ProfinetDevice device : discoveredDevices.values()) {
                boolean deviceOk = deviceManager.isDeviceHealthy(device);
                analysis.append("  ").append(device.getStationName()).append(": ")
                        .append(deviceOk ? "OK" : "PROBLÈME").append("\n");
            }

        } catch (Exception e) {
            analysis.append("Erreur lors de l'analyse: ").append(e.getMessage()).append("\n");
            LogLevelManager.logError(DRIVER_NAME, "Erreur analyse réseau: " + e.getMessage());
        }

        return analysis.toString();
    }

    /**
     * Lecture en batch optimisée pour Profinet
     */
    public Map<String, Object> readBatch(List<Tag> tags) {
        Map<String, Object> results = new HashMap<>();

        if (!isConnected() || tags == null || tags.isEmpty()) {
            LogLevelManager.logDebug(DRIVER_NAME, "Batch read impossible: "
                    + (isConnected() ? "pas de tags" : "non connecté"));
            return results;
        }

        LogLevelManager.logDebug(DRIVER_NAME, "Début lecture batch Profinet de " + tags.size() + " tags");

        // Regrouper les tags par équipement pour optimiser
        Map<String, List<Tag>> tagsByDevice = groupTagsByDevice(tags);

        for (Map.Entry<String, List<Tag>> entry : tagsByDevice.entrySet()) {
            String deviceName = entry.getKey();
            List<Tag> deviceTags = entry.getValue();

            LogLevelManager.logTrace(DRIVER_NAME, "Lecture batch équipement: " + deviceName
                    + " (" + deviceTags.size() + " tags)");

            // Lecture optimisée par équipement
            for (Tag tag : deviceTags) {
                Object value = read(tag);
                if (value != null) {
                    results.put(tag.getName(), value);
                }
            }
        }

        LogLevelManager.logInfo(DRIVER_NAME, "Lecture batch terminée: " + results.size() + "/"
                + tags.size() + " tags réussis");
        return results;
    }

    private Map<String, List<Tag>> groupTagsByDevice(List<Tag> tags) {
        Map<String, List<Tag>> grouped = new HashMap<>();

        for (Tag tag : tags) {
            String deviceName = extractStationName(tag);
            grouped.computeIfAbsent(deviceName, k -> new ArrayList<>()).add(tag);
        }

        return grouped;
    }

    /**
     * Obtient des informations de diagnostic détaillées
     */
    public String getDiagnosticInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== Diagnostic Driver Profinet ===\n");
        info.append("Machine: ").append(machine != null ? machine.getName() : "Non configurée").append("\n");
        info.append("Station: ").append(config != null ? config.getStationName() : "N/A").append("\n");
        info.append("Interface réseau: ").append(config != null ? config.getNetworkInterface() : "N/A").append("\n");
        info.append("Connecté: ").append(isConnected()).append("\n");
        info.append("Communication cyclique: ").append(cyclicRunning.get()).append("\n");
        info.append("Tentatives de reconnexion: ").append(reconnectAttempts).append("/").append(MAX_RECONNECT_ATTEMPTS).append("\n");

        // Ajout des informations d'alarmes
        if (alarmHandler != null) {
            info.append("\n=== État des Alarmes ===\n");
            info.append("Alarmes actives: ").append(getActiveAlarms().size()).append("\n");
            info.append("Alarmes critiques: ").append(getCriticalAlarmsCount()).append("\n");
            info.append("Gestionnaire d'alarmes: Opérationnel\n");

            Map<String, Object> alarmStats = alarmHandler.getAlarmStatistics();
            info.append("Total générées: ").append(alarmStats.get("totalGenerated")).append("\n");
            info.append("Taux acquittement: ").append(String.format("%.1f%%",
                    (Double) alarmStats.get("acknowledgmentRate") * 100)).append("\n");
        } else {
            info.append("\n=== État des Alarmes ===\n");
            info.append("Gestionnaire d'alarmes: Non disponible\n");
        }

        // Métriques de performance
        info.append("\n=== Métriques de Performance ===\n");
        info.append("Total lectures: ").append(totalReads).append("\n");
        info.append("Total écritures: ").append(totalWrites).append("\n");
        info.append("Total erreurs: ").append(totalErrors).append("\n");
        info.append("Temps de connexion: ").append(connectionTime).append("ms\n");
        info.append("Cycles cycliques: ").append(cyclicCycleCount).append("\n");
        info.append("Données en cache (entrée): ").append(inputCache.size()).append("\n");
        info.append("Données en cache (sortie): ").append(outputCache.size()).append("\n");

        // Équipements découverts
        info.append("\n=== Équipements Profinet ===\n");
        info.append("Équipements découverts: ").append(discoveredDevices.size()).append("\n");
        for (ProfinetDevice device : discoveredDevices.values()) {
            info.append("  - ").append(device.getStationName()).append(" (")
                    .append(device.getIpAddress()).append(") - ").append(device.getDeviceType()).append("\n");
        }

        // Configuration
        if (config != null) {
            info.append("\n=== Configuration ===\n");
            info.append("Intervalle cyclique: ").append(config.getCyclicInterval()).append("ms\n");
            info.append("Timeout connexion: ").append(config.getConnectionTimeout()).append("ms\n");
            info.append("Timeout découverte: ").append(config.getDiscoveryTimeout()).append("ms\n");
        }

        // Détail des erreurs
        if (!errorCounts.isEmpty()) {
            info.append("\n=== Répartition des Erreurs ===\n");
            for (Map.Entry<String, Long> entry : errorCounts.entrySet()) {
                info.append("Code ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" occurrences\n");
            }
        }

        LogLevelManager.logDebug(DRIVER_NAME, "Diagnostic généré");
        return info.toString();
    }

    /**
     * Réinitialise les métriques de performance
     */
    public void resetMetrics() {
        long oldReads = totalReads;
        long oldWrites = totalWrites;
        long oldErrors = totalErrors;
        long oldCycles = cyclicCycleCount;

        totalReads = 0;
        totalWrites = 0;
        totalErrors = 0;
        cyclicCycleCount = 0;
        errorCounts.clear();

        LogLevelManager.logInfo(DRIVER_NAME, "Métriques réinitialisées (anciens: " + oldReads + " lectures, "
                + oldWrites + " écritures, " + oldErrors + " erreurs, " + oldCycles + " cycles)");
    }

    /**
     * Nettoie les caches
     */
    public void clearCache() {
        int inputSize = inputCache.size();
        int outputSize = outputCache.size();

        inputCache.clear();
        outputCache.clear();
        lastUpdateTimes.clear();

        LogLevelManager.logInfo(DRIVER_NAME, "Caches nettoyés (" + inputSize + " entrées, " + outputSize + " sorties)");
    }

    /**
     * Obtient les métriques de performance
     */
    public PerformanceMetrics getPerformanceMetrics() {
        return new PerformanceMetrics(
                totalReads,
                totalWrites,
                totalErrors,
                connectionTime,
                cyclicCycleCount,
                inputCache.size(),
                outputCache.size(),
                discoveredDevices.size(),
                new HashMap<>(errorCounts)
        );
    }

    /**
     * Configuration des paramètres du driver
     */
    public void setCyclicInterval(int intervalMs) {
        if (config != null && intervalMs > 0) {
            config.setCyclicInterval(intervalMs);
            LogLevelManager.logInfo(DRIVER_NAME, "Intervalle cyclique configuré à " + intervalMs + "ms");

            // Si la communication cyclique est active, redémarrer avec le nouvel intervalle
            if (cyclicRunning.get()) {
                LogLevelManager.logInfo(DRIVER_NAME, "Redémarrage de la communication cyclique avec nouvel intervalle");
                cyclicRunning.set(false);
                if (cyclicTask != null) {
                    cyclicTask.cancel(true);
                }
                startCyclicCommunication();
            }
        }
    }

    public void setConnectionTimeout(int timeoutMs) {
        if (config != null && timeoutMs > 0) {
            config.setConnectionTimeout(timeoutMs);
            LogLevelManager.logInfo(DRIVER_NAME, "Timeout de connexion configuré à " + timeoutMs + "ms");
        }
    }

    /**
     * Obtient le gestionnaire d'alarmes
     */
    public AlarmHandler getAlarmHandler() {
        return alarmHandler;
    }

    /**
     * Obtient les alarmes actives pour cette machine
     */
    public List<ProfinetAlarm> getActiveAlarms() {
        if (alarmHandler == null || machine == null) {
            return new ArrayList<>();
        }
        return alarmHandler.getDeviceAlarms(machine.getName());
    }

    /**
     * Obtient le nombre d'alarmes critiques actives
     */
    public long getCriticalAlarmsCount() {
        if (alarmHandler == null) {
            return 0;
        }
        return alarmHandler.getActiveAlarmsByPriority(AlarmPriority.CRITICAL).size();
    }

    /**
     * Vérifie s'il y a des alarmes critiques
     */
    public boolean hasCriticalAlarms() {
        return getCriticalAlarmsCount() > 0;
    }

    /**
     * Acquitte toutes les alarmes de cette machine
     */
    public int acknowledgeAllMachineAlarms() {
        if (alarmHandler == null || machine == null) {
            return 0;
        }

        List<ProfinetAlarm> machineAlarms = alarmHandler.getDeviceAlarms(machine.getName());
        int acknowledgedCount = 0;

        for (ProfinetAlarm alarm : machineAlarms) {
            if (alarm.getState() == AlarmState.ACTIVE) {
                if (alarmHandler.acknowledgeAlarm(alarm.getId())) {
                    acknowledgedCount++;
                }
            }
        }

        return acknowledgedCount;
    }

    /**
     * Génère un rapport d'alarmes spécifique à cette machine
     */
    public String getMachineAlarmReport() {
        if (alarmHandler == null || machine == null) {
            return "Gestionnaire d'alarmes non disponible";
        }

        StringBuilder report = new StringBuilder();
        report.append("=== Rapport d'Alarmes ").append(machine.getName()).append(" ===\n");

        List<ProfinetAlarm> machineAlarms = alarmHandler.getDeviceAlarms(machine.getName());

        // Compteurs par priorité
        Map<AlarmPriority, Long> priorityCount = machineAlarms.stream()
                .collect(Collectors.groupingBy(
                        ProfinetAlarm::getPriority,
                        Collectors.counting()
                ));

        report.append("Alarmes actives: ").append(machineAlarms.size()).append("\n");

        for (AlarmPriority priority : AlarmPriority.values()) {
            long count = priorityCount.getOrDefault(priority, 0L);
            if (count > 0) {
                report.append("  ").append(priority.getDescription()).append(": ").append(count).append("\n");
            }
        }

        // Détail des alarmes critiques et hautes
        List<ProfinetAlarm> urgentAlarms = machineAlarms.stream()
                .filter(alarm -> alarm.getPriority() == AlarmPriority.CRITICAL
                || alarm.getPriority() == AlarmPriority.HIGH)
                .sorted((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()))
                .collect(Collectors.toList());

        if (!urgentAlarms.isEmpty()) {
            report.append("\nAlarmes urgentes:\n");
            for (ProfinetAlarm alarm : urgentAlarms) {
                report.append("  [").append(alarm.getPriority().name()).append("] ")
                        .append(alarm.getType().getDescription()).append(" - ")
                        .append(alarm.getMessage()).append("\n");
            }
        }

        return report.toString();
    }

    // === CLASSES INTERNES ===
    /**
     * Classe pour les métriques de performance Profinet
     */
    public static class PerformanceMetrics {

        public final long totalReads;
        public final long totalWrites;
        public final long totalErrors;
        public final long connectionTime;
        public final long cyclicCycles;
        public final int inputCacheSize;
        public final int outputCacheSize;
        public final int devicesCount;
        public final Map<String, Long> errorCounts;

        public PerformanceMetrics(long totalReads, long totalWrites, long totalErrors,
                long connectionTime, long cyclicCycles, int inputCacheSize,
                int outputCacheSize, int devicesCount, Map<String, Long> errorCounts) {
            this.totalReads = totalReads;
            this.totalWrites = totalWrites;
            this.totalErrors = totalErrors;
            this.connectionTime = connectionTime;
            this.cyclicCycles = cyclicCycles;
            this.inputCacheSize = inputCacheSize;
            this.outputCacheSize = outputCacheSize;
            this.devicesCount = devicesCount;
            this.errorCounts = errorCounts;
        }

        public double getErrorRate() {
            long total = totalReads + totalWrites;
            return total > 0 ? (double) totalErrors / total : 0.0;
        }

        public double getCyclesPerSecond() {
            return connectionTime > 0 ? (double) cyclicCycles / (connectionTime / 1000.0) : 0.0;
        }

        @Override
        public String toString() {
            return String.format("ProfinetMetrics{reads=%d, writes=%d, errors=%d, errorRate=%.2f%%, "
                    + "connectionTime=%dms, cycles=%d, devices=%d, cache=[in:%d,out:%d]}",
                    totalReads, totalWrites, totalErrors, getErrorRate() * 100,
                    connectionTime, cyclicCycles, devicesCount, inputCacheSize, outputCacheSize);
        }
    }
}
