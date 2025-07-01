package org.dobi.profinet.device;

import org.dobi.entities.Tag;
import org.dobi.profinet.config.ProfinetConfig;
import org.dobi.logging.LogLevelManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DeviceManager {
    private static final String COMPONENT_NAME = "PROFINET-DEVICE";
    
    private final ProfinetConfig config;
    private final Map<String, ProfinetDevice> connectedDevices = new ConcurrentHashMap<>();
    
    public DeviceManager(ProfinetConfig config) {
        this.config = config;
    }
    
    public void configureDevice(ProfinetDevice device) throws Exception {
        LogLevelManager.logDebug(COMPONENT_NAME, "Configuration de l'équipement: " + device.getStationName());
        
        // Configuration des slots par défaut si non définis
        if (device.getSlots().isEmpty()) {
            // Slot 0: Interface module (toujours présent)
            SlotConfiguration interfaceSlot = new SlotConfiguration(0, "Interface");
            interfaceSlot.setModuleName("IM 155-6 Interface Module");
            device.addSlot(0, interfaceSlot);
            
            // Slot 1: Module d'E/S digital (exemple)
            SlotConfiguration digitalSlot = new SlotConfiguration(1, "Digital I/O");
            digitalSlot.setModuleName("DI 16x24VDC BA");
            digitalSlot.setInputSize(2); // 2 bytes d'entrées
            digitalSlot.setOutputSize(2); // 2 bytes de sorties
            device.addSlot(1, digitalSlot);
            
            LogLevelManager.logDebug(COMPONENT_NAME, "Configuration par défaut appliquée pour " + device.getStationName());
        }
        
        // Validation de la configuration
        validateDeviceConfiguration(device);
    }
    
    public boolean establishConnection(ProfinetDevice device) {
        try {
            LogLevelManager.logDebug(COMPONENT_NAME, "Établissement connexion AR avec: " + device.getStationName());
            
            // Simulation de l'établissement de la connexion AR (Application Relation)
            // En réalité, ceci nécessite l'échange de paquets Profinet spécialisés
            
            // Test de connectivité réseau basique
            boolean networkReachable = testNetworkConnectivity(device);
            if (!networkReachable) {
                LogLevelManager.logError(COMPONENT_NAME, "Équipement non accessible: " + device.getIpAddress());
                return false;
            }
            
            device.setConnected(true);
            connectedDevices.put(device.getStationName(), device);
            
            LogLevelManager.logInfo(COMPONENT_NAME, "Connexion AR établie avec: " + device.getStationName());
            return true;
            
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur établissement connexion: " + e.getMessage());
            return false;
        }
    }
    
    public void disconnectDevice(ProfinetDevice device) {
        try {
            device.setConnected(false);
            connectedDevices.remove(device.getStationName());
            LogLevelManager.logInfo(COMPONENT_NAME, "Équipement déconnecté: " + device.getStationName());
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur déconnexion: " + e.getMessage());
        }
    }
    
    public Object readParameter(ProfinetDevice device, Tag tag) throws Exception {
        if (!device.isConnected()) {
            throw new Exception("Équipement non connecté: " + device.getStationName());
        }
        
        LogLevelManager.logTrace(COMPONENT_NAME, "Lecture paramètre acyclique: " + tag.getName());
        
        // Simulation de lecture acyclique
        // En réalité, utilisation des services Read/Write Profinet
        
        Object value = simulateParameterRead(device, tag);
        
        LogLevelManager.logTrace(COMPONENT_NAME, "Paramètre lu: " + tag.getName() + " = " + value);
        return value;
    }
    
    public void writeParameter(ProfinetDevice device, Tag tag, Object value) throws Exception {
        if (!device.isConnected()) {
            throw new Exception("Équipement non connecté: " + device.getStationName());
        }
        
        LogLevelManager.logTrace(COMPONENT_NAME, "Écriture paramètre acyclique: " + tag.getName() + " = " + value);
        
        // Simulation d'écriture acyclique
        simulateParameterWrite(device, tag, value);
        
        LogLevelManager.logTrace(COMPONENT_NAME, "Paramètre écrit: " + tag.getName());
    }
    
    public boolean testConnection(ProfinetDevice device) {
        try {
            return testNetworkConnectivity(device) && device.isConnected();
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur test connexion: " + e.getMessage());
            return false;
        }
    }
    
    public Map<String, Object> readDeviceIdentification(ProfinetDevice device) throws Exception {
        Map<String, Object> identification = new HashMap<>();
        
        identification.put("StationName", device.getStationName());
        identification.put("DeviceType", device.getDeviceType());
        identification.put("VendorName", device.getVendorName());
        identification.put("DeviceName", device.getDeviceName());
        identification.put("Version", device.getVersion());
        identification.put("IPAddress", device.getIpAddress());
        identification.put("MACAddress", device.getMacAddress());
        identification.put("DeviceID", device.getDeviceId());
        identification.put("VendorID", device.getVendorId());
        identification.put("Slots", device.getSlots().size());
        
        return identification;
    }
    
    public boolean isDeviceHealthy(ProfinetDevice device) {
        try {
            return device.isConnected() && testNetworkConnectivity(device);
        } catch (Exception e) {
            return false;
        }
    }
    
    private void validateDeviceConfiguration(ProfinetDevice device) throws Exception {
        if (device.getStationName() == null || device.getStationName().isEmpty()) {
            throw new Exception("Nom de station manquant");
        }
        
        if (device.getIpAddress() == null || device.getIpAddress().isEmpty()) {
            throw new Exception("Adresse IP manquante");
        }
        
        if (device.getSlots().isEmpty()) {
            throw new Exception("Aucun slot configuré");
        }
        
        LogLevelManager.logDebug(COMPONENT_NAME, "Configuration validée pour: " + device.getStationName());
    }
    
    private boolean testNetworkConnectivity(ProfinetDevice device) {
        try {
            java.net.InetAddress address = java.net.InetAddress.getByName(device.getIpAddress());
            return address.isReachable(3000); // 3 secondes timeout
        } catch (Exception e) {
            LogLevelManager.logDebug(COMPONENT_NAME, "Test connectivité échoué pour " + device.getIpAddress() + ": " + e.getMessage());
            return false;
        }
    }
    
    private Object simulateParameterRead(ProfinetDevice device, Tag tag) {
        // Simulation basée sur le type de tag
        if (tag.getType() != null) {
            String typeName = tag.getType().getType().toUpperCase();
            
            switch (typeName) {
                case "BOOL":
                    return Math.random() > 0.5;
                case "INT":
                    return (int) (Math.random() * 1000);
                case "REAL":
                case "FLOAT":
                    return (float) (Math.random() * 100.0);
                case "STRING":
                    return "SimValue_" + System.currentTimeMillis() % 1000;
                default:
                    return 42; // Valeur par défaut
            }
        }
        
        return null;
    }
    
    private void simulateParameterWrite(ProfinetDevice device, Tag tag, Object value) {
        // Simulation d'écriture - stockage en paramètres de l'équipement
        String paramKey = "Tag_" + tag.getName();
        device.setParameter(paramKey, value);
        
        LogLevelManager.logTrace(COMPONENT_NAME, "Paramètre simulé stocké: " + paramKey + " = " + value);
    }
}