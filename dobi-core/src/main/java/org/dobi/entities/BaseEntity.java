package org.dobi.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "bit default 0")
    private boolean deleted;

    @Column(updatable = false)
    private OffsetDateTime created;

    @Column
    private OffsetDateTime changed;

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

    // Getters et Setters pour les nouveaux types
    public OffsetDateTime getCreated() {
        return created;
    }

    public void setCreated(OffsetDateTime created) {
        this.created = created;
    }

    public OffsetDateTime getChanged() {
        return changed;
    }

    public void setChanged(OffsetDateTime changed) {
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
        // On force l'utilisation de l'heure UTC
        this.created = OffsetDateTime.now(ZoneOffset.UTC);
        this.changed = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @PreUpdate
    protected void onUpdate() {
        this.changed = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
