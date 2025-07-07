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
@Table(name = "alarm_classes")
@NamedQueries({
    @NamedQuery(name = "AlarmClasses.findAll", query = "SELECT a FROM AlarmClasses a"),
    @NamedQuery(name = "AlarmClasses.findById", query = "SELECT a FROM AlarmClasses a WHERE a.id = :id"),
    @NamedQuery(name = "AlarmClasses.findByDeleted", query = "SELECT a FROM AlarmClasses a WHERE a.deleted = :deleted"),
    @NamedQuery(name = "AlarmClasses.findByCreated", query = "SELECT a FROM AlarmClasses a WHERE a.created = :created"),
    @NamedQuery(name = "AlarmClasses.findByChanged", query = "SELECT a FROM AlarmClasses a WHERE a.changed = :changed"),
    @NamedQuery(name = "AlarmClasses.findByClass1", query = "SELECT a FROM AlarmClasses a WHERE a.class1 = :class1"),
    @NamedQuery(name = "AlarmClasses.findByName", query = "SELECT a FROM AlarmClasses a WHERE a.name = :name"),
    @NamedQuery(name = "AlarmClasses.findByComment", query = "SELECT a FROM AlarmClasses a WHERE a.comment = :comment")})
public class AlarmClasses implements Serializable {

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
    @Column(name = "class")
    private String class1;
    @Column(name = "name")
    private String name;
    @Column(name = "comment")
    private String comment;
    @OneToMany(mappedBy = "class1")
    private Collection<Alarms> alarmsCollection;
    @JoinColumn(name = "render", referencedColumnName = "id")
    @ManyToOne
    private AlarmRender render;
    @JoinColumn(name = "company", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private Company company;

    public AlarmClasses() {
    }

    public AlarmClasses(Integer id) {
        this.id = id;
    }

    public AlarmClasses(Integer id, String class1) {
        this.id = id;
        this.class1 = class1;
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

    public String getClass1() {
        return class1;
    }

    public void setClass1(String class1) {
        this.class1 = class1;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Collection<Alarms> getAlarmsCollection() {
        return alarmsCollection;
    }

    public void setAlarmsCollection(Collection<Alarms> alarmsCollection) {
        this.alarmsCollection = alarmsCollection;
    }

    public AlarmRender getRender() {
        return render;
    }

    public void setRender(AlarmRender render) {
        this.render = render;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
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
        if (!(object instanceof AlarmClasses)) {
            return false;
        }
        AlarmClasses other = (AlarmClasses) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.dobi.entities.AlarmClasses[ id=" + id + " ]";
    }
    
}
