package org.dobi.profinet.config;

public class ProfinetConfig {
    private String stationName = "DOBI_Station";
    private String networkInterface = "eth0";
    private int cyclicInterval = 10; // ms
    private int connectionTimeout = 10000; // ms
    private int discoveryTimeout = 5000; // ms
    private boolean deviceScanEnabled = true;
    private boolean gsdmlValidation = true;
    
    // Getters et Setters
    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }
    
    public String getNetworkInterface() { return networkInterface; }
    public void setNetworkInterface(String networkInterface) { this.networkInterface = networkInterface; }
    
    public int getCyclicInterval() { return cyclicInterval; }
    public void setCyclicInterval(int cyclicInterval) { this.cyclicInterval = cyclicInterval; }
    
    public int getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(int connectionTimeout) { this.connectionTimeout = connectionTimeout; }
    
    public int getDiscoveryTimeout() { return discoveryTimeout; }
    public void setDiscoveryTimeout(int discoveryTimeout) { this.discoveryTimeout = discoveryTimeout; }
    
    public boolean isDeviceScanEnabled() { return deviceScanEnabled; }
    public void setDeviceScanEnabled(boolean deviceScanEnabled) { this.deviceScanEnabled = deviceScanEnabled; }
    
    public boolean isGsdmlValidation() { return gsdmlValidation; }
    public void setGsdmlValidation(boolean gsdmlValidation) { this.gsdmlValidation = gsdmlValidation; }
    
    @Override
    public String toString() {
        return "ProfinetConfig{" +
                "stationName='" + stationName + '\'' +
                ", networkInterface='" + networkInterface + '\'' +
                ", cyclicInterval=" + cyclicInterval +
                ", connectionTimeout=" + connectionTimeout +
                '}';
    }
}