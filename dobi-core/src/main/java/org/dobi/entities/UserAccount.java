package org.dobi.entities;

import jakarta.persistence.*;
import java.util.Set;

@Entity
@Table(name = "user_account")
public class UserAccount extends BaseEntity {

    @Column(name = "firstName", nullable = false)
    private String firstName;

    @Column(name = "lastName", nullable = false)
    private String lastName;

    @OneToOne(mappedBy = "userAccount", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserLoginData loginData;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_account_role",
        // CORRECTION : On entoure le nom de la colonne "user" de crochets
        // pour indiquer à JPA/Hibernate qu'il s'agit d'un identifiant et non d'un mot-clé SQL.
        joinColumns = @JoinColumn(name = "[user]"),
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
