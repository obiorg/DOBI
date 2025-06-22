package org.dobi.opcua;

import org.dobi.api.IDriver;
import org.dobi.entities.Machine;
import org.dobi.entities.Tag;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

public class OpcUaDriver implements IDriver {

    private Machine machine;
    private OpcUaClient client;

    @Override
    public void configure(Machine machine) {
        this.machine = machine;
    }

    @Override
    public boolean connect() {
        if (machine == null || machine.getAddress() == null || machine.getAddress().trim().isEmpty()) {
            System.err.println("Adresse IP non configurée pour la machine OPC UA.");
            return false;
        }
        
        try {
            // CORRECTION : Construire dynamiquement l'URL à partir des champs de la machine
            int port = machine.getPort() != null ? machine.getPort() : 4840; // Port standard pour OPC UA
            String endpointUrl = String.format("opc.tcp://%s:%d", machine.getAddress(), port);
            
            System.out.println("Tentative de connexion à l'endpoint OPC UA : " + endpointUrl);

            client = OpcUaClient.create(endpointUrl);
            
            // Connexion à la session
            client.connect().get();
            return true;
        } catch (Exception e) {
            System.err.println("Erreur de connexion OPC UA a " + machine.getName() + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public void disconnect() {
        if (client != null) {
            client.disconnect();
        }
    }

    @Override
    public boolean isConnected() {
        // On vérifie si le client existe et si la session
        // (qui est un CompletableFuture) est terminée et ne contient pas d'erreur.
        return client != null && client.getSession().isDone() && !client.getSession().isCompletedExceptionally();
    }

    @Override
    public Object read(Tag tag) {
        System.out.println("Lecture OPC UA pour le tag " + tag.getName() + " (non implementee)");
        // TODO: Implémenter la logique de lecture des noeuds OPC UA.
        // L'adresse sera dans tag.getName() ou un autre champ (ex: 'ns=2;s=MyVariable')
        return null;
    }

    @Override
    public void write(Tag tag, Object value) {
        System.out.println("Ecriture OPC UA pour le tag " + tag.getName() + " (non implementee)");
        // TODO: Implémenter la logique d'écriture
    }
}
