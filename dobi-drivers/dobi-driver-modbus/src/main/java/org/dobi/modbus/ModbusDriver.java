package org.dobi.modbus;

import com.digitalpetri.modbus.master.ModbusTcpMaster;
import com.digitalpetri.modbus.master.ModbusTcpMasterConfig;
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
                .setTimeout(Duration.ofSeconds(5))
                .build();
            
            master = new ModbusTcpMaster(config);

            // La connexion se fait à la première requête, on la force ici.
            CompletableFuture<Void> future = master.connect();
            future.get(); // Attend que la connexion soit établie.
            return true;
        } catch (Exception e) {
            System.err.println("Erreur de connexion Modbus à " + machine.getName() + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public void disconnect() {
        if (master != null) {
            master.disconnect();
        }
    }

    @Override
    public boolean isConnected() {
        // La librairie gère la reconnexion, on considère 'connecté' si l'objet existe.
        return master != null;
    }

    @Override
    public Object read(Tag tag) {
        System.out.println("Lecture Modbus pour le tag " + tag.getName() + " (non implémentée)");
        // TODO: Implémenter la logique de lecture des registres Modbus
        // (Read Holding Registers, Read Input Registers, etc.)
        return null;
    }

    @Override
    public void write(Tag tag, Object value) {
        System.out.println("Écriture Modbus pour le tag " + tag.getName() + " (non implémentée)");
        // TODO: Implémenter la logique d'écriture
    }
}
