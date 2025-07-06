package org.dobi.core.websocket; // Nouveau package après déplacement

import org.dobi.dto.TagData;
import org.dobi.logging.LogLevelManager;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller; // AJOUTÉ : Import de l'annotation @Controller

/**
 * Contrôleur WebSocket pour la diffusion des données de tags en temps réel. Il
 * permet aux clients de s'abonner à des topics pour recevoir les mises à jour.
 */
@Controller // AJOUTÉ : Annotation pour que Spring détecte cette classe comme un bean
public class TagWebSocketController {

    private static final String COMPONENT_NAME = "CORE-TAG-WEBSOCKET"; // Mis à jour après déplacement
    private final SimpMessagingTemplate messagingTemplate;

    public TagWebSocketController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        LogLevelManager.logInfo(COMPONENT_NAME, "TagWebSocketController initialisé.");
    }

    @MessageMapping("/hello")
    @SendTo("/topic/greetings")
    public String greeting(String message) {
        LogLevelManager.logDebug(COMPONENT_NAME, "Message 'hello' reçu via WebSocket: " + message);
        return "Hello, " + message + "!";
    }

    public void sendTagUpdate(TagData tagData) {
        messagingTemplate.convertAndSend("/topic/tags/update", tagData);
        LogLevelManager.logTrace(COMPONENT_NAME, "Mise à jour tag envoyée via WebSocket: " + tagData.tagName() + " = " + tagData.value());
    }

    public void sendSpecificTagUpdate(TagData tagData) {
        String destination = "/topic/tags/update/" + tagData.tagId();
        messagingTemplate.convertAndSend(destination, tagData);
        LogLevelManager.logTrace(COMPONENT_NAME, "Mise à jour tag spécifique envoyée via WebSocket à " + destination + ": " + tagData.tagName() + " = " + tagData.value());
    }
}
