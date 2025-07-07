package org.dobi.app.service;

import org.dobi.core.ports.AlarmNotifier; // <-- IMPLÉMENTE L'INTERFACE
import org.dobi.dto.ActiveAlarmDto;
import org.dobi.logging.LogLevelManager;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class AlarmService implements AlarmNotifier { // <-- IMPLÉMENTE L'INTERFACE

    private static final String COMPONENT_NAME = "ALARM-SERVICE";
    private final SimpMessagingTemplate messagingTemplate;

    public AlarmService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Diffuse une mise à jour d'alarme à tous les clients abonnés. C'est
     * l'implémentation concrète de la méthode du contrat.
     *
     * @param alarmDto Le DTO de l'alarme à diffuser.
     */
    @Override
    public void notifyAlarmUpdate(ActiveAlarmDto alarmDto) {
        if (alarmDto == null) {
            LogLevelManager.logWarn(COMPONENT_NAME, "Tentative de diffusion d'une alarme null.");
            return;
        }

        final String destination = "/topic/alarms/updates";
        messagingTemplate.convertAndSend(destination, alarmDto);

        LogLevelManager.logInfo(COMPONENT_NAME, "Alarme diffusée sur " + destination + ": " + alarmDto.alarmName());
    }
}
