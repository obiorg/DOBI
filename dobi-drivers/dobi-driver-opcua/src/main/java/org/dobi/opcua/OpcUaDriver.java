package org.dobi.opcua;

import org.dobi.api.IDriver;
import org.dobi.entities.Machine;
import org.dobi.entities.Tag;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.identity.UsernameProvider;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.util.EndpointUtil;

import java.util.Arrays;
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
        if (machine == null || machine.getAddress() == null || machine.getAddress().trim().isEmpty()) {
            System.err.println("Adresse IP non configurée pour la machine OPC UA.");
            return false;
        }

        try {
            int port = machine.getPort() != null ? machine.getPort() : 4840;
            String endpointUrl = String.format("opc.tcp://%s:%d", machine.getAddress(), port);
            System.out.println("Tentative de connexion à l'endpoint OPC UA : " + endpointUrl);

            // 1. Découvrir les points d'accès (endpoints) du serveur
            List<EndpointDescription> endpoints = OpcUaClient.getEndpoints(endpointUrl).get();

            // 2. Choisir un endpoint. On privilégie la sécurité, mais on accepte "None" si c'est la seule option.
            EndpointDescription endpoint = endpoints.stream()
                .filter(e -> e.getSecurityPolicyUri().equals(SecurityPolicy.None.getUri()))
                .findFirst()
                .orElseThrow(() -> new Exception("Aucun endpoint de sécurité compatible trouvé."));
            
            // 3. Création du client avec la configuration de l'endpoint
            client = OpcUaClient.create(endpoint);

            // 4. Gestion de l'identité (authentification)
            String username = machine.getMqttUser(); // On réutilise le champ mqtt_user
            String password = machine.getMqttPassword(); // On réutilise le champ mqtt_password

            if (username != null && !username.trim().isEmpty()) {
                System.out.println("Utilisation de l'authentification avec l'utilisateur: " + username);
                client.setIdentityProvider(new UsernameProvider(username, password));
            } else {
                System.out.println("Tentative de connexion anonyme.");
            }
            
            // 5. Connexion
            client.connect().get();
            return true;
        } catch (Exception e) {
            System.err.println("Erreur de connexion OPC UA à " + machine.getName() + ": " + e.getMessage());
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
        return client != null && client.getSession().isDone() && !client.getSession().isCompletedExceptionally();
    }

    @Override
    public Object read(Tag tag) {
        // La logique de lecture sera implémentée dans la prochaine étape
        System.out.println("Lecture OPC UA pour le tag " + tag.getName() + " (non implementee)");
        return null;
    }

    @Override
    public void write(Tag tag, Object value) {
        System.out.println("Ecriture OPC UA pour le tag " + tag.getName() + " (non implementee)");
    }
}
