/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.dobi.entities;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.io.Serializable;
import java.util.Collection;
import java.util.Date;

/**
 *
 * @author r.hendrick
 */
@Entity
@Table(name = "alarms")
@NamedQueries({
    @NamedQuery(name = "Alarms.findAll", query = "SELECT a FROM Alarms a"),
    @NamedQuery(name = "Alarms.findById", query = "SELECT a FROM Alarms a WHERE a.id = :id"),
    @NamedQuery(name = "Alarms.findByDeleted", query = "SELECT a FROM Alarms a WHERE a.deleted = :deleted"),
    @NamedQuery(name = "Alarms.findByCreated", query = "SELECT a FROM Alarms a WHERE a.created = :created"),
    @NamedQuery(name = "Alarms.findByChanged", query = "SELECT a FROM Alarms a WHERE a.changed = :changed"),
    @NamedQuery(name = "Alarms.findByAlarm", query = "SELECT a FROM Alarms a WHERE a.alarm = :alarm"),
    @NamedQuery(name = "Alarms.findByName", query = "SELECT a FROM Alarms a WHERE a.name = :name"),
    @NamedQuery(name = "Alarms.findByDescirption", query = "SELECT a FROM Alarms a WHERE a.descirption = :descirption"),
    @NamedQuery(name = "Alarms.findByLanguage", query = "SELECT a FROM Alarms a WHERE a.language = :language"),
    @NamedQuery(name = "Alarms.findByComment", query = "SELECT a FROM Alarms a WHERE a.comment = :comment")})
public class Alarms implements Serializable {

    private static final long serialVersionUID = 1L;
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
    @Column(name = "alarm")
    private String alarm;
    @Column(name = "name")
    private String name;
    @Column(name = "descirption")
    private String descirption;
    @Column(name = "language")
    private Integer language;
    @Column(name = "comment")
    private String comment;
    @JoinColumn(name = "class", referencedColumnName = "id")
    @ManyToOne
    private AlarmClasses class1;
    @JoinColumn(name = "group", referencedColumnName = "id")
    @ManyToOne
    private AlarmGroups group1;
    @JoinColumn(name = "company", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private Company company;
    @OneToMany(mappedBy = "alarm")
    private Collection<Tag> tagCollection;

    public Alarms() {
    }

    public Alarms(Integer id) {
        this.id = id;
    }

    public Alarms(Integer id, String alarm) {
        this.id = id;
        this.alarm = alarm;
    }

    public Integer getId() {
        return id;
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

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getChanged() {
        return changed;
    }

    public void setChanged(Date changed) {
        this.changed = changed;
    }

    public String getAlarm() {
        return alarm;
    }

    public void setAlarm(String alarm) {
        this.alarm = alarm;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescirption() {
        return descirption;
    }

    public void setDescirption(String descirption) {
        this.descirption = descirption;
    }

    public Integer getLanguage() {
        return language;
    }

    public void setLanguage(Integer language) {
        this.language = language;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public AlarmClasses getClass1() {
        return class1;
    }

    public void setClass1(AlarmClasses class1) {
        this.class1 = class1;
    }

    public AlarmGroups getGroup1() {
        return group1;
    }

    public void setGroup1(AlarmGroups group1) {
        this.group1 = group1;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
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
        if (!(object instanceof Alarms)) {
            return false;
        }
        Alarms other = (Alarms) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.dobi.entities.Alarms[ id=" + id + " ]";
    }
    
}
