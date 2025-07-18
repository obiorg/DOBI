package org.dobi.entities;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Set;

@Entity
@Table(name = "user_account")
public class UserAccount extends BaseEntity {

    @Column(name = "firstName", nullable = false)
    private String firstName;

    @Column(name = "lastName", nullable = false)
    private String lastName;

    // Relation inverse vers les données de connexion
    @OneToOne(mappedBy = "userAccount", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserLoginData loginData;

    // Relation Many-to-Many vers les rôles
    @ManyToMany(fetch = FetchType.EAGER) // EAGER pour charger les rôles avec l'utilisateur
    @JoinTable(
        name = "user_account_role",
        joinColumns = @JoinColumn(name = "user"),
        inverseJoinColumns = @JoinColumn(name = "role")
    )
    private Set<UserRole> roles;

    // Getters and Setters...
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public UserLoginData getLoginData() { return loginData; }
    public void setLoginData(UserLoginData loginData) { this.loginData = loginData; }
    public Set<UserRole> getRoles() { return roles; }
    public void setRoles(Set<UserRole> roles) { this.roles = roles; }
}
