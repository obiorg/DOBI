package org.dobi.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "user_login_data")
public class UserLoginData extends BaseEntity {

    @Column(name = "loginName", nullable = false, unique = true)
    private String loginName;

    @Column(name = "passwordHash", nullable = false)
    private String passwordHash;

    @Column(name = "passwordSalt")
    private String passwordSalt;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    // CORRECTION : Relation One-to-One standard via une colonne de jointure
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_account_id", referencedColumnName = "id")
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