package org.dobi.profinet.diagnostic;

import org.dobi.profinet.device.ProfinetDevice;
import java.time.LocalDateTime;

public class ProfinetAlarm {
    private String alarmId;
    private ProfinetDevice device;
    private AlarmHandler.AlarmType type;
    private AlarmHandler.AlarmPriority priority;
    private String message;
    private LocalDateTime timestamp;
    private boolean active;
    private boolean acknowledged;
    private LocalDateTime acknowledgeTime;
    private LocalDateTime resolveTime;
    private int slotNumber;
    private int subslotNumber;
    private int moduleIdentNumber;
    private byte[] rawData;
    
    // Constructeurs
    public ProfinetAlarm() {
        this.timestamp = LocalDateTime.now();
        this.active = true;
        this.acknowledged = false;
    }
    
    // Getters et Setters
    public String getAlarmId() { return alarmId; }
    public void setAlarmId(String alarmId) { this.alarmId = alarmId; }
    
    public ProfinetDevice getDevice() { return device; }
    public void setDevice(ProfinetDevice device) { this.device = device; }
    
    public AlarmHandler.AlarmType getType() { return type; }
    public void setType(AlarmHandler.AlarmType type) { this.type = type; }
    
    public AlarmHandler.AlarmPriority getPriority() { return priority; }
    public void setPriority(AlarmHandler.AlarmPriority priority) { this.priority = priority; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    
    public boolean isAcknowledged() { return acknowledged; }
    public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }
    
    public LocalDateTime getAcknowledgeTime() { return acknowledgeTime; }
    public void setAcknowledgeTime(LocalDateTime acknowledgeTime) { this.acknowledgeTime = acknowledgeTime; }
    
    public LocalDateTime getResolveTime() { return resolveTime; }
    public void setResolveTime(LocalDateTime resolveTime) { this.resolveTime = resolveTime; }
    
    public int getSlotNumber() { return slotNumber; }
    public void setSlotNumber(int slotNumber) { this.slotNumber = slotNumber; }
    
    public int getSubslotNumber() { return subslotNumber; }
    public void setSubslotNumber(int subslotNumber) { this.subslotNumber = subslotNumber; }
    
    public int getModuleIdentNumber() { return moduleIdentNumber; }
    public void setModuleIdentNumber(int moduleIdentNumber) { this.moduleIdentNumber = moduleIdentNumber; }
    
    public byte[] getRawData() { return rawData; }
    public void setRawData(byte[] rawData) { this.rawData = rawData; }
    
    @Override
    public String toString() {
        return String.format("ProfinetAlarm{id='%s', device='%s', type=%s, priority=%s, active=%s, ack=%s}", 
            alarmId, device != null ? device.getStationName() : "null", type, priority, active, acknowledged);
    }
}