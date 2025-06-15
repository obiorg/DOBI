package org.dobi.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "companies")
public class Company extends BaseEntity {
    
    @Column(name = "company", nullable = false, unique = true)
    private String companyCode;

    private String designation;

    // Getters and Setters
    public String getCompanyCode() { return companyCode; }
    public void setCompanyCode(String companyCode) { this.companyCode = companyCode; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
}
