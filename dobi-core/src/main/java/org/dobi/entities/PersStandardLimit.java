package org.dobi.entities;

import jakarta.persistence.*;

/**
 * Entité JPA pour la table 'pers_standard_limits'. C'est l'entité clé qui lie
 * un Tag, une condition (limite) et une Alarme à déclencher.
 */
@Entity
@Table(name = "pers_standard_limits")
public class PersStandardLimit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag", nullable = false)
    private Tag tag;

    // Note: La colonne 'pers_standard' dans la BDD est un FK vers la table 'alarms'.
    // Nous la mappons donc à l'entité 'Alarm'.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pers_standard", nullable = false)
    private Alarm alarmToTrigger;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "value")
    private Float value;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comparator", nullable = false)
    private MeasComparator comparator;

    @Column(name = "delay")
    private Integer delay;

    @Column(name = "hysteresis")
    private Float hysteresis;

    // Getters and Setters
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

    public Alarm getAlarmToTrigger() {
        return alarmToTrigger;
    }

    public void setAlarmToTrigger(Alarm alarmToTrigger) {
        this.alarmToTrigger = alarmToTrigger;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Float getValue() {
        return value;
    }

    public void setValue(Float value) {
        this.value = value;
    }

    public MeasComparator getComparator() {
        return comparator;
    }

    public void setComparator(MeasComparator comparator) {
        this.comparator = comparator;
    }

    public Integer getDelay() {
        return delay;
    }

    public void setDelay(Integer delay) {
        this.delay = delay;
    }

    public Float getHysteresis() {
        return hysteresis;
    }

    public void setHysteresis(Float hysteresis) {
        this.hysteresis = hysteresis;
    }
}
