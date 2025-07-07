package org.dobi.entities;

import jakarta.persistence.*;
import java.util.Date;
// import java.time.LocalDateTime; // Plus nécessaire si hérité
// import java.util.Date; // Plus nécessaire si hérité

/**
 *
 * @author r.hendrick
 */
@Entity
@Table(name = "persistence")
public class Persistence extends BaseEntity {

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
// Hérite de BaseEntity

// Les champs 'id', 'deleted', 'created', 'changed' sont maintenant hérités de BaseEntity.
// Il ne faut PAS les redéfinir ici, sinon cela crée un conflit de type.
// Les lignes commentées ci-dessous sont les lignes à SUPPRIMER ou commenter.
//    @Column(name = "deleted")
//    private Boolean deleted;
//    @Column(name = "created")
//    @Temporal(TemporalType.TIMESTAMP)
//    private Date created;
//    @Column(name = "changed")
//    @Temporal(TemporalType.TIMESTAMP)
//    private Date changed;
    @Column(name = "comment")
    private String comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company", nullable = false)
    private Company company;

    @ManyToOne
    @JoinColumn(name = "tag") // Correction déjà appliquée : "tag" au lieu de "tag_id"
    private Tag tag;

    @Column(name = "method")
    private Integer method;

    @Column(name = "activate")
    private Boolean activate = false;

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public Tag getTag() {
        return tag;
    }

    public void setTag(Tag tag) {
        this.tag = tag;
    }

    public Integer getMethod() {
        return method;
    }

    public void setMethod(Integer method) {
        this.method = method;
    }

    public Boolean getActivate() {
        return activate;
    }

    public void setActivate(Boolean activate) {
        this.activate = activate;
    }

    public Persistence() {
    }

    // Les getters et setters pour 'id', 'deleted', 'created', 'changed'
    // sont maintenant gérés par BaseEntity.
    // Les méthodes commentées ci-dessous sont les méthodes à SUPPRIMER ou commenter.
//    public Boolean getDeleted() {
//        return deleted;
//    }
//
//    public void setDeleted(Boolean deleted) {
//        this.deleted = deleted;
//    }
//
//    public Date getCreated() {
//        return created;
//    }
//
//    public void setCreated(Date created) {
//        this.created = created;
//    }
//
//    public Date getChanged() {
//        return changed;
//    }
//    public void setChanged(Date changed) {
//        this.changed = changed;
//    }
    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public String toString() {
        return "org.dobi.entities.Persistence[ id=" + getId() + " ]";
    }

    public Persistence(Integer id) {
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

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof Persistence)) {
            return false;
        }
        Persistence other = (Persistence) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

}
