package org.dobi.entities;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_login_data")
public class UserLoginData extends BaseEntity {

    @Column(name = "loginName", nullable = false, unique = true)
    private String loginName;

    @Column(name = "passwordHash", nullable = false)
    private String passwordHash;

    // Note : Le sel est souvent inclus dans le hash avec les encodeurs modernes (ex: BCrypt)
    // Ce champ est conservé pour correspondre au schéma existant.
    @Column(name = "passwordSalt")
    private String passwordSalt;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    // Relation vers le compte utilisateur principal
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id") // Suppose une relation 1-1 sur la même clé primaire
    @MapsId
    private UserAccount userAccount;

    // Getters and Setters...
    public String getLoginName() { return loginName; }
    public void setLoginName(String loginName) { this.loginName = loginName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getPasswordSalt() { return passwordSalt; }
    public void setPasswordSalt(String passwordSalt) { this.passwordSalt = passwordSalt; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public UserAccount getUserAccount() { return userAccount; }
    public void setUserAccount(UserAccount userAccount) { this.userAccount = userAccount; }
}