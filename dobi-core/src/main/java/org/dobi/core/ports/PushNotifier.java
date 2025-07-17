package org.dobi.core.ports;

import org.dobi.dto.ActiveAlarmDto;

/**
 * Port (au sens de l'architecture hexagonale) pour l'envoi de notifications
 * push. Le moteur d'alarme utilise ce port sans connaître l'implémentation
 * sous-jacente (WebPush, etc.).
 */
public interface PushNotifier {

    /**
     * Envoie une notification à tous les abonnés.
     *
     * @param alarmDto Le DTO de l'alarme à envoyer comme payload.
     */
    void sendNotificationToAll(ActiveAlarmDto alarmDto);
}
