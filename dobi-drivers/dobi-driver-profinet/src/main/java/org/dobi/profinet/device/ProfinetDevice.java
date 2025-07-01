package org.dobi.profinet.device;

import java.util.HashMap;
import java.util.Map;

public class ProfinetDevice {
    private String stationName;
    private String ipAddress;
    private String macAddress;
    private String deviceType;
    private String vendorName;
    private String deviceName;
    private String version;
    private int deviceId;
    private int vendorId;
    private boolean connected;
    
    // Configuration des slots et modules
    private Map<Integer, SlotConfiguration> slots = new HashMap<>();
    private Map<String, Object> parameters = new HashMap<>();
    
    // Constructeurs
    public ProfinetDevice() {}
    
    public ProfinetDevice(String stationName, String ipAddress) {
        this.stationName = stationName;
        this.ipAddress = ipAddress;
    }
    
    // Getters et Setters
    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
    
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    
    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }
    
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    
    public int getDeviceId() { return deviceId; }
    public void setDeviceId(int deviceId) { this.deviceId = deviceId; }
    
    public int getVendorId() { return vendorId; }
    public void setVendorId(int vendorId) { this.vendorId = vendorId; }
    
    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }
    
    public Map<Integer, SlotConfiguration> getSlots() { return slots; }
    public void setSlots(Map<Integer, SlotConfiguration> slots) { this.slots = slots; }
    
    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    
    // Méthodes utilitaires
    public void addSlot(int slotNumber, SlotConfiguration slot) {
        slots.put(slotNumber, slot);
    }
    
    public SlotConfiguration getSlot(int slotNumber) {
        return slots.get(slotNumber);
    }
    
    public void setParameter(String name, Object value) {
        parameters.put(name, value);
    }
    
    public Object getParameter(String name) {
        return parameters.get(name);
    }
    
    @Override
    public String toString() {
        return "ProfinetDevice{" +
                "stationName='" + stationName + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", deviceType='" + deviceType + '\'' +
                ", connected=" + connected +
                '}';
    }
}