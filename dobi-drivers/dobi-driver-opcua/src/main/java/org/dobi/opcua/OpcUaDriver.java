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
        if (machine == null || machine.getAddress() == null || machine.getAddress().trim().isEmpty()) {
            return false;
        }

        try {
            int port = machine.getPort() != null ? machine.getPort() : 4840;
            String endpointUrl = String.format("opc.tcp://%s:%d", machine.getAddress(), port);
            
            System.out.println("[OPC-UA DEBUG] Tentative de découverte des endpoints sur: " + endpointUrl);
            
            // Étape 1: Découvrir les points d'accès (endpoints)
            List<EndpointDescription> endpoints = DiscoveryClient.getEndpoints(endpointUrl).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            System.out.println("[OPC-UA DEBUG] " + endpoints.size() + " endpoint(s) trouvé(s).");
            
            // Affiche tous les endpoints trouvés pour le diagnostic
            for(EndpointDescription e : endpoints) {
                System.out.println("    -> Endpoint trouvé: " + e.getEndpointUrl() + " [Securité: " + e.getSecurityPolicyUri() + "]");
            }

            // Étape 2: Choisir le point d'accès le moins sécurisé (pour les tests)
            EndpointDescription endpoint = endpoints.stream()
                .filter(e -> e.getSecurityPolicyUri().equals(SecurityPolicy.None.getUri()))
                .findFirst()
                .orElseThrow(() -> new Exception("Aucun endpoint de sécurité 'None' trouvé. Le serveur requiert une connexion sécurisée."));
            
            System.out.println("[OPC-UA DEBUG] Endpoint choisi: " + endpoint.getEndpointUrl());

            // Étape 3: Configurer le client
            OpcUaClientConfigBuilder configBuilder = OpcUaClientConfig.builder()
                .setEndpoint(endpoint)
                .setRequestTimeout(uint(TIMEOUT_SECONDS * 1000));

            IdentityProvider identityProvider = getIdentityProvider();
            if (identityProvider != null) {
                configBuilder.setIdentityProvider(identityProvider);
            }

            System.out.println("[OPC-UA DEBUG] Création du client OPC UA...");
            client = OpcUaClient.create(configBuilder.build());
            
            System.out.println("[OPC-UA DEBUG] Connexion en cours...");
            client.connect().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            System.out.println("[OPC-UA DEBUG] Connexion réussie !");
            
            return true;
        } catch (Exception e) {
            System.err.println("[OPC-UA DEBUG] Echec de la connexion pour " + machine.getName() + ": " + e.getMessage());
            e.printStackTrace(); // Affiche la trace complète de l'erreur
            return false;
        }
    }

    private IdentityProvider getIdentityProvider() {
        String username = machine.getMqttUser();
        String password = machine.getMqttPassword();

        if (username != null && !username.trim().isEmpty()) {
            System.out.println("[OPC-UA DEBUG] Utilisation de l'authentification avec l'utilisateur: " + username);
            return new UsernameProvider(username, password);
        } else {
            System.out.println("[OPC-UA DEBUG] Utilisation d'une connexion anonyme.");
            return null;
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
