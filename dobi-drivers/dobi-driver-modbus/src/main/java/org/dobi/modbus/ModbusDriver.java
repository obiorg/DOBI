package org.dobi.modbus;

import com.digitalpetri.modbus.master.ModbusTcpMaster;
import com.digitalpetri.modbus.master.ModbusTcpMasterConfig;
import com.digitalpetri.modbus.requests.*;
import com.digitalpetri.modbus.responses.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import org.dobi.api.IDriver;
import org.dobi.entities.Machine;
import org.dobi.entities.Tag;
import org.dobi.logging.LogLevelManager;
import org.dobi.logging.LogLevelManager.LogLevel;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ModbusDriver implements IDriver {

    private static final String DRIVER_NAME = "MODBUS-TCP";
    
    // Configuration par défaut
    private static final int DEFAULT_PORT = 502;
    private static final int DEFAULT_UNIT_ID = 1;
    private static final int DEFAULT_TIMEOUT_SECONDS = 5;
    private static final int RECONNECT_DELAY_MS = 3000;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int MAX_BATCH_SIZE = 20;
    
    // Codes d'erreur Modbus standardisés
    public static final class ErrorCodes {
        public static final int SUCCESS = 0;
        public static final int CONNECTION_LOST = 2001;
        public static final int INVALID_CONFIGURATION = 2002;
        public static final int TAG_READ_ERROR = 2003;
        public static final int TAG_WRITE_ERROR = 2004;
        public static final int INVALID_MEMORY_AREA = 2005;
        public static final int TYPE_CONVERSION_ERROR = 2006;
        public static final int NETWORK_ERROR = 2007;
        public static final int TIMEOUT_ERROR = 2008;
        public static final int MODBUS_EXCEPTION = 2009;
        public static final int INVALID_ADDRESS = 2010;
    }
    
    private Machine machine;
    private ModbusTcpMaster master;
    private volatile boolean connected = false;
    private volatile boolean shouldReconnect = true;
    private volatile int reconnectAttempts = 0;
    
    // Métriques de performance
    private long totalReads = 0;
    private long totalWrites = 0;
    private long totalErrors = 0;
    private long connectionTime = 0;
    private final Map<String, Long> errorCounts = new ConcurrentHashMap<>();
    
    // Configuration
    private int connectionTimeout = DEFAULT_TIMEOUT_SECONDS;
    private int unitId = DEFAULT_UNIT_ID;
    
    // Cache pour optimiser les accès
    private final Map<String, TagConfiguration> tagConfigCache = new ConcurrentHashMap<>();

    @Override
    public void configure(Machine machine) {
        this.machine = machine;
        this.unitId = machine.getBus() != null ? machine.getBus() : DEFAULT_UNIT_ID;
        LogLevelManager.logInfo(DRIVER_NAME, "Configuration du driver pour la machine: " + machine.getName() + 
                             " (Adresse: " + machine.getAddress() + ", Unit ID: " + unitId + ")");
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
            int port = machine.getPort() != null ? machine.getPort() : DEFAULT_PORT;
            
            LogLevelManager.logInfo(DRIVER_NAME, "Tentative de connexion à " + machine.getAddress() + ":" + port + 
                                 " (Unit ID: " + unitId + ")");
            
            ModbusTcpMasterConfig config = new ModbusTcpMasterConfig.Builder(machine.getAddress())
                .setPort(port)
                .setTimeout(Duration.ofSeconds(connectionTimeout))
                .build();
                
            master = new ModbusTcpMaster(config);
            master.connect().get(connectionTimeout, TimeUnit.SECONDS);
            
            connected = true;
            connectionTime = System.currentTimeMillis() - startTime;
            reconnectAttempts = 0;
            
            LogLevelManager.logInfo(DRIVER_NAME, "Connexion établie avec " + machine.getName() + 
                                 " en " + connectionTime + "ms");
            
            // Test de connectivité
            performConnectionTest();
            
            return true;
            
        } catch (Exception e) {
            connected = false;
            recordError(ErrorCodes.NETWORK_ERROR, "Erreur de connexion à " + machine.getName() + ": " + e.getMessage());
            
            if (master != null) {
                try {
                    master.disconnect();
                } catch (Exception disconnectEx) {
                    LogLevelManager.logDebug(DRIVER_NAME, "Erreur lors de la déconnexion de nettoyage: " + disconnectEx.getMessage());
                }
                master = null;
            }
            return false;
        }
    }
    
    private void performConnectionTest() {
        try {
            LogLevelManager.logDebug(DRIVER_NAME, "Test de connectivité Modbus...");
            
            // Test simple : lecture d'une coil à l'adresse 0
            ReadCoilsRequest testRequest = new ReadCoilsRequest(0, 1);
            
            CompletableFuture<Boolean> testFuture = master.sendRequest(testRequest, unitId)
                .thenApply(response -> {
                    ReferenceCountUtil.release(response);
                    return response != null;
                });
            
            Boolean testResult = testFuture.get(2, TimeUnit.SECONDS);
            
            if (testResult) {
                LogLevelManager.logDebug(DRIVER_NAME, "Test de connectivité réussi");
            } else {
                LogLevelManager.logError(DRIVER_NAME, "Test de connectivité échoué");
            }
            
        } catch (Exception e) {
            LogLevelManager.logDebug(DRIVER_NAME, "Test de connectivité avec exception (normal si pas de coil à l'adresse 0): " + e.getMessage());
        }
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
                
                // Attente progressive
                int delay = RECONNECT_DELAY_MS * Math.min(currentAttempt, 4);
                Thread.sleep(delay);
                
                // Nettoyage de l'ancienne connexion
                if (master != null) {
                    try {
                        master.disconnect();
                    } catch (Exception e) {
                        LogLevelManager.logDebug(DRIVER_NAME, "Erreur lors de la déconnexion: " + e.getMessage());
                    }
                    master = null;
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
            recordError(ErrorCodes.CONNECTION_LOST, "Master non connecté pour la lecture du tag: " + tag.getName());
            return null;
        }
        
        TagConfiguration config = getOrCreateTagConfiguration(tag);
        if (config == null) {
            return null;
        }
        
        LogLevelManager.logDebug(DRIVER_NAME, "Lecture tag '" + tag.getName() + 
                             "' - Zone: " + config.memoryArea + ", Adresse: " + config.address + 
                             ", Type: " + config.dataType);
        
        try {
            CompletableFuture<Object> readFuture;
            
            switch (config.memoryArea) {
                case COIL:
                    readFuture = readCoils(config);
                    break;
                case DISCRETE_INPUT:
                    readFuture = readDiscreteInputs(config);
                    break;
                case HOLDING_REGISTER:
                    readFuture = readHoldingRegisters(config);
                    break;
                case INPUT_REGISTER:
                    readFuture = readInputRegisters(config);
                    break;
                default:
                    recordError(ErrorCodes.INVALID_MEMORY_AREA, "Zone mémoire non supportée: " + config.memoryArea);
                    return null;
            }
            
            Object result = readFuture.get(connectionTimeout, TimeUnit.SECONDS);
            
            if (result != null) {
                totalReads++;
                LogLevelManager.logTrace(DRIVER_NAME, "Lecture réussie pour '" + tag.getName() + "': " + result);
            }
            
            return result;
            
        } catch (Exception e) {
            recordError(ErrorCodes.TAG_READ_ERROR, "Exception de lecture pour le tag '" + tag.getName() + "': " + e.getMessage());
            
            // Vérifier si c'est une perte de connexion
            if (isConnectionLostException(e)) {
                connected = false;
                attemptReconnection();
            }
            
            return null;
        }
    }

    @Override
    public void write(Tag tag, Object value) {
        if (!isConnected()) {
            recordError(ErrorCodes.CONNECTION_LOST, "Master non connecté pour l'écriture du tag: " + tag.getName());
            return;
        }
        
        TagConfiguration config = getOrCreateTagConfiguration(tag);
        if (config == null) {
            return;
        }
        
        LogLevelManager.logDebug(DRIVER_NAME, "Écriture tag '" + tag.getName() + 
                             "' avec valeur: " + value + " (Zone: " + config.memoryArea + 
                             ", Adresse: " + config.address + ")");
        
        try {
            CompletableFuture<Void> writeFuture;
            
            switch (config.memoryArea) {
                case COIL:
                    writeFuture = writeSingleCoil(config, value);
                    break;
                case HOLDING_REGISTER:
                    writeFuture = writeHoldingRegisters(config, value);
                    break;
                case DISCRETE_INPUT:
                case INPUT_REGISTER:
                    recordError(ErrorCodes.TAG_WRITE_ERROR, "Zone mémoire en lecture seule: " + config.memoryArea + " pour tag: " + tag.getName());
                    return;
                default:
                    recordError(ErrorCodes.INVALID_MEMORY_AREA, "Zone mémoire non supportée pour écriture: " + config.memoryArea);
                    return;
            }
            
            writeFuture.get(connectionTimeout, TimeUnit.SECONDS);
            totalWrites++;
            LogLevelManager.logInfo(DRIVER_NAME, "Écriture réussie pour tag: " + tag.getName());
            
        } catch (Exception e) {
            recordError(ErrorCodes.TAG_WRITE_ERROR, "Exception d'écriture pour le tag '" + tag.getName() + "': " + e.getMessage());
            
            // Vérifier si c'est une perte de connexion
            if (isConnectionLostException(e)) {
                connected = false;
                attemptReconnection();
            }
        }
    }
    
    // === MÉTHODES DE LECTURE ===
    
    private CompletableFuture<Object> readCoils(TagConfiguration config) {
        ReadCoilsRequest request = new ReadCoilsRequest(config.address, 1);
        
        LogLevelManager.logTrace(DRIVER_NAME, "Envoi ReadCoilsRequest - UnitID: " + unitId + 
                             ", Adresse: " + config.address);
        
        return master.sendRequest(request, unitId).thenApply(response -> {
            try {
                if (response instanceof ReadCoilsResponse) {
                    ByteBuf buffer = ((ReadCoilsResponse) response).getCoilStatus();
                    boolean result = buffer.readBoolean();
                    LogLevelManager.logTrace(DRIVER_NAME, "ReadCoilsResponse: " + result);
                    return result;
                } else {
                    LogLevelManager.logError(DRIVER_NAME, "Type de réponse inattendu pour ReadCoils: " + 
                                         (response != null ? response.getClass().getSimpleName() : "null"));
                    return null;
                }
            } catch (Exception e) {
                LogLevelManager.logError(DRIVER_NAME, "Erreur traitement ReadCoilsResponse: " + e.getMessage());
                return null;
            } finally {
                ReferenceCountUtil.release(response);
            }
        });
    }

    private CompletableFuture<Object> readDiscreteInputs(TagConfiguration config) {
        ReadDiscreteInputsRequest request = new ReadDiscreteInputsRequest(config.address, 1);
        
        LogLevelManager.logTrace(DRIVER_NAME, "Envoi ReadDiscreteInputsRequest - UnitID: " + unitId + 
                             ", Adresse: " + config.address);
        
        return master.sendRequest(request, unitId).thenApply(response -> {
            try {
                if (response instanceof ReadDiscreteInputsResponse) {
                    ByteBuf buffer = ((ReadDiscreteInputsResponse) response).getInputStatus();
                    boolean result = buffer.readBoolean();
                    LogLevelManager.logTrace(DRIVER_NAME, "ReadDiscreteInputsResponse: " + result);
                    return result;
                } else {
                    LogLevelManager.logError(DRIVER_NAME, "Type de réponse inattendu pour ReadDiscreteInputs: " + 
                                         (response != null ? response.getClass().getSimpleName() : "null"));
                    return null;
                }
            } catch (Exception e) {
                LogLevelManager.logError(DRIVER_NAME, "Erreur traitement ReadDiscreteInputsResponse: " + e.getMessage());
                return null;
            } finally {
                ReferenceCountUtil.release(response);
            }
        });
    }
    
    private CompletableFuture<Object> readHoldingRegisters(TagConfiguration config) {
        ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest(config.address, config.quantity);
        
        LogLevelManager.logTrace(DRIVER_NAME, "Envoi ReadHoldingRegistersRequest - UnitID: " + unitId + 
                             ", Adresse: " + config.address + ", Quantité: " + config.quantity);
        
        return master.sendRequest(request, unitId).thenApply(response -> {
            try {
                if (response instanceof ReadHoldingRegistersResponse) {
                    ByteBuf buffer = ((ReadHoldingRegistersResponse) response).getRegisters();
                    Object result = decodeRegisterValue(buffer, config.dataType);
                    LogLevelManager.logTrace(DRIVER_NAME, "ReadHoldingRegistersResponse (type " + 
                                         config.dataType + "): " + result);
                    return result;
                } else {
                    LogLevelManager.logError(DRIVER_NAME, "Type de réponse inattendu pour ReadHoldingRegisters: " + 
                                         (response != null ? response.getClass().getSimpleName() : "null"));
                    return null;
                }
            } catch (Exception e) {
                LogLevelManager.logError(DRIVER_NAME, "Erreur traitement ReadHoldingRegistersResponse: " + e.getMessage());
                return null;
            } finally {
                ReferenceCountUtil.release(response);
            }
        });
    }

    private CompletableFuture<Object> readInputRegisters(TagConfiguration config) {
        ReadInputRegistersRequest request = new ReadInputRegistersRequest(config.address, config.quantity);
        
        LogLevelManager.logTrace(DRIVER_NAME, "Envoi ReadInputRegistersRequest - UnitID: " + unitId + 
                             ", Adresse: " + config.address + ", Quantité: " + config.quantity);
        
        return master.sendRequest(request, unitId).thenApply(response -> {
            try {
                if (response instanceof ReadInputRegistersResponse) {
                    ByteBuf buffer = ((ReadInputRegistersResponse) response).getRegisters();
                    Object result = decodeRegisterValue(buffer, config.dataType);
                    LogLevelManager.logTrace(DRIVER_NAME, "ReadInputRegistersResponse (type " + 
                                         config.dataType + "): " + result);
                    return result;
                } else {
                    LogLevelManager.logError(DRIVER_NAME, "Type de réponse inattendu pour ReadInputRegisters: " + 
                                         (response != null ? response.getClass().getSimpleName() : "null"));
                    return null;
                }
            } catch (Exception e) {
                LogLevelManager.logError(DRIVER_NAME, "Erreur traitement ReadInputRegistersResponse: " + e.getMessage());
                return null;
            } finally {
                ReferenceCountUtil.release(response);
            }
        });
    }
    
    // === MÉTHODES D'ÉCRITURE ===
    
    private CompletableFuture<Void> writeSingleCoil(TagConfiguration config, Object value) {
        boolean boolValue = convertToBoolean(value);
        WriteSingleCoilRequest request = new WriteSingleCoilRequest(config.address, boolValue);
        
        LogLevelManager.logTrace(DRIVER_NAME, "Envoi WriteSingleCoilRequest - UnitID: " + unitId + 
                             ", Adresse: " + config.address + ", Valeur: " + boolValue);
        
        return master.sendRequest(request, unitId).thenApply(response -> {
            try {
                if (response instanceof WriteSingleCoilResponse) {
                    WriteSingleCoilResponse coilResponse = (WriteSingleCoilResponse) response;
                    LogLevelManager.logTrace(DRIVER_NAME, "WriteSingleCoilResponse - Adresse: " + 
                                         coilResponse.getAddress() + ", Valeur: " + coilResponse.getValue());
                    return null;
                } else {
                    LogLevelManager.logError(DRIVER_NAME, "Type de réponse inattendu pour WriteSingleCoil: " + 
                                         (response != null ? response.getClass().getSimpleName() : "null"));
                    return null;
                }
            } finally {
                ReferenceCountUtil.release(response);
            }
        });
    }
    
    private CompletableFuture<Void> writeHoldingRegisters(TagConfiguration config, Object value) {
        try {
            ByteBuf buffer = encodeRegisterValue(value, config.dataType, config.quantity);
            
            if (config.quantity == 1) {
                // Écriture d'un seul registre
                int registerValue = buffer.readUnsignedShort();
                WriteSingleRegisterRequest request = new WriteSingleRegisterRequest(config.address, registerValue);
                
                LogLevelManager.logTrace(DRIVER_NAME, "Envoi WriteSingleRegisterRequest - UnitID: " + unitId + 
                                     ", Adresse: " + config.address + ", Valeur: " + registerValue);
                
                return master.sendRequest(request, unitId).thenApply(response -> {
                    try {
                        if (response instanceof WriteSingleRegisterResponse) {
                            WriteSingleRegisterResponse regResponse = (WriteSingleRegisterResponse) response;
                            LogLevelManager.logTrace(DRIVER_NAME, "WriteSingleRegisterResponse - Adresse: " + 
                                                 regResponse.getAddress() + ", Valeur: " + regResponse.getValue());
                            return null;
                        } else {
                            LogLevelManager.logError(DRIVER_NAME, "Type de réponse inattendu pour WriteSingleRegister: " + 
                                                 (response != null ? response.getClass().getSimpleName() : "null"));
                            return null;
                        }
                    } finally {
                        ReferenceCountUtil.release(response);
                    }
                });
            } else {
                // Écriture de plusieurs registres
                WriteMultipleRegistersRequest request = new WriteMultipleRegistersRequest(
                    config.address, config.quantity, buffer);
                
                LogLevelManager.logTrace(DRIVER_NAME, "Envoi WriteMultipleRegistersRequest - UnitID: " + unitId + 
                                     ", Adresse: " + config.address + ", Quantité: " + config.quantity);
                
                return master.sendRequest(request, unitId).thenApply(response -> {
                    try {
                        if (response instanceof WriteMultipleRegistersResponse) {
                            WriteMultipleRegistersResponse multiResponse = (WriteMultipleRegistersResponse) response;
                            LogLevelManager.logTrace(DRIVER_NAME, "WriteMultipleRegistersResponse - Adresse: " + 
                                                 multiResponse.getAddress() + ", Quantité: " + multiResponse.getQuantity());
                            return null;
                        } else {
                            LogLevelManager.logError(DRIVER_NAME, "Type de réponse inattendu pour WriteMultipleRegisters: " + 
                                                 (response != null ? response.getClass().getSimpleName() : "null"));
                            return null;
                        }
                    } finally {
                        ReferenceCountUtil.release(response);
                    }
                });
            }
        } catch (Exception e) {
            recordError(ErrorCodes.TYPE_CONVERSION_ERROR, "Erreur d'encodage pour l'écriture: " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }
    
    // === MÉTHODES DE CONVERSION ===
    
    private Object decodeRegisterValue(ByteBuf buffer, DataType dataType) {
        LogLevelManager.logTrace(DRIVER_NAME, "Décodage registre pour type: " + dataType);
        
        try {
            switch (dataType) {
                case INT16:
                    short intResult = buffer.readShort(); // 16-bit signed
                    LogLevelManager.logTrace(DRIVER_NAME, "Décodage INT16: " + intResult);
                    return intResult;
                    
                case UINT16:
                    int uintResult = buffer.readUnsignedShort(); // 16-bit unsigned
                    LogLevelManager.logTrace(DRIVER_NAME, "Décodage UINT16: " + uintResult);
                    return uintResult;
                    
                case INT32:
                    int dintResult = buffer.readInt(); // 32-bit signed
                    LogLevelManager.logTrace(DRIVER_NAME, "Décodage INT32: " + dintResult);
                    return dintResult;
                    
                case UINT32:
                    long udintResult = buffer.readUnsignedInt(); // 32-bit unsigned
                    LogLevelManager.logTrace(DRIVER_NAME, "Décodage UINT32: " + udintResult);
                    return udintResult;
                    
                case FLOAT32:
                    float realResult = buffer.readFloat(); // 32-bit float
                    LogLevelManager.logTrace(DRIVER_NAME, "Décodage FLOAT32: " + realResult);
                    return realResult;
                    
                case STRING:
                    return decodeString(buffer);
                    
                default:
                    String errorMsg = "Type de registre inconnu: " + dataType;
                    LogLevelManager.logError(DRIVER_NAME, errorMsg);
                    return errorMsg;
            }
        } catch (Exception e) {
            LogLevelManager.logError(DRIVER_NAME, "Erreur lors du décodage du type " + dataType + ": " + e.getMessage());
            return null;
        }
    }
    
    private ByteBuf encodeRegisterValue(Object value, DataType dataType, int quantity) {
        LogLevelManager.logTrace(DRIVER_NAME, "Encodage valeur " + value + " pour type: " + dataType);
        
        ByteBuf buffer = Unpooled.buffer(quantity * 2); // 2 bytes par registre
        
        try {
            switch (dataType) {
                case INT16:
                    buffer.writeShort(convertToShort(value));
                    break;
                    
                case UINT16:
                    buffer.writeShort(convertToInt(value) & 0xFFFF);
                    break;
                    
                case INT32:
                    buffer.writeInt(convertToInt(value));
                    break;
                    
                case UINT32:
                    buffer.writeInt((int) (convertToLong(value) & 0xFFFFFFFFL));
                    break;
                    
                case FLOAT32:
                    buffer.writeFloat(convertToFloat(value));
                    break;
                    
                case STRING:
                    encodeString(buffer, convertToString(value), quantity);
                    break;
                    
                default:
                    LogLevelManager.logError(DRIVER_NAME, "Type non supporté pour l'encodage: " + dataType);
                    buffer.release();
                    return null;
            }
            
            LogLevelManager.logTrace(DRIVER_NAME, "Encodage réussi pour type: " + dataType);
            return buffer;
            
        } catch (Exception e) {
            LogLevelManager.logError(DRIVER_NAME, "Erreur lors de l'encodage: " + e.getMessage());
            buffer.release();
            return null;
        }
    }
    
    private String decodeString(ByteBuf buffer) {
        List<Character> chars = new ArrayList<>();
        while (buffer.isReadable()) {
            char c = (char) buffer.readUnsignedShort();
            if (c == 0) break; // Fin de chaîne
            chars.add(c);
        }
        
        StringBuilder sb = new StringBuilder();
        for (char c : chars) {
            sb.append(c);
        }
        
        String result = sb.toString().trim();
        LogLevelManager.logTrace(DRIVER_NAME, "Décodage STRING: '" + result + "'");
        return result;
    }
    
    private void encodeString(ByteBuf buffer, String str, int maxRegisters) {
        if (str == null) str = "";
        
        int maxLength = maxRegisters; // 1 caractère par registre pour simplicité
        String truncated = str.length() > maxLength ? str.substring(0, maxLength) : str;
        
        for (int i = 0; i < maxRegisters; i++) {
            if (i < truncated.length()) {
                buffer.writeShort((short) truncated.charAt(i));
            } else {
                buffer.writeShort(0); // Padding avec des zéros
            }
        }
        
        LogLevelManager.logTrace(DRIVER_NAME, "Encodage STRING: '" + truncated + "' sur " + maxRegisters + " registres");
    }
    
    // Méthodes de conversion utilitaires
    private boolean convertToBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return false;
    }
    
    private short convertToShort(Object value) {
        if (value instanceof Number) return ((Number) value).shortValue();
        if (value instanceof String) return Short.parseShort((String) value);
        return 0;
    }
    
    private int convertToInt(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) return Integer.parseInt((String) value);
        return 0;
    }
    
    private long convertToLong(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) return Long.parseLong((String) value);
        return 0;
    }
    
    private float convertToFloat(Object value) {
        if (value instanceof Number) return ((Number) value).floatValue();
        if (value instanceof String) return Float.parseFloat((String) value);
        return 0.0f;
    }
    
    private String convertToString(Object value) {
        return value != null ? value.toString() : "";
    }
    
    // === MÉTHODES UTILITAIRES ===
    
    private TagConfiguration getOrCreateTagConfiguration(Tag tag) {
        String cacheKey = tag.getId() + "_" + tag.getName();
        
        return tagConfigCache.computeIfAbsent(cacheKey, k -> {
            if (!validateTagConfiguration(tag)) {
                return null;
            }
            
            TagConfiguration config = new TagConfiguration();
            config.address = tag.getByteAddress();
            config.memoryArea = parseMemoryArea(tag.getMemory().getName());
            config.dataType = parseDataType(tag.getType().getType());
            config.quantity = getQuantityForType(config.dataType);
            
            LogLevelManager.logTrace(DRIVER_NAME, "Configuration créée pour tag " + tag.getName() + 
                                 ": Zone=" + config.memoryArea + ", Adresse=" + config.address + 
                                 ", Type=" + config.dataType + ", Quantité=" + config.quantity);
            
            return config;
        });
    }
    
    private boolean validateTagConfiguration(Tag tag) {
        if (tag == null) {
            LogLevelManager.logError(DRIVER_NAME, "Tag null fourni pour validation");
            return false;
        }
        
        if (tag.getByteAddress() == null) {
            recordError(ErrorCodes.INVALID_CONFIGURATION, "Adresse byte manquante pour tag: " + tag.getName());
            return false;
        }
        
        if (tag.getMemory() == null || tag.getMemory().getName() == null) {
            recordError(ErrorCodes.INVALID_CONFIGURATION, "Zone mémoire manquante pour tag: " + tag.getName());
            return false;
        }
        
        if (tag.getType() == null || tag.getType().getType() == null) {
            recordError(ErrorCodes.INVALID_CONFIGURATION, "Type de données manquant pour tag: " + tag.getName());
            return false;
        }
        
        // Validation de l'adresse
        if (tag.getByteAddress() < 0 || tag.getByteAddress() > 65535) {
            recordError(ErrorCodes.INVALID_ADDRESS, "Adresse invalide pour tag " + tag.getName() + ": " + tag.getByteAddress());
            return false;
        }
        
        LogLevelManager.logTrace(DRIVER_NAME, "Validation réussie pour tag: " + tag.getName());
        return true;
    }
    
    private MemoryArea parseMemoryArea(String memoryName) {
        switch (memoryName.toUpperCase()) {
            case "COIL":
            case "COILS":
                return MemoryArea.COIL;
            case "DISCRETE INPUT":
            case "DISCRETE_INPUT":
            case "DI":
                return MemoryArea.DISCRETE_INPUT;
            case "HOLDING REGISTER":
            case "HOLDING_REGISTER":
            case "HR":
                return MemoryArea.HOLDING_REGISTER;
            case "INPUT REGISTER":
            case "INPUT_REGISTER":
            case "IR":
                return MemoryArea.INPUT_REGISTER;
            default:
                LogLevelManager.logError(DRIVER_NAME, "Zone mémoire Modbus invalide: " + memoryName);
                return MemoryArea.HOLDING_REGISTER; // Par défaut
        }
    }
    
    private DataType parseDataType(String typeName) {
        switch (typeName.toUpperCase()) {
            case "INT":
            case "INT16":
                return DataType.INT16;
            case "UINT":
            case "UINT16":
            case "WORD":
                return DataType.UINT16;
            case "DINT":
            case "INT32":
                return DataType.INT32;
            case "UDINT":
            case "UINT32":
            case "DWORD":
                return DataType.UINT32;
            case "REAL":
            case "FLOAT":
            case "FLOAT32":
                return DataType.FLOAT32;
            case "STRING":
                return DataType.STRING;
            default:
                LogLevelManager.logError(DRIVER_NAME, "Type de données non supporté: " + typeName + ", utilisation de INT16 par défaut");
                return DataType.INT16;
        }
    }
    
    private int getQuantityForType(DataType dataType) {
        switch (dataType) {
            case INT16:
            case UINT16:
                return 1; // 1 registre de 16 bits
            case INT32:
            case UINT32:
            case FLOAT32:
                return 2; // 2 registres de 16 bits = 32 bits
            case STRING:
                return 10; // 10 registres par défaut pour les chaînes (20 caractères max)
            default:
                return 1;
        }
    }
    
    private boolean isConnectionLostException(Exception e) {
        return e.getMessage() != null && (
            e.getMessage().contains("Connection") ||
            e.getMessage().contains("connection") ||
            e.getMessage().contains("timeout") ||
            e.getMessage().contains("Timeout") ||
            e.getMessage().contains("refused") ||
            e.getMessage().contains("reset")
        );
    }
    
    private void recordError(int errorCode, String message) {
        totalErrors++;
        String errorKey = String.valueOf(errorCode);
        errorCounts.merge(errorKey, 1L, Long::sum);
        LogLevelManager.logError(DRIVER_NAME, "[" + errorCode + "] " + message);
    }

    @Override
    public void disconnect() {
        shouldReconnect = false;
        
        LogLevelManager.logInfo(DRIVER_NAME, "Déconnexion du master Modbus...");
        
        if (master != null) {
            try {
                master.disconnect();
                LogLevelManager.logInfo(DRIVER_NAME, "Déconnexion Modbus réussie pour " + 
                                     (machine != null ? machine.getName() : "machine inconnue"));
            } catch (Exception e) {
                LogLevelManager.logError(DRIVER_NAME, "Erreur lors de la déconnexion: " + e.getMessage());
            } finally {
                master = null;
                connected = false;
            }
        } else {
            LogLevelManager.logDebug(DRIVER_NAME, "Master déjà déconnecté");
        }
        
        // Nettoyage du cache
        tagConfigCache.clear();
        LogLevelManager.logDebug(DRIVER_NAME, "Cache des configurations nettoyé");
    }

    @Override
    public boolean isConnected() {
        boolean masterConnected = master != null && connected;
        
        LogLevelManager.logTrace(DRIVER_NAME, "Vérification connexion: " + 
                             (masterConnected ? "connecté" : "déconnecté"));
        
        return masterConnected;
    }
    
    // === MÉTHODES DE DIAGNOSTIC ET UTILITAIRES ===
    
    /**
     * Lecture en batch de plusieurs tags pour optimiser les performances
     */
    public Map<String, Object> readBatch(List<Tag> tags) {
        Map<String, Object> results = new HashMap<>();
        
        if (!isConnected() || tags == null || tags.isEmpty()) {
            LogLevelManager.logDebug(DRIVER_NAME, "Batch read impossible: " + 
                                 (isConnected() ? "pas de tags" : "non connecté"));
            return results;
        }
        
        LogLevelManager.logDebug(DRIVER_NAME, "Début lecture batch de " + tags.size() + " tags");
        
        // Regrouper les tags par zone mémoire pour optimiser
        Map<MemoryArea, List<Tag>> tagsByMemoryArea = groupTagsByMemoryArea(tags);
        
        for (Map.Entry<MemoryArea, List<Tag>> entry : tagsByMemoryArea.entrySet()) {
            List<Tag> areaTags = entry.getValue();
            LogLevelManager.logTrace(DRIVER_NAME, "Traitement zone mémoire: " + entry.getKey() + 
                                 " (" + areaTags.size() + " tags)");
            
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
        
        LogLevelManager.logInfo(DRIVER_NAME, "Lecture batch terminée: " + results.size() + "/" + 
                             tags.size() + " tags réussis");
        return results;
    }
    
    private Map<MemoryArea, List<Tag>> groupTagsByMemoryArea(List<Tag> tags) {
        Map<MemoryArea, List<Tag>> grouped = new HashMap<>();
        
        for (Tag tag : tags) {
            if (validateTagConfiguration(tag)) {
                MemoryArea area = parseMemoryArea(tag.getMemory().getName());
                grouped.computeIfAbsent(area, k -> new ArrayList<>()).add(tag);
            }
        }
        
        return grouped;
    }
    
    /**
     * Test de connectivité spécifique Modbus
     */
    public String testConnection() {
        StringBuilder result = new StringBuilder();
        result.append("=== Test de Connexion Modbus ===\n");
        
        LogLevelManager.logDebug(DRIVER_NAME, "Début test de connexion");
        
        if (machine == null) {
            result.append("ÉCHEC: Machine non configurée\n");
            LogLevelManager.logError(DRIVER_NAME, "Test connexion échoué: machine null");
            return result.toString();
        }
        
        if (machine.getAddress() == null) {
            result.append("ÉCHEC: Adresse non configurée\n");
            LogLevelManager.logError(DRIVER_NAME, "Test connexion échoué: adresse null");
            return result.toString();
        }
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Test de ping réseau
            java.net.InetAddress address = java.net.InetAddress.getByName(machine.getAddress());
            boolean reachable = address.isReachable(5000);
            long pingTime = System.currentTimeMillis() - startTime;
            
            result.append("Test Ping: ").append(reachable ? "SUCCÈS" : "ÉCHEC").append(" (").append(pingTime).append("ms)\n");
            LogLevelManager.logDebug(DRIVER_NAME, "Test ping: " + reachable + " (" + pingTime + "ms)");
            
            if (!reachable) {
                result.append("L'adresse n'est pas accessible via ping\n");
                LogLevelManager.logError(DRIVER_NAME, "Adresse non accessible: " + machine.getAddress());
                return result.toString();
            }
            
            // Test de connexion Modbus
            startTime = System.currentTimeMillis();
            boolean modbusConnected = false;
            
            if (isConnected()) {
                modbusConnected = true;
                result.append("Connexion Modbus: DÉJÀ CONNECTÉ\n");
            } else {
                modbusConnected = connect();
                long connectTime = System.currentTimeMillis() - startTime;
                result.append("Test Connexion Modbus: ").append(modbusConnected ? "SUCCÈS" : "ÉCHEC")
                      .append(" (").append(connectTime).append("ms)\n");
            }
            
            LogLevelManager.logInfo(DRIVER_NAME, "Test connexion Modbus: " + (modbusConnected ? "réussi" : "échoué"));
            
            if (modbusConnected) {
                result.append("Driver prêt pour la communication\n");
                
                // Test de lecture basique
                result.append("\n--- Test de Lecture Basique ---\n");
                try {
                    ReadCoilsRequest testRequest = new ReadCoilsRequest(0, 1);
                    startTime = System.currentTimeMillis();
                    
                    master.sendRequest(testRequest, unitId).get(2, TimeUnit.SECONDS);
                    long readTime = System.currentTimeMillis() - startTime;
                    result.append("Test lecture coil #0: SUCCÈS (").append(readTime).append("ms)\n");
                    
                } catch (Exception e) {
                    result.append("Test lecture coil #0: ÉCHEC (").append(e.getMessage()).append(")\n");
                    LogLevelManager.logDebug(DRIVER_NAME, "Test lecture échoué (normal si pas de coil): " + e.getMessage());
                }
            } else {
                result.append("Impossible d'établir la connexion Modbus\n");
            }
            
        } catch (Exception e) {
            result.append("EXCEPTION: ").append(e.getMessage()).append("\n");
            LogLevelManager.logError(DRIVER_NAME, "Exception lors du test de connexion: " + e.getMessage());
        }
        
        LogLevelManager.logDebug(DRIVER_NAME, "Test de connexion terminé");
        return result.toString();
    }
    
    /**
     * Test de lecture d'un tag spécifique
     */
    public String testTagRead(Tag tag) {
        StringBuilder result = new StringBuilder();
        result.append("=== Test de Lecture Tag Modbus: ").append(tag.getName()).append(" ===\n");
        
        LogLevelManager.logDebug(DRIVER_NAME, "Début test de lecture pour tag: " + tag.getName());
        
        TagConfiguration config = getOrCreateTagConfiguration(tag);
        if (config == null) {
            result.append("ÉCHEC: Configuration du tag invalide\n");
            LogLevelManager.logError(DRIVER_NAME, "Test tag échoué: configuration invalide");
            return result.toString();
        }
        
        // Affichage de la configuration
        result.append("Configuration:\n");
        result.append("  Zone mémoire: ").append(config.memoryArea).append("\n");
        result.append("  Adresse: ").append(config.address).append("\n");
        result.append("  Type: ").append(config.dataType).append("\n");
        result.append("  Quantité: ").append(config.quantity).append(" registre(s)\n");
        result.append("  Unit ID: ").append(unitId).append("\n");
        
        if (!isConnected()) {
            result.append("ÉCHEC: Driver non connecté\n");
            LogLevelManager.logError(DRIVER_NAME, "Test tag échoué: non connecté");
            return result.toString();
        }
        
        try {
            long startTime = System.currentTimeMillis();
            Object value = read(tag);
            long duration = System.currentTimeMillis() - startTime;
            
            if (value != null) {
                result.append("SUCCÈS: Valeur = ").append(value).append("\n");
                result.append("Type de la valeur: ").append(value.getClass().getSimpleName()).append("\n");
                result.append("Durée: ").append(duration).append("ms\n");
                LogLevelManager.logInfo(DRIVER_NAME, "Test tag réussi: " + tag.getName() + " = " + value + " (" + duration + "ms)");
            } else {
                result.append("ÉCHEC: Valeur nulle retournée\n");
                result.append("Durée: ").append(duration).append("ms\n");
                LogLevelManager.logError(DRIVER_NAME, "Test tag échoué: valeur nulle retournée");
            }
            
        } catch (Exception e) {
            result.append("ÉCHEC: Exception - ").append(e.getMessage()).append("\n");
            LogLevelManager.logError(DRIVER_NAME, "Test tag échoué avec exception: " + e.getMessage());
        }
        
        LogLevelManager.logDebug(DRIVER_NAME, "Test de lecture terminé pour tag: " + tag.getName());
        return result.toString();
    }
    
    /**
     * Test d'écriture d'un tag spécifique
     */
    public String testTagWrite(Tag tag, Object value) {
        StringBuilder result = new StringBuilder();
        result.append("=== Test d'Écriture Tag Modbus: ").append(tag.getName()).append(" ===\n");
        
        LogLevelManager.logDebug(DRIVER_NAME, "Début test d'écriture pour tag: " + tag.getName() + " avec valeur: " + value);
        
        TagConfiguration config = getOrCreateTagConfiguration(tag);
        if (config == null) {
            result.append("ÉCHEC: Configuration du tag invalide\n");
            LogLevelManager.logError(DRIVER_NAME, "Test écriture échoué: configuration invalide");
            return result.toString();
        }
        
        // Vérification que la zone est écrivable
        if (config.memoryArea == MemoryArea.DISCRETE_INPUT || config.memoryArea == MemoryArea.INPUT_REGISTER) {
            result.append("ÉCHEC: Zone mémoire en lecture seule (").append(config.memoryArea).append(")\n");
            LogLevelManager.logError(DRIVER_NAME, "Test écriture échoué: zone lecture seule");
            return result.toString();
        }
        
        result.append("Configuration:\n");
        result.append("  Zone mémoire: ").append(config.memoryArea).append(" (écrivable)\n");
        result.append("  Adresse: ").append(config.address).append("\n");
        result.append("  Type: ").append(config.dataType).append("\n");
        result.append("  Valeur à écrire: ").append(value).append(" (").append(value.getClass().getSimpleName()).append(")\n");
        
        if (!isConnected()) {
            result.append("ÉCHEC: Driver non connecté\n");
            LogLevelManager.logError(DRIVER_NAME, "Test écriture échoué: non connecté");
            return result.toString();
        }
        
        try {
            // Lecture de la valeur actuelle
            Object currentValue = read(tag);
            result.append("Valeur actuelle: ").append(currentValue).append("\n");
            
            // Écriture de la nouvelle valeur
            long startTime = System.currentTimeMillis();
            write(tag, value);
            long writeDuration = System.currentTimeMillis() - startTime;
            
            // Vérification en relisant
            Thread.sleep(100); // Petit délai pour s'assurer que l'écriture est prise en compte
            Object verifyValue = read(tag);
            long totalDuration = System.currentTimeMillis() - startTime;
            
            result.append("SUCCÈS: Écriture effectuée\n");
            result.append("Durée écriture: ").append(writeDuration).append("ms\n");
            result.append("Valeur vérifiée: ").append(verifyValue).append("\n");
            result.append("Durée totale: ").append(totalDuration).append("ms\n");
            
            // Vérification de cohérence
            if (Objects.equals(String.valueOf(value), String.valueOf(verifyValue))) {
                result.append("✅ VÉRIFICATION: Valeurs cohérentes\n");
                LogLevelManager.logInfo(DRIVER_NAME, "Test écriture réussi et vérifié: " + tag.getName());
            } else {
                result.append("⚠️ ATTENTION: Valeurs différentes (écrite: ").append(value).append(", lue: ").append(verifyValue).append(")\n");
                LogLevelManager.logError(DRIVER_NAME, "Test écriture: valeurs incohérentes");
            }
            
        } catch (Exception e) {
            result.append("ÉCHEC: Exception - ").append(e.getMessage()).append("\n");
            LogLevelManager.logError(DRIVER_NAME, "Test écriture échoué avec exception: " + e.getMessage());
        }
        
        LogLevelManager.logDebug(DRIVER_NAME, "Test d'écriture terminé pour tag: " + tag.getName());
        return result.toString();
    }
    
    /**
     * Méthode pour obtenir des informations de diagnostic détaillées
     */
    public String getDiagnosticInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== Diagnostic Modbus TCP Driver ===\n");
        info.append("Machine: ").append(machine != null ? machine.getName() : "Non configurée").append("\n");
        info.append("Adresse: ").append(machine != null ? machine.getAddress() : "N/A").append("\n");
        info.append("Port: ").append(machine != null && machine.getPort() != null ? machine.getPort() : DEFAULT_PORT).append("\n");
        info.append("Unit ID: ").append(unitId).append("\n");
        info.append("Connecté: ").append(isConnected()).append("\n");
        info.append("Tentatives de reconnexion: ").append(reconnectAttempts).append("/").append(MAX_RECONNECT_ATTEMPTS).append("\n");
        
        // Métriques de performance
        info.append("\n=== Métriques de Performance ===\n");
        info.append("Total lectures: ").append(totalReads).append("\n");
        info.append("Total écritures: ").append(totalWrites).append("\n");
        info.append("Total erreurs: ").append(totalErrors).append("\n");
        info.append("Temps de connexion: ").append(connectionTime).append("ms\n");
        info.append("Configurations en cache: ").append(tagConfigCache.size()).append("\n");
        
        // Détail des erreurs
        if (!errorCounts.isEmpty()) {
            info.append("\n=== Répartition des Erreurs ===\n");
            for (Map.Entry<String, Long> entry : errorCounts.entrySet()) {
                info.append("Code ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" occurrences\n");
            }
        }
        
        // Configuration
        info.append("\n=== Configuration ===\n");
        info.append("Timeout connexion: ").append(connectionTimeout).append("s\n");
        info.append("Taille batch max: ").append(MAX_BATCH_SIZE).append("\n");
        info.append("Délai reconnexion: ").append(RECONNECT_DELAY_MS).append("ms\n");
        
        // Types supportés
        info.append("\n=== Types de Données Supportés ===\n");
        for (DataType type : DataType.values()) {
            info.append("- ").append(type).append(" (").append(getQuantityForType(type)).append(" registre").append(getQuantityForType(type) > 1 ? "s" : "").append(")\n");
        }
        
        // Zones mémoire supportées
        info.append("\n=== Zones Mémoire Supportées ===\n");
        for (MemoryArea area : MemoryArea.values()) {
            String readWrite = (area == MemoryArea.COIL || area == MemoryArea.HOLDING_REGISTER) ? "Lecture/Écriture" : "Lecture seule";
            info.append("- ").append(area).append(" (").append(readWrite).append(")\n");
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
        
        totalReads = 0;
        totalWrites = 0;
        totalErrors = 0;
        errorCounts.clear();
        
        LogLevelManager.logInfo(DRIVER_NAME, "Métriques réinitialisées (anciens: " + oldReads + " lectures, " + 
                             oldWrites + " écritures, " + oldErrors + " erreurs)");
    }
    
    /**
     * Nettoie le cache des configurations
     */
    public void clearCache() {
        int oldSize = tagConfigCache.size();
        tagConfigCache.clear();
        LogLevelManager.logInfo(DRIVER_NAME, "Cache nettoyé (" + oldSize + " configurations supprimées)");
    }
    
    /**
     * Configure le timeout de connexion
     */
    public void setConnectionTimeout(int timeoutSeconds) {
        this.connectionTimeout = Math.max(1, timeoutSeconds);
        LogLevelManager.logInfo(DRIVER_NAME, "Timeout de connexion configuré à " + this.connectionTimeout + " secondes");
    }
    
    /**
     * Obtient les statistiques du driver
     */
    public PerformanceMetrics getPerformanceMetrics() {
        return new PerformanceMetrics(
            totalReads,
            totalWrites,
            totalErrors,
            connectionTime,
            tagConfigCache.size(),
            new HashMap<>(errorCounts)
        );
    }
    
    // === CLASSES INTERNES ===
    
    private enum MemoryArea {
        COIL,
        DISCRETE_INPUT,
        HOLDING_REGISTER,
        INPUT_REGISTER
    }
    
    private enum DataType {
        INT16,
        UINT16,
        INT32,
        UINT32,
        FLOAT32,
        STRING
    }
    
    private static class TagConfiguration {
        int address;
        MemoryArea memoryArea;
        DataType dataType;
        int quantity;
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
}