package org.dobi.core.websocket;

import org.dobi.dto.TagData; // Assurez-vous que TagData est accessible (via dobi-core)
import org.dobi.logging.LogLevelManager;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Contrôleur WebSocket pour la diffusion des données de tags en temps réel. Il
 * permet aux clients de s'abonner à des topics pour recevoir les mises à jour.
 */
@Controller
public class TagWebSocketController {

    private static final String COMPONENT_NAME = "CORE-TAG-WEBSOCKET";
    private final SimpMessagingTemplate messagingTemplate; // Utilisé pour envoyer des messages aux clients

    public TagWebSocketController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        LogLevelManager.logInfo(COMPONENT_NAME, "TagWebSocketController initialisé.");
    }

    /**
     * Exemple de gestion de message entrant (pas directement utilisé pour la
     * diffusion des tags, mais montre comment un client pourrait envoyer un
     * message au serveur).
     *
     * @param message Le message reçu du client.
     * @return Le message qui sera renvoyé au topic de réponse.
     */
    @MessageMapping("/hello") // Les clients envoient à /app/hello
    @SendTo("/topic/greetings") // La réponse est envoyée à /topic/greetings
    public String greeting(String message) {
        LogLevelManager.logDebug(COMPONENT_NAME, "Message 'hello' reçu via WebSocket: " + message);
        return "Hello, " + message + "!";
    }

    /**
     * Méthode pour envoyer les mises à jour des données de tags à tous les
     * clients abonnés. Cette méthode sera appelée par le KafkaConsumerService.
     *
     * @param tagData Les données du tag à diffuser.
     */
    public void sendTagUpdate(TagData tagData) {
        // Envoie le message au topic "/topic/tags/update".
        // Tous les clients abonnés à ce topic recevront le message.
        messagingTemplate.convertAndSend("/topic/tags/update", tagData);
        LogLevelManager.logTrace(COMPONENT_NAME, "Mise à jour tag envoyée via WebSocket: " + tagData.tagName() + " = " + tagData.value());
    }

    /**
     * Méthode pour envoyer les mises à jour des données d'un tag spécifique à
     * tous les clients abonnés. Les clients pourront s'abonner à
     * /topic/tags/update/{tagId}
     *
     * @param tagData Les données du tag à diffuser.
     */
    public void sendSpecificTagUpdate(TagData tagData) {
        String destination = "/topic/tags/update/" + tagData.tagId();
        messagingTemplate.convertAndSend(destination, tagData);
        LogLevelManager.logTrace(COMPONENT_NAME, "Mise à jour tag spécifique envoyée via WebSocket à " + destination + ": " + tagData.tagName() + " = " + tagData.value());
    }
}

