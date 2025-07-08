package org.dobi.services.alarm;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import org.dobi.core.ports.AlarmNotifier; // <-- DÉPEND DE L'INTERFACE
import org.dobi.dto.ActiveAlarmDto;
import org.dobi.dto.TagData;
import org.dobi.entities.*;
import org.dobi.logging.LogLevelManager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service central pour la logique de détection et de gestion des alarmes. Ce
 * service est piloté par la configuration de la base de données.
 */
@Service
public class AlarmEngineService {

    private static final String COMPONENT_NAME = "ALARM-ENGINE";
    private final EntityManagerFactory emf;
    private final AlarmNotifier alarmNotifier; // <-- DÉPEND DE L'INTERFACE

    // Le constructeur demande l'interface, pas l'implémentation
    public AlarmEngineService(EntityManagerFactory emf, AlarmNotifier alarmNotifier) {
        this.emf = emf;
        this.alarmNotifier = alarmNotifier;
        LogLevelManager.logInfo(COMPONENT_NAME, "AlarmEngineService initialisé.");
    }

    /**
     * Méthode principale appelée pour chaque nouvelle donnée de tag.
     *
     * @param tagData Les données du tag reçues de Kafka.
     */
    public void checkForAlarms(TagData tagData) {
        // Tenter de convertir la valeur reçue en nombre pour la comparaison
        Float currentValue = convertToFloat(tagData.value());
        if (currentValue == null) {
            // Ne peut pas traiter une alarme de limite pour une valeur non numérique
            LogLevelManager.logWarn(COMPONENT_NAME, "Ne peut pas traiter une alarme de limite pour une valeur non numérique : id - " + tagData.tagId() + " pour tag " + tagData.tagName());
            return;
        }

        EntityManager em = emf.createEntityManager();
        try {
            // 1. Récupérer toutes les limites d'alarme définies pour ce tag
            List<PersStandardLimit> limits = findLimitsForTag(em, tagData.tagId());

            for (PersStandardLimit limit : limits) {
                // 2. Évaluer chaque limite
                evaluateLimit(em, limit, tagData, currentValue);
            }

        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de la vérification des alarmes pour le tag " + tagData.tagId() + ": " + e.getMessage());
        } finally {
            if (em != null && em.isOpen()) {
                em.close();
            }
        }
    }

    /**
     * Évalue une limite spécifique par rapport à la valeur actuelle du tag.
     */
    private void evaluateLimit(EntityManager em, PersStandardLimit limit,
            TagData tagData, float currentValue) {
        boolean conditionMet = compareValue(currentValue, limit.getValue(),
                limit.getComparator().getSymbol());
        LogLevelManager.logInfo(COMPONENT_NAME, "Un seuil de limite franchie pour le tag "
                + tagData.tagName() + " valeur actuelle (" + currentValue
                + " est " + limit.getComparator().getSymbol() + " à " + limit.getValue());
        ActiveAlarm existingAlarm = findActiveAlarmForLimit(em, limit.getId());

        em.getTransaction().begin();

        if (conditionMet && existingAlarm == null) {
            // CAS 1: Nouvelle alarme -> La condition est remplie et il n'y a pas d'alarme active.
            createNewActiveAlarm(em, limit, tagData, currentValue);

        } else if (!conditionMet && existingAlarm != null) {
            // CAS 2: Alarme résolue -> La condition n'est plus remplie mais une alarme était active.
            resolveActiveAlarm(em, existingAlarm);
        }
        // CAS 3 (conditionMet && existingAlarm != null) -> L'alarme est toujours active, on ne fait rien.
        // CAS 4 (!conditionMet && existingAlarm == null) -> Tout est normal, on ne fait rien.

        em.getTransaction().commit();
    }

    /**
     * Crée et persiste une nouvelle alarme active, puis notifie le frontend.
     */
    private void createNewActiveAlarm(EntityManager em, PersStandardLimit limit, TagData tagData, float currentValue) {
        LogLevelManager.logWarn(COMPONENT_NAME, "NOUVELLE ALARME : " + limit.getName() + " pour tag " + tagData.tagName());

        ActiveAlarm newAlarm = new ActiveAlarm();
        newAlarm.setAlarmDefinition(limit.getAlarmToTrigger());
        newAlarm.setTag(limit.getTag());
        newAlarm.setCompany(limit.getCompany());
        newAlarm.setTriggerTime(LocalDateTime.now());
        newAlarm.setTriggerValue(currentValue);
        newAlarm.setAcknowledged(false); // Toujours non acquittée à la création

        em.persist(newAlarm);

        /// Notification via l'interface
        alarmNotifier.notifyAlarmUpdate(convertToDto(newAlarm));
    }

    /**
     * Met à jour une alarme active comme étant résolue.
     */
    private void resolveActiveAlarm(EntityManager em, ActiveAlarm alarm) {
        LogLevelManager.logInfo(COMPONENT_NAME, "ALARME RESOLUE : " + alarm.getAlarmDefinition().getName());
        alarm.setResolvedTime(LocalDateTime.now());
        em.merge(alarm);

        /// Notification via l'interface
        alarmNotifier.notifyAlarmUpdate(convertToDto(alarm));
    }

    // Méthode pour convertir l'entité en DTO pour la notification
    private ActiveAlarmDto convertToDto(ActiveAlarm alarm) {
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
                render.getColor(),
                render.getBackground(),
                render.getBlink()
        );
    }

    // --- Méthodes de recherche en base de données ---
    private List<PersStandardLimit> findLimitsForTag(EntityManager em, long tagId) {
        TypedQuery<PersStandardLimit> query = em.createQuery(
                "SELECT l FROM PersStandardLimit l "
                + "JOIN FETCH l.comparator "
                + "JOIN FETCH l.alarmToTrigger "
                + "WHERE l.tag.id = :tagId", PersStandardLimit.class);
        query.setParameter("tagId", tagId);
        return query.getResultList();
    }

    private ActiveAlarm findActiveAlarmForLimit(EntityManager em, long limitId) {
        try {
            TypedQuery<ActiveAlarm> query = em.createQuery(
                    "SELECT a FROM ActiveAlarm a "
                    + "WHERE a.alarmDefinition.id = :limitId AND a.resolvedTime IS NULL", ActiveAlarm.class);
            query.setParameter("limitId", limitId);
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null; // Aucune alarme active trouvée pour cette limite
        }
    }

    // --- Méthodes utilitaires ---
    private Float convertToFloat(Object value) {
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        try {
            return Float.parseFloat(value.toString());
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    private boolean compareValue(float currentValue, float limitValue, String comparator) {
        return switch (comparator) {
            case ">" ->
                currentValue > limitValue;
            case ">=" ->
                currentValue >= limitValue;
            case "<" ->
                currentValue < limitValue;
            case "<=" ->
                currentValue <= limitValue;
            case "=" ->
                currentValue == limitValue;
            case "!=" ->
                currentValue != limitValue;
            default ->
                false;
        };
    }
}
