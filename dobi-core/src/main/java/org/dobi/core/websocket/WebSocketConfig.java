package org.dobi.core.websocket;

import org.dobi.logging.LogLevelManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker // Active la gestion des messages WebSocket via un broker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String COMPONENT_NAME = "CORE-WEBSOCKET-CONFIG";

    /**
     * Configure le broker de messages. Un broker est responsable de router les
     * messages d'un client à l'autre.
     *
     * @param config Le registre du broker de messages.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Active un broker de messages en mémoire simple.
        // Les messages dont la destination commence par "/topic" seront routés vers ce broker.
        config.enableSimpleBroker("/topic");

        // Définit le préfixe pour les messages destinés aux méthodes annotées avec @MessageMapping.
        // Les clients enverront des messages à des destinations comme "/app/hello".
        config.setApplicationDestinationPrefixes("/app");

        LogLevelManager.logInfo(COMPONENT_NAME, "Broker de messages WebSocket configuré. Destinations: /topic, Préfixe application: /app.");
    }

    /**
     * Enregistre les endpoints STOMP. Les endpoints sont les URLs auxquelles
     * les clients WebSocket se connecteront.
     *
     * @param registry Le registre des endpoints STOMP.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Enregistre l'endpoint "/ws-dobi".
        // Les clients se connecteront à ws://localhost:8080/ws-dobi.
        // .withSockJS() est ajouté pour la compatibilité avec les navigateurs plus anciens ou les environnements qui ne supportent pas nativement les WebSockets.
        registry.addEndpoint("/ws-dobi").withSockJS();

        // Vous pouvez ajouter d'autres endpoints si nécessaire, par exemple sans SockJS
        // registry.addEndpoint("/ws-dobi-native");
        LogLevelManager.logInfo(COMPONENT_NAME, "Endpoint WebSocket STOMP enregistré: /ws-dobi (avec SockJS).");
    }
}

