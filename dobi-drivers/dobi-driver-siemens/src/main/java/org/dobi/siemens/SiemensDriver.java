package org.dobi.siemens;

import org.dobi.api.IDriver;
import org.dobi.entities.Machine;
// CORRECTION: Utilisation du package Moka7 refactorisÃƒÂ©
import org.dobi.moka7.S7Client;
import org.dobi.moka7.S7;

public class SiemensDriver implements IDriver {

    private Machine machine;
    private S7Client client;
    private boolean connected = false;

    public SiemensDriver() {
        this.client = new S7Client();
    }

    @Override
    public void configure(Machine machine) {
        this.machine = machine;
    }

    @Override
    public boolean connect() {
        if (machine == null) {
            System.err.println("Driver not configured. Call configure() first.");
            return false;
        }
        try {
            // Parametres de connexion Rack et Slot pour S7-300/400/1200/1500
            int rack = (machine.getRack() != null) ? machine.getRack() : 0;
            int slot = (machine.getSlot() != null) ? machine.getSlot() : 1; // Le slot 0 est souvent reserve, le 1 est commun pour la CPU
            
            client.ConnectTo(machine.getAddress(), rack, slot);
            connected = (client.Connected);
            return connected;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void disconnect() {
        if (client != null && client.Connected) {
            client.Disconnect();
        }
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected && client.Connected;
    }

            @Override
    public Object read(org.dobi.entities.Tag tag) {
        if (!isConnected() || tag.getDbNumber() == null || tag.getByteAddress() == null) {
            return null;
        }
        
        try {
            byte[] buffer = new byte[4]; // Buffer assez grand pour un DWord ou un Real
            client.ReadArea(S7.S7AreaDB, tag.getDbNumber(), tag.getByteAddress(), 4, buffer);

            String typeName = tag.getType().getType().toUpperCase();
            switch (typeName) {
                case "REAL":
                    return S7.GetDWordAt(buffer, 0); // Note: sera affiché comme un Long, le cast en float se fera plus tard
                case "DINT":
                     return S7.GetDIntAt(buffer, 0);
                case "INT":
                    return S7.GetWordAt(buffer, 0); // Siemens INT = 16 bits
                case "BOOL":
                    if (tag.getBitAddress() != null) {
                        return S7.GetBitAt(buffer, 0, tag.getBitAddress());
                    }
                    return null;
                // Ajoutez d'autres types ici (STRING, etc.)
                default:
                    return "Type non supporte: " + typeName;
            }
        } catch (Exception e) {
            System.err.println("Erreur de lecture du tag " + tag.getName() + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public void write(org.dobi.entities.Tag tag, Object value) {
        // TODO: Implémenter la logique d'écriture
        System.out.println("Writing to " + tag.getName() + " (not implemented yet)");
    }
