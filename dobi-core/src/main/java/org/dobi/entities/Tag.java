package org.dobi.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.LocalDateTime;

@Entity
@Table(name = "tags")
public class Tag extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "db")
    private Integer dbNumber;

    @Column(name = "byte")
    private Integer byteAddress;
    
    @Column(name = "bit")
    private Integer bitAddress;

    private boolean active;

    // --- Champs pour la valeur "Live" ---
    private Float vFloat;
    private Integer vInt;
    private Boolean vBool;
    private String vStr;
    private LocalDateTime vDateTime;
    private LocalDateTime vStamp;
    // --- Fin des champs de valeur "Live" ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine", nullable = false)
    private Machine machine;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type", nullable = false)
    private TagType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "memory", nullable = false)
    private TagMemory memory;

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getDbNumber() { return dbNumber; }
    public void setDbNumber(Integer dbNumber) { this.dbNumber = dbNumber; }
    public Integer getByteAddress() { return byteAddress; }
    public void setByteAddress(Integer byteAddress) { this.byteAddress = byteAddress; }
    public Integer getBitAddress() { return bitAddress; }
    public void setBitAddress(Integer bitAddress) { this.bitAddress = bitAddress; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Machine getMachine() { return machine; }
    public void setMachine(Machine machine) { this.machine = machine; }
    public TagType getType() { return type; }
    public void setType(TagType type) { this.type = type; }
    public TagMemory getMemory() { return memory; }
    public void setMemory(TagMemory memory) { this.memory = memory; }

    // --- Getters and Setters pour les valeurs "Live" ---
    public Float getvFloat() { return vFloat; }
    public void setvFloat(Float vFloat) { this.vFloat = vFloat; }
    public Integer getvInt() { return vInt; }
    public void setvInt(Integer vInt) { this.vInt = vInt; }
    public Boolean getvBool() { return vBool; }
    public void setvBool(Boolean vBool) { this.vBool = vBool; }
    public String getvStr() { return vStr; }
    public void setvStr(String vStr) { this.vStr = vStr; }
    public LocalDateTime getvDateTime() { return vDateTime; }
    public void setvDateTime(LocalDateTime vDateTime) { this.vDateTime = vDateTime; }
    public LocalDateTime getvStamp() { return vStamp; }
    public void setvStamp(LocalDateTime vStamp) { this.vStamp = vStamp; }
}
