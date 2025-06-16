package org.dobi.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.LocalDateTime;

@Entity
@Table(name = "pers_standard")
public class PersStandard extends BaseEntity {

    @Column(nullable = false)
    private Long tag; // ID du tag

    private Float vFloat;
    private Integer vInt;
    private Boolean vBool;
    private String vStr;
    private LocalDateTime vDateTime;
    
    @Column(nullable = false)
    private LocalDateTime vStamp;
    
    // Getters and Setters
    public Long getTag() { return tag; }
    public void setTag(Long tag) { this.tag = tag; }
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
