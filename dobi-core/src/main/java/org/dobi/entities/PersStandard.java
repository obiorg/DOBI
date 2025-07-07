package org.dobi.entities;

import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;

@Entity
@Table(name = "pers_standard")
public class PersStandard extends BaseEntity {

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
    // @Max(value=?)  @Min(value=?)//if you know range of your decimal fields consider using these annotations to enforce field validation
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
    @Column(name = "stampStart")
    @Temporal(TemporalType.TIMESTAMP)
    private Date stampStart;
    @Column(name = "stampEnd")
    @Temporal(TemporalType.TIMESTAMP)
    private Date stampEnd;
    @Column(name = "tbf")
    private Float tbf;
    @Column(name = "ttr")
    private Float ttr;
    @Column(name = "error")
    private Boolean error;
    @Column(name = "errorMsg")
    private String errorMsg;
    @Column(name = "created_minute")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdMinute;
    @JoinColumn(name = "company", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private Company company;
    @JoinColumn(name = "tag", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private Tag tag;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "persStandard")
    private Collection<PersStandardLimits> persStandardLimitsCollection;

    // Getters and Setters
    public Integer getvInt() {
        return vInt;
    }

    public void setvInt(Integer vInt) {
        this.vInt = vInt;
    }

    public Boolean getvBool() {
        return vBool;
    }

    public void setvBool(Boolean vBool) {
        this.vBool = vBool;
    }

    public String getvStr() {
        return vStr;
    }

    public void setvStr(String vStr) {
        this.vStr = vStr;
    }

    public PersStandard(Integer id) {
        this.id = id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public void setChanged(Date changed) {
        this.changed = changed;
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

    public Date getStampStart() {
        return stampStart;
    }

    public void setStampStart(Date stampStart) {
        this.stampStart = stampStart;
    }

    public Date getStampEnd() {
        return stampEnd;
    }

    public void setStampEnd(Date stampEnd) {
        this.stampEnd = stampEnd;
    }

    public Float getTbf() {
        return tbf;
    }

    public void setTbf(Float tbf) {
        this.tbf = tbf;
    }

    public Float getTtr() {
        return ttr;
    }

    public void setTtr(Float ttr) {
        this.ttr = ttr;
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

    public Date getCreatedMinute() {
        return createdMinute;
    }

    public void setCreatedMinute(Date createdMinute) {
        this.createdMinute = createdMinute;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public void setTag(Tag tag) {
        this.tag = tag;
    }

    public Collection<PersStandardLimits> getPersStandardLimitsCollection() {
        return persStandardLimitsCollection;
    }

    public void setPersStandardLimitsCollection(Collection<PersStandardLimits> persStandardLimitsCollection) {
        this.persStandardLimitsCollection = persStandardLimitsCollection;
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
        if (!(object instanceof PersStandard)) {
            return false;
        }
        PersStandard other = (PersStandard) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.dobi.entities.PersStandard[ id=" + id + " ]";
    }
}
