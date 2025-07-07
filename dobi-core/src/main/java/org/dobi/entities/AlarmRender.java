package org.dobi.entities;

import jakarta.persistence.*;

/**
 * Entité JPA pour la table 'alarm_render'. Définit l'apparence visuelle d'une
 * classe d'alarme (couleurs, clignotement).
 */
@Entity
@Table(name = "alarm_render")
public class AlarmRender extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company", nullable = false)
    private Company company;

    @Column(name = "render", length = 45, nullable = false)
    private String render;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "color", length = 45)
    private String color;

    @Column(name = "background", length = 45)
    private String background;

    @Column(name = "blink")
    private Boolean blink;

    @Column(name = "colorBlink", length = 45)
    private String colorBlink;

    @Column(name = "backgroundBlink", length = 45)
    private String backgroundBlink;

    @Column(name = "comment", length = 512)
    private String comment;

    // Getters and Setters
    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getRender() {
        return render;
    }

    public void setRender(String render) {
        this.render = render;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getBackground() {
        return background;
    }

    public void setBackground(String background) {
        this.background = background;
    }

    public Boolean getBlink() {
        return blink;
    }

    public void setBlink(Boolean blink) {
        this.blink = blink;
    }

    public String getColorBlink() {
        return colorBlink;
    }

    public void setColorBlink(String colorBlink) {
        this.colorBlink = colorBlink;
    }

    public String getBackgroundBlink() {
        return backgroundBlink;
    }

    public void setBackgroundBlink(String backgroundBlink) {
        this.backgroundBlink = backgroundBlink;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
