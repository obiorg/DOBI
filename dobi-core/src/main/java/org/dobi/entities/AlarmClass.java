package org.dobi.entities;

import jakarta.persistence.*;

/**
 * Entité JPA pour la table 'alarm_classes'. Définit la sévérité d'une alarme
 * (Critique, Haute, Avertissement...) et la lie à un rendu visuel.
 */
@Entity
@Table(name = "alarm_classes")
public class AlarmClass extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company", nullable = false)
    private Company company;

    // 'class' est un mot-clé réservé en Java
    @Column(name = "class", length = 45, nullable = false)
    private String className;

    @Column(name = "name", length = 255)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "render")
    private AlarmRender render;

    @Column(name = "comment", length = 512)
    private String comment;

    // Getters and Setters
    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AlarmRender getRender() {
        return render;
    }

    public void setRender(AlarmRender render) {
        this.render = render;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
