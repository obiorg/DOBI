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

public class OpcUaDriver implements IDriver {

    private Machine machine;
    private OpcUaClient client;

    @Override
    public void configure(Machine machine) {
        this.machine = machine;
    }

    @Override
    public boolean connect() {
        if (machine == null) return false;
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
            System.err.println("Erreur connexion OPC UA: " + e.getMessage());
            return false;
        }
    }

    private java.util.Optional<IdentityProvider> getIdentityProvider() {
        // Utilisation des nouveaux champs dédiés
        String username = machine.getOpcuaUser();
        String password = machine.getOpcuaPassword();
        if (username != null && !username.trim().isEmpty()) {
            return java.util.Optional.of(new UsernameProvider(username, password));
        } else {
            return java.util.Optional.empty(); // Connexion anonyme
        }
    }

    @Override
    public Object read(Tag tag) {
        if (!isConnected() || tag.getOpcNamespaceIndex() == null || tag.getOpcIdentifier() == null) {
            return null;
        }
        try {
            NodeId nodeId = new NodeId(tag.getOpcNamespaceIndex(), tag.getOpcIdentifier());
            DataValue dataValue = client.readValue(0, null, nodeId).get();
            if (dataValue != null && dataValue.getValue() != null && dataValue.getValue().isNotNull()) {
                return dataValue.getValue().getValue();
            }
        } catch (Exception e) {
            System.err.println("Erreur de lecture OPC UA pour le tag '" + tag.getName() + "': " + e.getMessage());
        }
        return null;
    }

    @Override
    public void write(Tag tag, Object value) { /* TODO */ }
    @Override
    public void disconnect() { if (client != null) client.disconnect(); }
    @Override
    public boolean isConnected() {
        return client != null && client.getSession().isDone() && !client.getSession().isCompletedExceptionally();
    }
}
