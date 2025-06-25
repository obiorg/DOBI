package org.dobi.opcua;

import org.dobi.api.IDriver;
import org.dobi.entities.Machine;
import org.dobi.entities.Tag;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import java.util.List;
import java.util.Optional;

public class OpcUaDriver implements IDriver {

    private Machine machine;
    private OpcUaClient client;

    @Override
    public void configure(Machine machine) {
        this.machine = machine;
    }

    @Override
    public boolean connect() {
        if (machine == null || machine.getAddress() == null) return false;
        try {
            int port = machine.getPort() != null ? machine.getPort() : 4840;
            String endpointUrl = String.format("opc.tcp://%s:%d", machine.getAddress(), port);

            List<EndpointDescription> endpoints = DiscoveryClient.getEndpoints(endpointUrl).get();
            EndpointDescription endpoint = endpoints.stream()
                .filter(e -> e.getSecurityPolicyUri().equals(SecurityPolicy.None.getUri()))
                .findFirst().orElseThrow(() -> new Exception("Aucun endpoint de sécurité 'None' trouvé."));

            OpcUaClientConfigBuilder cfg = OpcUaClientConfig.builder().setEndpoint(endpoint);
            getIdentityProvider().ifPresent(cfg::setIdentityProvider);

            client = OpcUaClient.create(cfg.build());
            client.connect().get();
            return true;
        } catch (Exception e) {
            System.err.println("Erreur de connexion OPC UA à " + machine.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private Optional<IdentityProvider> getIdentityProvider() {
        String username = machine.getOpcuaUser();
        String password = machine.getOpcuaPassword();
        if (username != null && !username.trim().isEmpty()) {
            return Optional.of(new UsernameProvider(username, password));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Object read(Tag tag) {
        if (!isConnected() || tag.getOpcNamespaceIndex() == null || tag.getOpcIdentifier() == null) {
            return null;
        }
        
        try {
            // Construction du NodeId à partir des informations de la base de données
            NodeId nodeId;
            String identifierType = tag.getOpcIdentifierType() != null ? tag.getOpcIdentifierType().toUpperCase() : "STRING";
            
            if ("INTEGER".equals(identifierType)) {
                nodeId = new NodeId(tag.getOpcNamespaceIndex(), Integer.parseInt(tag.getOpcIdentifier()));
            } else { // Par défaut, on considère que c'est une chaîne de caractères
                nodeId = new NodeId(tag.getOpcNamespaceIndex(), tag.getOpcIdentifier());
            }

            // Lecture de la valeur du noeud
            DataValue dataValue = client.readValue(0.0, null, nodeId).get();

            if (dataValue != null && dataValue.getValue() != null && dataValue.getValue().isNotNull()) {
                return dataValue.getValue().getValue();
            }
        } catch (Exception e) {
            System.err.println("Erreur de lecture OPC UA pour le tag '" + tag.getName() + "': " + e.getMessage());
        }
        return null;
    }

    @Override
    public void write(Tag tag, Object value) {
        System.out.println("Ecriture OPC UA pour le tag " + tag.getName() + " (non implementee)");
    }

    @Override
    public void disconnect() {
        if (client != null) client.disconnect();
    }

    @Override
    public boolean isConnected() {
        return client != null && client.getSession().isDone() && !client.getSession().isCompletedExceptionally();
    }
}
