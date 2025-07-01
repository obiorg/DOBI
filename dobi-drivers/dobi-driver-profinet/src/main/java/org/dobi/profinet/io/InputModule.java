package org.dobi.profinet.io;

import org.dobi.profinet.device.ProfinetDevice;
import org.dobi.profinet.device.SlotConfiguration;
import org.dobi.logging.LogLevelManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InputModule {

    private static final String COMPONENT_NAME = "PROFINET-INPUT";

    private final ProfinetDevice device;
    private final SlotConfiguration slotConfig;
    private final Map<String, Object> inputValues = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> inputTypes = new HashMap<>();
    private final Map<String, LocalDateTime> lastUpdateTimes = new ConcurrentHashMap<>();

    // Configuration du module
    private int slotNumber;
    private String moduleType;
    private int inputSize;
    private boolean isActive = false;

    public InputModule(ProfinetDevice device, SlotConfiguration slotConfig) {
        this.device = device;
        this.slotConfig = slotConfig;
        this.slotNumber = slotConfig.getSlotNumber();
        this.moduleType = slotConfig.getModuleType();
        this.inputSize = slotConfig.getInputSize();

        initializeInputStructure();

        LogLevelManager.logDebug(COMPONENT_NAME, "Module d'entrée créé - Slot: " + slotNumber
                + ", Type: " + moduleType + ", Taille: " + inputSize + " bytes");
    }

    private void initializeInputStructure() {
        // Initialisation de la structure des entrées selon le type de module
        switch (moduleType) {
            case "Digital I/O":
                initializeDigitalInputs();
                break;
            case "Analog I/O":
                initializeAnalogInputs();
                break;
            case "Interface":
                initializeInterfaceInputs();
                break;
            default:
                initializeGenericInputs();
                break;
        }
    }

    private void initializeDigitalInputs() {
        // 16 entrées digitales (2 bytes)
        for (int bit = 0; bit < 16; bit++) {
            String inputName = "DI" + bit;
            inputValues.put(inputName, false);
            inputTypes.put(inputName, Boolean.class);
        }

        LogLevelManager.logTrace(COMPONENT_NAME, "16 entrées digitales initialisées pour slot " + slotNumber);
    }

    private void initializeAnalogInputs() {
        // 4 entrées analogiques (8 bytes = 4 channels x 2 bytes)
        for (int channel = 0; channel < 4; channel++) {
            String inputName = "AI" + channel;
            inputValues.put(inputName, 0.0f);
            inputTypes.put(inputName, Float.class);
        }

        LogLevelManager.logTrace(COMPONENT_NAME, "4 entrées analogiques initialisées pour slot " + slotNumber);
    }

    private void initializeInterfaceInputs() {
        // Module d'interface - informations de statut
        inputValues.put("POWER_STATUS", true);
        inputTypes.put("POWER_STATUS", Boolean.class);

        inputValues.put("ERROR_CODE", 0);
        inputTypes.put("ERROR_CODE", Integer.class);

        inputValues.put("DIAGNOSTIC_DATA", 0);
        inputTypes.put("DIAGNOSTIC_DATA", Integer.class);

        LogLevelManager.logTrace(COMPONENT_NAME, "Entrées interface initialisées pour slot " + slotNumber);
    }

    private void initializeGenericInputs() {
        // Module générique - entrées byte par byte
        for (int i = 0; i < inputSize; i++) {
            String inputName = "Byte" + i;
            inputValues.put(inputName, (byte) 0);
            inputTypes.put(inputName, Byte.class);
        }

        LogLevelManager.logTrace(COMPONENT_NAME, inputSize + " entrées génériques initialisées pour slot " + slotNumber);
    }

    /**
     * Décode les données binaires reçues et met à jour les valeurs d'entrée
     */
    public void decodeInputData(byte[] data, int offset) {
        if (data == null || offset + inputSize > data.length) {
            LogLevelManager.logError(COMPONENT_NAME, "Données insuffisantes pour décoder slot " + slotNumber);
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(data, offset, inputSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        try {
            switch (moduleType) {
                case "Digital I/O":
                    decodeDigitalInputs(buffer);
                    break;
                case "Analog I/O":
                    decodeAnalogInputs(buffer);
                    break;
                case "Interface":
                    decodeInterfaceInputs(buffer);
                    break;
                default:
                    decodeGenericInputs(buffer);
                    break;
            }

            // Mise à jour du timestamp
            LocalDateTime now = LocalDateTime.now();
            for (String inputName : inputValues.keySet()) {
                lastUpdateTimes.put(inputName, now);
            }

            LogLevelManager.logTrace(COMPONENT_NAME, "Données d'entrée décodées: " + inputSize
                    + " bytes (Slot " + slotNumber + ")");

        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur décodage entrées slot " + slotNumber + ": " + e.getMessage());
        }
    }

    private void decodeDigitalInputs(ByteBuffer buffer) {
        // Lecture de 2 bytes en Little Endian
        int digitalWord = (buffer.get() & 0xFF) | ((buffer.get() & 0xFF) << 8);

        // Extraction des bits individuels
        for (int bit = 0; bit < 16; bit++) {
            String inputName = "DI" + bit;
            boolean bitValue = (digitalWord & (1 << bit)) != 0;
            inputValues.put(inputName, bitValue);
        }

        LogLevelManager.logTrace(COMPONENT_NAME, "Entrées digitales décodées: 0x" + Integer.toHexString(digitalWord));
    }

    private void decodeAnalogInputs(ByteBuffer buffer) {
        // 4 channels x 2 bytes chacun
        for (int channel = 0; channel < 4; channel++) {
            String inputName = "AI" + channel;

            // Lecture valeur 16-bit en Little Endian
            int rawValue = (buffer.get() & 0xFF) | ((buffer.get() & 0xFF) << 8);

            // Conversion vers valeur engineering (0-10V)
            float engineeringValue = (rawValue / 32767.0f) * 10.0f;
            inputValues.put(inputName, engineeringValue);
        }

        LogLevelManager.logTrace(COMPONENT_NAME, "Entrées analogiques décodées: 4 channels");
    }

    private void decodeInterfaceInputs(ByteBuffer buffer) {
        // Lecture du byte de statut
        byte statusByte = buffer.get();

        inputValues.put("POWER_STATUS", (statusByte & 0x01) != 0);
        inputValues.put("ERROR_CODE", (statusByte >> 1) & 0x07);

        // Lecture des données de diagnostic si disponibles
        if (buffer.hasRemaining()) {
            int diagnosticData = 0;
            while (buffer.hasRemaining()) {
                diagnosticData = (diagnosticData << 8) | (buffer.get() & 0xFF);
            }
            inputValues.put("DIAGNOSTIC_DATA", diagnosticData);
        }

        LogLevelManager.logTrace(COMPONENT_NAME, "Entrées interface décodées: 0x" + Integer.toHexString(statusByte));
    }

    private void decodeGenericInputs(ByteBuffer buffer) {
        // Module générique - lecture byte par byte
        for (int i = 0; i < inputSize && buffer.hasRemaining(); i++) {
            String inputName = "Byte" + i;
            byte value = buffer.get();
            inputValues.put(inputName, value);
        }

        LogLevelManager.logTrace(COMPONENT_NAME, "Entrées génériques décodées: " + inputSize + " bytes");
    }

    /**
     * Obtient la valeur d'une entrée spécifique
     */
    public Object getInput(String inputName) {
        return inputValues.get(inputName);
    }

    /**
     * Obtient toutes les valeurs d'entrée avec le préfixe slot
     */
    public Map<String, Object> getAllInputsWithPrefix() {
        Map<String, Object> prefixedInputs = new HashMap<>();
        String slotPrefix = "Slot" + slotNumber + "_";

        for (Map.Entry<String, Object> entry : inputValues.entrySet()) {
            prefixedInputs.put(slotPrefix + entry.getKey(), entry.getValue());
        }

        return prefixedInputs;
    }

    /**
     * Obtient toutes les valeurs d'entrée
     */
    public Map<String, Object> getAllInputs() {
        return new HashMap<>(inputValues);
    }

    /**
     * Obtient le timestamp de dernière mise à jour d'une entrée
     */
    public LocalDateTime getLastUpdateTime(String inputName) {
        return lastUpdateTimes.get(inputName);
    }

    /**
     * Vérifie si une entrée a été mise à jour récemment
     */
    public boolean isInputFresh(String inputName, long maxAgeSeconds) {
        LocalDateTime lastUpdate = lastUpdateTimes.get(inputName);
        if (lastUpdate == null) {
            return false;
        }

        return lastUpdate.plusSeconds(maxAgeSeconds).isAfter(LocalDateTime.now());
    }

    /**
     * Active/désactive le module
     */
    public void setActive(boolean active) {
        this.isActive = active;
        LogLevelManager.logDebug(COMPONENT_NAME, "Module slot " + slotNumber + " "
                + (active ? "activé" : "désactivé"));
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

    public int getInputSize() {
        return inputSize;
    }

    public boolean isActive() {
        return isActive;
    }

    public Map<String, Class<?>> getInputTypes() {
        return new HashMap<>(inputTypes);
    }

    @Override
    public String toString() {
        return "InputModule{"
                + "slot=" + slotNumber
                + ", type='" + moduleType + '\''
                + ", size=" + inputSize
                + ", active=" + isActive
                + ", inputs=" + inputValues.size()
                + '}';
    }
}
