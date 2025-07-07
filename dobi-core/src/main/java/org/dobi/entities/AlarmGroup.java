package org.dobi.entities;

import jakarta.persistence.*;

/**
 * Entité JPA pour la table 'alarm_groups'.
 * Permet de regrouper les alarmes de manière logique pour le filtrage.
 */
@Entity
@Table(name = "alarm_groups")
public class AlarmGroup extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company", nullable = false)
    private Company company;

    // Le nom de la colonne est un mot-clé SQL, d'où l'utilisation de guillemets
    @Column(name = "\"group\"", length = 45, nullable = false)
    private String groupName;

    @Column(name = "comment", length = 512)
    private String comment;

    // Getters and Setters
    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
