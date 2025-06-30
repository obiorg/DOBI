package org.dobi.opcua;

import org.dobi.api.IDriver;
import org.dobi.entities.Machine;
import org.dobi.entities.Tag;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.X509IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.*;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.*;
import org.eclipse.milo.opcua.stack.core.util.EndpointUtil;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
// Ajouter l'import en haut du fichier
import org.dobi.logging.LogLevelManager;
import org.dobi.logging.LogLevelManager.LogLevel;

public class OpcUaDriver implements IDriver {

    private static final String DRIVER_NAME = "OPC-UA";
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private static final int RECONNECT_DELAY_MS = 5000;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;

    private Machine machine;
    private OpcUaClient client;
    private UaSubscription subscription;
    private final Map<String, UaMonitoredItem> monitoredItems = new ConcurrentHashMap<>();
    private final Map<String, Consumer<Object>> subscriptionCallbacks = new ConcurrentHashMap<>();
    private volatile boolean shouldReconnect = true;
    private volatile int reconnectAttempts = 0;

    @Override
    public void configure(Machine machine) {
        this.machine = machine;
        LogLevelManager.logInfo(DRIVER_NAME, "Configuration du driver pour la machine: " + machine.getName());
    }

// Modifier la méthode connect()
    @Override
    public boolean connect() {
        if (machine == null || machine.getAddress() == null) {
            LogLevelManager.logError(DRIVER_NAME, "Configuration machine invalide");
            return false;
        }

        try {
            shouldReconnect = true;
            reconnectAttempts = 0;
            return establishConnection();
        } catch (Exception e) {
            LogLevelManager.logError(DRIVER_NAME, "Erreur lors de la connexion initiale: " + e.getMessage());
            return false;
        }
    }

    // Modifier establishConnection()
    private boolean establishConnection() {
        try {
            // Construction de l'URL endpoint
            int port = machine.getPort() != null ? machine.getPort() : 4840;
            String endpointUrl = String.format("opc.tcp://%s:%d", machine.getAddress(), port);
            LogLevelManager.logInfo(DRIVER_NAME, "Tentative de connexion à: " + endpointUrl);

            // Découverte des endpoints
            List<EndpointDescription> endpoints = DiscoveryClient.getEndpoints(endpointUrl)
                    .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (endpoints.isEmpty()) {
                throw new Exception("Aucun endpoint découvert");
            }

            // Sélection de l'endpoint selon la politique de sécurité
            EndpointDescription selectedEndpoint = selectEndpoint(endpoints);
            if (selectedEndpoint == null) {
                throw new Exception("Aucun endpoint compatible trouvé");
            }

            // Suite de la connexion...
            EndpointDescription endpoint = EndpointUtil.updateUrl(selectedEndpoint, machine.getAddress(), port);

            // Configuration du client
            OpcUaClientConfigBuilder configBuilder = OpcUaClientConfig.builder()
                    .setEndpoint(endpoint)
                    .setRequestTimeout(UInteger.valueOf(DEFAULT_TIMEOUT_SECONDS * 1000))
                    .setSessionTimeout(UInteger.valueOf(60000));

            // Configuration de l'identité
            Optional<IdentityProvider> identityProvider = getIdentityProvider();
            if (identityProvider.isPresent()) {
                configBuilder.setIdentityProvider(identityProvider.get());
            }

            // Configuration des certificats si nécessaire
            if (requiresCertificate(selectedEndpoint.getSecurityPolicyUri())) {
                configureCertificates(configBuilder);
            }

            // Création et connexion du client
            client = OpcUaClient.create(configBuilder.build());
            client.connect().get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            LogLevelManager.logInfo(DRIVER_NAME, "Connexion établie avec " + machine.getName());
            reconnectAttempts = 0;

            // Mise en place de la surveillance de connexion
            setupConnectionMonitoring();

            return true;

        } catch (Exception e) {
            LogLevelManager.logError(DRIVER_NAME, "Erreur de connexion à " + machine.getName() + ": " + e.getMessage());
            return false;
        }
    }

// Modifier selectEndpoint()
    private EndpointDescription selectEndpoint(List<EndpointDescription> endpoints) {
        String securityPolicy = machine.getOpcuaSecurityPolicy();

        if (securityPolicy == null || securityPolicy.trim().isEmpty()) {
            securityPolicy = "None";
        }

        LogLevelManager.logDebug(DRIVER_NAME, "Sélection endpoint avec politique: " + securityPolicy);

        final SecurityPolicy targetPolicy;
        try {
            switch (securityPolicy.toUpperCase()) {
                case "NONE":
                    targetPolicy = SecurityPolicy.None;
                    break;
                case "BASIC256":
                    targetPolicy = SecurityPolicy.Basic256;
                    break;
                case "BASIC256SHA256":
                    targetPolicy = SecurityPolicy.Basic256Sha256;
                    break;
                case "AES128_SHA256_RSAOAEP":
                    targetPolicy = SecurityPolicy.Aes128_Sha256_RsaOaep;
                    break;
                case "AES256_SHA256_RSAPSS":
                    targetPolicy = SecurityPolicy.Aes256_Sha256_RsaPss;
                    break;
                default:
                    LogLevelManager.logError(DRIVER_NAME, "Politique de sécurité non reconnue: " + securityPolicy + ", utilisation de 'None'");
                    targetPolicy = SecurityPolicy.None;
            }
        } catch (Exception e) {
            LogLevelManager.logError(DRIVER_NAME, "Erreur lors de la sélection de la politique de sécurité: " + e.getMessage());
            final SecurityPolicy finalPolicy = SecurityPolicy.None;

            Optional<EndpointDescription> endpoint = endpoints.stream()
                    .filter(a -> a.getSecurityPolicyUri().equals(finalPolicy.getUri()))
                    .findFirst();

            if (endpoint.isPresent()) {
                LogLevelManager.logInfo(DRIVER_NAME, "Endpoint sélectionné avec politique par défaut: " + finalPolicy.getUri());
                return endpoint.get();
            }

            LogLevelManager.logError(DRIVER_NAME, "Aucun endpoint compatible trouvé, utilisation du premier endpoint disponible");
            return endpoints.get(0);
        }

        // Recherche de l'endpoint avec la politique de sécurité souhaitée
        Optional<EndpointDescription> endpoint = endpoints.stream()
                .filter(e -> e.getSecurityPolicyUri().equals(targetPolicy.getUri()))
                .findFirst();

        if (endpoint.isPresent()) {
            LogLevelManager.logInfo(DRIVER_NAME, "Endpoint sélectionné avec politique: " + targetPolicy.getUri());
            return endpoint.get();
        }

        LogLevelManager.logError(DRIVER_NAME, "Politique de sécurité '" + securityPolicy + "' non trouvée, utilisation du premier endpoint disponible");
        return endpoints.get(0);
    }

    private boolean requiresCertificate(String securityPolicyUri) {
        return !SecurityPolicy.None.getUri().equals(securityPolicyUri);
    }

// Modifier configureCertificates()
    private void configureCertificates(OpcUaClientConfigBuilder configBuilder) throws Exception {
        String keystorePath = machine.getOpcuaKeystorePath();
        String keystorePassword = machine.getOpcuaKeystorePassword();

        if (keystorePath == null || keystorePath.trim().isEmpty()) {
            throw new Exception("Chemin du keystore requis pour la politique de sécurité configurée");
        }

        LogLevelManager.logDebug(DRIVER_NAME, "Configuration des certificats depuis: " + keystorePath);

        Path keyStorePath = Paths.get(keystorePath);
        if (!Files.exists(keyStorePath)) {
            throw new Exception("Fichier keystore introuvable: " + keystorePath);
        }

        // Chargement du keystore
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(keyStorePath.toFile())) {
            keyStore.load(fis, keystorePassword != null ? keystorePassword.toCharArray() : null);
        }

        // Récupération du certificat et de la clé privée
        String alias = keyStore.aliases().nextElement();
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias,
                keystorePassword != null ? keystorePassword.toCharArray() : null);

        KeyPair keyPair = new KeyPair(certificate.getPublicKey(), privateKey);

        configBuilder.setCertificate(certificate).setKeyPair(keyPair);
        LogLevelManager.logInfo(DRIVER_NAME, "Certificats configurés avec succès");
    }

    private Optional<IdentityProvider> getIdentityProvider() {
        String username = machine.getOpcuaUser();
        String password = machine.getOpcuaPassword();

        if (username != null && !username.trim().isEmpty()) {
            LogLevelManager.logInfo(DRIVER_NAME, "Utilisation de l'authentification par nom d'utilisateur: " + username);
            return Optional.of(new UsernameProvider(username, password));
        } else {
            LogLevelManager.logInfo(DRIVER_NAME, "Utilisation de l'authentification anonyme");
            return Optional.of(new AnonymousProvider());
        }
    }

    private void setupConnectionMonitoring() {
        if (client != null) {
            client.getSession().whenComplete((session, throwable) -> {
                if (throwable != null && shouldReconnect) {
                    System.err.println("[OPC-UA] Session fermée de manière inattendue: " + throwable.getMessage());
                    // Utilisation d'un CompletableFuture pour éviter le problème de variable finale
                    CompletableFuture.runAsync(this::attemptReconnection);
                }
            });
        }
    }

    private void attemptReconnection() {
        if (!shouldReconnect || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            LogLevelManager.logError(DRIVER_NAME, "Abandon de la reconnexion après " + reconnectAttempts + " tentatives");
            return;
        }

        reconnectAttempts++;
        final int currentAttempt = reconnectAttempts;

        CompletableFuture.runAsync(() -> {
            try {
                LogLevelManager.logInfo(DRIVER_NAME, "Tentative de reconnexion " + currentAttempt + "/" + MAX_RECONNECT_ATTEMPTS);

                Thread.sleep(RECONNECT_DELAY_MS);

                if (client != null) {
                    try {
                        client.disconnect().get(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        LogLevelManager.logDebug(DRIVER_NAME, "Erreur lors de la déconnexion (ignorée): " + e.getMessage());
                    }
                }

                if (establishConnection()) {
                    LogLevelManager.logInfo(DRIVER_NAME, "Reconnexion réussie");
                    recreateSubscriptions();
                } else {
                    attemptReconnection();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LogLevelManager.logError(DRIVER_NAME, "Erreur lors de la reconnexion: " + e.getMessage());
                attemptReconnection();
            }
        });
    }

    private void recreateSubscriptions() {
        // Recréer les subscriptions après reconnexion
        if (!monitoredItems.isEmpty()) {
            try {
                createSubscriptionIfNeeded();
                // Re-créer tous les items surveillés
                Map<String, Consumer<Object>> callbacks = new HashMap<>(subscriptionCallbacks);
                monitoredItems.clear();
                subscriptionCallbacks.clear();

                for (Map.Entry<String, Consumer<Object>> entry : callbacks.entrySet()) {
                    // Ici vous devriez récupérer le tag correspondant et relancer la subscription
                    // Cette partie dépend de votre architecture pour retrouver les tags
                    System.out.println("[OPC-UA] Recréation de la subscription pour: " + entry.getKey());
                }
            } catch (Exception e) {
                System.err.println("[OPC-UA] Erreur lors de la recréation des subscriptions: " + e.getMessage());
            }
        }
    }

    @Override
    public Object read(Tag tag) {
        if (!isConnected()) {
            LogLevelManager.logError(DRIVER_NAME, "Client non connecté pour la lecture du tag: " + tag.getName());
            return null;
        }

        if (tag.getOpcNamespaceIndex() == null || tag.getOpcIdentifier() == null) {
            LogLevelManager.logError(DRIVER_NAME, "Configuration OPC UA incomplète pour le tag: " + tag.getName());
            return null;
        }

        try {
            NodeId nodeId = createNodeId(tag);
            LogLevelManager.logDebug(DRIVER_NAME, "Lecture du tag '" + tag.getName() + "' avec NodeId: " + nodeId);

            DataValue dataValue = client.readValue(0.0, TimestampsToReturn.Both, nodeId)
                    .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            LogLevelManager.logTrace(DRIVER_NAME, "Réponse pour '" + tag.getName() + "': " + dataValue);

            if (dataValue.getStatusCode().isGood()) {
                if (dataValue.getValue() != null && dataValue.getValue().isNotNull()) {
                    Object rawValue = dataValue.getValue().getValue();
                    Object convertedValue = convertValue(rawValue, tag);
                    LogLevelManager.logTrace(DRIVER_NAME, "Valeur convertie pour '" + tag.getName() + "': " + convertedValue);
                    return convertedValue;
                } else {
                    LogLevelManager.logError(DRIVER_NAME, "Valeur nulle pour le tag: " + tag.getName());
                }
            } else {
                LogLevelManager.logError(DRIVER_NAME, "Erreur de lecture - Status: " + dataValue.getStatusCode() + " pour le tag: " + tag.getName());
            }

        } catch (Exception e) {
            LogLevelManager.logError(DRIVER_NAME, "Exception lors de la lecture du tag '" + tag.getName() + "': " + e.getMessage());
        }

        return null;
    }

    @Override
    public void write(Tag tag, Object value) {
        if (!isConnected()) {
            LogLevelManager.logError(DRIVER_NAME, "Client non connecté pour l'écriture du tag: " + tag.getName());
            return;
        }

        if (tag.getOpcNamespaceIndex() == null || tag.getOpcIdentifier() == null) {
            LogLevelManager.logError(DRIVER_NAME, "Configuration OPC UA incomplète pour le tag: " + tag.getName());
            return;
        }

        try {
            NodeId nodeId = createNodeId(tag);
            Variant variant = convertToVariant(value, tag);

            LogLevelManager.logDebug(DRIVER_NAME, "Écriture du tag '" + tag.getName() + "' avec NodeId: " + nodeId + ", valeur: " + value);

            DataValue dataValue = new DataValue(variant, null, null);
            WriteValue writeValue = new WriteValue(nodeId, AttributeId.Value.uid(), null, dataValue);
            List<WriteValue> writeValues = Collections.singletonList(writeValue);

            WriteResponse writeResponse = client.write(writeValues)
                    .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            StatusCode[] statusCodes = writeResponse.getResults();
            if (statusCodes != null && statusCodes.length > 0) {
                StatusCode statusCode = statusCodes[0];
                if (statusCode.isGood()) {
                    LogLevelManager.logInfo(DRIVER_NAME, "Écriture réussie pour le tag: " + tag.getName());
                } else {
                    LogLevelManager.logError(DRIVER_NAME, "Erreur d'écriture - Status: " + statusCode + " pour le tag: " + tag.getName());
                }
            }

        } catch (Exception e) {
            LogLevelManager.logError(DRIVER_NAME, "Exception lors de l'écriture du tag '" + tag.getName() + "': " + e.getMessage());
        }
    }

    private NodeId createNodeId(Tag tag) {
        String identifierType = tag.getOpcIdentifierType() != null
                ? tag.getOpcIdentifierType().toUpperCase() : "STRING";

        String identifier = tag.getOpcIdentifier();
        int namespaceIndex = tag.getOpcNamespaceIndex();

        LogLevelManager.logTrace(DRIVER_NAME, "Création NodeId - Type: " + identifierType
                + ", Namespace: " + namespaceIndex + ", Identifier: " + identifier);

        try {
            switch (identifierType) {
                case "INTEGER":
                case "NUMERIC":
                    return new NodeId(namespaceIndex, Integer.parseInt(identifier));

                case "STRING":
                    return new NodeId(namespaceIndex, identifier);

                case "GUID":
                    return new NodeId(namespaceIndex, UUID.fromString(identifier));

                case "OPAQUE":
                    return new NodeId(namespaceIndex, ByteString.of(identifier.getBytes()));

                default:
                    LogLevelManager.logError(DRIVER_NAME, "Type d'identifiant non supporté: " + identifierType + ", utilisation de STRING");
                    return new NodeId(namespaceIndex, identifier);
            }
        } catch (Exception e) {
            LogLevelManager.logError(DRIVER_NAME, "Erreur lors de la création du NodeId: " + e.getMessage());
            LogLevelManager.logDebug(DRIVER_NAME, "Fallback vers NodeId STRING");
            return new NodeId(namespaceIndex, identifier);
        }
    }

    private Object convertValue(Object rawValue, Tag tag) {
        if (rawValue == null) {
            return null;
        }

        try {
            // Conversion selon le type attendu du tag
            if (tag.getType() != null) {
                String typeName = tag.getType().toString().toUpperCase();

                switch (typeName) {
                    case "BOOLEAN":
                    case "BOOL":
                        return convertToBoolean(rawValue);
                    case "INTEGER":
                    case "INT":
                        return convertToInteger(rawValue);
                    case "FLOAT":
                    case "REAL":
                    case "DOUBLE":
                        return convertToFloat(rawValue);
                    case "STRING":
                        return convertToString(rawValue);
                    case "DATETIME":
                        return convertToDateTime(rawValue);
                    default:
                        return rawValue;
                }
            }

            return rawValue;

        } catch (Exception e) {
            System.err.println("[OPC-UA] Erreur de conversion de valeur pour le tag " + tag.getName() + ": " + e.getMessage());
            return rawValue;
        }
    }

    private Boolean convertToBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return null;
    }

    private Integer convertToInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            return Integer.parseInt((String) value);
        }
        return null;
    }

    private Float convertToFloat(Object value) {
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        if (value instanceof String) {
            return Float.parseFloat((String) value);
        }
        return null;
    }

    private String convertToString(Object value) {
        return value != null ? value.toString() : null;
    }

    private LocalDateTime convertToDateTime(Object value) {
        if (value instanceof DateTime) {
            DateTime dateTime = (DateTime) value;
            return LocalDateTime.ofInstant(dateTime.getJavaInstant(), ZoneId.systemDefault());
        }
        if (value instanceof java.util.Date) {
            return LocalDateTime.ofInstant(((java.util.Date) value).toInstant(), ZoneId.systemDefault());
        }
        return null;
    }

    private Variant convertToVariant(Object value, Tag tag) {
        if (value == null) {
            return new Variant(null);
        }

        try {
            if (tag.getType() != null) {
                String typeName = tag.getType().toString().toUpperCase();

                switch (typeName) {
                    case "BOOLEAN":
                    case "BOOL":
                        return new Variant(convertToBoolean(value));
                    case "INTEGER":
                    case "INT":
                        return new Variant(convertToInteger(value));
                    case "FLOAT":
                    case "REAL":
                        return new Variant(convertToFloat(value));
                    case "DOUBLE":
                        return new Variant(((Number) value).doubleValue());
                    case "STRING":
                        return new Variant(convertToString(value));
                    case "DATETIME":
                        if (value instanceof LocalDateTime) {
                            return new Variant(new DateTime(java.util.Date.from(
                                    ((LocalDateTime) value).atZone(ZoneId.systemDefault()).toInstant())));
                        }
                        break;
                }
            }

            return new Variant(value);

        } catch (Exception e) {
            System.err.println("[OPC-UA] Erreur de conversion vers Variant pour le tag " + tag.getName() + ": " + e.getMessage());
            return new Variant(value);
        }
    }

    /**
     * Méthode pour créer une subscription temps réel sur un tag
     */
    public void subscribeToTag(Tag tag, Consumer<Object> callback) {
        if (!isConnected()) {
            LogLevelManager.logError(DRIVER_NAME, "Client non connecté pour la subscription du tag: " + tag.getName());
            return;
        }

        try {
            createSubscriptionIfNeeded();

            NodeId nodeId = createNodeId(tag);
            ReadValueId readValueId = new ReadValueId(nodeId, AttributeId.Value.uid(), null, null);

            MonitoringParameters parameters = new MonitoringParameters(
                    UInteger.valueOf(tag.hashCode()),
                    1000.0,
                    null,
                    UInteger.valueOf(10),
                    true
            );

            MonitoredItemCreateRequest request = new MonitoredItemCreateRequest(
                    readValueId, MonitoringMode.Reporting, parameters);

            List<UaMonitoredItem> items = subscription.createMonitoredItems(
                    TimestampsToReturn.Both,
                    Collections.singletonList(request)
            ).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!items.isEmpty()) {
                UaMonitoredItem item = items.get(0);
                monitoredItems.put(tag.getName(), item);
                subscriptionCallbacks.put(tag.getName(), callback);

                item.setValueConsumer((monitoredItem, dataValue) -> {
                    if (dataValue.getStatusCode().isGood() && dataValue.getValue() != null) {
                        Object convertedValue = convertValue(dataValue.getValue().getValue(), tag);
                        LogLevelManager.logTrace(DRIVER_NAME, "Subscription - Nouvelle valeur pour " + tag.getName() + ": " + convertedValue);
                        callback.accept(convertedValue);
                    }
                });

                LogLevelManager.logInfo(DRIVER_NAME, "Subscription créée pour le tag: " + tag.getName());
            }

        } catch (Exception e) {
            LogLevelManager.logError(DRIVER_NAME, "Erreur lors de la création de la subscription pour le tag " + tag.getName() + ": " + e.getMessage());
        }
    }

    private void createSubscriptionIfNeeded() throws Exception {
        if (subscription == null) {
            subscription = client.getSubscriptionManager()
                    .createSubscription(1000.0) // publishing interval
                    .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            System.out.println("[OPC-UA] Subscription créée avec ID: " + subscription.getSubscriptionId());
        }
    }

    /**
     * Méthode pour arrêter la subscription d'un tag
     */
    public void unsubscribeFromTag(String tagName) {
        UaMonitoredItem item = monitoredItems.remove(tagName);
        if (item != null) {
            try {
                subscription.deleteMonitoredItems(Collections.singletonList(item))
                        .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                subscriptionCallbacks.remove(tagName);
                System.out.println("[OPC-UA SUBSCRIPTION] Subscription supprimée pour le tag: " + tagName);
            } catch (Exception e) {
                System.err.println("[OPC-UA SUBSCRIPTION] Erreur lors de la suppression de la subscription pour le tag " + tagName + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void disconnect() {
        shouldReconnect = false;

        try {
            if (!monitoredItems.isEmpty()) {
                LogLevelManager.logInfo(DRIVER_NAME, "Nettoyage des subscriptions...");
                monitoredItems.clear();
                subscriptionCallbacks.clear();
            }

            if (subscription != null) {
                try {
                    client.getSubscriptionManager().deleteSubscription(subscription.getSubscriptionId())
                            .get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    LogLevelManager.logError(DRIVER_NAME, "Erreur lors de la suppression de la subscription: " + e.getMessage());
                }
                subscription = null;
            }

            if (client != null) {
                LogLevelManager.logInfo(DRIVER_NAME, "Déconnexion du client...");
                client.disconnect().get(5, TimeUnit.SECONDS);
                client = null;
            }

            LogLevelManager.logInfo(DRIVER_NAME, "Déconnexion complète de " + (machine != null ? machine.getName() : "machine inconnue"));

        } catch (Exception e) {
            LogLevelManager.logError(DRIVER_NAME, "Erreur lors de la déconnexion: " + e.getMessage());
        }
    }

    @Override
    public boolean isConnected() {
        if (client == null) {
            return false;
        }

        try {
            return client.getSession().isDone()
                    && !client.getSession().isCompletedExceptionally()
                    && client.getSession().getNow(null) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Méthode pour tester différentes variantes d'un NodeId
     */
    public String testNodeIdVariants(Tag tag) {
        StringBuilder result = new StringBuilder();
        result.append("=== Test NodeId Variants pour: ").append(tag.getName()).append(" ===\n");

        if (!isConnected()) {
            result.append("ERREUR: Non connecté\n");
            return result.toString();
        }

        String baseIdentifier = tag.getOpcIdentifier();
        int namespace = tag.getOpcNamespaceIndex();

        // Variantes à tester
        String[] variants = {
            baseIdentifier, // Original
            baseIdentifier.replace("[", ".").replace("]", ""), // Remplacer crochets par points
            baseIdentifier.replace(".", "_"), // Remplacer points par underscores  
            baseIdentifier.replace("[", "_").replace("]", "_"), // Remplacer crochets par underscores
            baseIdentifier.toUpperCase(), // En majuscules
            baseIdentifier.toLowerCase(), // En minuscules
        };

        for (int i = 0; i < variants.length; i++) {
            String variant = variants[i];
            try {
                NodeId testNodeId = new NodeId(namespace, variant);
                result.append("Test ").append(i + 1).append(": ").append(testNodeId).append("\n");

                DataValue dataValue = client.readValue(0.0, TimestampsToReturn.Both, testNodeId)
                        .get(5, TimeUnit.SECONDS);

                if (dataValue.getStatusCode().isGood()) {
                    result.append("  ✅ SUCCÈS: ").append(dataValue.getValue().getValue()).append("\n");
                    break; // Arrêter dès qu'on trouve une variante qui marche
                } else {
                    result.append("  ❌ Échec: ").append(dataValue.getStatusCode()).append("\n");
                }

            } catch (Exception e) {
                result.append("  ❌ Exception: ").append(e.getMessage()).append("\n");
            }
        }

        return result.toString();
    }

    /**
     * Méthode pour explorer les noeuds enfants d'un namespace
     */
    public String browseNamespace(int namespaceIndex) {
        StringBuilder result = new StringBuilder();
        result.append("=== Exploration Namespace ").append(namespaceIndex).append(" ===\n");

        if (!isConnected()) {
            result.append("ERREUR: Non connecté\n");
            return result.toString();
        }

        try {
            // Test de différents namespaces
            for (int ns = 0; ns <= 6; ns++) {
                result.append("\n--- Test Namespace ").append(ns).append(" ---\n");

                // Test avec des nœuds racine standards
                String[] rootCandidates = {
                    "ENERGY_1",
                    "Application",
                    "GVL",
                    "Objects",
                    "Root",
                    "Server",
                    "",
                    "1", "2", "3", "4", "5" // Identifiants numériques simples
                };

                for (String candidate : rootCandidates) {
                    try {
                        NodeId testNode = new NodeId(ns, candidate);

                        DataValue testRead = client.readValue(0.0, TimestampsToReturn.Both, testNode)
                                .get(2, TimeUnit.SECONDS);

                        if (testRead.getStatusCode().isGood()) {
                            result.append("  ✅ TROUVÉ NS").append(ns).append(": ").append(candidate)
                                    .append(" = ").append(testRead.getValue().getValue()).append("\n");
                        } else if (!testRead.getStatusCode().toString().contains("Bad_NodeIdUnknown")) {
                            // Afficher autres erreurs (pas NodeIdUnknown)
                            result.append("  ⚠️ NS").append(ns).append(": ").append(candidate)
                                    .append(" → ").append(testRead.getStatusCode()).append("\n");
                        }

                    } catch (Exception e) {
                        // Ignorer les exceptions pour éviter le spam
                    }
                }

                // Test avec des identifiants numériques
                for (int i = 1; i <= 10; i++) {
                    try {
                        NodeId numericNode = new NodeId(ns, i);

                        DataValue testRead = client.readValue(0.0, TimestampsToReturn.Both, numericNode)
                                .get(2, TimeUnit.SECONDS);

                        if (testRead.getStatusCode().isGood()) {
                            result.append("  ✅ TROUVÉ NS").append(ns).append(":i=").append(i)
                                    .append(" = ").append(testRead.getValue().getValue()).append("\n");
                            if (i > 3) {
                                break; // Limiter l'affichage
                            }
                        }

                    } catch (Exception e) {
                        // Ignorer
                    }
                }
            }

            // Test spécial pour les nœuds système OPC UA
            result.append("\n--- Nœuds Système OPC UA ---\n");
            try {
                // Nœud Objects (normalement ns=0;i=85)
                NodeId objectsNode = new NodeId(0, 85);
                DataValue objectsRead = client.readValue(0.0, TimestampsToReturn.Both, objectsNode)
                        .get(3, TimeUnit.SECONDS);

                if (objectsRead.getStatusCode().isGood()) {
                    result.append("✅ Objects Node trouvé: ").append(objectsRead.getValue().getValue()).append("\n");
                } else {
                    result.append("❌ Objects Node: ").append(objectsRead.getStatusCode()).append("\n");
                }

                // Nœud Server (normalement ns=0;i=2253)
                NodeId serverNode = new NodeId(0, 2253);
                DataValue serverRead = client.readValue(0.0, TimestampsToReturn.Both, serverNode)
                        .get(3, TimeUnit.SECONDS);

                if (serverRead.getStatusCode().isGood()) {
                    result.append("✅ Server Node trouvé: ").append(serverRead.getValue().getValue()).append("\n");
                } else {
                    result.append("❌ Server Node: ").append(serverRead.getStatusCode()).append("\n");
                }

            } catch (Exception e) {
                result.append("Erreur test nœuds système: ").append(e.getMessage()).append("\n");
            }

        } catch (Exception e) {
            result.append("Erreur générale: ").append(e.getMessage()).append("\n");
        }

        return result.toString();
    }

    /**
     * Méthode pour tester la structure exacte depuis UaExpert
     */
    public String testUaExpertStructure() {
        StringBuilder result = new StringBuilder();
        result.append("=== Test Structure UaExpert ===\n");

        if (!isConnected()) {
            result.append("ERREUR: Non connecté\n");
            return result.toString();
        }

        // Test des chemins suggérés par la structure UaExpert
        String[] testPaths = {
            // Chemins courts pour remonter la hiérarchie
            "ENERGY_1",
            "Application",
            "GVL",
            "tfos",
            "stdset",
            "frequency",
            "value",
            // Chemins partiels
            "ENERGY_1.Application",
            "Application.GVL",
            "GVL.tfos",
            // Essai avec des identifiants numériques dans ns=4
            "1", "2", "3", "4", "5", "10", "100", "1000"
        };

        for (String path : testPaths) {
            try {
                NodeId testNode = new NodeId(4, path);

                DataValue testRead = client.readValue(0.0, TimestampsToReturn.Both, testNode)
                        .get(2, TimeUnit.SECONDS);

                if (testRead.getStatusCode().isGood()) {
                    result.append("✅ TROUVÉ: ns=4;s=").append(path)
                            .append(" = ").append(testRead.getValue().getValue()).append("\n");
                } else if (!testRead.getStatusCode().toString().contains("Bad_NodeIdUnknown")) {
                    result.append("⚠️ ns=4;s=").append(path)
                            .append(" → ").append(testRead.getStatusCode()).append("\n");
                }

            } catch (Exception e) {
                // Ignorer pour éviter le spam
            }
        }

        return result.toString();
    }

    /**
     * Test manuel d'un NodeId spécifique
     */
    public String testSpecificNodeId(String nodeIdString) {
        StringBuilder result = new StringBuilder();
        result.append("=== Test NodeId Spécifique ===\n");
        result.append("NodeId: ").append(nodeIdString).append("\n");

        if (!isConnected()) {
            result.append("ERREUR: Non connecté\n");
            return result.toString();
        }

        try {
            // Test avec l'identifiant exact de UaExpert
            NodeId testNode = new NodeId(4, nodeIdString);

            DataValue testRead = client.readValue(0.0, TimestampsToReturn.Both, testNode)
                    .get(5, TimeUnit.SECONDS);

            result.append("Status: ").append(testRead.getStatusCode()).append("\n");

            if (testRead.getStatusCode().isGood()) {
                result.append("✅ SUCCÈS ! Valeur: ").append(testRead.getValue().getValue()).append("\n");
                result.append("Type: ").append(testRead.getValue().getValue() != null
                        ? testRead.getValue().getValue().getClass().getSimpleName() : "null").append("\n");
            } else {
                result.append("❌ Échec: ").append(testRead.getStatusCode()).append("\n");
            }

        } catch (Exception e) {
            result.append("❌ Exception: ").append(e.getMessage()).append("\n");
        }

        return result.toString();
    }

    public String getDiagnosticInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== Diagnostic OPC UA Driver ===\n");
        info.append("Machine: ").append(machine != null ? machine.getName() : "Non configurée").append("\n");
        info.append("Adresse: ").append(machine != null ? machine.getAddress() : "N/A").append("\n");
        info.append("Port: ").append(machine != null ? machine.getPort() : "N/A").append("\n");
        info.append("Politique de sécurité: ").append(machine != null ? machine.getOpcuaSecurityPolicy() : "N/A").append("\n");
        info.append("Connecté: ").append(isConnected()).append("\n");
        info.append("Tentatives de reconnexion: ").append(reconnectAttempts).append("/").append(MAX_RECONNECT_ATTEMPTS).append("\n");
        info.append("Subscriptions actives: ").append(monitoredItems.size()).append("\n");

        if (client != null) {
            try {
                info.append("Endpoint URL: ").append(client.getConfig().getEndpoint().getEndpointUrl()).append("\n");
                info.append("Session timeout: ").append(client.getConfig().getSessionTimeout()).append("ms\n");
            } catch (Exception e) {
                info.append("Erreur lors de la récupération des infos client: ").append(e.getMessage()).append("\n");
            }
        }

        return info.toString();
    }
}
