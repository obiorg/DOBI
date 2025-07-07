package org.dobi.entities;

import jakarta.persistence.*;
import java.util.Collection;
import java.util.Date;

@Entity
@Table(name = "meas_comparators")
public class MeasComparator extends BaseEntity {

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
    @Column(name = "symbol")
    private String symbol;
    @Column(name = "name")
    private String name;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "comparator")
    private Collection<PersStandardLimits> persStandardLimitsCollection;

    // Getters & Setters...
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public MeasComparator() {
    }

    public MeasComparator(Integer id) {
        this.id = id;
    }

    public MeasComparator(Integer id, String symbol) {
        this.id = id;
        this.symbol = symbol;
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
        if (!(object instanceof MeasComparator)) {
            return false;
        }
        MeasComparator other = (MeasComparator) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.dobi.entities.MeasComparator[ id=" + id + " ]";
    }
}