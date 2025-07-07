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
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.io.Serializable;
import java.util.Date;

/**
 *
 * @author r.hendrick
 */
@Entity
@Table(name = "pers_standard_limits")
@NamedQueries({
    @NamedQuery(name = "PersStandardLimits.findAll", query = "SELECT p FROM PersStandardLimits p"),
    @NamedQuery(name = "PersStandardLimits.findById", query = "SELECT p FROM PersStandardLimits p WHERE p.id = :id"),
    @NamedQuery(name = "PersStandardLimits.findByDeleted", query = "SELECT p FROM PersStandardLimits p WHERE p.deleted = :deleted"),
    @NamedQuery(name = "PersStandardLimits.findByCreated", query = "SELECT p FROM PersStandardLimits p WHERE p.created = :created"),
    @NamedQuery(name = "PersStandardLimits.findByChanged", query = "SELECT p FROM PersStandardLimits p WHERE p.changed = :changed"),
    @NamedQuery(name = "PersStandardLimits.findByName", query = "SELECT p FROM PersStandardLimits p WHERE p.name = :name"),
    @NamedQuery(name = "PersStandardLimits.findByValue", query = "SELECT p FROM PersStandardLimits p WHERE p.value = :value"),
    @NamedQuery(name = "PersStandardLimits.findByDelay", query = "SELECT p FROM PersStandardLimits p WHERE p.delay = :delay"),
    @NamedQuery(name = "PersStandardLimits.findByHysteresis", query = "SELECT p FROM PersStandardLimits p WHERE p.hysteresis = :hysteresis"),
    @NamedQuery(name = "PersStandardLimits.findBySort", query = "SELECT p FROM PersStandardLimits p WHERE p.sort = :sort"),
    @NamedQuery(name = "PersStandardLimits.findByHit", query = "SELECT p FROM PersStandardLimits p WHERE p.hit = :hit"),
    @NamedQuery(name = "PersStandardLimits.findByReached", query = "SELECT p FROM PersStandardLimits p WHERE p.reached = :reached")})
public class PersStandardLimits implements Serializable {

    @JoinColumn(name = "comparator", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private MeasComparator comparator;

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
    @Column(name = "name")
    private String name;
    // @Max(value=?)  @Min(value=?)//if you know range of your decimal fields consider using these annotations to enforce field validation
    @Column(name = "value")
    private Double value;
    @Column(name = "delay")
    private Integer delay;
    @Column(name = "hysteresis")
    private Double hysteresis;
    @Column(name = "sort")
    private Integer sort;
    @Column(name = "hit")
    private Boolean hit;
    @Column(name = "reached")
    private Boolean reached;
    @JoinColumn(name = "company", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private Company company;
    @JoinColumn(name = "pers_standard", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private PersStandard persStandard;
    @JoinColumn(name = "tag", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private Tag tag;

    public PersStandardLimits() {
    }

    public PersStandardLimits(Integer id) {
        this.id = id;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public Integer getDelay() {
        return delay;
    }

    public void setDelay(Integer delay) {
        this.delay = delay;
    }

    public Double getHysteresis() {
        return hysteresis;
    }

    public void setHysteresis(Double hysteresis) {
        this.hysteresis = hysteresis;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public Boolean getHit() {
        return hit;
    }

    public void setHit(Boolean hit) {
        this.hit = hit;
    }

    public Boolean getReached() {
        return reached;
    }

    public void setReached(Boolean reached) {
        this.reached = reached;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public PersStandard getPersStandard() {
        return persStandard;
    }

    public void setPersStandard(PersStandard persStandard) {
        this.persStandard = persStandard;
    }

    public Tag getTag() {
        return tag;
    }

    public void setTag(Tag tag) {
        this.tag = tag;
    }

    public MeasComparator getComparator() {
        return comparator;
    }

    public void setComparator(MeasComparator comparator) {
        this.comparator = comparator;
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
        if (!(object instanceof PersStandardLimits)) {
            return false;
        }
        PersStandardLimits other = (PersStandardLimits) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.dobi.entities.PersStandardLimits[ id=" + id + " ]";
    }

}
