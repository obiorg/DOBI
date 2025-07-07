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
        // MODIFIÉ : La ligne .setAllowedOrigins(...) a été supprimée.
        // La configuration CORS est maintenant gérée par SecurityConfig.
        registry.addEndpoint("/ws-dobi").withSockJS();

        LogLevelManager.logInfo(COMPONENT_NAME, "Endpoint WebSocket STOMP enregistré: /ws-dobi.");
    }
}
