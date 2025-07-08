package org.dobi.services.alarm;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import org.dobi.core.ports.AlarmNotifier;
import org.dobi.dto.ActiveAlarmDto;
import org.dobi.dto.TagData;
import org.dobi.entities.*;
import org.dobi.logging.LogLevelManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service central pour la logique de détection et de gestion des alarmes.
 * Ce service est piloté par la configuration de la base de données.
 */
@Service
public class AlarmEngineService {

    private static final String COMPONENT_NAME = "ALARM-ENGINE";
    private final EntityManagerFactory emf;
    private final AlarmNotifier alarmNotifier;

    public AlarmEngineService(EntityManagerFactory emf, AlarmNotifier alarmNotifier) {
        this.emf = emf;
        this.alarmNotifier = alarmNotifier;
        LogLevelManager.logInfo(COMPONENT_NAME, "AlarmEngineService initialisé.");
    }

    /**
     * Méthode principale appelée pour chaque nouvelle donnée de tag.
     * @param tagData Les données du tag reçues de Kafka.
     */
    public void checkForAlarms(TagData tagData) {
        Float currentValue = convertToFloat(tagData.value());
        if (currentValue == null) {
            return;
        }

        EntityManager em = emf.createEntityManager();
        try {
            List<PersStandardLimit> limits = findLimitsForTag(em, tagData.tagId());
            for (PersStandardLimit limit : limits) {
                evaluateLimit(em, limit, tagData, currentValue);
            }
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de la vérification des alarmes pour le tag " + tagData.tagId() + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (em != null && em.isOpen()) {
                em.close();
            }
        }
    }

    /**
     * Évalue une limite spécifique par rapport à la valeur actuelle du tag.
     * Gère la création ou la résolution d'une alarme.
     */
    @Transactional
    private void evaluateLimit(EntityManager em, PersStandardLimit limit, TagData tagData, float currentValue) {
        boolean conditionMet = compareValue(currentValue, limit.getValue(), limit.getComparator().getSymbol());
        
        // CORRECTION : On cherche une alarme existante en utilisant l'ID du tag et l'ID de la définition de l'alarme.
        ActiveAlarm existingAlarm = findActiveAlarm(em, limit.getTag().getId(), limit.getAlarmToTrigger().getId());

        if (conditionMet && existingAlarm == null) {
            // CAS 1: NOUVELLE ALARME -> La condition est remplie et il n'y a pas d'alarme active pour ce couple tag/alarme.
            createNewActiveAlarm(em, limit, tagData, currentValue);

        } else if (!conditionMet && existingAlarm != null) {
            // CAS 2: ALARME RÉSOLUE -> La condition n'est plus remplie mais une alarme était active.
            resolveActiveAlarm(em, existingAlarm);
        } else if (conditionMet && existingAlarm != null) {
            // CAS 3: ALARME TOUJOURS ACTIVE -> La condition est toujours remplie, aucune action nécessaire.
            LogLevelManager.logTrace(COMPONENT_NAME, "Alarme " + limit.getName() + " toujours active pour le tag " + tagData.tagName() + ". Aucune action.");
        }
        // CAS 4: (!conditionMet && existingAlarm == null) -> Tout est normal, aucune action.
    }

    private void createNewActiveAlarm(EntityManager em, PersStandardLimit limit, TagData tagData, float currentValue) {
        LogLevelManager.logWarn(COMPONENT_NAME, "NOUVELLE ALARME : " + limit.getName() + " pour tag " + tagData.tagName() + " (valeur=" + currentValue + ", limite=" + limit.getValue() + ")");

        em.getTransaction().begin();
        ActiveAlarm newAlarm = new ActiveAlarm();
        newAlarm.setAlarmDefinition(limit.getAlarmToTrigger());
        newAlarm.setTag(limit.getTag());
        newAlarm.setCompany(limit.getCompany());
        newAlarm.setTriggerTime(LocalDateTime.now());
        newAlarm.setTriggerValue(currentValue);
        newAlarm.setAcknowledged(false);

        em.persist(newAlarm);
        em.getTransaction().commit();

        alarmNotifier.notifyAlarmUpdate(convertToDto(newAlarm));
    }

    private void resolveActiveAlarm(EntityManager em, ActiveAlarm alarm) {
        LogLevelManager.logInfo(COMPONENT_NAME, "ALARME RESOLUE : " + alarm.getAlarmDefinition().getName() + " pour le tag " + alarm.getTag().getName());

        em.getTransaction().begin();
        alarm.setResolvedTime(LocalDateTime.now());
        em.merge(alarm);
        em.getTransaction().commit();

        alarmNotifier.notifyAlarmUpdate(convertToDto(alarm));
    }

    private List<PersStandardLimit> findLimitsForTag(EntityManager em, long tagId) {
        TypedQuery<PersStandardLimit> query = em.createQuery(
                "SELECT l FROM PersStandardLimit l JOIN FETCH l.comparator JOIN FETCH l.alarmToTrigger WHERE l.tag.id = :tagId", PersStandardLimit.class);
        query.setParameter("tagId", tagId);
        return query.getResultList();
    }

    /**
     * CORRIGÉ : Utilise getResultList() pour éviter NonUniqueResultException.
     * Prend le premier résultat s'il y en a plusieurs, ce qui est une situation
     * de récupération après un bug.
     */
    private ActiveAlarm findActiveAlarm(EntityManager em, long tagId, long alarmDefinitionId) {
        TypedQuery<ActiveAlarm> query = em.createQuery(
                "SELECT a FROM ActiveAlarm a WHERE a.tag.id = :tagId AND a.alarmDefinition.id = :alarmDefinitionId AND a.resolvedTime IS NULL", ActiveAlarm.class);
        query.setParameter("tagId", tagId);
        query.setParameter("alarmDefinitionId", alarmDefinitionId);

        List<ActiveAlarm> results = query.getResultList();
        if (results.isEmpty()) {
            return null;
        }
        if (results.size() > 1) {
            LogLevelManager.logWarn(COMPONENT_NAME, "Plusieurs alarmes actives (" + results.size() + ") trouvées pour le tag ID " + tagId + " et la définition d'alarme ID " + alarmDefinitionId + ". Seule la première sera considérée.");
        }
        return results.get(0); // Retourne la première alarme trouvée
    }

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
            case ">" -> currentValue > limitValue;
            case ">=" -> currentValue >= limitValue;
            case "<" -> currentValue < limitValue;
            case "<=" -> currentValue <= limitValue;
            case "=" -> currentValue == limitValue;
            case "!=" -> currentValue != limitValue;
            default -> false;
        };
    }

    private ActiveAlarmDto convertToDto(ActiveAlarm alarm) {
        if (alarm == null) return null;
        
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
            render != null ? render.getColor() : "128;128;128",
            render != null ? render.getBackground() : "50;50;50",
            render != null && render.getBlink() != null ? render.getBlink() : false
        );
    }
}
