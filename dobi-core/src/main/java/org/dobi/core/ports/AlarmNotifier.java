package org.dobi.core.ports;

import org.dobi.dto.ActiveAlarmDto; // Nous aurons besoin de ce DTO

/**
 * Port (au sens de l'architecture hexagonale) pour notifier les systèmes
 * externes des changements d'état des alarmes.
 *
 * Le moteur d'alarme (domaine métier) utilise ce port sans connaître
 * l'implémentation sous-jacente (WebSocket, email, etc.).
 */
public interface AlarmNotifier {

    /**
     * Notifie la création ou la mise à jour d'une alarme.
     *
     * @param alarmDto Le DTO représentant l'état actuel de l'alarme.
     */
    void notifyAlarmUpdate(ActiveAlarmDto alarmDto);

}
