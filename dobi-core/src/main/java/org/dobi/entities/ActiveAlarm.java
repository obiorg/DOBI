package org.dobi.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entité JPA représentant une alarme active dans le système. CORRECTION : Cette
 * entité ne doit pas hériter de BaseEntity car sa table n'a pas les colonnes
 * d'audit (created, changed, deleted). Sa clé primaire est définie directement
 * ici.
 */
@Entity
@Table(name = "active_alarms")
public class ActiveAlarm { // <-- Ne hérite plus de BaseEntity

    // Définition de la clé primaire directement dans la classe
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relation vers la définition statique de l'alarme (nom, description, classe, etc.)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alarm_definition_id", nullable = false)
    private Alarm alarmDefinition;

    // Relation vers le tag dont la valeur a déclenché l'alarme
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    // Relation vers la société à laquelle cette alarme appartient
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // Horodatage du déclenchement de l'alarme
    @Column(name = "trigger_time", nullable = false)
    private LocalDateTime triggerTime;

    // Valeur du tag au moment du déclenchement
    @Column(name = "trigger_value")
    private Float triggerValue;

    // Indique si l'alarme a été acquittée par un opérateur
    @Column(name = "acknowledged", nullable = false)
    private boolean acknowledged = false;

    // Horodatage de l'acquittement
    @Column(name = "ack_time")
    private LocalDateTime ackTime;

    // Identifiant de l'utilisateur ou du système ayant acquitté
    @Column(name = "ack_by")
    private String ackBy;

    // Horodatage de la résolution de l'alarme (retour à la normale)
    @Column(name = "resolved_time")
    private LocalDateTime resolvedTime;

    // Constructeurs
    public ActiveAlarm() {
        this.triggerTime = LocalDateTime.now();
    }

    // Getters et Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Alarm getAlarmDefinition() {
        return alarmDefinition;
    }

    public void setAlarmDefinition(Alarm alarmDefinition) {
        this.alarmDefinition = alarmDefinition;
    }

    public Tag getTag() {
        return tag;
    }

    public void setTag(Tag tag) {
        this.tag = tag;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public LocalDateTime getTriggerTime() {
        return triggerTime;
    }

    public void setTriggerTime(LocalDateTime triggerTime) {
        this.triggerTime = triggerTime;
    }

    public Float getTriggerValue() {
        return triggerValue;
    }

    public void setTriggerValue(Float triggerValue) {
        this.triggerValue = triggerValue;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public void setAcknowledged(boolean acknowledged) {
        this.acknowledged = acknowledged;
    }

    public LocalDateTime getAckTime() {
        return ackTime;
    }

    public void setAckTime(LocalDateTime ackTime) {
        this.ackTime = ackTime;
    }

    public String getAckBy() {
        return ackBy;
    }

    public void setAckBy(String ackBy) {
        this.ackBy = ackBy;
    }

    public LocalDateTime getResolvedTime() {
        return resolvedTime;
    }

    public void setResolvedTime(LocalDateTime resolvedTime) {
        this.resolvedTime = resolvedTime;
    }
}
