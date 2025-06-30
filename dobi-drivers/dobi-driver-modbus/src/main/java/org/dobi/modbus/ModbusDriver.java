package org.dobi.modbus;

import com.digitalpetri.modbus.master.ModbusTcpMaster;
import com.digitalpetri.modbus.master.ModbusTcpMasterConfig;
import com.digitalpetri.modbus.requests.*;
import com.digitalpetri.modbus.responses.*;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import org.dobi.api.IDriver;
import org.dobi.entities.Machine;
import org.dobi.entities.Tag;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.dobi.logging.LogLevelManager;
import org.dobi.logging.LogLevelManager.LogLevel;

public class ModbusDriver implements IDriver {

    private static final String DRIVER_NAME = "MODBUS-TCP";

    private Machine machine;
    private ModbusTcpMaster master;

    @Override
    public void configure(Machine machine) {
        this.machine = machine;
        LogLevelManager.logInfo(DRIVER_NAME, "Configuration du driver pour la machine: " + machine.getName());
    }

    @Override
    public boolean connect() {
        if (machine == null) {
            LogLevelManager.logError(DRIVER_NAME, "Configuration machine null");
            return false;
        }

        if (machine.getAddress() == null) {
            LogLevelManager.logError(DRIVER_NAME, "Adresse machine non configurée");
            return false;
        }

        try {
            int port = machine.getPort() != null ? machine.getPort() : 502;

            LogLevelManager.logInfo(DRIVER_NAME, "Tentative de connexion à " + machine.getAddress() + ":" + port);

            ModbusTcpMasterConfig config = new ModbusTcpMasterConfig.Builder(machine.getAddress())
                    .setPort(port)
                    .setTimeout(Duration.ofSeconds(2))
                    .build();

            master = new ModbusTcpMaster(config);
            master.connect().get();

            LogLevelManager.logInfo(DRIVER_NAME, "Connexion établie avec " + machine.getName()
                    + " (" + machine.getAddress() + ":" + port + ")");
            return true;

        } catch (Exception e) {
            LogLevelManager.logError(DRIVER_NAME, "Erreur de connexion à " + machine.getName() + ": " + e.getMessage());
            if (master != null) {
                try {
                    master.disconnect();
                } catch (Exception disconnectEx) {
                    LogLevelManager.logDebug(DRIVER_NAME, "Erreur lors de la déconnexion de nettoyage: " + disconnectEx.getMessage());
                }
            }
            return false;
        }
    }

    @Override
    public Object read(Tag tag) {
        if (!isConnected()) {
            LogLevelManager.logError(DRIVER_NAME, "Master non connecté pour la lecture du tag: " + tag.getName());
            return null;
        }

        if (tag.getByteAddress() == null || tag.getMemory() == null) {
            LogLevelManager.logError(DRIVER_NAME, "Configuration incomplète pour le tag: " + tag.getName()
                    + " (adresse=" + tag.getByteAddress() + ", mémoire=" + tag.getMemory() + ")");
            return null;
        }

        // L'ID de l'esclave Modbus (Unit ID)
        int unitId = machine.getBus() != null ? machine.getBus() : 1;
        String memoryArea = tag.getMemory().getName().toUpperCase();

        LogLevelManager.logDebug(DRIVER_NAME, "Lecture tag '" + tag.getName()
                + "' - Zone: " + memoryArea + ", Adresse: " + tag.getByteAddress()
                + ", UnitID: " + unitId);

        try {
            switch (memoryArea) {
                case "COIL": // Lecture d'un bit (BOOL)
                    LogLevelManager.logTrace(DRIVER_NAME, "Lecture COIL pour " + tag.getName());
                    return readCoils(tag, unitId).get();

                case "DISCRETE INPUT": // Lecture d'un bit (BOOL, lecture seule)
                    LogLevelManager.logTrace(DRIVER_NAME, "Lecture DISCRETE INPUT pour " + tag.getName());
                    return readDiscreteInputs(tag, unitId).get();

                case "HOLDING REGISTER": // Lecture d'un ou plusieurs registres (INT, REAL...)
                    LogLevelManager.logTrace(DRIVER_NAME, "Lecture HOLDING REGISTER pour " + tag.getName());
                    return readHoldingRegisters(tag, unitId).get();

                case "INPUT REGISTER": // Lecture d'un ou plusieurs registres (INT, REAL..., lecture seule)
                    LogLevelManager.logTrace(DRIVER_NAME, "Lecture INPUT REGISTER pour " + tag.getName());
                    return readInputRegisters(tag, unitId).get();

                default:
                    LogLevelManager.logError(DRIVER_NAME, "Zone mémoire Modbus non reconnue: " + memoryArea + " pour tag " + tag.getName());
                    return null;
            }
        } catch (Exception e) {
            LogLevelManager.logError(DRIVER_NAME, "Exception de lecture Modbus pour le tag '" + tag.getName() + "': " + e.getMessage());
            return null;
        }
    }

    private CompletableFuture<Object> readCoils(Tag tag, int unitId) {
        ReadCoilsRequest request = new ReadCoilsRequest(tag.getByteAddress(), 1);

        LogLevelManager.logTrace(DRIVER_NAME, "Envoi ReadCoilsRequest - UnitID: " + unitId
                + ", Adresse: " + tag.getByteAddress());

        return master.sendRequest(request, unitId).thenApply(response -> {
            try {
                if (response instanceof ReadCoilsResponse) {
                    ByteBuf buffer = ((ReadCoilsResponse) response).getCoilStatus();
                    boolean result = buffer.readBoolean();
                    LogLevelManager.logTrace(DRIVER_NAME, "ReadCoilsResponse pour " + tag.getName() + ": " + result);
                    return result;
                } else {
                    LogLevelManager.logError(DRIVER_NAME, "Type de réponse inattendu pour ReadCoils: "
                            + (response != null ? response.getClass().getSimpleName() : "null"));
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

    private CompletableFuture<Object> readDiscreteInputs(Tag tag, int unitId) {
        ReadDiscreteInputsRequest request = new ReadDiscreteInputsRequest(tag.getByteAddress(), 1);

        LogLevelManager.logTrace(DRIVER_NAME, "Envoi ReadDiscreteInputsRequest - UnitID: " + unitId
                + ", Adresse: " + tag.getByteAddress());

        return master.sendRequest(request, unitId).thenApply(response -> {
            try {
                if (response instanceof ReadDiscreteInputsResponse) {
                    ByteBuf buffer = ((ReadDiscreteInputsResponse) response).getInputStatus();
                    boolean result = buffer.readBoolean();
                    LogLevelManager.logTrace(DRIVER_NAME, "ReadDiscreteInputsResponse pour " + tag.getName() + ": " + result);
                    return result;
                } else {
                    LogLevelManager.logError(DRIVER_NAME, "Type de réponse inattendu pour ReadDiscreteInputs: "
                            + (response != null ? response.getClass().getSimpleName() : "null"));
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

    private CompletableFuture<Object> readHoldingRegisters(Tag tag, int unitId) {
        int quantity = getQuantityToRead(tag.getType().getType());
        ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest(tag.getByteAddress(), quantity);

        LogLevelManager.logTrace(DRIVER_NAME, "Envoi ReadHoldingRegistersRequest - UnitID: " + unitId
                + ", Adresse: " + tag.getByteAddress() + ", Quantité: " + quantity);

        return master.sendRequest(request, unitId).thenApply(response -> {
            try {
                if (response instanceof ReadHoldingRegistersResponse) {
                    ByteBuf buffer = ((ReadHoldingRegistersResponse) response).getRegisters();
                    Object result = decodeRegisterValue(buffer, tag.getType().getType());
                    LogLevelManager.logTrace(DRIVER_NAME, "ReadHoldingRegistersResponse pour " + tag.getName()
                            + " (type " + tag.getType().getType() + "): " + result);
                    return result;
                } else {
                    LogLevelManager.logError(DRIVER_NAME, "Type de réponse inattendu pour ReadHoldingRegisters: "
                            + (response != null ? response.getClass().getSimpleName() : "null"));
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

    private CompletableFuture<Object> readInputRegisters(Tag tag, int unitId) {
        int quantity = getQuantityToRead(tag.getType().getType());
        ReadInputRegistersRequest request = new ReadInputRegistersRequest(tag.getByteAddress(), quantity);

        LogLevelManager.logTrace(DRIVER_NAME, "Envoi ReadInputRegistersRequest - UnitID: " + unitId
                + ", Adresse: " + tag.getByteAddress() + ", Quantité: " + quantity);

        return master.sendRequest(request, unitId).thenApply(response -> {
            try {
                if (response instanceof ReadInputRegistersResponse) {
                    ByteBuf buffer = ((ReadInputRegistersResponse) response).getRegisters();
                    Object result = decodeRegisterValue(buffer, tag.getType().getType());
                    LogLevelManager.logTrace(DRIVER_NAME, "ReadInputRegistersResponse pour " + tag.getName()
                            + " (type " + tag.getType().getType() + "): " + result);
                    return result;
                } else {
                    LogLevelManager.logError(DRIVER_NAME, "Type de réponse inattendu pour ReadInputRegisters: "
                            + (response != null ? response.getClass().getSimpleName() : "null"));
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

    private int getQuantityToRead(String typeName) {
        int quantity;
        switch (typeName.toUpperCase()) {
            case "REAL":
            case "DINT":
            case "UDINT":
                quantity = 2; // Un REAL ou un DINT occupe 2 registres de 16 bits
                break;
            default:
                quantity = 1; // INT, UINT, WORD, etc.
                break;
        }

        LogLevelManager.logTrace(DRIVER_NAME, "Quantité à lire pour type " + typeName + ": " + quantity + " registres");
        return quantity;
    }

    private Object decodeRegisterValue(ByteBuf buffer, String typeName) {
        LogLevelManager.logTrace(DRIVER_NAME, "Décodage registre pour type: " + typeName);

        try {
            switch (typeName.toUpperCase()) {
                case "INT":
                    short intResult = buffer.readShort(); // 16-bit signed
                    LogLevelManager.logTrace(DRIVER_NAME, "Décodage INT: " + intResult);
                    return intResult;

                case "UINT":
                    int uintResult = buffer.readUnsignedShort(); // 16-bit unsigned
                    LogLevelManager.logTrace(DRIVER_NAME, "Décodage UINT: " + uintResult);
                    return uintResult;

                case "DINT":
                    int dintResult = buffer.readInt(); // 32-bit signed
                    LogLevelManager.logTrace(DRIVER_NAME, "Décodage DINT: " + dintResult);
                    return dintResult;

                case "UDINT":
                    long udintResult = buffer.readUnsignedInt(); // 32-bit unsigned
                    LogLevelManager.logTrace(DRIVER_NAME, "Décodage UDINT: " + udintResult);
                    return udintResult;

                case "REAL":
                    float realResult = buffer.readFloat(); // 32-bit float
                    LogLevelManager.logTrace(DRIVER_NAME, "Décodage REAL: " + realResult);
                    return realResult;

                default:
                    String errorMsg = "Type de registre inconnu: " + typeName;
                    LogLevelManager.logError(DRIVER_NAME, errorMsg);
                    return errorMsg;
            }
        } catch (Exception e) {
            LogLevelManager.logError(DRIVER_NAME, "Erreur lors du décodage du type " + typeName + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public void write(Tag tag, Object value) {
        LogLevelManager.logInfo(DRIVER_NAME, "Tentative d'écriture sur tag " + tag.getName()
                + " avec valeur: " + value + " (écriture non implémentée)");

        // TODO: Implémenter l'écriture Modbus
        // La logique d'écriture sera implémentée plus tard
        if (!isConnected()) {
            LogLevelManager.logError(DRIVER_NAME, "Master non connecté pour l'écriture du tag: " + tag.getName());
            return;
        }

        LogLevelManager.logDebug(DRIVER_NAME, "Écriture Modbus pour le tag " + tag.getName()
                + " - Fonctionnalité en cours de développement");
    }

    @Override
    public void disconnect() {
        LogLevelManager.logInfo(DRIVER_NAME, "Déconnexion du master Modbus...");
        if (master != null) {
            master.disconnect();
        }
    }

    @Override
    public boolean isConnected() {
        boolean connected = master != null;

        if (!connected) {
            LogLevelManager.logTrace(DRIVER_NAME, "Vérification connexion: master null");
        } else {
            LogLevelManager.logTrace(DRIVER_NAME, "Vérification connexion: master présent");
        }

        return connected;
    }

    /**
     * Méthode utilitaire pour obtenir des informations de diagnostic
     */
    public String getDiagnosticInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== Diagnostic Modbus TCP Driver ===\n");
        info.append("Machine: ").append(machine != null ? machine.getName() : "Non configurée").append("\n");
        info.append("Adresse: ").append(machine != null ? machine.getAddress() : "N/A").append("\n");
        info.append("Port: ").append(machine != null ? machine.getPort() : "N/A").append("\n");
        info.append("Unit ID: ").append(machine != null && machine.getBus() != null ? machine.getBus() : "1 (défaut)").append("\n");
        info.append("Connecté: ").append(isConnected()).append("\n");

        if (machine != null) {
            info.append("Configuration complète: ");
            info.append("Adresse=" + machine.getAddress() + ", ");
            info.append("Port=" + (machine.getPort() != null ? machine.getPort() : 502) + ", ");
            info.append("UnitID=" + (machine.getBus() != null ? machine.getBus() : 1));
            info.append("\n");
        }

        LogLevelManager.logDebug(DRIVER_NAME, "Diagnostic généré");
        return info.toString();
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
            boolean modbuConnected = false;

            if (isConnected()) {
                modbuConnected = true;
                result.append("Connexion Modbus: DÉJÀ CONNECTÉ\n");
            } else {
                modbuConnected = connect();
                long connectTime = System.currentTimeMillis() - startTime;
                result.append("Test Connexion Modbus: ").append(modbuConnected ? "SUCCÈS" : "ÉCHEC")
                        .append(" (").append(connectTime).append("ms)\n");
            }

            LogLevelManager.logInfo(DRIVER_NAME, "Test connexion Modbus: " + (modbuConnected ? "réussi" : "échoué"));

            if (modbuConnected) {
                result.append("Driver prêt pour la communication\n");
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

        // Validation de la configuration
        if (tag.getByteAddress() == null) {
            result.append("ÉCHEC: Adresse byte non configurée\n");
            LogLevelManager.logError(DRIVER_NAME, "Test tag échoué: adresse byte null");
            return result.toString();
        }

        if (tag.getMemory() == null) {
            result.append("ÉCHEC: Zone mémoire non configurée\n");
            LogLevelManager.logError(DRIVER_NAME, "Test tag échoué: zone mémoire null");
            return result.toString();
        }

        if (tag.getType() == null) {
            result.append("ÉCHEC: Type de données non configuré\n");
            LogLevelManager.logError(DRIVER_NAME, "Test tag échoué: type null");
            return result.toString();
        }

        // Affichage de la configuration
        result.append("Configuration:\n");
        result.append("  Zone mémoire: ").append(tag.getMemory().getName()).append("\n");
        result.append("  Adresse: ").append(tag.getByteAddress()).append("\n");
        result.append("  Type: ").append(tag.getType().getType()).append("\n");
        result.append("  Unit ID: ").append(machine.getBus() != null ? machine.getBus() : 1).append("\n");

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
     * Validation de la configuration d'un tag
     */
    private boolean validateTagConfiguration(Tag tag) {
        if (tag == null) {
            LogLevelManager.logError(DRIVER_NAME, "Tag null fourni pour validation");
            return false;
        }

        if (tag.getByteAddress() == null) {
            LogLevelManager.logError(DRIVER_NAME, "Adresse byte manquante pour tag: " + tag.getName());
            return false;
        }

        if (tag.getMemory() == null || tag.getMemory().getName() == null) {
            LogLevelManager.logError(DRIVER_NAME, "Zone mémoire manquante pour tag: " + tag.getName());
            return false;
        }

        if (tag.getType() == null || tag.getType().getType() == null) {
            LogLevelManager.logError(DRIVER_NAME, "Type de données manquant pour tag: " + tag.getName());
            return false;
        }

        // Validation de la zone mémoire
        String memoryArea = tag.getMemory().getName().toUpperCase();
        if (!memoryArea.equals("COIL")
                && !memoryArea.equals("DISCRETE INPUT")
                && !memoryArea.equals("HOLDING REGISTER")
                && !memoryArea.equals("INPUT REGISTER")) {
            LogLevelManager.logError(DRIVER_NAME, "Zone mémoire Modbus invalide '" + memoryArea + "' pour tag: " + tag.getName());
            return false;
        }

        LogLevelManager.logTrace(DRIVER_NAME, "Validation réussie pour tag: " + tag.getName());
        return true;
    }

    /**
     * Méthode pour obtenir des statistiques du driver
     */
    public String getDriverStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("=== Statistiques Driver Modbus ===\n");
        stats.append("État: ").append(isConnected() ? "Connecté" : "Déconnecté").append("\n");

        if (machine != null) {
            stats.append("Machine: ").append(machine.getName()).append("\n");
            stats.append("Endpoint: ").append(machine.getAddress()).append(":")
                    .append(machine.getPort() != null ? machine.getPort() : 502).append("\n");
            stats.append("Unit ID: ").append(machine.getBus() != null ? machine.getBus() : 1).append("\n");
        }

        // Note: Pour avoir des vraies statistiques, il faudrait ajouter des compteurs
        // comme dans SiemensDriver (totalReads, totalWrites, totalErrors, etc.)
        stats.append("Fonctionnalités:\n");
        stats.append("  - Lecture: Implémentée\n");
        stats.append("  - Écriture: En développement\n");
        stats.append("  - Types supportés: INT, UINT, DINT, UDINT, REAL, BOOL\n");
        stats.append("  - Zones mémoires: COIL, DISCRETE INPUT, HOLDING REGISTER, INPUT REGISTER\n");

        LogLevelManager.logDebug(DRIVER_NAME, "Statistiques générées");
        return stats.toString();
    }
}
