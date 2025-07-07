package org.dobi.core.websocket;

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
        LogLevelManager.logInfo(COMPONENT_NAME, "Broker de messages WebSocket configuré.");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // CORRIGÉ : On restaure la configuration des origines autorisées
        // spécifiquement pour l'endpoint SockJS. C'est crucial pour la poignée de main.
        registry.addEndpoint("/ws-dobi")
                .setAllowedOrigins("http://localhost:3000","http://10.242.14.3:3000", "http://192.168.242.32:3000")
                .withSockJS();
        
        LogLevelManager.logInfo(COMPONENT_NAME, "Endpoint WebSocket STOMP enregistré: /ws-dobi.");
    }
}
