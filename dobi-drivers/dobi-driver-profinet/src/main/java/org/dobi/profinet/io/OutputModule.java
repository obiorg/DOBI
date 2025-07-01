package org.dobi.profinet.io;

import org.dobi.profinet.device.ProfinetDevice;
import org.dobi.profinet.device.SlotConfiguration;
import org.dobi.logging.LogLevelManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OutputModule {

    private static final String COMPONENT_NAME = "PROFINET-OUTPUT";

    private final ProfinetDevice device;
    private final SlotConfiguration slotConfig;
    private final Map<String, Object> outputValues = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> outputTypes = new HashMap<>();

    // Configuration du module
    private int slotNumber;
    private String moduleType;
    private int outputSize;
    private boolean isActive = false;

    public OutputModule(ProfinetDevice device, SlotConfiguration slotConfig) {
        this.device = device;
        this.slotConfig = slotConfig;
        this.slotNumber = slotConfig.getSlotNumber();
        this.moduleType = slotConfig.getModuleType();
        this.outputSize = slotConfig.getOutputSize();

        initializeOutputStructure();

        LogLevelManager.logDebug(COMPONENT_NAME, "Module de sortie créé - Slot: " + slotNumber
                + ", Type: " + moduleType + ", Taille: " + outputSize + " bytes");
    }

    private void initializeOutputStructure() {
        // Initialisation de la structure des sorties selon le type de module
        switch (moduleType) {
            case "Digital I/O":
                initializeDigitalOutputs();
                break;
            case "Analog I/O":
                initializeAnalogOutputs();
                break;
            case "Interface":
                initializeInterfaceOutputs();
                break;
            default:
                initializeGenericOutputs();
                break;
        }
    }

    private void initializeDigitalOutputs() {
        // 16 sorties digitales (2 bytes)
        for (int bit = 0; bit < 16; bit++) {
            String outputName = "DO" + bit;
            outputValues.put(outputName, false);
            outputTypes.put(outputName, Boolean.class);
        }

        LogLevelManager.logTrace(COMPONENT_NAME, "16 sorties digitales initialisées pour slot " + slotNumber);
    }

    private void initializeAnalogOutputs() {
        // 4 sorties analogiques (8 bytes = 4 channels x 2 bytes)
        for (int channel = 0; channel < 4; channel++) {
            String outputName = "AO" + channel;
            outputValues.put(outputName, 0.0f);
            outputTypes.put(outputName, Float.class);
        }

        LogLevelManager.logTrace(COMPONENT_NAME, "4 sorties analogiques initialisées pour slot " + slotNumber);
    }

    private void initializeInterfaceOutputs() {
        // Module d'interface - généralement pas de sorties cycliques
        // Mais peut contenir des paramètres de configuration
        outputValues.put("STATUS_LED", false);
        outputTypes.put("STATUS_LED", Boolean.class);

        outputValues.put("CONFIG_MODE", 0);
        outputTypes.put("CONFIG_MODE", Integer.class);

        LogLevelManager.logTrace(COMPONENT_NAME, "Sorties interface initialisées pour slot " + slotNumber);
    }

    private void initializeGenericOutputs() {
        // Module générique - sorties byte par byte
        for (int i = 0; i < outputSize; i++) {
            String outputName = "Byte" + i;
            outputValues.put(outputName, (byte) 0);
            outputTypes.put(outputName, Byte.class);
        }

        LogLevelManager.logTrace(COMPONENT_NAME, outputSize + " sorties génériques initialisées pour slot " + slotNumber);
    }

    /**
     * Définit la valeur d'une sortie spécifique
     */
    public void setOutput(String outputName, Object value) {
        if (!outputTypes.containsKey(outputName)) {
            LogLevelManager.logError(COMPONENT_NAME, "Sortie inconnue: " + outputName + " (Slot " + slotNumber + ")");
            return;
        }

        try {
            // Validation et conversion du type
            Object convertedValue = convertToTargetType(value, outputTypes.get(outputName));
            outputValues.put(outputName, convertedValue);

            LogLevelManager.logTrace(COMPONENT_NAME, "Sortie mise à jour: " + outputName + " = " + convertedValue
                    + " (Slot " + slotNumber + ")");

        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur conversion sortie " + outputName + ": " + e.getMessage());
        }
    }

    /**
     * Obtient la valeur d'une sortie spécifique
     */
    public Object getOutput(String outputName) {
        return outputValues.get(outputName);
    }

    /**
     * Obtient toutes les valeurs de sortie
     */
    public Map<String, Object> getAllOutputs() {
        return new HashMap<>(outputValues);
    }

    /**
     * Encode toutes les sorties en données binaires pour transmission
     */
    public byte[] encodeOutputData() {
        if (outputSize <= 0) {
            return new byte[0];
        }

        ByteBuffer buffer = ByteBuffer.allocate(outputSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        try {
            switch (moduleType) {
                case "Digital I/O":
                    encodeDigitalOutputs(buffer);
                    break;
                case "Analog I/O":
                    encodeAnalogOutputs(buffer);
                    break;
                case "Interface":
                    encodeInterfaceOutputs(buffer);
                    break;
                default:
                    encodeGenericOutputs(buffer);
                    break;
            }

            LogLevelManager.logTrace(COMPONENT_NAME, "Données de sortie encodées: " + outputSize
                    + " bytes (Slot " + slotNumber + ")");

        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur encodage sorties slot " + slotNumber + ": " + e.getMessage());
        }

        return buffer.array();
    }

    private void encodeDigitalOutputs(ByteBuffer buffer) {
        // Assemblage des 16 bits en 2 bytes
        int digitalWord = 0;

        for (int bit = 0; bit < 16; bit++) {
            String outputName = "DO" + bit;
            Boolean value = (Boolean) outputValues.get(outputName);

            if (value != null && value) {
                digitalWord |= (1 << bit);
            }
        }

        // Écriture en Little Endian
        buffer.put((byte) (digitalWord & 0xFF));
        buffer.put((byte) ((digitalWord >> 8) & 0xFF));

        LogLevelManager.logTrace(COMPONENT_NAME, "Sorties digitales encodées: 0x" + Integer.toHexString(digitalWord));
    }

    private void encodeAnalogOutputs(ByteBuffer buffer) {
        // 4 channels x 2 bytes chacun
        for (int channel = 0; channel < 4; channel++) {
            String outputName = "AO" + channel;
            Float value = (Float) outputValues.get(outputName);

            if (value == null) {
                value = 0.0f;
            }

            // Conversion 0-10V vers valeur 16-bit (0-32767)
            int rawValue = Math.round((value / 10.0f) * 32767.0f);
            rawValue = Math.max(0, Math.min(32767, rawValue));

            // Écriture en Little Endian
            buffer.put((byte) (rawValue & 0xFF));
            buffer.put((byte) ((rawValue >> 8) & 0xFF));
        }

        LogLevelManager.logTrace(COMPONENT_NAME, "Sorties analogiques encodées: 4 channels");
    }

    private void encodeInterfaceOutputs(ByteBuffer buffer) {
        // Module d'interface - données de statut
        byte statusByte = 0;

        Boolean statusLed = (Boolean) outputValues.get("STATUS_LED");
        if (statusLed != null && statusLed) {
            statusByte |= 0x01;
        }

        Integer configMode = (Integer) outputValues.get("CONFIG_MODE");
        if (configMode != null) {
            statusByte |= (configMode << 1) & 0x0E;
        }

        buffer.put(statusByte);

        // Remplissage avec des zéros si nécessaire
        while (buffer.hasRemaining()) {
            buffer.put((byte) 0);
        }

        LogLevelManager.logTrace(COMPONENT_NAME, "Sorties interface encodées: 0x" + Integer.toHexString(statusByte));
    }

    private void encodeGenericOutputs(ByteBuffer buffer) {
        // Module générique - copie byte par byte
        for (int i = 0; i < outputSize; i++) {
            String outputName = "Byte" + i;
            Byte value = (Byte) outputValues.get(outputName);

            if (value == null) {
                value = 0;
            }

            buffer.put(value);
        }

        LogLevelManager.logTrace(COMPONENT_NAME, "Sorties génériques encodées: " + outputSize + " bytes");
    }

    /**
     * Met à jour les sorties depuis des données externes
     */
    public void updateFromExternalData(Map<String, Object> externalData) {
        String slotPrefix = "Slot" + slotNumber + "_";

        for (Map.Entry<String, Object> entry : externalData.entrySet()) {
            String key = entry.getKey();

            if (key.startsWith(slotPrefix)) {
                String outputName = key.substring(slotPrefix.length());
                setOutput(outputName, entry.getValue());
            }
        }

        LogLevelManager.logTrace(COMPONENT_NAME, "Sorties mises à jour depuis données externes (Slot " + slotNumber + ")");
    }

    /**
     * Réinitialise toutes les sorties à leurs valeurs par défaut
     */
    public void resetOutputs() {
        for (Map.Entry<String, Class<?>> entry : outputTypes.entrySet()) {
            String outputName = entry.getKey();
            Class<?> type = entry.getValue();

            Object defaultValue;
            if (type == Boolean.class) {
                defaultValue = false;
            } else if (type == Float.class) {
                defaultValue = 0.0f;
            } else if (type == Integer.class) {
                defaultValue = 0;
            } else if (type == Byte.class) {
                defaultValue = (byte) 0;
            } else {
                defaultValue = null;
            }

            outputValues.put(outputName, defaultValue);
        }

        LogLevelManager.logDebug(COMPONENT_NAME, "Sorties réinitialisées (Slot " + slotNumber + ")");
    }

    /**
     * Active/désactive le module
     */
    public void setActive(boolean active) {
        this.isActive = active;

        if (!active) {
            resetOutputs();
        }

        LogLevelManager.logDebug(COMPONENT_NAME, "Module slot " + slotNumber + " "
                + (active ? "activé" : "désactivé"));
    }

    /**
     * Conversion de type sécurisée
     */
    private Object convertToTargetType(Object value, Class<?> targetType) throws Exception {
        if (value == null) {
            return null;
        }

        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        if (targetType == Boolean.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue() != 0;
            } else if (value instanceof String) {
                return Boolean.parseBoolean((String) value);
            }
        } else if (targetType == Float.class) {
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            } else if (value instanceof String) {
                return Float.parseFloat((String) value);
            }
        } else if (targetType == Integer.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value instanceof String) {
                return Integer.parseInt((String) value);
            }
        } else if (targetType == Byte.class) {
            if (value instanceof Number) {
                return ((Number) value).byteValue();
            } else if (value instanceof String) {
                return Byte.parseByte((String) value);
            }
        }

        throw new Exception("Impossible de convertir " + value.getClass().getSimpleName()
                + " vers " + targetType.getSimpleName());
    }

    // Getters
    public ProfinetDevice getDevice() {
        return device;
    }

    public SlotConfiguration getSlotConfig() {
        return slotConfig;
    }

    public int getSlotNumber() {
        return slotNumber;
    }

    public String getModuleType() {
        return moduleType;
    }

    public int getOutputSize() {
        return outputSize;
    }

    public boolean isActive() {
        return isActive;
    }

    public Map<String, Class<?>> getOutputTypes() {
        return new HashMap<>(outputTypes);
    }

    @Override
    public String toString() {
        return "OutputModule{"
                + "slot=" + slotNumber
                + ", type='" + moduleType + '\''
                + ", size=" + outputSize
                + ", active=" + isActive
                + ", outputs=" + outputValues.size()
                + '}';
    }
}
