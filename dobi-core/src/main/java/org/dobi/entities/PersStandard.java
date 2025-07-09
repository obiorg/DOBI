package org.dobi.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "pers_standard")
public class PersStandard extends BaseEntity {

    @Column(nullable = false)
    private Long tag;

    @Column(nullable = false)
    private Integer company; // ID du tag

    private Float vFloat;
    private Integer vInt;
    private Boolean vBool;
    private String vStr;
    // Tous les champs de date sont maintenant en OffsetDateTime
    private OffsetDateTime vDateTime;
    
    @Column(nullable = false)
    private OffsetDateTime vStamp;

    // AJOUT : Les champs manquants sont ajoutés pour que JPA les gère
    private OffsetDateTime stampStart;
    private OffsetDateTime stampEnd;
    
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
    public OffsetDateTime getvDateTime() { return vDateTime; }
    public void setvDateTime(OffsetDateTime vDateTime) { this.vDateTime = vDateTime; }
    public OffsetDateTime getvStamp() { return vStamp; }
    public void setvStamp(OffsetDateTime vStamp) { this.vStamp = vStamp; }
    public OffsetDateTime getStampStart() { return stampStart; }
    public void setStampStart(OffsetDateTime stampStart) { this.stampStart = stampStart; }
    public OffsetDateTime getStampEnd() { return stampEnd; }
    public void setStampEnd(OffsetDateTime stampEnd) { this.stampEnd = stampEnd; }

    public Integer getCompany() { return company; }
    public void setCompany(Integer company) { this.company = company; }
}

