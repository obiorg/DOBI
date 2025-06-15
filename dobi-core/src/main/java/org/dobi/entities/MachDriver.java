package org.dobi.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "mach_drivers")
public class MachDriver extends BaseEntity {

    private String driver;
    private String designation;

    // Getters and Setters
    public String getDriver() { return driver; }
    public void setDriver(String driver) { this.driver = driver; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
}
