package org.dobi.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "machines")
public class Machine extends BaseEntity {

    @Column(nullable = false)
    private String address;

    private Integer port;
    private Integer rack;
    private Integer slot;
    private String name;
    
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
    public Integer getRack() { return rack; }
    public void setRack(Integer rack) { this.rack = rack; }
    public Integer getSlot() { return slot; }
    public void setSlot(Integer slot) { this.slot = slot; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public MachDriver getDriver() { return driver; }
    public void setDriver(MachDriver driver) { this.driver = driver; }
}
