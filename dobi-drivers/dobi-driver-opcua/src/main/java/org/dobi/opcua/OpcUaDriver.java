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
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

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
        // Log de débogage ajouté AVANT toute opération réseau
        System.out.println("[OPC-UA DEBUG pour " + machine.getName() + "] Entrée dans la méthode connect(). Le thread n'est pas bloqué.");

        if (machine == null || machine.getAddress() == null) {
            System.err.println("[OPC-UA DEBUG pour " + machine.getName() + "] Erreur: Configuration invalide (machine ou adresse null).");
            return false;
        }

        try {
            int port = machine.getPort() != null ? machine.getPort() : 4840;
            String endpointUrl = String.format("opc.tcp://%s:%d", machine.getAddress(), port);
            
            System.out.println("[OPC-UA DEBUG pour " + machine.getName() + "] Tentative de découverte des endpoints sur: " + endpointUrl);
            
            List<EndpointDescription> endpoints = DiscoveryClient.getEndpoints(endpointUrl).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            System.out.println("[OPC-UA DEBUG pour " + machine.getName() + "] " + endpoints.size() + " endpoint(s) trouvé(s).");
            
            for(EndpointDescription e : endpoints) {
                System.out.println("    -> Endpoint trouvé: " + e.getEndpointUrl() + " [Securité: " + e.getSecurityPolicyUri() + "]");
            }

            EndpointDescription endpoint = endpoints.stream()
                .filter(e -> e.getSecurityPolicyUri().equals(SecurityPolicy.None.getUri()))
                .findFirst()
                .orElseThrow(() -> new Exception("Aucun endpoint de sécurité 'None' trouvé. Le serveur requiert une connexion sécurisée."));
            
            System.out.println("[OPC-UA DEBUG pour " + machine.getName() + "] Endpoint choisi: " + endpoint.getEndpointUrl());

            OpcUaClientConfigBuilder configBuilder = OpcUaClientConfig.builder()
                .setEndpoint(endpoint)
                .setRequestTimeout(uint(TIMEOUT_SECONDS * 1000));

            getIdentityProvider().ifPresent(configBuilder::setIdentityProvider);

            System.out.println("[OPC-UA DEBUG pour " + machine.getName() + "] Création du client OPC UA...");
            client = OpcUaClient.create(configBuilder.build());
            
            System.out.println("[OPC-UA DEBUG pour " + machine.getName() + "] Connexion en cours...");
            client.connect().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            System.out.println("[OPC-UA DEBUG pour " + machine.getName() + "] Connexion réussie !");
            
            return true;
        } catch (Exception e) {
            System.err.println("[OPC-UA DEBUG] Echec de la connexion pour " + machine.getName() + ": " + e.getClass().getSimpleName() + " -> " + e.getMessage());
            return false;
        }
    }

    private java.util.Optional<IdentityProvider> getIdentityProvider() {
        String username = machine.getOpcuaUser();
        String password = machine.getOpcuaPassword();
        if (username != null && !username.trim().isEmpty()) {
            System.out.println("[OPC-UA DEBUG pour " + machine.getName() + "] Utilisation de l'authentification avec l'utilisateur: " + username);
            return java.util.Optional.of(new UsernameProvider(username, password));
        } else {
            System.out.println("[OPC-UA DEBUG pour " + machine.getName() + "] Utilisation d'une connexion anonyme.");
            return java.util.Optional.empty();
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
