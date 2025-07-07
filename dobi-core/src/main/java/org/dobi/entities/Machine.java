package org.dobi.entities;

import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "machines")
public class Machine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;
    @Column(name = "deleted")
    private Boolean deleted;
    @Column(name = "created")
    @Temporal(TemporalType.TIMESTAMP)
    private Date created;
    @Column(name = "changed")
    @Temporal(TemporalType.TIMESTAMP)
    private Date changed;
    @Basic(optional = false)
    @Column(name = "address")
    private String address;
    @Column(name = "mask")
    private String mask;
    @Column(name = "dns")
    private String dns;
    @Column(name = "ipv6")
    private String ipv6;
    @Column(name = "port")
    private Integer port;
    @Column(name = "name")
    private String name;
    @Column(name = "rack")
    private Integer rack;
    @Column(name = "slot")
    private Integer slot;
    @Column(name = "mqtt")
    private Boolean mqtt;
    @Column(name = "webhook")
    private Boolean webhook;
    @Column(name = "webhook_secret")
    private String webhookSecret;
    @Column(name = "bus")
    private Integer bus;
    @Column(name = "description")
    private String description;



    // --- Champs OPC UA ajoutÃ©s ---
    @Column(name = "opcua_security_policy")
    private String opcuaSecurityPolicy;
    @Column(name = "opcua_user")
    private String opcuaUser;
    @Column(name = "opcua_password")
    private String opcuaPassword;
    @Column(name = "opcua_keystore_path")
    private String opcuaKeystorePath;
    @Column(name = "opcua_keystore_password")
    private String opcuaKeystorePassword;

    @Column(name = "hostname") private String hostname;
    // --- Fin des champs OPC UA ---

    @Column(name = "mqtt_user")
    private String mqttUser;

    @Column(name = "mqtt_password")
    private String mqttPassword;
    // --- Fin des lignes ajoutées ---

    @OneToMany(mappedBy = "machine", cascade = CascadeType.ALL)
    private List<Tag> tags;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver", nullable = false)
    private MachDriver driver;

    // Getters and Setters
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getRack() { return rack; }
    public void setRack(Integer rack) { this.rack = rack; }
    public Integer getSlot() { return slot; }
    public void setSlot(Integer slot) { this.slot = slot; }
    public Integer getBus() { return bus; }
    public void setBus(Integer bus) { this.bus = bus; }
    
    // --- Méthodes ajoutées ---
    public String getMqttUser() { return mqttUser; }
    public void setMqttUser(String mqttUser) { this.mqttUser = mqttUser; }
    public String getMqttPassword() { return mqttPassword; }
    public void setMqttPassword(String mqttPassword) { this.mqttPassword = mqttPassword; }
    // --- Fin des méthodes ajoutées ---

    public List<Tag> getTags() { return tags; }
    public void setTags(List<Tag> tags) { this.tags = tags; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public MachDriver getDriver() { return driver; }
    public void setDriver(MachDriver driver) { this.driver = driver; }

    // --- Getters et Setters pour les champs OPC UA ---
    public String getOpcuaSecurityPolicy() { return opcuaSecurityPolicy; }
    public void setOpcuaSecurityPolicy(String opcuaSecurityPolicy) { this.opcuaSecurityPolicy = opcuaSecurityPolicy; }
    public String getOpcuaUser() { return opcuaUser; }
    public void setOpcuaUser(String opcuaUser) { this.opcuaUser = opcuaUser; }
    public String getOpcuaPassword() { return opcuaPassword; }
    public void setOpcuaPassword(String opcuaPassword) { this.opcuaPassword = opcuaPassword; }
    public String getOpcuaKeystorePath() { return opcuaKeystorePath; }
    public void setOpcuaKeystorePath(String opcuaKeystorePath) { this.opcuaKeystorePath = opcuaKeystorePath; }
    public String getOpcuaKeystorePassword() { return opcuaKeystorePassword; }
    public void setOpcuaKeystorePassword(String opcuaKeystorePassword) { this.opcuaKeystorePassword = opcuaKeystorePassword; }


    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public Machine() {
    }

    public Machine(Integer id) {
        this.id = id;
    }

    public Machine(Integer id, String address) {
        this.id = id;
        this.address = address;
    }



    public void setId(Integer id) {
        this.id = id;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }


    public void setCreated(Date created) {
        this.created = created;
    }


    public void setChanged(Date changed) {
        this.changed = changed;
    }


    public String getMask() {
        return mask;
    }

    public void setMask(String mask) {
        this.mask = mask;
    }

    public String getDns() {
        return dns;
    }

    public void setDns(String dns) {
        this.dns = dns;
    }

    public String getIpv6() {
        return ipv6;
    }

    public void setIpv6(String ipv6) {
        this.ipv6 = ipv6;
    }


    public Boolean getMqtt() {
        return mqtt;
    }

    public void setMqtt(Boolean mqtt) {
        this.mqtt = mqtt;
    }

    public Boolean getWebhook() {
        return webhook;
    }

    public void setWebhook(Boolean webhook) {
        this.webhook = webhook;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }


    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof Machine)) {
            return false;
        }
        Machine other = (Machine) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.dobi.entities.Machine[ id=" + id + " ]";
    }
}

