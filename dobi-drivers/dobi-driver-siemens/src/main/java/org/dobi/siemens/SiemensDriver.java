package org.dobi.siemens;

import org.dobi.api.IDriver;
import org.dobi.entities.Machine;
import org.dobi.entities.Tag;
import org.dobi.moka7.S7;
import org.dobi.moka7.S7Client;

public class SiemensDriver implements IDriver {

    private Machine machine;
    private final S7Client client;
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
        if (machine == null) return false;
        try {
            int rack = (machine.getRack() != null) ? machine.getRack() : 0;
            int slot = (machine.getSlot() != null) ? machine.getSlot() : 1;
            client.ConnectTo(machine.getAddress(), rack, slot);
            connected = client.Connected;
            return connected;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public Object read(Tag tag) {
        if (!isConnected() || tag.getByteAddress() == null) return null;

        int area = getS7Area(tag.getMemory().getName());
        int dbNumber = (area == S7.S7AreaDB && tag.getDbNumber() != null) ? tag.getDbNumber() : 0;
        int bytesToRead = getBytesToRead(tag.getType().getType());
        
        if (bytesToRead == 0) return "Type non géré: " + tag.getType().getType();

        try {
            byte[] buffer = new byte[bytesToRead];
            int result = client.ReadArea(area, dbNumber, tag.getByteAddress(), bytesToRead, buffer);
            if (result != 0) {
                System.err.println("Erreur de lecture du tag " + tag.getName() + ": " + S7Client.ErrorText(result));
                return null;
            }
            return convertBufferToValue(buffer, tag);
        } catch (Exception e) {
            System.err.println("Exception de lecture du tag " + tag.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private Object convertBufferToValue(byte[] buffer, Tag tag) {
        String typeName = tag.getType().getType().toUpperCase();
        Integer bit = tag.getBitAddress();

        switch (typeName) {
            case "BOOL":
                return bit != null ? S7.GetBitAt(buffer, 0, bit) : null;
            case "BYTE":
            case "SINT": // Signed 8-bit integer
                return buffer[0];
            case "USINT": // Unsigned 8-bit integer
                return S7.GetUSIntAt(buffer, 0);
            case "WORD":
                return S7.GetWordAt(buffer, 0);
            case "INT":
                return S7.GetShortAt(buffer, 0);
            case "UINT":
                return S7.GetUShortAt(buffer, 0);
            case "DWORD":
                return S7.GetDWordAt(buffer, 0);
            case "DINT":
                return S7.GetDIntAt(buffer, 0);
            case "UDINT":
                return S7.GetUDIntAt(buffer, 0);
            case "REAL":
                return S7.GetFloatAt(buffer, 0);
            case "DATETIME":
                return S7.GetDateAt(buffer, 0);
            default:
                return "Type inconnu pour conversion: " + typeName;
        }
    }

    private int getBytesToRead(String typeName) {
        switch (typeName.toUpperCase()) {
            case "BOOL":
            case "BYTE":
            case "SINT":
            case "USINT":
                return 1;
            case "WORD":
            case "INT":
            case "UINT":
                return 2;
            case "DWORD":
            case "DINT":
            case "UDINT":
            case "REAL":
                return 4;
            case "DATETIME":
                return 8;
            default:
                return 0; // Type non supporté
        }
    }

    private int getS7Area(String memoryName) {
        switch (memoryName.toUpperCase()) {
            case "DB":
                return S7.S7AreaDB;
            case "M": // Merkers (Flags)
                return S7.S7AreaMK;
            case "E": // Entrées (Inputs)
            case "I":
                return S7.S7AreaPE;
            case "A": // Sorties (Outputs)
            case "Q":
                return S7.S7AreaPA;
            case "T": // Timers
                return S7.S7AreaTM;
            case "C": // Counters
                return S7.S7AreaCT;
            default:
                return S7.S7AreaDB; // Par défaut, on suppose un DB
        }
    }

    @Override
    public void write(Tag tag, Object value) {
        // TODO
    }

    @Override
    public void disconnect() {
        if (client != null) client.Disconnect();
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }
}
