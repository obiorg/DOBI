package org.dobi.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "push_subscriptions")
public class PushSubscription extends BaseEntity {

    @Column(name = "endpoint", unique = true, length = 1024)
    private String endpoint;

    @Column(name = "p256dh")
    private String p256dh;

    @Column(name = "auth")
    private String auth;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public void setP256dh(String p256dh) {
        this.p256dh = p256dh;
    }

    public String getAuth() {
        return auth;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    
}