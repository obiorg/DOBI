package org.dobi.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.List;

@Entity
@Table(name = "machines")
public class Machine extends BaseEntity {

    @Column(nullable = false)
    private String address;
    private Integer port;
    private String name;
    private Integer rack;
    private Integer slot;
    private Integer bus;

    // --- Champs OPC UA ajoutés ---
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
    // --- Fin des champs OPC UA ---

    @Column(name = "mqtt_user")
    private String mqttUser;
    @Column(name = "mqtt_password")
    private String mqttPassword;

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
    public String getMqttUser() { return mqttUser; }
    public void setMqttUser(String mqttUser) { this.mqttUser = mqttUser; }
    public String getMqttPassword() { return mqttPassword; }
    public void setMqttPassword(String mqttPassword) { this.mqttPassword = mqttPassword; }
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
}
