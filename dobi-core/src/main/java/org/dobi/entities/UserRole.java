package org.dobi.entities;

import jakarta.persistence.*;
import java.util.Set;

@Entity
@Table(name = "user_roles")
public class UserRole extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "description")
    private String description;

    // Relation Many-to-Many vers les permissions
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_role_permissions",
        joinColumns = @JoinColumn(name = "role"),
        inverseJoinColumns = @JoinColumn(name = "permission")
    )
    private Set<UserPermission> permissions;

    // Getters and Setters...
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Set<UserPermission> getPermissions() { return permissions; }
    public void setPermissions(Set<UserPermission> permissions) { this.permissions = permissions; }
}