package org.dobi.entities;

import jakarta.persistence.*;

/**
 * Entité JPA pour la table 'alarms'. Représente la définition statique d'une
 * alarme (son nom, sa description, etc.).
 */
@Entity
@Table(name = "alarms")
public class Alarm extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company", nullable = false)
    private Company company;

    @Column(name = "alarm", length = 45, nullable = false)
    private String alarmCode;

    @Column(name = "name", length = 255)
    private String name;

    // 'descirption' est une coquille dans le schéma de BDD, nous la mappons telle quelle.
    @Column(name = "descirption", length = 512)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"group\"") // 'group' est un mot-clé SQL
    private AlarmGroup alarmGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class") // 'class' est un mot-clé SQL
    private AlarmClass alarmClass;

    @Column(name = "language")
    private Integer language;

    @Column(name = "comment", length = 512)
    private String comment;

    // Getters and Setters
    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getAlarmCode() {
        return alarmCode;
    }

    public void setAlarmCode(String alarmCode) {
        this.alarmCode = alarmCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public AlarmGroup getAlarmGroup() {
        return alarmGroup;
    }

    public void setAlarmGroup(AlarmGroup alarmGroup) {
        this.alarmGroup = alarmGroup;
    }

    public AlarmClass getAlarmClass() {
        return alarmClass;
    }

    public void setAlarmClass(AlarmClass alarmClass) {
        this.alarmClass = alarmClass;
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
}
