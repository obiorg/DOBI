package org.dobi.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.dobi.dto.ActiveAlarmDto;
import org.dobi.dto.PushSubscriptionDto;
import org.dobi.entities.PushSubscription;
import org.dobi.logging.LogLevelManager;
import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.dobi.core.ports.PushNotifier;

@Service
public class PushNotificationService implements PushNotifier {

    private static final String COMPONENT_NAME = "PUSH-SERVICE";
    private final EntityManagerFactory emf;
    private final PushService pushService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PushNotificationService(EntityManagerFactory emf,
            @Value("${vapid.public.key}") String publicKey,
            @Value("${vapid.private.key}") String privateKey,
            @Value("${vapid.subject}") String subject) throws GeneralSecurityException {
        this.emf = emf;

        // Ajout du provider BouncyCastle nécessaire pour la cryptographie
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        this.pushService = new PushService(publicKey, privateKey, subject);
        LogLevelManager.logInfo(COMPONENT_NAME, "PushNotificationService initialisé avec les clés VAPID.");
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

    /**
     * Envoie une notification à tous les abonnés.
     *
     * @param alarmDto Le DTO de l'alarme à envoyer comme payload.
     */
    @Override
    public void sendNotificationToAll(ActiveAlarmDto alarmDto) {
        EntityManager em = emf.createEntityManager();
        try {
            List<PushSubscription> subscriptions = em.createQuery("SELECT s FROM PushSubscription s", PushSubscription.class).getResultList();
            LogLevelManager.logInfo(COMPONENT_NAME, "Envoi d'une notification à " + subscriptions.size() + " abonné(s).");

            String payload = objectMapper.writeValueAsString(alarmDto);

            for (PushSubscription sub : subscriptions) {
                try {
                    Notification notification = new Notification(sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payload);
                    pushService.send(notification);
                } catch (GeneralSecurityException | IOException | JoseException | ExecutionException | InterruptedException e) {
                    LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de l'envoi de la notification à " + sub.getEndpoint() + ": " + e.getMessage());
                    // Ici, on pourrait ajouter une logique pour supprimer les abonnements invalides.
                }
            }
        } catch (JsonProcessingException e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur de sérialisation du payload de la notification: " + e.getMessage());
        } finally {
            em.close();
        }
    }
}
