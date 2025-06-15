package org.dobi.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "tags")
public class Tag extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "db")
    private Integer dbNumber;

    @Column(name = "byte")
    private Integer byteAddress;
    
    @Column(name = "bit")
    private Integer bitAddress;

    private boolean active;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine", nullable = false)
    private Machine machine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "	ype", nullable = false)
    private TagType type;

    
    // Ajoutez ici d'autres champs de la table 'tags' si nÃ©cessaire (type, cycle, etc.)

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getDbNumber() { return dbNumber; }
    public void setDbNumber(Integer dbNumber) { this.dbNumber = dbNumber; }
    public Integer getByteAddress() { return byteAddress; }
    public void setByteAddress(Integer byteAddress) { this.byteAddress = byteAddress; }
    public Integer getBitAddress() { return bitAddress; }
    public void setBitAddress(Integer bitAddress) { this.bitAddress = bitAddress; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Machine getMachine() { return machine; }
    public void setMachine(Machine machine) { this.machine = machine; }


    public TagType getType() { return type; }
    public void setType(TagType type) { this.type = type; }
}

