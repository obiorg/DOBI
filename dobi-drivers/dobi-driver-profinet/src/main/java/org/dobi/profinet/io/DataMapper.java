package org.dobi.profinet.io;

import org.dobi.profinet.config.ProfinetConfig;
import org.dobi.profinet.device.ProfinetDevice;
import org.dobi.profinet.device.SlotConfiguration;
import org.dobi.logging.LogLevelManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

public class DataMapper {
    private static final String COMPONENT_NAME = "PROFINET-MAPPER";
    
    private final ProfinetConfig config;
    
    public DataMapper(ProfinetConfig config) {
        this.config = config;
    }
    
    public Map<String, Object> decodeInputData(ProfinetDevice device, byte[] data) {
        Map<String, Object> decodedData = new HashMap<>();
        
        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.order(ByteOrder.LITTLE_ENDIAN); // Profinet utilise Little Endian
            
            LogLevelManager.logTrace(COMPONENT_NAME, "Décodage " + data.length + " bytes pour " + device.getStationName());
            
            int offset = 0;
            
            // Traitement des données par slot
            for (SlotConfiguration slot : device.getSlots().values()) {
                if (slot.getInputSize() > 0 && offset + slot.getInputSize() <= data.length) {
                    
                    // Décodage selon le type de module
                    Map<String, Object> slotData = decodeSlotInputData(slot, data, offset);
                    decodedData.putAll(slotData);
                    
                    offset += slot.getInputSize();
                }
            }
            
            LogLevelManager.logTrace(COMPONENT_NAME, "Données décodées: " + decodedData.size() + " variables");
            
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur décodage données: " + e.getMessage());
        }
        
        return decodedData;
    }
    
    public byte[] encodeOutputData(ProfinetDevice device, Map<String, Object> outputData) {
        try {
            // Calcul de la taille totale des données de sortie
            int totalSize = device.getSlots().values().stream()
                .mapToInt(SlotConfiguration::getOutputSize)
                .sum();
            
            if (totalSize == 0) {
                return new byte[0];
            }
            
            ByteBuffer buffer = ByteBuffer.allocate(totalSize);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            
            LogLevelManager.logTrace(COMPONENT_NAME, "Encodage " + outputData.size() + " variables vers " + totalSize + " bytes");
            
            // Encodage des données par slot
            for (SlotConfiguration slot : device.getSlots().values()) {
                if (slot.getOutputSize() > 0) {
                    byte[] slotData = encodeSlotOutputData(slot, outputData);
                    buffer.put(slotData);
                }
            }
            
            return buffer.array();
            
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur encodage données: " + e.getMessage());
            return new byte[0];
        }
    }
    
    private Map<String, Object> decodeSlotInputData(SlotConfiguration slot, byte[] data, int offset) {
        Map<String, Object> slotData = new HashMap<>();
        
        try {
            String slotPrefix = "Slot" + slot.getSlotNumber() + "_";
            
            // Décodage selon le type de module
            switch (slot.getModuleType()) {
                case "Digital I/O":
                    // 2 bytes d'entrées digitales (16 bits)
                    if (offset + 2 <= data.length) {
                        int digitalInputs = ((data[offset + 1] & 0xFF) << 8) | (data[offset] & 0xFF);
                        
                        // Extraction des bits individuels
                        for (int bit = 0; bit < 16; bit++) {
                            boolean bitValue = (digitalInputs & (1 << bit)) != 0;
                            slotData.put(slotPrefix + "DI" + bit, bitValue);
                        }
                    }
                    break;
                    
                case "Analog I/O":
                    // 8 bytes d'entrées analogiques (4 channels x 2 bytes)
                    if (offset + 8 <= data.length) {
                        for (int channel = 0; channel < 4; channel++) {
                            int channelOffset = offset + (channel * 2);
                            int rawValue = ((data[channelOffset + 1] & 0xFF) << 8) | (data[channelOffset] & 0xFF);
                            
                            // Conversion en valeur engineering (0-10V par exemple)
                            float engineeringValue = (rawValue / 32767.0f) * 10.0f;
                            slotData.put(slotPrefix + "AI" + channel, engineeringValue);
                        }
                    }
                    break;
                    
                default:
                    // Module générique - traitement byte par byte
                    for (int i = 0; i < slot.getInputSize() && offset + i < data.length; i++) {
                        slotData.put(slotPrefix + "Byte" + i, data[offset + i] & 0xFF);
                    }
                    break;
            }
            
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur décodage slot " + slot.getSlotNumber() + ": " + e.getMessage());
        }
        
        return slotData;
    }
    
    private byte[] encodeSlotOutputData(SlotConfiguration slot, Map<String, Object> outputData) {
        byte[] slotBytes = new byte[slot.getOutputSize()];
        
        try {
            String slotPrefix = "Slot" + slot.getSlotNumber() + "_";
            
            // Encodage selon le type de module
            switch (slot.getModuleType()) {
                case "Digital I/O":
                    // 2 bytes de sorties digitales (16 bits)
                    if (slotBytes.length >= 2) {
                        int digitalOutputs = 0;
                        
                        // Assemblage des bits individuels
                        for (int bit = 0; bit < 16; bit++) {
                            String bitKey = slotPrefix + "DO" + bit;
                            Object bitValue = outputData.get(bitKey);
                            
                            if (bitValue instanceof Boolean && (Boolean) bitValue) {
                                digitalOutputs |= (1 << bit);
                            }
                        }
                        
                        slotBytes[0] = (byte) (digitalOutputs & 0xFF);
                        slotBytes[1] = (byte) ((digitalOutputs >> 8) & 0xFF);
                    }
                    break;
                    
                case "Analog I/O":
                    // 8 bytes de sorties analogiques (4 channels x 2 bytes)
                    if (slotBytes.length >= 8) {
                        for (int channel = 0; channel < 4; channel++) {
                            String channelKey = slotPrefix + "AO" + channel;
                            Object channelValue = outputData.get(channelKey);
                            
                            int rawValue = 0;
                            if (channelValue instanceof Number) {
                                // Conversion depuis valeur engineering vers valeur brute
                                float engineeringValue = ((Number) channelValue).floatValue();
                                rawValue = (int) ((engineeringValue / 10.0f) * 32767.0f);
                                rawValue = Math.max(0, Math.min(32767, rawValue));
                            }
                            
                            int channelOffset = channel * 2;
                            slotBytes[channelOffset] = (byte) (rawValue & 0xFF);
                            slotBytes[channelOffset + 1] = (byte) ((rawValue >> 8) & 0xFF);
                        }
                    }
                    break;
                    
                default:
                    // Module générique
                    for (int i = 0; i < slotBytes.length; i++) {
                        String byteKey = slotPrefix + "Byte" + i;
                        Object byteValue = outputData.get(byteKey);
                        
                        if (byteValue instanceof Number) {
                            slotBytes[i] = ((Number) byteValue).byteValue();
                        }
                    }
                    break;
            }
            
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur encodage slot " + slot.getSlotNumber() + ": " + e.getMessage());
        }
        
        return slotBytes;
    }
}