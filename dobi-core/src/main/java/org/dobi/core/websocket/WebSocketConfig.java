    package org.dobi.core.websocket; // Nouveau package après déplacement

    import org.dobi.logging.LogLevelManager;
    import org.springframework.context.annotation.Configuration;
    import org.springframework.messaging.simp.config.MessageBrokerRegistry;
    import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
    import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
    import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

    @Configuration
    @EnableWebSocketMessageBroker
    public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

        private static final String COMPONENT_NAME = "CORE-WEBSOCKET-CONFIG";

        @Override
        public void configureMessageBroker(MessageBrokerRegistry config) {
            config.enableSimpleBroker("/topic");
            config.setApplicationDestinationPrefixes("/app");
            
            LogLevelManager.logInfo(COMPONENT_NAME, "Broker de messages WebSocket configuré. Destinations: /topic, Préfixe application: /app.");
        }

        @Override
        public void registerStompEndpoints(StompEndpointRegistry registry) {
            // Enregistre l'endpoint "/ws-dobi".
            // .withSockJS() est ajouté pour la compatibilité.
            // .setAllowedOrigins("*") est CRUCIAL ici pour CORS sur l'endpoint SockJS.
            // Pour la production, remplacez "*" par les origines spécifiques de votre frontend.
            registry.addEndpoint("/ws-dobi")
                    .setAllowedOrigins(
                        "http://localhost",       // Pour les tests locaux via un serveur web
                        "http://10.242.14.3",     // Pour l'accès direct via l'IP de votre serveur DOBI
                        "null"                    // Pour les fichiers HTML ouverts directement depuis le système de fichiers (origin est 'null')
                    )
                    .withSockJS();
            
            LogLevelManager.logInfo(COMPONENT_NAME, "Endpoint WebSocket STOMP enregistré: /ws-dobi (avec SockJS et CORS configuré).");
        }
    }
    