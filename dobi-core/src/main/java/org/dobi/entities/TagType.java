package org.dobi.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "tags_types")
public class TagType extends BaseEntity {
    private String type;
    private String designation;
    
    // Getters and Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
}
