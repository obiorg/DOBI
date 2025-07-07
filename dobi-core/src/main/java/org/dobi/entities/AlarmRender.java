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
@Table(name = "alarm_render")
@NamedQueries({
    @NamedQuery(name = "AlarmRender.findAll", query = "SELECT a FROM AlarmRender a"),
    @NamedQuery(name = "AlarmRender.findById", query = "SELECT a FROM AlarmRender a WHERE a.id = :id"),
    @NamedQuery(name = "AlarmRender.findByDeleted", query = "SELECT a FROM AlarmRender a WHERE a.deleted = :deleted"),
    @NamedQuery(name = "AlarmRender.findByCreated", query = "SELECT a FROM AlarmRender a WHERE a.created = :created"),
    @NamedQuery(name = "AlarmRender.findByChanged", query = "SELECT a FROM AlarmRender a WHERE a.changed = :changed"),
    @NamedQuery(name = "AlarmRender.findByRender", query = "SELECT a FROM AlarmRender a WHERE a.render = :render"),
    @NamedQuery(name = "AlarmRender.findByName", query = "SELECT a FROM AlarmRender a WHERE a.name = :name"),
    @NamedQuery(name = "AlarmRender.findByColor", query = "SELECT a FROM AlarmRender a WHERE a.color = :color"),
    @NamedQuery(name = "AlarmRender.findByBackground", query = "SELECT a FROM AlarmRender a WHERE a.background = :background"),
    @NamedQuery(name = "AlarmRender.findByBlink", query = "SELECT a FROM AlarmRender a WHERE a.blink = :blink"),
    @NamedQuery(name = "AlarmRender.findByColorBlink", query = "SELECT a FROM AlarmRender a WHERE a.colorBlink = :colorBlink"),
    @NamedQuery(name = "AlarmRender.findByBackgroundBlink", query = "SELECT a FROM AlarmRender a WHERE a.backgroundBlink = :backgroundBlink"),
    @NamedQuery(name = "AlarmRender.findByComment", query = "SELECT a FROM AlarmRender a WHERE a.comment = :comment")})
public class AlarmRender implements Serializable {

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
    @Column(name = "render")
    private String render;
    @Column(name = "name")
    private String name;
    @Column(name = "color")
    private String color;
    @Column(name = "background")
    private String background;
    @Column(name = "blink")
    private Boolean blink;
    @Column(name = "colorBlink")
    private String colorBlink;
    @Column(name = "backgroundBlink")
    private String backgroundBlink;
    @Column(name = "comment")
    private String comment;
    @JoinColumn(name = "company", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private Company company;
    @OneToMany(mappedBy = "render")
    private Collection<AlarmClasses> alarmClassesCollection;

    public AlarmRender() {
    }

    public AlarmRender(Integer id) {
        this.id = id;
    }

    public AlarmRender(Integer id, String render) {
        this.id = id;
        this.render = render;
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

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public Collection<AlarmClasses> getAlarmClassesCollection() {
        return alarmClassesCollection;
    }

    public void setAlarmClassesCollection(Collection<AlarmClasses> alarmClassesCollection) {
        this.alarmClassesCollection = alarmClassesCollection;
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
        if (!(object instanceof AlarmRender)) {
            return false;
        }
        AlarmRender other = (AlarmRender) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.dobi.entities.AlarmRender[ id=" + id + " ]";
    }
    
}
