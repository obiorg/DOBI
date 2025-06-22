package org.dobi.opcua;

import org.dobi.api.IDriver;
import org.dobi.entities.Machine;
import org.dobi.entities.Tag;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.identity.UsernameProvider;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.util.EndpointUtil;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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
            int port = machine.getPort() != null ? machine.getPort() : 4840;
            String endpointUrl = String.format("opc.tcp://%s:%d", machine.getAddress(), port);
            System.out.println("Tentative de connexion à l'endpoint OPC UA : " + endpointUrl);

            List<EndpointDescription> endpoints = OpcUaClient.getEndpoints(endpointUrl).get();

            EndpointDescription endpoint = endpoints.stream()
                .filter(e -> e.getSecurityPolicyUri().equals(SecurityPolicy.None.getUri()))
                .findFirst()
                .orElseThrow(() -> new Exception("Aucun endpoint de sécurité compatible trouvé."));

            OpcUaClientConfig config = OpcUaClientConfig.builder()
                .setEndpoint(endpoint)
                .setIdentityProvider(getIdentityProvider())
                .build();

            client = OpcUaClient.create(config);
            client.connect().get();
            return true;
        } catch (Exception e) {
            System.err.println("Erreur de connexion OPC UA à " + machine.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private UsernameProvider getIdentityProvider() {
        String username = machine.getMqttUser();
        String password = machine.getMqttPassword();

        if (username != null && !username.trim().isEmpty()) {
            System.out.println("Utilisation de l'authentification avec l'utilisateur: " + username);
            return new UsernameProvider(username, password);
        } else {
            System.out.println("Tentative de connexion anonyme.");
            return new UsernameProvider(); // Connexion anonyme
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
        return client != null && client.getSession().isDone() && !client.getSession().isCompletedExceptionally();
    }

    @Override
    public Object read(Tag tag) {
        System.out.println("Lecture OPC UA pour le tag " + tag.getName() + " (non implementee)");
        return null;
    }

    @Override
    public void write(Tag tag, Object value) {
        System.out.println("Ecriture OPC UA pour le tag " + tag.getName() + " (non implementee)");
    }
}
