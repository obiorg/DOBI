package org.dobi.app.service;

import org.dobi.app.controller.AlarmController.AlarmDto; // Nous utiliserons le DTO du contrôleur
import org.dobi.logging.LogLevelManager;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class AlarmService {

    private static final String COMPONENT_NAME = "ALARM-SERVICE";
    private final SimpMessagingTemplate messagingTemplate;

    public AlarmService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Diffuse une mise à jour d'alarme à tous les clients abonnés.
     *
     * @param alarm L'alarme à diffuser.
     */
    public void broadcastAlarm(AlarmDto alarm) {
        if (alarm == null) {
            LogLevelManager.logWarn(COMPONENT_NAME, "Tentative de diffusion d'une alarme null.");
            return;
        }

        // Le topic sur lequel les clients frontend s'abonneront
        String destination = "/topic/alarms/updates";

        messagingTemplate.convertAndSend(destination, alarm);

        LogLevelManager.logInfo(COMPONENT_NAME, "Alarme diffusée sur " + destination + ": " + alarm.message());
    }
}
