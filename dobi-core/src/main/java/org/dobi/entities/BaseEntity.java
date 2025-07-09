package org.dobi.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "bit default 0")
    private boolean deleted;

    @Column(updatable = false, columnDefinition = "datetime default getdate()")
    private LocalDateTime created;

    @Column(columnDefinition = "datetime default getdate()")
    private LocalDateTime changed;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public LocalDateTime getChanged() {
        return changed;
    }

    public void setChanged(LocalDateTime changed) {
        this.changed = changed;
    }

    /**
     * CORRECTION MAJEURE : Utilise Instant.now() pour obtenir un point temporel
     * absolu en UTC. Ensuite, le convertit en LocalDateTime pour le stockage,
     * en spécifiant explicitement que ce LocalDateTime représente une heure
     * UTC. Cela élimine toute ambiguïté liée au fuseau horaire du serveur.
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime nowUtc = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        this.created = nowUtc;
        this.changed = nowUtc;
    }

    @PreUpdate
    protected void onUpdate() {
        this.changed = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
    }
}
