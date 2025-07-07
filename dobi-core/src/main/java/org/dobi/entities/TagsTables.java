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
@Table(name = "tags_tables")
@NamedQueries({
    @NamedQuery(name = "TagsTables.findAll", query = "SELECT t FROM TagsTables t"),
    @NamedQuery(name = "TagsTables.findById", query = "SELECT t FROM TagsTables t WHERE t.id = :id"),
    @NamedQuery(name = "TagsTables.findByDeleted", query = "SELECT t FROM TagsTables t WHERE t.deleted = :deleted"),
    @NamedQuery(name = "TagsTables.findByCreated", query = "SELECT t FROM TagsTables t WHERE t.created = :created"),
    @NamedQuery(name = "TagsTables.findByChanged", query = "SELECT t FROM TagsTables t WHERE t.changed = :changed"),
    @NamedQuery(name = "TagsTables.findByTable", query = "SELECT t FROM TagsTables t WHERE t.table = :table"),
    @NamedQuery(name = "TagsTables.findByDesignation", query = "SELECT t FROM TagsTables t WHERE t.designation = :designation"),
    @NamedQuery(name = "TagsTables.findByComment", query = "SELECT t FROM TagsTables t WHERE t.comment = :comment")})
public class TagsTables implements Serializable {

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
    @Column(name = "table")
    private String table;
    @Column(name = "designation")
    private String designation;
    @Column(name = "comment")
    private String comment;
    @JoinColumn(name = "company", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private Company company;
    @OneToMany(mappedBy = "table")
    private Collection<Tag> tagCollection;

    public TagsTables() {
    }

    public TagsTables(Integer id) {
        this.id = id;
    }

    public TagsTables(Integer id, String table) {
        this.id = id;
        this.table = table;
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

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
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
        if (!(object instanceof TagsTables)) {
            return false;
        }
        TagsTables other = (TagsTables) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.dobi.entities.TagsTables[ id=" + id + " ]";
    }
    
}
