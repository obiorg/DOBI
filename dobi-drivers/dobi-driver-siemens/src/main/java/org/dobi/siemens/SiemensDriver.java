package org.dobi.siemens;

import org.dobi.api.IDriver;
import org.dobi.entities.Machine;
import org.dobi.entities.Tag;
import org.dobi.moka7.S7;
import org.dobi.moka7.S7Client;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.dobi.logging.LogLevelManager;
import org.dobi.logging.LogLevelManager.LogLevel;

public class SiemensDriver implements IDriver {

    // Constantes de configuration
    private static final int DEFAULT_CONNECTION_TIMEOUT = 5000; // 5 secondes
    private static final int DEFAULT_READ_TIMEOUT = 3000; // 3 secondes
    private static final int DEFAULT_WRITE_TIMEOUT = 3000; // 3 secondes
    private static final int RECONNECT_DELAY_MS = 3000;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int MAX_BATCH_SIZE = 20; // Limite pour éviter les timeouts
    private static final int HEALTH_CHECK_INTERVAL_MS = 30000; // 30 secondes

    // Codes d'erreur standardisés
    public static final class ErrorCodes {

        public static final int SUCCESS = 0;
        public static final int CONNECTION_LOST = 1001;
        public static final int INVALID_CONFIGURATION = 1002;
        public static final int TAG_READ_ERROR = 1003;
        public static final int TAG_WRITE_ERROR = 1004;
        public static final int MEMORY_ACCESS_ERROR = 1005;
        public static final int TYPE_CONVERSION_ERROR = 1006;
        public static final int NETWORK_ERROR = 1007;
        public static final int TIMEOUT_ERROR = 1008;
        public static final int AUTHENTICATION_ERROR = 1009;
        public static final int RESOURCE_UNAVAILABLE = 1010;
    }

    private Machine machine;
    private final S7Client client;
    private volatile boolean connected = false;
    private volatile boolean shouldReconnect = true;
    private volatile int reconnectAttempts = 0;

    // Métriques de performance
    private long totalReads = 0;
    private long totalWrites = 0;
    private long totalErrors = 0;
    private long connectionTime = 0;
    private final Map<String, Long> errorCounts = new ConcurrentHashMap<>();

    // Configuration personnalisable
    private int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
    private int readTimeout = DEFAULT_READ_TIMEOUT;
    private int writeTimeout = DEFAULT_WRITE_TIMEOUT;

    // Cache pour optimiser les accès répétés
    private final Map<String, TagMemoryInfo> tagCache = new ConcurrentHashMap<>();

    // Surveillance de santé
    private Timer healthCheckTimer;

    private static final String DRIVER_NAME = "SIEMENS-S7";

    public SiemensDriver() {
        this.client = new S7Client();
        initializeHealthCheck();
    }

    @Override
    public void configure(Machine machine) {
        this.machine = machine;
        loadConfiguration();
        LogLevelManager.logInfo(DRIVER_NAME, "Configuration du driver pour la machine: " + machine.getName());
    }

    private void loadConfiguration() {
        if (machine != null) {
            LogLevelManager.logDebug(DRIVER_NAME, "Configuration chargée pour " + machine.getName());
        }
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

            int rack = (machine.getRack() != null) ? machine.getRack() : 0;
            int slot = (machine.getSlot() != null) ? machine.getSlot() : 1;

            LogLevelManager.logInfo(DRIVER_NAME, "Tentative de connexion à " + machine.getAddress()
                    + " (Rack: " + rack + ", Slot: " + slot + ")");

            // Connexion
            int result = client.ConnectTo(machine.getAddress(), rack, slot);

            if (result == 0) {
                connected = true;
                connectionTime = System.currentTimeMillis() - startTime;
                reconnectAttempts = 0;

                LogLevelManager.logInfo(DRIVER_NAME, "Connexion établie avec " + machine.getName()
                        + " en " + connectionTime + "ms");

                // Vérification de la qualité de connexion
                performConnectionDiagnostics();

                // Mise en place de la surveillance de connexion
                setupConnectionMonitoring();

                return true;
            } else {
                String errorMsg = S7Client.ErrorText(result);
                recordError(mapS7ErrorToCode(result), "Échec de connexion: " + errorMsg);
                return false;
            }

        } catch (Exception e) {
            recordError(ErrorCodes.NETWORK_ERROR, "Exception lors de la connexion: " + e.getMessage());
            return false;
        }
    }

    private void performConnectionDiagnostics() {
        try {
            LogLevelManager.logDebug(DRIVER_NAME, "Test de qualité de connexion...");

            // Test de la qualité de connexion avec une lecture simple
            byte[] buffer = new byte[1];
            int result = client.ReadArea(S7.S7AreaMK, 0, 0, 1, buffer);

            if (result != 0) {
                LogLevelManager.logError(DRIVER_NAME, "Test de connexion échoué: " + S7Client.ErrorText(result));
            } else {
                LogLevelManager.logDebug(DRIVER_NAME, "Test de connexion réussi");
            }
        } catch (Exception e) {
            LogLevelManager.logError(DRIVER_NAME, "Erreur lors du test de connexion: " + e.getMessage());
        }
    }

    private void setupConnectionMonitoring() {
        LogLevelManager.logDebug(DRIVER_NAME, "Démarrage du monitoring de connexion");
        CompletableFuture.runAsync(this::monitorConnection);
    }

    private void monitorConnection() {
        LogLevelManager.logTrace(DRIVER_NAME, "Thread de monitoring de connexion démarré");

        while (shouldReconnect && connected) {
            try {
                Thread.sleep(HEALTH_CHECK_INTERVAL_MS);

                if (!isConnected()) {
                    LogLevelManager.logError(DRIVER_NAME, "Perte de connexion détectée");
                    connected = false;
                    attemptReconnection();
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        LogLevelManager.logTrace(DRIVER_NAME, "Thread de monitoring de connexion arrêté");
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
                LogLevelManager.logInfo(DRIVER_NAME, "Tentative de reconnexion " + currentAttempt + "/" + MAX_RECONNECT_ATTEMPTS);

                // Attente progressive (backoff exponentiel)
                int delay = RECONNECT_DELAY_MS * (int) Math.pow(2, Math.min(currentAttempt - 1, 3));
                LogLevelManager.logDebug(DRIVER_NAME, "Attente de " + delay + "ms avant reconnexion");
                Thread.sleep(delay);

                // Nettoyage de l'ancienne connexion
                if (client.Connected) {
                    LogLevelManager.logDebug(DRIVER_NAME, "Fermeture de l'ancienne connexion");
                    client.Disconnect();
                }

                if (establishConnection()) {
                    LogLevelManager.logInfo(DRIVER_NAME, "Reconnexion réussie");
                } else {
                    attemptReconnection();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LogLevelManager.logError(DRIVER_NAME, "Erreur lors de la reconnexion: " + e.getMessage());
                attemptReconnection();
            }
        });
    }

    @Override
    public Object read(Tag tag) {
        if (!isConnected()) {
            recordError(ErrorCodes.CONNECTION_LOST, "Client non connecté pour la lecture du tag: " + tag.getName());
            return null;
        }

        if (!validateTagConfiguration(tag)) {
            recordError(ErrorCodes.INVALID_CONFIGURATION, "Configuration invalide pour le tag: " + tag.getName());
            return null;
        }

        try {
            TagMemoryInfo memInfo = getOrCreateTagMemoryInfo(tag);
            byte[] buffer = new byte[memInfo.bytesToRead];

            LogLevelManager.logDebug(DRIVER_NAME, "Lecture du tag '" + tag.getName()
                    + "' (Area: " + getAreaName(memInfo.area) + ", DB: " + memInfo.dbNumber
                    + ", Offset: " + memInfo.byteAddress + ", Size: " + memInfo.bytesToRead + ")");

            int result = client.ReadArea(memInfo.area, memInfo.dbNumber,
                    memInfo.byteAddress, memInfo.bytesToRead, buffer);

            if (result == 0) {
                totalReads++;
                Object value = convertBufferToValue(buffer, tag);
                LogLevelManager.logTrace(DRIVER_NAME, "Succès lecture '" + tag.getName() + "': " + value);
                return value;
            } else {
                String errorMsg = S7Client.ErrorText(result);
                recordError(mapS7ErrorToCode(result), "Erreur de lecture pour " + tag.getName() + ": " + errorMsg);

                // Si erreur de connexion, marquer comme déconnecté
                if (isConnectionError(result)) {
                    connected = false;
                    attemptReconnection();
                }
                return null;
            }

        } catch (Exception e) {
            recordError(ErrorCodes.TAG_READ_ERROR, "Exception lors de la lecture du tag " + tag.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Lecture en batch de plusieurs tags pour optimiser les performances
     */
    public Map<String, Object> readBatch(List<Tag> tags) {
        Map<String, Object> results = new HashMap<>();

        if (!isConnected() || tags == null || tags.isEmpty()) {
            return results;
        }

        LogLevelManager.logDebug(DRIVER_NAME, "Début lecture batch de " + tags.size() + " tags");

        // Regrouper les tags par zone mémoire pour optimiser
        Map<String, List<Tag>> tagsByMemoryArea = groupTagsByMemoryArea(tags);

        for (Map.Entry<String, List<Tag>> entry : tagsByMemoryArea.entrySet()) {
            List<Tag> areaTags = entry.getValue();
            LogLevelManager.logTrace(DRIVER_NAME, "Traitement zone mémoire: " + entry.getKey() + " (" + areaTags.size() + " tags)");

            // Traiter par batch pour éviter les timeouts
            for (int i = 0; i < areaTags.size(); i += MAX_BATCH_SIZE) {
                int endIndex = Math.min(i + MAX_BATCH_SIZE, areaTags.size());
                List<Tag> batchTags = areaTags.subList(i, endIndex);

                for (Tag tag : batchTags) {
                    Object value = read(tag);
                    if (value != null) {
                        results.put(tag.getName(), value);
                    }
                }
            }
        }

        LogLevelManager.logInfo(DRIVER_NAME, "Lecture batch terminée: " + results.size() + "/" + tags.size() + " tags réussis");
        return results;
    }

    private Map<String, List<Tag>> groupTagsByMemoryArea(List<Tag> tags) {
        Map<String, List<Tag>> grouped = new HashMap<>();

        for (Tag tag : tags) {
            if (validateTagConfiguration(tag)) {
                String memoryKey = tag.getMemory().getName() + "_"
                        + (tag.getDbNumber() != null ? tag.getDbNumber() : 0);
                grouped.computeIfAbsent(memoryKey, k -> new ArrayList<>()).add(tag);
            }
        }

        return grouped;
    }

    @Override
    public void write(Tag tag, Object value) {
        if (!isConnected()) {
            recordError(ErrorCodes.CONNECTION_LOST, "Client non connecté pour l'écriture du tag: " + tag.getName());
            return;
        }

        if (!validateTagConfiguration(tag)) {
            recordError(ErrorCodes.INVALID_CONFIGURATION, "Configuration invalide pour le tag: " + tag.getName());
            return;
        }

        try {
            TagMemoryInfo memInfo = getOrCreateTagMemoryInfo(tag);
            byte[] buffer = convertValueToBuffer(value, tag);

            if (buffer == null) {
                recordError(ErrorCodes.TYPE_CONVERSION_ERROR, "Impossible de convertir la valeur pour le tag: " + tag.getName());
                return;
            }

            LogLevelManager.logDebug(DRIVER_NAME, "Écriture du tag '" + tag.getName()
                    + "' avec valeur: " + value + " (Size: " + buffer.length + " bytes)");

            int result = client.WriteArea(memInfo.area, memInfo.dbNumber,
                    memInfo.byteAddress, buffer.length, buffer);

            if (result == 0) {
                totalWrites++;
                LogLevelManager.logInfo(DRIVER_NAME, "Écriture réussie pour '" + tag.getName() + "'");
            } else {
                String errorMsg = S7Client.ErrorText(result);
                recordError(mapS7ErrorToCode(result), "Erreur d'écriture pour " + tag.getName() + ": " + errorMsg);

                // Si erreur de connexion, marquer comme déconnecté
                if (isConnectionError(result)) {
                    connected = false;
                    attemptReconnection();
                }
            }

        } catch (Exception e) {
            recordError(ErrorCodes.TAG_WRITE_ERROR, "Exception lors de l'écriture du tag " + tag.getName() + ": " + e.getMessage());
        }
    }

    private byte[] convertValueToBuffer(Object value, Tag tag) {
        if (value == null) {
            return null;
        }

        try {
            String typeName = tag.getType().getType().toUpperCase();
            Integer bit = tag.getBitAddress();

            switch (typeName) {
                case "BOOL":
                    byte[] boolBuffer = new byte[1];
                    if (bit != null) {
                        boolean boolValue = convertToBoolean(value);
                        S7.SetBitAt(boolBuffer, 0, bit, boolValue);
                    }
                    return boolBuffer;

                case "BYTE":
                case "SINT":
                case "USINT":
                    byte[] byteBuffer = new byte[1];
                    byteBuffer[0] = convertToByte(value);
                    return byteBuffer;

                case "WORD":
                case "INT":
                case "UINT":
                    byte[] wordBuffer = new byte[2];
                    if (typeName.equals("INT")) {
                        S7.SetShortAt(wordBuffer, 0, convertToShort(value));
                    } else {
                        S7.SetWordAt(wordBuffer, 0, convertToInt(value));
                    }
                    return wordBuffer;

                case "DWORD":
                case "DINT":
                case "UDINT":
                    byte[] dwordBuffer = new byte[4];
                    if (typeName.equals("DINT")) {
                        S7.SetDIntAt(dwordBuffer, 0, convertToInt(value));
                    } else {
                        S7.SetDWordAt(dwordBuffer, 0, convertToLong(value));
                    }
                    return dwordBuffer;

                case "REAL":
                case "FLOAT":
                    byte[] floatBuffer = new byte[4];
                    S7.SetFloatAt(floatBuffer, 0, convertToFloat(value));
                    return floatBuffer;

                case "STRING":
                    return convertStringToS7Buffer(convertToString(value));

                case "DATETIME":
                    byte[] dateBuffer = new byte[8];
                    Date dateValue = convertToDate(value);
                    S7.SetDateAt(dateBuffer, 0, dateValue);
                    return dateBuffer;

                default:
                    System.err.println("[S7] Type non supporté pour l'écriture: " + typeName);
                    return null;
            }

        } catch (Exception e) {
            System.err.println("[S7] Erreur de conversion pour l'écriture du tag " + tag.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private byte[] convertStringToS7Buffer(String str) {
        if (str == null) {
            str = "";
        }

        byte[] strBytes = str.getBytes();
        byte[] buffer = new byte[strBytes.length + 2]; // +2 pour la longueur max et actuelle

        buffer[0] = (byte) Math.min(254, strBytes.length); // Longueur max
        buffer[1] = (byte) strBytes.length; // Longueur actuelle

        System.arraycopy(strBytes, 0, buffer, 2, strBytes.length);

        return buffer;
    }

    // Méthodes de conversion pour l'écriture
    private boolean convertToBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return false;
    }

    private byte convertToByte(Object value) {
        if (value instanceof Number) {
            return ((Number) value).byteValue();
        }
        if (value instanceof String) {
            return Byte.parseByte((String) value);
        }
        return 0;
    }

    private short convertToShort(Object value) {
        if (value instanceof Number) {
            return ((Number) value).shortValue();
        }
        if (value instanceof String) {
            return Short.parseShort((String) value);
        }
        return 0;
    }

    private int convertToInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            return Integer.parseInt((String) value);
        }
        return 0;
    }

    private long convertToLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            return Long.parseLong((String) value);
        }
        return 0;
    }

    private float convertToFloat(Object value) {
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        if (value instanceof String) {
            return Float.parseFloat((String) value);
        }
        return 0.0f;
    }

    private String convertToString(Object value) {
        return value != null ? value.toString() : "";
    }

    private Date convertToDate(Object value) {
        if (value instanceof Date) {
            return (Date) value;
        }
        if (value instanceof LocalDateTime) {
            return Date.from(((LocalDateTime) value).atZone(ZoneId.systemDefault()).toInstant());
        }
        if (value instanceof String) {
            // Parsing basique, pourrait être amélioré
            try {
                return new Date(Long.parseLong((String) value));
            } catch (NumberFormatException e) {
                return new Date();
            }
        }
        return new Date();
    }

    private TagMemoryInfo getOrCreateTagMemoryInfo(Tag tag) {
        String cacheKey = tag.getId() + "_" + tag.getName();

        return tagCache.computeIfAbsent(cacheKey, k -> {
            TagMemoryInfo info = new TagMemoryInfo();
            info.area = getS7Area(tag.getMemory().getName());
            info.dbNumber = (info.area == S7.S7AreaDB && tag.getDbNumber() != null) ? tag.getDbNumber() : 0;
            info.byteAddress = tag.getByteAddress();
            info.bytesToRead = getBytesToRead(tag.getType().getType());
            info.bitAddress = tag.getBitAddress();
            return info;
        });
    }

    private boolean validateTagConfiguration(Tag tag) {
        if (tag == null) {
            return false;
        }
        if (tag.getByteAddress() == null) {
            return false;
        }
        if (tag.getMemory() == null || tag.getMemory().getName() == null) {
            return false;
        }
        if (tag.getType() == null || tag.getType().getType() == null) {
            return false;
        }

        int bytesToRead = getBytesToRead(tag.getType().getType());
        if (bytesToRead == 0) {
            System.err.println("[S7] Type de données non géré: " + tag.getType().getType());
            return false;
        }

        return true;
    }

    private Object convertBufferToValue(byte[] buffer, Tag tag) {
        String typeName = tag.getType().getType().toUpperCase();
        Integer bit = tag.getBitAddress();

        LogLevelManager.logTrace(DRIVER_NAME, "Conversion buffer vers " + typeName + " pour tag " + tag.getName());

        try {
            switch (typeName) {
                case "BOOL":
                    boolean boolResult = bit != null ? S7.GetBitAt(buffer, 0, bit) : false;
                    LogLevelManager.logTrace(DRIVER_NAME, "Conversion BOOL: " + boolResult + " (bit " + bit + ")");
                    return boolResult;
                case "BYTE":
                case "SINT":
                case "USINT":
                    return buffer[0];
                case "WORD":
//                case "UINT":
//                    return S7.GetWordAt(buffer, 0);
                case "INT":                     // Cas spécifique pour INT (Short)
                    return S7.GetShortAt(buffer, 0);
                case "UINT": // Cas spécifique pour UINT (Word)
                    return S7.GetWordAt(buffer, 0) & 0xFFFF;
                case "DWORD":
                    return S7.GetDWordAt(buffer, 0);
                case "DINT":
                    return S7.GetDIntAt(buffer, 0);
                case "UDINT":
                    return S7.GetDWordAt(buffer, 0) & 0xFFFFFFFFL;
                case "REAL":
                case "FLOAT":
                    float floatResult = S7.GetFloatAt(buffer, 0);
                    LogLevelManager.logTrace(DRIVER_NAME, "Conversion REAL: " + floatResult);
                    return floatResult;
                case "STRING":
                    String stringResult = extractStringFromS7Buffer(buffer);
                    LogLevelManager.logTrace(DRIVER_NAME, "Conversion STRING: '" + stringResult + "'");
                    return stringResult;
                case "DATETIME":
                    Date date = S7.GetDateAt(buffer, 0);
                    LocalDateTime dateResult = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
                    LogLevelManager.logTrace(DRIVER_NAME, "Conversion DATETIME: " + dateResult);
                    return dateResult;
                default:
                    recordError(ErrorCodes.TYPE_CONVERSION_ERROR, "Type inconnu pour conversion: " + typeName);
                    return "Type inconnu: " + typeName;
            }
        } catch (Exception e) {
            recordError(ErrorCodes.TYPE_CONVERSION_ERROR, "Erreur de conversion pour le tag " + tag.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private String extractStringFromS7Buffer(byte[] buffer) {
        if (buffer.length < 2) {
            return "";
        }

        int maxLength = buffer[0] & 0xFF;
        int actualLength = buffer[1] & 0xFF;

        if (actualLength > maxLength || actualLength > buffer.length - 2) {
            actualLength = Math.min(maxLength, buffer.length - 2);
        }

        return new String(buffer, 2, actualLength);
    }

    private int getBytesToRead(String typeName) {
        switch (typeName.toUpperCase()) {
            case "BOOL":
            case "BYTE":
            case "SINT":
            case "USINT":
                return 1;
            case "WORD":
            case "INT":
            case "UINT":
                return 2;
            case "DWORD":
            case "DINT":
            case "UDINT":
            case "REAL":
            case "FLOAT":
                return 4;
            case "STRING":
                return 256; // Taille max par défaut, pourrait être configurée
            case "DATETIME":
                return 8;
            default:
                return 0;
        }
    }

    private int getS7Area(String memoryName) {
        switch (memoryName.toUpperCase()) {
            case "DB":
                return S7.S7AreaDB;
            case "M":
            case "MK": // AJOUTÉ : Reconnaître "MK" pour Merkers
                return S7.S7AreaMK;
            case "E":
            case "I":
                return S7.S7AreaPE;
            case "A":
            case "Q":
                return S7.S7AreaPA;
            case "T":
                return S7.S7AreaTM;
            case "C":
                return S7.S7AreaCT;
            default:
                System.err.println("[S7] Zone mémoire non reconnue: " + memoryName + ", utilisation de DB par défaut");
                return S7.S7AreaDB;
        }
    }

    private boolean isConnectionError(int s7ErrorCode) {
        // Codes d'erreur Moka7/S7 qui indiquent une perte de connexion
        return s7ErrorCode == S7Client.errTCPConnectionFailed
                || s7ErrorCode == S7Client.errTCPDataSend
                || s7ErrorCode == S7Client.errTCPDataRecv
                || s7ErrorCode == S7Client.errTCPDataRecvTout
                || s7ErrorCode == S7Client.errTCPConnectionReset
                || s7ErrorCode == S7Client.errISOConnectionFailed;
    }

    private int mapS7ErrorToCode(int s7ErrorCode) {
        switch (s7ErrorCode) {
            case S7Client.errTCPConnectionFailed:
            case S7Client.errTCPConnectionReset:
            case S7Client.errISOConnectionFailed:
                return ErrorCodes.NETWORK_ERROR;
            case S7Client.errTCPDataRecvTout:
                return ErrorCodes.TIMEOUT_ERROR;
            case S7Client.errTCPDataSend:
            case S7Client.errTCPDataRecv:
                return ErrorCodes.CONNECTION_LOST;
            default:
                return ErrorCodes.TAG_READ_ERROR;
        }
    }

    private void recordError(int errorCode, String message) {
        totalErrors++;
        String errorKey = String.valueOf(errorCode);
        errorCounts.merge(errorKey, 1L, Long::sum);
        LogLevelManager.logError(DRIVER_NAME, "[" + errorCode + "] " + message);
    }

    private void initializeHealthCheck() {
        healthCheckTimer = new Timer("S7HealthCheck", true);
        healthCheckTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                performHealthCheck();
            }
        }, HEALTH_CHECK_INTERVAL_MS, HEALTH_CHECK_INTERVAL_MS);
    }

    private void performHealthCheck() {
        if (connected && client != null && client.Connected) {
            try {
                LogLevelManager.logTrace(DRIVER_NAME, "Exécution test de santé...");

                // Test simple avec une lecture de 1 byte en zone M
                byte[] testBuffer = new byte[1];
                int result = client.ReadArea(S7.S7AreaMK, 0, 0, 1, testBuffer);

                if (result != 0) {
                    LogLevelManager.logError(DRIVER_NAME, "Test de santé échoué: " + S7Client.ErrorText(result));
                    if (isConnectionError(result)) {
                        connected = false;
                    }
                } else {
                    LogLevelManager.logTrace(DRIVER_NAME, "Test de santé réussi");
                }
            } catch (Exception e) {
                LogLevelManager.logError(DRIVER_NAME, "Exception lors du test de santé: " + e.getMessage());
                connected = false;
            }
        }
    }

    @Override
    public void disconnect() {
        shouldReconnect = false;

        try {
            if (healthCheckTimer != null) {
                LogLevelManager.logDebug(DRIVER_NAME, "Arrêt du timer de santé");
                healthCheckTimer.cancel();
                healthCheckTimer = null;
            }

            if (client != null && client.Connected) {
                LogLevelManager.logInfo(DRIVER_NAME, "Déconnexion du client S7...");
                client.Disconnect();
            }

            connected = false;
            tagCache.clear();

            LogLevelManager.logInfo(DRIVER_NAME, "Déconnexion complète de "
                    + (machine != null ? machine.getName() : "machine inconnue"));

        } catch (Exception e) {
            LogLevelManager.logError(DRIVER_NAME, "Erreur lors de la déconnexion: " + e.getMessage());
        }
    }

    @Override
    public boolean isConnected() {
        if (client == null) {
            return false;
        }

        try {
            // Vérification plus robuste de la connexion
            boolean clientConnected = client.Connected;

            if (!clientConnected && connected) {
                // Détection de perte de connexion
                connected = false;
                LogLevelManager.logError(DRIVER_NAME, "Perte de connexion détectée");
            }

            return clientConnected && connected;

        } catch (Exception e) {
            LogLevelManager.logError(DRIVER_NAME, "Erreur lors de la vérification de connexion: " + e.getMessage());
            connected = false;
            return false;
        }
    }

    /**
     * Méthode utilitaire pour obtenir des informations de diagnostic
     */
    public String getDiagnosticInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== Diagnostic Siemens S7 Driver ===\n");
        info.append("Machine: ").append(machine != null ? machine.getName() : "Non configurée").append("\n");
        info.append("Adresse: ").append(machine != null ? machine.getAddress() : "N/A").append("\n");
        info.append("Rack/Slot: ").append(machine != null ? machine.getRack() + "/" + machine.getSlot() : "N/A").append("\n");
        info.append("Connecté: ").append(isConnected()).append("\n");
        info.append("Tentatives de reconnexion: ").append(reconnectAttempts).append("/").append(MAX_RECONNECT_ATTEMPTS).append("\n");

        // Métriques de performance
        info.append("\n=== Métriques de Performance ===\n");
        info.append("Total lectures: ").append(totalReads).append("\n");
        info.append("Total écritures: ").append(totalWrites).append("\n");
        info.append("Total erreurs: ").append(totalErrors).append("\n");
        info.append("Temps de connexion: ").append(connectionTime).append("ms\n");
        info.append("Tags en cache: ").append(tagCache.size()).append("\n");

        // Détail des erreurs
        if (!errorCounts.isEmpty()) {
            info.append("\n=== Répartition des Erreurs ===\n");
            for (Map.Entry<String, Long> entry : errorCounts.entrySet()) {
                info.append("Code ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" occurrences\n");
            }
        }

        // Configuration
        info.append("\n=== Configuration ===\n");
        info.append("Timeout connexion: ").append(connectionTimeout).append("ms\n");
        info.append("Timeout lecture: ").append(readTimeout).append("ms\n");
        info.append("Timeout écriture: ").append(writeTimeout).append("ms\n");
        info.append("Taille batch max: ").append(MAX_BATCH_SIZE).append("\n");

        // État du client S7
        if (client != null) {
            try {
                info.append("\n=== État Client S7 ===\n");
                info.append("Connected: ").append(client.Connected).append("\n");
                // Note: Moka7 n'a pas de LastError() comme méthode publique
                info.append("Client Status: Actif\n");
            } catch (Exception e) {
                info.append("Erreur lors de la récupération des infos client: ").append(e.getMessage()).append("\n");
            }
        }

        return info.toString();
    }

    /**
     * Méthode pour obtenir les métriques de performance
     */
    public PerformanceMetrics getPerformanceMetrics() {
        return new PerformanceMetrics(
                totalReads,
                totalWrites,
                totalErrors,
                connectionTime,
                tagCache.size(),
                new HashMap<>(errorCounts)
        );
    }

    /**
     * Méthode pour réinitialiser les métriques
     */
    public void resetMetrics() {
        totalReads = 0;
        totalWrites = 0;
        totalErrors = 0;
        errorCounts.clear();
        LogLevelManager.logInfo(DRIVER_NAME, "Métriques réinitialisées");
    }

    /**
     * Méthode pour nettoyer le cache
     */
    public void clearCache() {
        tagCache.clear();
        LogLevelManager.logInfo(DRIVER_NAME, "Cache des tags nettoyé (" + tagCache.size() + " entrées supprimées)");
    }

    /**
     * Configuration des timeouts - Limitation de Moka7
     */
    public void setConnectionTimeout(int timeoutMs) {
        this.connectionTimeout = timeoutMs;
        // Note: Moka7 n'expose pas de méthode pour configurer les timeouts
        // Les timeouts sont gérés par la socket TCP interne
        LogLevelManager.logInfo(DRIVER_NAME, "Timeout de connexion configuré à " + timeoutMs + "ms");
    }

    public void setReadTimeout(int timeoutMs) {
        this.readTimeout = timeoutMs;
    }

    public void setWriteTimeout(int timeoutMs) {
        this.writeTimeout = timeoutMs;
    }

    /**
     * Test de connectivité réseau
     */
    public NetworkDiagnostics performNetworkDiagnostics() {
        NetworkDiagnostics diagnostics = new NetworkDiagnostics();

        LogLevelManager.logDebug(DRIVER_NAME, "Début des diagnostics réseau");

        if (machine == null || machine.getAddress() == null) {
            diagnostics.reachable = false;
            diagnostics.errorMessage = "Configuration machine invalide";
            LogLevelManager.logError(DRIVER_NAME, "Diagnostics impossible: " + diagnostics.errorMessage);
            return diagnostics;
        }

        try {
            // Test de ping simple (Java)
            long startTime = System.currentTimeMillis();
            java.net.InetAddress address = java.net.InetAddress.getByName(machine.getAddress());
            diagnostics.reachable = address.isReachable(5000);
            diagnostics.pingTime = System.currentTimeMillis() - startTime;

            LogLevelManager.logDebug(DRIVER_NAME, "Test ping: " + diagnostics.reachable + " (" + diagnostics.pingTime + "ms)");

            if (!diagnostics.reachable) {
                diagnostics.errorMessage = "Adresse non accessible";
                LogLevelManager.logError(DRIVER_NAME, diagnostics.errorMessage);
            }

            // Test de connexion S7 si le ping réussit
            if (diagnostics.reachable) {
                if (isConnected()) {
                    diagnostics.s7Connected = true;

                    // Test de lecture simple
                    startTime = System.currentTimeMillis();
                    byte[] buffer = new byte[1];
                    int result = client.ReadArea(S7.S7AreaMK, 0, 0, 1, buffer);
                    diagnostics.readTestTime = System.currentTimeMillis() - startTime;
                    diagnostics.readTestSuccess = (result == 0);

                    LogLevelManager.logDebug(DRIVER_NAME, "Test lecture S7: " + diagnostics.readTestSuccess + " (" + diagnostics.readTestTime + "ms)");

                    if (!diagnostics.readTestSuccess) {
                        diagnostics.s7ErrorCode = result;
                        diagnostics.s7ErrorMessage = S7Client.ErrorText(result);
                        LogLevelManager.logError(DRIVER_NAME, "Test lecture échoué: " + diagnostics.s7ErrorMessage);
                    }
                } else {
                    diagnostics.s7Connected = false;
                    diagnostics.errorMessage = "Connexion S7 fermée";
                    LogLevelManager.logError(DRIVER_NAME, diagnostics.errorMessage);
                }
            }

        } catch (Exception e) {
            diagnostics.reachable = false;
            diagnostics.errorMessage = "Exception réseau: " + e.getMessage();
            LogLevelManager.logError(DRIVER_NAME, diagnostics.errorMessage);
        }

        LogLevelManager.logDebug(DRIVER_NAME, "Diagnostics terminés: " + diagnostics);
        return diagnostics;
    }

    /**
     * Classes internes pour l'organisation des données
     */
    private static class TagMemoryInfo {

        int area;
        int dbNumber;
        int byteAddress;
        int bytesToRead;
        Integer bitAddress;
    }

    /**
     * Classe pour les métriques de performance
     */
    public static class PerformanceMetrics {

        public final long totalReads;
        public final long totalWrites;
        public final long totalErrors;
        public final long connectionTime;
        public final int cacheSize;
        public final Map<String, Long> errorCounts;

        public PerformanceMetrics(long totalReads, long totalWrites, long totalErrors,
                long connectionTime, int cacheSize, Map<String, Long> errorCounts) {
            this.totalReads = totalReads;
            this.totalWrites = totalWrites;
            this.totalErrors = totalErrors;
            this.connectionTime = connectionTime;
            this.cacheSize = cacheSize;
            this.errorCounts = errorCounts;
        }

        public double getErrorRate() {
            long total = totalReads + totalWrites;
            return total > 0 ? (double) totalErrors / total : 0.0;
        }

        @Override
        public String toString() {
            return String.format("PerformanceMetrics{reads=%d, writes=%d, errors=%d, errorRate=%.2f%%, connectionTime=%dms, cache=%d}",
                    totalReads, totalWrites, totalErrors, getErrorRate() * 100, connectionTime, cacheSize);
        }
    }

    /**
     * Classe pour les diagnostics réseau
     */
    public static class NetworkDiagnostics {

        public boolean reachable = false;
        public long pingTime = -1;
        public boolean s7Connected = false;
        public boolean readTestSuccess = false;
        public long readTestTime = -1;
        public int s7ErrorCode = 0;
        public String s7ErrorMessage = "";
        public String errorMessage = "";

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("NetworkDiagnostics{\n");
            sb.append("  Reachable: ").append(reachable);
            if (reachable) {
                sb.append(" (").append(pingTime).append("ms)");
            }
            sb.append("\n");
            sb.append("  S7 Connected: ").append(s7Connected).append("\n");
            sb.append("  Read Test: ").append(readTestSuccess);
            if (readTestSuccess) {
                sb.append(" (").append(readTestTime).append("ms)");
            }
            sb.append("\n");
            if (s7ErrorCode != 0) {
                sb.append("  S7 Error: ").append(s7ErrorCode).append(" - ").append(s7ErrorMessage).append("\n");
            }
            if (!errorMessage.isEmpty()) {
                sb.append("  Error: ").append(errorMessage).append("\n");
            }
            sb.append("}");
            return sb.toString();
        }
    }

    /**
     * Méthode pour obtenir des statistiques de la session
     */
    public String getSessionStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("=== Statistiques de Session ===\n");
        stats.append("Durée de connexion: ").append(connectionTime).append("ms\n");

        long totalOperations = totalReads + totalWrites;
        if (totalOperations > 0) {
            double successRate = 1.0 - ((double) totalErrors / totalOperations);
            stats.append("Taux de succès: ").append(String.format("%.2f%%", successRate * 100)).append("\n");
            stats.append("Moyenne opérations/min: ").append(totalOperations).append("\n");
        }

        stats.append("Reconnexions effectuées: ").append(reconnectAttempts).append("\n");

        return stats.toString();
    }

    /**
     * Méthode utilitaire pour tester la lecture d'un tag spécifique
     */
    public String testTagRead(Tag tag) {
        StringBuilder result = new StringBuilder();
        result.append("=== Test de Lecture Tag: ").append(tag.getName()).append(" ===\n");

        LogLevelManager.logDebug(DRIVER_NAME, "Début test de lecture pour tag: " + tag.getName());

        if (!validateTagConfiguration(tag)) {
            result.append("ÉCHEC: Configuration du tag invalide\n");
            LogLevelManager.logError(DRIVER_NAME, "Test tag échoué: configuration invalide");
            return result.toString();
        }

        TagMemoryInfo memInfo = getOrCreateTagMemoryInfo(tag);
        result.append("Configuration:\n");
        result.append("  Zone: ").append(getAreaName(memInfo.area)).append("\n");
        result.append("  DB: ").append(memInfo.dbNumber).append("\n");
        result.append("  Offset: ").append(memInfo.byteAddress).append("\n");
        result.append("  Taille: ").append(memInfo.bytesToRead).append(" bytes\n");
        result.append("  Type: ").append(tag.getType().getType()).append("\n");

        if (!isConnected()) {
            result.append("ÉCHEC: Non connecté\n");
            LogLevelManager.logError(DRIVER_NAME, "Test tag échoué: non connecté");
            return result.toString();
        }

        try {
            long startTime = System.currentTimeMillis();
            Object value = read(tag);
            long duration = System.currentTimeMillis() - startTime;

            if (value != null) {
                result.append("SUCCÈS: Valeur = ").append(value).append("\n");
                result.append("Durée: ").append(duration).append("ms\n");
                LogLevelManager.logInfo(DRIVER_NAME, "Test tag réussi: " + tag.getName() + " = " + value);
            } else {
                result.append("ÉCHEC: Valeur nulle retournée\n");
                LogLevelManager.logError(DRIVER_NAME, "Test tag échoué: valeur nulle");
            }

        } catch (Exception e) {
            result.append("ÉCHEC: Exception - ").append(e.getMessage()).append("\n");
            LogLevelManager.logError(DRIVER_NAME, "Test tag échoué: " + e.getMessage());
        }

        return result.toString();
    }

    private String getAreaName(int area) {
        switch (area) {
            case S7.S7AreaDB:
                return "DB";
            case S7.S7AreaMK:
                return "M";
            case S7.S7AreaPE:
                return "E/I";
            case S7.S7AreaPA:
                return "A/Q";
            case S7.S7AreaTM:
                return "T";
            case S7.S7AreaCT:
                return "C";
            default:
                return "Unknown(" + area + ")";
        }
    }
}
