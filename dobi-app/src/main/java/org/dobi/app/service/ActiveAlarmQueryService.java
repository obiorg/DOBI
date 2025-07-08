package org.dobi.app.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.TypedQuery;
import org.dobi.core.ports.AlarmNotifier;
import org.dobi.dto.ActiveAlarmDto;
import org.dobi.entities.ActiveAlarm;
import org.dobi.entities.AlarmRender;
import org.dobi.logging.LogLevelManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service dédié aux opérations de lecture et d'écriture sur les alarmes actives
 * pour les contrôleurs de l'API.
 */
@Service
public class ActiveAlarmQueryService {

    private static final String COMPONENT_NAME = "ALARM-QUERY-SERVICE";
    private final EntityManagerFactory emf;
    private final AlarmNotifier alarmNotifier;

    public ActiveAlarmQueryService(EntityManagerFactory emf, AlarmNotifier alarmNotifier) {
        this.emf = emf;
        this.alarmNotifier = alarmNotifier;
    }

    /**
     * Récupère toutes les alarmes actuellement actives (non résolues).
     *
     * @return Une liste de DTOs représentant les alarmes actives.
     */
    public List<ActiveAlarmDto> getActiveAlarms() {
        EntityManager em = emf.createEntityManager();
        try {
            TypedQuery<ActiveAlarm> query = em.createQuery(
                    "SELECT a FROM ActiveAlarm a "
                    + "JOIN FETCH a.alarmDefinition ad "
                    + "JOIN FETCH ad.alarmClass ac "
                    + "JOIN FETCH ac.render "
                    + "JOIN FETCH a.tag "
                    + "WHERE a.resolvedTime IS NULL "
                    + "ORDER BY a.triggerTime DESC", ActiveAlarm.class);

            List<ActiveAlarm> alarms = query.getResultList();
            return alarms.stream().map(this::convertToDto).collect(Collectors.toList());
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de la récupération des alarmes actives: " + e.getMessage());
            return Collections.emptyList();
        } finally {
            em.close();
        }
    }

    /**
     * Acquitte une alarme active spécifique.
     *
     * @param activeAlarmId L'ID de l'alarme active à acquitter.
     * @return true si l'acquittement a réussi, false sinon.
     */
    @Transactional
    public boolean acknowledgeAlarm(Long activeAlarmId) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            ActiveAlarm alarm = em.find(ActiveAlarm.class, activeAlarmId);
            if (alarm == null || alarm.isAcknowledged()) {
                LogLevelManager.logWarn(COMPONENT_NAME, "Tentative d'acquittement d'une alarme inexistante ou déjà acquittée: " + activeAlarmId);
                em.getTransaction().rollback();
                return false;
            }

            alarm.setAcknowledged(true);
            alarm.setAckTime(LocalDateTime.now());
            // Pour l'instant, on met "system" mais on pourra y mettre un vrai nom d'utilisateur plus tard
            alarm.setAckBy("system");
            em.merge(alarm);
            em.getTransaction().commit();

            LogLevelManager.logInfo(COMPONENT_NAME, "Alarme acquittée: " + activeAlarmId);

            // Notifier le frontend du changement d'état
            alarmNotifier.notifyAlarmUpdate(convertToDto(alarm));
            return true;

        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de l'acquittement de l'alarme " + activeAlarmId + ": " + e.getMessage());
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            return false;
        } finally {
            em.close();
        }
    }

    /**
     * Convertit une entité ActiveAlarm en son DTO pour le transport.
     */
    private ActiveAlarmDto convertToDto(ActiveAlarm alarm) {
        if (alarm == null) {
            return null;
        }

        AlarmRender render = alarm.getAlarmDefinition().getAlarmClass().getRender();
        return new ActiveAlarmDto(
                alarm.getId(),
                alarm.getAlarmDefinition().getName(),
                alarm.getAlarmDefinition().getDescription(),
                alarm.getTag().getName(),
                alarm.getTriggerValue(),
                alarm.getTriggerTime(),
                alarm.isAcknowledged(),
                alarm.getAckTime(),
                alarm.getResolvedTime(),
                alarm.getAlarmDefinition().getAlarmClass().getClassName(),
                render != null ? render.getColor() : "128;128;128", // Gris par défaut
                render != null ? render.getBackground() : "50;50;50",
                render != null && render.getBlink() != null ? render.getBlink() : false
        );
    }

    /**
     * Acquitte toutes les alarmes actives non encore acquittées.
     *
     * @return Le nombre d'alarmes qui ont été acquittées.
     */
    @Transactional
    public int acknowledgeAllAlarms() {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();

            // 1. Récupérer toutes les alarmes non acquittées pour les notifier après
            TypedQuery<ActiveAlarm> query = em.createQuery(
                    "SELECT a FROM ActiveAlarm a WHERE a.acknowledged = false AND a.resolvedTime IS NULL", ActiveAlarm.class);
            List<ActiveAlarm> alarmsToAck = query.getResultList();

            if (alarmsToAck.isEmpty()) {
                em.getTransaction().rollback();
                return 0;
            }

            // 2. Exécuter une requête de mise à jour en masse (plus performant)
            int updatedCount = em.createQuery(
                    "UPDATE ActiveAlarm a SET a.acknowledged = true, a.ackTime = :now, a.ackBy = 'system_bulk_ack' "
                    + "WHERE a.acknowledged = false AND a.resolvedTime IS NULL")
                    .setParameter("now", LocalDateTime.now())
                    .executeUpdate();

            em.getTransaction().commit();

            // 3. Notifier le frontend pour chaque alarme mise à jour
            alarmsToAck.forEach(alarm -> {
                // On met à jour l'état localement avant de l'envoyer
                alarm.setAcknowledged(true);
                alarmNotifier.notifyAlarmUpdate(convertToDto(alarm));
            });

            LogLevelManager.logInfo(COMPONENT_NAME, updatedCount + " alarme(s) ont été acquittées en masse.");
            return updatedCount;

        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de l'acquittement de masse des alarmes: " + e.getMessage());
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            return 0;
        } finally {
            em.close();
        }
    }
}
