package org.dobi.app.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.dobi.dto.PushSubscriptionDto;
import org.dobi.entities.PushSubscription;
import org.dobi.logging.LogLevelManager;
import org.springframework.stereotype.Service;

@Service
public class PushNotificationService {

    private static final String COMPONENT_NAME = "PUSH-SERVICE";
    private final EntityManagerFactory emf;

    public PushNotificationService(EntityManagerFactory emf) {
        this.emf = emf;
    }

    public void subscribe(PushSubscriptionDto subscriptionDto) {
        EntityManager em = emf.createEntityManager();
        try {
            // Vérifier si l'abonnement existe déjà
            long count = em.createQuery("SELECT count(s) FROM PushSubscription s WHERE s.endpoint = :endpoint", Long.class)
                    .setParameter("endpoint", subscriptionDto.endpoint())
                    .getSingleResult();

            if (count > 0) {
                LogLevelManager.logInfo(COMPONENT_NAME, "Abonnement push déjà existant pour l'endpoint : " + subscriptionDto.endpoint());
                return;
            }

            em.getTransaction().begin();
            PushSubscription subscription = new PushSubscription();
            subscription.setEndpoint(subscriptionDto.endpoint());
            subscription.setP256dh(subscriptionDto.keys().p256dh());
            subscription.setAuth(subscriptionDto.keys().auth());
            em.persist(subscription);
            em.getTransaction().commit();
            LogLevelManager.logInfo(COMPONENT_NAME, "Nouvel abonnement push enregistré.");

        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de l'enregistrement de l'abonnement push: " + e.getMessage());
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
        } finally {
            em.close();
        }
    }

    // Nous ajouterons la méthode pour envoyer les notifications ici plus tard
}
