package org.dobi.modbus;

import com.digitalpetri.modbus.master.ModbusTcpMaster;
import com.digitalpetri.modbus.master.ModbusTcpMasterConfig;
import com.digitalpetri.modbus.requests.*;
import com.digitalpetri.modbus.responses.*;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import org.dobi.api.IDriver;
import org.dobi.entities.Machine;
import org.dobi.entities.Tag;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class ModbusDriver implements IDriver {

    private Machine machine;
    private ModbusTcpMaster master;

    @Override
    public void configure(Machine machine) {
        this.machine = machine;
    }

    @Override
    public boolean connect() {
        if (machine == null) return false;
        try {
            ModbusTcpMasterConfig config = new ModbusTcpMasterConfig.Builder(machine.getAddress())
                .setPort(machine.getPort() != null ? machine.getPort() : 502)
                .setTimeout(Duration.ofSeconds(2))
                .build();
            master = new ModbusTcpMaster(config);
            master.connect().get();
            return true;
        } catch (Exception e) {
            System.err.println("Erreur de connexion Modbus a " + machine.getName() + ": " + e.getMessage());
            if (master != null) master.disconnect();
            return false;
        }
    }

    @Override
    public Object read(Tag tag) {
        if (!isConnected() || tag.getByteAddress() == null || tag.getMemory() == null) {
            return null;
        }
        // L'ID de l'esclave Modbus (Unit ID)
        int unitId = machine.getBus() != null ? machine.getBus() : 1;
        String memoryArea = tag.getMemory().getName().toUpperCase();
        
        try {
            switch (memoryArea) {
                case "COIL": // Lecture d'un bit (BOOL)
                    return readCoils(tag, unitId).get();
                case "DISCRETE INPUT": // Lecture d'un bit (BOOL, lecture seule)
                    return readDiscreteInputs(tag, unitId).get();
                case "HOLDING REGISTER": // Lecture d'un ou plusieurs registres (INT, REAL...)
                    return readHoldingRegisters(tag, unitId).get();
                case "INPUT REGISTER": // Lecture d'un ou plusieurs registres (INT, REAL..., lecture seule)
                    return readInputRegisters(tag, unitId).get();
                default:
                    System.err.println("Zone memoire Modbus non reconnue: " + memoryArea);
                    return null;
            }
        } catch (Exception e) {
            System.err.println("Exception de lecture Modbus pour le tag '" + tag.getName() + "': " + e.getMessage());
            return null;
        }
    }

    private CompletableFuture<Object> readCoils(Tag tag, int unitId) {
        ReadCoilsRequest request = new ReadCoilsRequest(tag.getByteAddress(), 1);
        return master.sendRequest(request, unitId).thenApply(response -> {
            try {
                if (response instanceof ReadCoilsResponse) {
                    ByteBuf buffer = ((ReadCoilsResponse) response).getCoilStatus();
                    return buffer.readBoolean();
                }
                return null;
            } finally {
                ReferenceCountUtil.release(response);
            }
        });
    }

    private CompletableFuture<Object> readDiscreteInputs(Tag tag, int unitId) {
        ReadDiscreteInputsRequest request = new ReadDiscreteInputsRequest(tag.getByteAddress(), 1);
        return master.sendRequest(request, unitId).thenApply(response -> {
            try {
                if (response instanceof ReadDiscreteInputsResponse) {
                    ByteBuf buffer = ((ReadDiscreteInputsResponse) response).getInputStatus();
                    return buffer.readBoolean();
                }
                return null;
            } finally {
                ReferenceCountUtil.release(response);
            }
        });
    }
    
    private CompletableFuture<Object> readHoldingRegisters(Tag tag, int unitId) {
        int quantity = getQuantityToRead(tag.getType().getType());
        ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest(tag.getByteAddress(), quantity);
        return master.sendRequest(request, unitId).thenApply(response -> {
            try {
                if (response instanceof ReadHoldingRegistersResponse) {
                    ByteBuf buffer = ((ReadHoldingRegistersResponse) response).getRegisters();
                    return decodeRegisterValue(buffer, tag.getType().getType());
                }
                return null;
            } finally {
                ReferenceCountUtil.release(response);
            }
        });
    }

    private CompletableFuture<Object> readInputRegisters(Tag tag, int unitId) {
        int quantity = getQuantityToRead(tag.getType().getType());
        ReadInputRegistersRequest request = new ReadInputRegistersRequest(tag.getByteAddress(), quantity);
        return master.sendRequest(request, unitId).thenApply(response -> {
            try {
                if (response instanceof ReadInputRegistersResponse) {
                    ByteBuf buffer = ((ReadInputRegistersResponse) response).getRegisters();
                    return decodeRegisterValue(buffer, tag.getType().getType());
                }
                return null;
            } finally {
                ReferenceCountUtil.release(response);
            }
        });
    }

    private int getQuantityToRead(String typeName) {
        switch (typeName.toUpperCase()) {
            case "REAL": case "DINT": case "UDINT":
                return 2; // Un REAL ou un DINT occupe 2 registres de 16 bits
            default:
                return 1; // INT, UINT, WORD, etc.
        }
    }

    private Object decodeRegisterValue(ByteBuf buffer, String typeName) {
        switch (typeName.toUpperCase()) {
            case "INT":
                return buffer.readShort(); // 16-bit signed
            case "UINT":
                return buffer.readUnsignedShort(); // 16-bit unsigned
            case "DINT":
                return buffer.readInt(); // 32-bit signed
            case "UDINT":
                return buffer.readUnsignedInt(); // 32-bit unsigned
            case "REAL":
                return buffer.readFloat(); // 32-bit float
            default:
                return "Type de registre inconnu: " + typeName;
        }
    }

    @Override
    public void write(Tag tag, Object value) {
        // La logique d'écriture sera implémentée plus tard
        System.out.println("Ecriture Modbus pour le tag " + tag.getName() + " (non implementee)");
    }

    @Override
    public void disconnect() {
        if (master != null) {
            master.disconnect();
        }
    }

    @Override
    public boolean isConnected() {
        return master != null;
    }
}
