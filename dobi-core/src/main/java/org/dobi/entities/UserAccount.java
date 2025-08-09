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
    
    @Column(name = "genre", nullable = false)
    private String genre;
    
    @Column(name = "dateOfBirth", nullable = false)
    private LocalDate dateOfBirth;

    // CORRECTION : La relation inverse est maintenant correcte
    @OneToOne(mappedBy = "userAccount", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserLoginData loginData;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_account_role",
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
    public void setLoginData(UserLoginData loginData) {
        // Assure la cohérence de la relation bidirectionnelle
        if (loginData != null) {
            loginData.setUserAccount(this);
        }
        this.loginData = loginData;
    }
    public Set<UserRole> getRoles() { return roles; }
    public void setRoles(Set<UserRole> roles) { this.roles = roles; }

    public String getGenre() {        return genre;    }

    public void setGenre(String genre) {        this.genre = genre;    }

    public LocalDate getDateOfBirth() {        return dateOfBirth;    }

    public void setDateOfBirth(LocalDate dateOfBirth) {        this.dateOfBirth = dateOfBirth;    }
    
    
}
