package org.dobi.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "user_permissions")
public class UserPermission extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "designation")
    private String designation;

    // Getters and Setters...
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
}