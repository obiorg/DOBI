package org.dobi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Entité JPA pour la table 'meas_comparators'.
 * Représente les opérateurs de comparaison (>, <, =, etc.).
 */
@Entity
@Table(name = "meas_comparators")
public class MeasComparator extends BaseEntity {

    @Column(name = "symbol", length = 45, nullable = false)
    private String symbol;

    @Column(name = "name", length = 255)
    private String name;

    // Getters and Setters
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
