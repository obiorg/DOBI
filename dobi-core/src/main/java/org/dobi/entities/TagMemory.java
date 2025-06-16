package org.dobi.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "tags_memories")
public class TagMemory extends BaseEntity {
    private String name;
    private String comment;

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
