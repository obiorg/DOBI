package org.dobi.entities;

import jakarta.persistence.*;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Table(name = "tags")
public class Tag extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "deleted")
    private Boolean deleted;
    @Column(name = "created")
    @Temporal(TemporalType.TIMESTAMP)
    private Date created;
    @Column(name = "changed")
    @Temporal(TemporalType.TIMESTAMP)
    private Date changed;
    @Basic(optional = false)
    @Column(name = "name")
    private String name;
    @Column(name = "active")
    private Boolean active;
    @Column(name = "delta")
    private Boolean delta;
    // @Max(value=?)  @Min(value=?)//if you know range of your decimal fields consider using these annotations to enforce field validation
    @Column(name = "deltaFloat")
    private Double deltaFloat;
    @Column(name = "deltaInt")
    private Integer deltaInt;
    @Column(name = "deltaBool")
    private Integer deltaBool;
    @Column(name = "deltaDateTime")
    private BigInteger deltaDateTime;
    @Column(name = "vFloat")
    private Double vFloat;
    @Column(name = "vInt")
    private Integer vInt;
    @Column(name = "vBool")
    private Boolean vBool;
    @Column(name = "vStr")
    private String vStr;
    @Column(name = "vDateTime")
    @Temporal(TemporalType.TIMESTAMP)
    private Date vDateTime;
    @Column(name = "vStamp")
    @Temporal(TemporalType.TIMESTAMP)
    private Date vStamp;
    @Column(name = "vDefault")
    private Boolean vDefault;
    @Column(name = "vFloatDefault")
    private Double vFloatDefault;
    @Column(name = "vIntDefault")
    private Integer vIntDefault;
    @Column(name = "vBoolDefault")
    private Boolean vBoolDefault;
    @Column(name = "vStrDefault")
    private String vStrDefault;
    @Column(name = "vDateTimeDefault")
    @Temporal(TemporalType.TIMESTAMP)
    private Date vDateTimeDefault;
    @Column(name = "vStampDefault")
    @Temporal(TemporalType.TIMESTAMP)
    private Date vStampDefault;
    @Column(name = "counter")
    private Boolean counter;
    @Column(name = "counterType")
    private Integer counterType;
    @Column(name = "mesure")
    private Boolean mesure;
    @Column(name = "mesureMin")
    private Double mesureMin;
    @Column(name = "mesureMax")
    private Double mesureMax;
    @Column(name = "mqtt_topic")
    private String mqttTopic;
    @Column(name = "webhook")
    private String webhook;
    @Column(name = "laboratory")
    private Boolean laboratory;
    @Column(name = "formula")
    private Boolean formula;
    @Column(name = "formCalculus")
    private String formCalculus;
    @Column(name = "formProcessing")
    private Integer formProcessing;
    @Column(name = "error")
    private Boolean error;
    @Column(name = "errorMsg")
    private String errorMsg;
    @Column(name = "errorStamp")
    @Temporal(TemporalType.TIMESTAMP)
    private Date errorStamp;
    @Column(name = "alarmEnable")
    private Boolean alarmEnable;
    @Column(name = "persistenceEnable")
    private Boolean persistenceEnable;
    @Column(name = "persOffsetEnable")
    private Boolean persOffsetEnable;
    @Column(name = "persOffsetFloat")
    private Double persOffsetFloat;
    @Column(name = "persOffsetInt")
    private Integer persOffsetInt;
    @Column(name = "persOffsetBool")
    private Boolean persOffsetBool;
    @Column(name = "persOffsetDateTime")
    @Temporal(TemporalType.TIMESTAMP)
    private Date persOffsetDateTime;
    @Column(name = "comment")
    private String comment;

//    @Column(nullable = false)
//    private String name;
    @Column(name = "db")
    private Integer dbNumber;
    @Column(name = "byte")
    private Integer byteAddress;
    @Column(name = "bit")
    private Integer bitAddress;
//    private boolean active;
//    private Float vFloat;
//    private Integer vInt;
//    private Boolean vBool;
//    private String vStr;
//    private LocalDateTime vDateTime;
//    private LocalDateTime vStamp;
    
    // Dans Tag.java, ajouter :
    @Column(name = "cycle")
    private Integer cycle; // Fréquence en secondes

//    @Column(name = "persistence_enable")
//    private Boolean persistenceEnable = false;


    @Column(name = "opc_namespace_index")
    private Integer opcNamespaceIndex;
    @Column(name = "opc_identifier")
    private String opcIdentifier;
    @Column(name = "opc_identifier_type")
    private String opcIdentifierType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine", nullable = false)
    private Machine machine;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type", nullable = false)
    private TagType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "memory", nullable = false)
    private TagMemory memory;

    // Getters & Setters
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
//    public Float getvFloat() { return vFloat; }
//    public void setvFloat(Float vFloat) { this.vFloat = vFloat; }
    public Integer getvInt() { return vInt; }
    public void setvInt(Integer vInt) { this.vInt = vInt; }
    public Boolean getvBool() { return vBool; }
    public void setvBool(Boolean vBool) { this.vBool = vBool; }
    public String getvStr() { return vStr; }
    public void setvStr(String vStr) { this.vStr = vStr; }
//    public LocalDateTime getvDateTime() { return vDateTime; }
//    public void setvDateTime(LocalDateTime vDateTime) { this.vDateTime = vDateTime; }
//    public LocalDateTime getvStamp() { return vStamp; }
//    public void setvStamp(LocalDateTime vStamp) { this.vStamp = vStamp; }
    public Integer getOpcNamespaceIndex() { return opcNamespaceIndex; }
    public void setOpcNamespaceIndex(Integer opcNamespaceIndex) { this.opcNamespaceIndex = opcNamespaceIndex; }
    public String getOpcIdentifier() { return opcIdentifier; }
    public void setOpcIdentifier(String opcIdentifier) { this.opcIdentifier = opcIdentifier; }
    public String getOpcIdentifierType() { return opcIdentifierType; }
    public void setOpcIdentifierType(String opcIdentifierType) { this.opcIdentifierType = opcIdentifierType; }
    public Machine getMachine() { return machine; }
    public void setMachine(Machine machine) { this.machine = machine; }
    public TagType getType() { return type; }
    public void setType(TagType type) { this.type = type; }
    public TagMemory getMemory() { return memory; }
    public void setMemory(TagMemory memory) { this.memory = memory; }

    public Integer getCycle() {     return cycle;   }
    public void setCycle(Integer cycle) {     this.cycle = cycle;    }
    public Boolean getPersistenceEnable() {    return persistenceEnable;    }
    public void setPersistenceEnable(Boolean persistenceEnable) {    this.persistenceEnable = persistenceEnable;}

    public Tag() {
    }

    public Tag(Integer id) {
        this.id = id;
    }

    public Tag(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

//    public Integer getId() {
//        return id;
//    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

//    public Date getCreated() {
//        return created;
//    }

    public void setCreated(Date created) {
        this.created = created;
    }
//
//    public Date getChanged() {
//        return changed;
//    }

    public void setChanged(Date changed) {
        this.changed = changed;
    }

//    public String getName() {
//        return name;
//    }
//
//    public void setName(String name) {
//        this.name = name;
//    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Boolean getDelta() {
        return delta;
    }

    public void setDelta(Boolean delta) {
        this.delta = delta;
    }

    public Double getDeltaFloat() {
        return deltaFloat;
    }

    public void setDeltaFloat(Double deltaFloat) {
        this.deltaFloat = deltaFloat;
    }

    public Integer getDeltaInt() {
        return deltaInt;
    }

    public void setDeltaInt(Integer deltaInt) {
        this.deltaInt = deltaInt;
    }

    public Integer getDeltaBool() {
        return deltaBool;
    }

    public void setDeltaBool(Integer deltaBool) {
        this.deltaBool = deltaBool;
    }

    public BigInteger getDeltaDateTime() {
        return deltaDateTime;
    }

    public void setDeltaDateTime(BigInteger deltaDateTime) {
        this.deltaDateTime = deltaDateTime;
    }

    public Double getVFloat() {
        return vFloat;
    }

    public void setVFloat(Double vFloat) {
        this.vFloat = vFloat;
    }

    public Integer getVInt() {
        return vInt;
    }

    public void setVInt(Integer vInt) {
        this.vInt = vInt;
    }

    public Boolean getVBool() {
        return vBool;
    }

    public void setVBool(Boolean vBool) {
        this.vBool = vBool;
    }

    public String getVStr() {
        return vStr;
    }

    public void setVStr(String vStr) {
        this.vStr = vStr;
    }

    public Date getVDateTime() {
        return vDateTime;
    }

    public void setVDateTime(Date vDateTime) {
        this.vDateTime = vDateTime;
    }

    public Date getVStamp() {
        return vStamp;
    }

    public void setVStamp(Date vStamp) {
        this.vStamp = vStamp;
    }

    public Boolean getVDefault() {
        return vDefault;
    }

    public void setVDefault(Boolean vDefault) {
        this.vDefault = vDefault;
    }

    public Double getVFloatDefault() {
        return vFloatDefault;
    }

    public void setVFloatDefault(Double vFloatDefault) {
        this.vFloatDefault = vFloatDefault;
    }

    public Integer getVIntDefault() {
        return vIntDefault;
    }

    public void setVIntDefault(Integer vIntDefault) {
        this.vIntDefault = vIntDefault;
    }

    public Boolean getVBoolDefault() {
        return vBoolDefault;
    }

    public void setVBoolDefault(Boolean vBoolDefault) {
        this.vBoolDefault = vBoolDefault;
    }

    public String getVStrDefault() {
        return vStrDefault;
    }

    public void setVStrDefault(String vStrDefault) {
        this.vStrDefault = vStrDefault;
    }

    public Date getVDateTimeDefault() {
        return vDateTimeDefault;
    }

    public void setVDateTimeDefault(Date vDateTimeDefault) {
        this.vDateTimeDefault = vDateTimeDefault;
    }

    public Date getVStampDefault() {
        return vStampDefault;
    }

    public void setVStampDefault(Date vStampDefault) {
        this.vStampDefault = vStampDefault;
    }

    public Boolean getCounter() {
        return counter;
    }

    public void setCounter(Boolean counter) {
        this.counter = counter;
    }

    public Integer getCounterType() {
        return counterType;
    }

    public void setCounterType(Integer counterType) {
        this.counterType = counterType;
    }

    public Boolean getMesure() {
        return mesure;
    }

    public void setMesure(Boolean mesure) {
        this.mesure = mesure;
    }

    public Double getMesureMin() {
        return mesureMin;
    }

    public void setMesureMin(Double mesureMin) {
        this.mesureMin = mesureMin;
    }

    public Double getMesureMax() {
        return mesureMax;
    }

    public void setMesureMax(Double mesureMax) {
        this.mesureMax = mesureMax;
    }

    public String getMqttTopic() {
        return mqttTopic;
    }

    public void setMqttTopic(String mqttTopic) {
        this.mqttTopic = mqttTopic;
    }

    public String getWebhook() {
        return webhook;
    }

    public void setWebhook(String webhook) {
        this.webhook = webhook;
    }

    public Boolean getLaboratory() {
        return laboratory;
    }

    public void setLaboratory(Boolean laboratory) {
        this.laboratory = laboratory;
    }

    public Boolean getFormula() {
        return formula;
    }

    public void setFormula(Boolean formula) {
        this.formula = formula;
    }

    public String getFormCalculus() {
        return formCalculus;
    }

    public void setFormCalculus(String formCalculus) {
        this.formCalculus = formCalculus;
    }

    public Integer getFormProcessing() {
        return formProcessing;
    }

    public void setFormProcessing(Integer formProcessing) {
        this.formProcessing = formProcessing;
    }

    public Boolean getError() {
        return error;
    }

    public void setError(Boolean error) {
        this.error = error;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public Date getErrorStamp() {
        return errorStamp;
    }

    public void setErrorStamp(Date errorStamp) {
        this.errorStamp = errorStamp;
    }

    public Boolean getAlarmEnable() {
        return alarmEnable;
    }

    public void setAlarmEnable(Boolean alarmEnable) {
        this.alarmEnable = alarmEnable;
    }

//    public Boolean getPersistenceEnable() {
//        return persistenceEnable;
//    }
//
//    public void setPersistenceEnable(Boolean persistenceEnable) {
//        this.persistenceEnable = persistenceEnable;
//    }

    public Boolean getPersOffsetEnable() {
        return persOffsetEnable;
    }

    public void setPersOffsetEnable(Boolean persOffsetEnable) {
        this.persOffsetEnable = persOffsetEnable;
    }

    public Double getPersOffsetFloat() {
        return persOffsetFloat;
    }

    public void setPersOffsetFloat(Double persOffsetFloat) {
        this.persOffsetFloat = persOffsetFloat;
    }

    public Integer getPersOffsetInt() {
        return persOffsetInt;
    }

    public void setPersOffsetInt(Integer persOffsetInt) {
        this.persOffsetInt = persOffsetInt;
    }

    public Boolean getPersOffsetBool() {
        return persOffsetBool;
    }

    public void setPersOffsetBool(Boolean persOffsetBool) {
        this.persOffsetBool = persOffsetBool;
    }

    public Date getPersOffsetDateTime() {
        return persOffsetDateTime;
    }

    public void setPersOffsetDateTime(Date persOffsetDateTime) {
        this.persOffsetDateTime = persOffsetDateTime;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof Tag)) {
            return false;
        }
        Tag other = (Tag) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.dobi.entities.Tag[ id=" + id + " ]";
    }
    
}
