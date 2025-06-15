package org.dobi.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

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
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public LocalDateTime getCreated() { return created; }
    public void setCreated(LocalDateTime created) { this.created = created; }
    public LocalDateTime getChanged() { return changed; }
    public void setChanged(LocalDateTime changed) { this.changed = changed; }

    @PrePersist
    protected void onCreate() {
        created = LocalDateTime.now();
        changed = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        changed = LocalDateTime.now();
    }
}
