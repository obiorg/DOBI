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
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.util.EndpointUtil; // Import ajouté

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class OpcUaDriver implements IDriver {

    private Machine machine;
    private OpcUaClient client;
    private static final int TIMEOUT_SECONDS = 10;

    @Override
    public void configure(Machine machine) {
        this.machine = machine;
    }

    @Override
    public boolean connect() {
        if (machine == null || machine.getAddress() == null) return false;

        try {
            int port = machine.getPort() != null ? machine.getPort() : 4840;
            String discoveryUrl = String.format("opc.tcp://%s:%d", machine.getAddress(), port);
            
            System.out.println("[OPC-UA] Tentative de découverte des endpoints sur: " + discoveryUrl);
            
            List<EndpointDescription> endpoints = DiscoveryClient.getEndpoints(discoveryUrl).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            EndpointDescription originalEndpoint = endpoints.stream()
                .filter(e -> e.getSecurityPolicyUri().equals(SecurityPolicy.None.getUri()))
                .findFirst()
                .orElseThrow(() -> new Exception("Aucun endpoint de sécurité 'None' trouvé."));
            
            // CORRECTION: Forcer l'utilisation de l'adresse IP pour éviter la résolution DNS inversée
            EndpointDescription endpoint = EndpointUtil.updateUrl(originalEndpoint, machine.getAddress(), port);
            System.out.println("[OPC-UA] Endpoint choisi et forcé sur IP: " + endpoint.getEndpointUrl());

            OpcUaClientConfigBuilder cfg = OpcUaClientConfig.builder()
                .setEndpoint(endpoint)
                .setRequestTimeout(org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint(TIMEOUT_SECONDS * 1000));

            getIdentityProvider().ifPresent(cfg::setIdentityProvider);
            
            client = OpcUaClient.create(cfg.build());
            client.connect().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            System.out.println("[OPC-UA] Connexion réussie !");
            
            return true;
        } catch (Exception e) {
            System.err.println("[OPC-UA] Echec de la connexion pour " + machine.getName() + ": " + e.getClass().getSimpleName() + " -> " + e.getMessage());
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
    public void disconnect() { if (client != null) client.disconnect(); }

    @Override
    public boolean isConnected() {
        return client != null && client.getSession().isDone() && !client.getSession().isCompletedExceptionally();
    }

    @Override
    public Object read(Tag tag) { /* TODO */ return null; }

    @Override
    public void write(Tag tag, Object value) { /* TODO */ }
}