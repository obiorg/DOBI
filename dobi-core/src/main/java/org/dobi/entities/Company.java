package org.dobi.entities;

import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.util.Collection;
import java.util.Date;

@Entity
@Table(name = "companies")
public class Company extends BaseEntity {

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
    @Column(name = "designation")
    private String designation;
    @Column(name = "builded")
    private Integer builded;
    @Column(name = "main")
    private Boolean main;
    @Column(name = "activated")
    private Boolean activated;
    @Column(name = "logoPath")
    private String logoPath;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "company")
    private Collection<TagsTables> tagsTablesCollection;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "company")
    private Collection<AlarmGroups> alarmGroupsCollection;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "company")
    private Collection<PersStandard> persStandardCollection;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "company")
    private Collection<AlarmRender> alarmRenderCollection;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "company")
    private Collection<Alarms> alarmsCollection;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "company")
    private Collection<PersStandardLimits> persStandardLimitsCollection;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "company")
    private Collection<Persistence> persistenceCollection;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "company")
    private Collection<Machine> machineCollection;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "company")
    private Collection<AlarmClasses> alarmClassesCollection;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "company")
    private Collection<Tag> tagCollection;

    @Column(name = "company", nullable = false, unique = true)
    private String companyCode;

    // Getters and Setters
    public String getCompanyCode() {
        return companyCode;
    }

    public void setCompanyCode(String companyCode) {
        this.companyCode = companyCode;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public Company() {
    }

    public Company(Integer id) {
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

    public Integer getBuilded() {
        return builded;
    }

    public void setBuilded(Integer builded) {
        this.builded = builded;
    }

    public Boolean getMain() {
        return main;
    }

    public void setMain(Boolean main) {
        this.main = main;
    }

    public Boolean getActivated() {
        return activated;
    }

    public void setActivated(Boolean activated) {
        this.activated = activated;
    }

    public String getLogoPath() {
        return logoPath;
    }

    public void setLogoPath(String logoPath) {
        this.logoPath = logoPath;
    }

    public Collection<TagsTables> getTagsTablesCollection() {
        return tagsTablesCollection;
    }

    public void setTagsTablesCollection(Collection<TagsTables> tagsTablesCollection) {
        this.tagsTablesCollection = tagsTablesCollection;
    }

    public Collection<AlarmGroups> getAlarmGroupsCollection() {
        return alarmGroupsCollection;
    }

    public void setAlarmGroupsCollection(Collection<AlarmGroups> alarmGroupsCollection) {
        this.alarmGroupsCollection = alarmGroupsCollection;
    }

    public Collection<PersStandard> getPersStandardCollection() {
        return persStandardCollection;
    }

    public void setPersStandardCollection(Collection<PersStandard> persStandardCollection) {
        this.persStandardCollection = persStandardCollection;
    }

    public Collection<AlarmRender> getAlarmRenderCollection() {
        return alarmRenderCollection;
    }

    public void setAlarmRenderCollection(Collection<AlarmRender> alarmRenderCollection) {
        this.alarmRenderCollection = alarmRenderCollection;
    }

    public Collection<Alarms> getAlarmsCollection() {
        return alarmsCollection;
    }

    public void setAlarmsCollection(Collection<Alarms> alarmsCollection) {
        this.alarmsCollection = alarmsCollection;
    }

    public Collection<PersStandardLimits> getPersStandardLimitsCollection() {
        return persStandardLimitsCollection;
    }

    public void setPersStandardLimitsCollection(Collection<PersStandardLimits> persStandardLimitsCollection) {
        this.persStandardLimitsCollection = persStandardLimitsCollection;
    }

    public Collection<Persistence> getPersistenceCollection() {
        return persistenceCollection;
    }

    public void setPersistenceCollection(Collection<Persistence> persistenceCollection) {
        this.persistenceCollection = persistenceCollection;
    }

    public Collection<Machine> getMachineCollection() {
        return machineCollection;
    }

    public void setMachineCollection(Collection<Machine> machineCollection) {
        this.machineCollection = machineCollection;
    }

    public Collection<AlarmClasses> getAlarmClassesCollection() {
        return alarmClassesCollection;
    }

    public void setAlarmClassesCollection(Collection<AlarmClasses> alarmClassesCollection) {
        this.alarmClassesCollection = alarmClassesCollection;
    }

    public Collection<Tag> getTagCollection() {
        return tagCollection;
    }

    public void setTagCollection(Collection<Tag> tagCollection) {
        this.tagCollection = tagCollection;
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
        if (!(object instanceof Company)) {
            return false;
        }
        Company other = (Company) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.dobi.entities.Company[ id=" + id + " ]";
    }
}
