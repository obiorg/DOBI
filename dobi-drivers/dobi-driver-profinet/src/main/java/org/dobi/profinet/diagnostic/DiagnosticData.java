package org.dobi.profinet.diagnostic;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class DiagnosticData {

    // Informations de base
    private String deviceName;
    private String ipAddress;
    private LocalDateTime timestamp;
    private DiagnosticStatus status;

    // Métriques réseau
    private long bytesReceived;
    private long bytesSent;
    private long packetsReceived;
    private long packetsSent;
    private long errorsCount;
    private double latencyMs;
    private double jitterMs;
    private double packetLossPercent;

    // Métriques Profinet
    private long cyclicCyclesCount;
    private double cyclicCycleTime;
    private long missedCycles;
    private double cycleJitter;
    private long alarmCount;
    private long diagnosisCount;

    // État des modules
    private Map<Integer, ModuleDiagnostic> moduleStates = new HashMap<>();

    // Métriques de performance
    private double cpuUsage;
    private double memoryUsage;
    private double networkLoad;
    private int connectionCount;

    // Données brutes supplémentaires
    private Map<String, Object> additionalData = new HashMap<>();

    public enum DiagnosticStatus {
        GOOD("Bon fonctionnement"),
        WARNING("Avertissement"),
        ERROR("Erreur"),
        CRITICAL("Critique"),
        UNKNOWN("Inconnu");

        private final String description;

        DiagnosticStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public static class ModuleDiagnostic {

        private int slotNumber;
        private int subslotNumber;
        private String moduleName;
        private DiagnosticStatus status;
        private String statusMessage;
        private boolean present;
        private boolean configured;
        private boolean operational;
        private int errorCode;
        private LocalDateTime lastUpdate;

        // Constructeurs
        public ModuleDiagnostic() {
        }

        public ModuleDiagnostic(int slotNumber, int subslotNumber) {
            this.slotNumber = slotNumber;
            this.subslotNumber = subslotNumber;
            this.lastUpdate = LocalDateTime.now();
        }

        // Getters et Setters
        public int getSlotNumber() {
            return slotNumber;
        }

        public void setSlotNumber(int slotNumber) {
            this.slotNumber = slotNumber;
        }

        public int getSubslotNumber() {
            return subslotNumber;
        }

        public void setSubslotNumber(int subslotNumber) {
            this.subslotNumber = subslotNumber;
        }

        public String getModuleName() {
            return moduleName;
        }

        public void setModuleName(String moduleName) {
            this.moduleName = moduleName;
        }

        public DiagnosticStatus getStatus() {
            return status;
        }

        public void setStatus(DiagnosticStatus status) {
            this.status = status;
        }

        public String getStatusMessage() {
            return statusMessage;
        }

        public void setStatusMessage(String statusMessage) {
            this.statusMessage = statusMessage;
        }

        public boolean isPresent() {
            return present;
        }

        public void setPresent(boolean present) {
            this.present = present;
        }

        public boolean isConfigured() {
            return configured;
        }

        public void setConfigured(boolean configured) {
            this.configured = configured;
        }

        public boolean isOperational() {
            return operational;
        }

        public void setOperational(boolean operational) {
            this.operational = operational;
        }

        public int getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(int errorCode) {
            this.errorCode = errorCode;
        }

        public LocalDateTime getLastUpdate() {
            return lastUpdate;
        }

        public void setLastUpdate(LocalDateTime lastUpdate) {
            this.lastUpdate = lastUpdate;
        }

        @Override
        public String toString() {
            return String.format("Module[%d.%d] %s - %s (%s)",
                    slotNumber, subslotNumber, moduleName, status, statusMessage);
        }
    }

    // Constructeurs
    public DiagnosticData() {
        this.timestamp = LocalDateTime.now();
        this.status = DiagnosticStatus.UNKNOWN;
    }

    public DiagnosticData(String deviceName) {
        this();
        this.deviceName = deviceName;
    }

    // Getters et Setters
    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public DiagnosticStatus getStatus() {
        return status;
    }

    public void setStatus(DiagnosticStatus status) {
        this.status = status;
    }

    public long getBytesReceived() {
        return bytesReceived;
    }

    public void setBytesReceived(long bytesReceived) {
        this.bytesReceived = bytesReceived;
    }

    public long getBytesSent() {
        return bytesSent;
    }

    public void setBytesSent(long bytesSent) {
        this.bytesSent = bytesSent;
    }

    public long getPacketsReceived() {
        return packetsReceived;
    }

    public void setPacketsReceived(long packetsReceived) {
        this.packetsReceived = packetsReceived;
    }

    public long getPacketsSent() {
        return packetsSent;
    }

    public void setPacketsSent(long packetsSent) {
        this.packetsSent = packetsSent;
    }

    public long getErrorsCount() {
        return errorsCount;
    }

    public void setErrorsCount(long errorsCount) {
        this.errorsCount = errorsCount;
    }

    public double getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(double latencyMs) {
        this.latencyMs = latencyMs;
    }

    public double getJitterMs() {
        return jitterMs;
    }

    public void setJitterMs(double jitterMs) {
        this.jitterMs = jitterMs;
    }

    public double getPacketLossPercent() {
        return packetLossPercent;
    }

    public void setPacketLossPercent(double packetLossPercent) {
        this.packetLossPercent = packetLossPercent;
    }

    public long getCyclicCyclesCount() {
        return cyclicCyclesCount;
    }

    public void setCyclicCyclesCount(long cyclicCyclesCount) {
        this.cyclicCyclesCount = cyclicCyclesCount;
    }

    public double getCyclicCycleTime() {
        return cyclicCycleTime;
    }

    public void setCyclicCycleTime(double cyclicCycleTime) {
        this.cyclicCycleTime = cyclicCycleTime;
    }

    public long getMissedCycles() {
        return missedCycles;
    }

    public void setMissedCycles(long missedCycles) {
        this.missedCycles = missedCycles;
    }

    public double getCycleJitter() {
        return cycleJitter;
    }

    public void setCycleJitter(double cycleJitter) {
        this.cycleJitter = cycleJitter;
    }

    public long getAlarmCount() {
        return alarmCount;
    }

    public void setAlarmCount(long alarmCount) {
        this.alarmCount = alarmCount;
    }

    public long getDiagnosisCount() {
        return diagnosisCount;
    }

    public void setDiagnosisCount(long diagnosisCount) {
        this.diagnosisCount = diagnosisCount;
    }

    public Map<Integer, ModuleDiagnostic> getModuleStates() {
        return moduleStates;
    }

    public void setModuleStates(Map<Integer, ModuleDiagnostic> moduleStates) {
        this.moduleStates = moduleStates;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public double getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(double memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public double getNetworkLoad() {
        return networkLoad;
    }

    public void setNetworkLoad(double networkLoad) {
        this.networkLoad = networkLoad;
    }

    public int getConnectionCount() {
        return connectionCount;
    }

    public void setConnectionCount(int connectionCount) {
        this.connectionCount = connectionCount;
    }

    public Map<String, Object> getAdditionalData() {
        return additionalData;
    }

    public void setAdditionalData(Map<String, Object> additionalData) {
        this.additionalData = additionalData;
    }

    // Méthodes utilitaires
    public void addModuleDiagnostic(ModuleDiagnostic module) {
        int key = module.getSlotNumber() * 1000 + module.getSubslotNumber();
        moduleStates.put(key, module);
    }

    public ModuleDiagnostic getModuleDiagnostic(int slotNumber, int subslotNumber) {
        int key = slotNumber * 1000 + subslotNumber;
        return moduleStates.get(key);
    }

    public void setAdditionalData(String key, Object value) {
        additionalData.put(key, value);
    }

    public Object getAdditionalData(String key) {
        return additionalData.get(key);
    }

    public double calculateOverallHealth() {
        double health = 100.0;

        // Pénalités selon les métriques
        if (packetLossPercent > 1.0) {
            health -= 20.0;
        }
        if (latencyMs > 10.0) {
            health -= 15.0;
        }
        if (errorsCount > 0) {
            health -= 10.0;
        }
        if (missedCycles > cyclicCyclesCount * 0.01) {
            health -= 25.0; // Plus de 1% de cycles ratés
        }
        if (alarmCount > 0) {
            health -= 30.0;
        }

        // État des modules
        long problematicModules = moduleStates.values().stream()
                .mapToLong(module -> module.getStatus() == DiagnosticStatus.ERROR
                || module.getStatus() == DiagnosticStatus.CRITICAL ? 1 : 0)
                .sum();

        if (problematicModules > 0) {
            health -= (problematicModules * 10.0);
        }

        return Math.max(0.0, Math.min(100.0, health));
    }

    public DiagnosticStatus calculateOverallStatus() {
        double health = calculateOverallHealth();

        if (health >= 90.0) {
            return DiagnosticStatus.GOOD;
        }
        if (health >= 70.0) {
            return DiagnosticStatus.WARNING;
        }
        if (health >= 40.0) {
            return DiagnosticStatus.ERROR;
        }
        return DiagnosticStatus.CRITICAL;
    }

    @Override
    public String toString() {
        return String.format("DiagnosticData{device='%s', status=%s, health=%.1f%%, cycles=%d, alarms=%d}",
                deviceName, status, calculateOverallHealth(), cyclicCyclesCount, alarmCount);
    }
}
