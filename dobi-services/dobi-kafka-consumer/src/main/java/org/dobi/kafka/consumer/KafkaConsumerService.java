package org.dobi.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NoResultException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.dobi.dto.TagData;
import org.dobi.entities.PersStandard;
import org.dobi.entities.Tag;
import org.dobi.entities.Persistence;
import org.dobi.influxdb.InfluxDBWriterService; // Ajouté

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Properties;

import org.dobi.logging.LogLevelManager;
import org.dobi.logging.LogLevelManager.LogLevel;

public class KafkaConsumerService implements Runnable {

    private static final String COMPONENT_NAME = "KAFKA-CONSUMER";

    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityManagerFactory emf;
    private volatile boolean running = true;

    private final String bootstrapServers; // Déclaration unique
    private final InfluxDBWriterService influxDBWriterService;

    public KafkaConsumerService(String bootstrapServers, String groupId, String topic, EntityManagerFactory emf, InfluxDBWriterService influxDBWriterService) {
        this.emf = emf;
        this.bootstrapServers = bootstrapServers;
        this.influxDBWriterService = influxDBWriterService;

        LogLevelManager.logInfo(COMPONENT_NAME, "Initialisation du consommateur Kafka");
        LogLevelManager.logDebug(COMPONENT_NAME, "Configuration - Servers: " + bootstrapServers
                + ", GroupID: " + groupId + ", Topic: " + topic);

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000");

        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(Collections.singletonList(topic));
        LogLevelManager.logInfo(COMPONENT_NAME, "Consommateur Kafka initialisé et abonné au topic '" + topic + "'");
    }

    @Override
    public void run() {
        LogLevelManager.logInfo(COMPONENT_NAME, "Démarrage de la boucle de consommation Kafka");

        int emptyPollCount = 0;
        int totalMessagesProcessed = 0;

        while (running) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

                if (!records.isEmpty()) {
                    emptyPollCount = 0; // Reset du compteur
                    LogLevelManager.logDebug(COMPONENT_NAME, records.count() + " message(s) reçu(s) de Kafka");

                    int successCount = 0;
                    int errorCount = 0;

                    for (ConsumerRecord<String, String> record : records) {
                        try {
                            LogLevelManager.logTrace(COMPONENT_NAME, "Traitement message - Key: " + record.key()
                                    + ", Partition: " + record.partition() + ", Offset: " + record.offset());

                            TagData tagData = objectMapper.readValue(record.value(), TagData.class);
                            processTagData(tagData);
                            successCount++;
                            totalMessagesProcessed++;

                        } catch (Exception e) {
                            errorCount++;
                            LogLevelManager.logError(COMPONENT_NAME, "Erreur de désérialisation du message Kafka "
                                    + "(Key: " + record.key() + "): " + e.getMessage());
                            LogLevelManager.logTrace(COMPONENT_NAME, "Contenu du message en erreur: " + record.value());
                        }
                    }

                    if (successCount > 0) {
                        LogLevelManager.logDebug(COMPONENT_NAME, "Batch traité: " + successCount + " succès, "
                                + errorCount + " erreurs (Total traité: " + totalMessagesProcessed + ")");
                    }
                } else {
                    emptyPollCount++;

                    // Log périodique pour indiquer que le service est actif
                    if (emptyPollCount % 60 == 0) { // Toutes les 60 secondes
                        LogLevelManager.logTrace(COMPONENT_NAME, "Consommateur actif - En attente de messages "
                                + "(Total traité: " + totalMessagesProcessed + ")");
                    }
                }

            } catch (TimeoutException te) {
                LogLevelManager.logError(COMPONENT_NAME, "Timeout de connexion au serveur Kafka ("
                        + bootstrapServers + "). Vérifiez l'adresse, la configuration des listeners et le pare-feu");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                    LogLevelManager.logInfo(COMPONENT_NAME, "Thread interrompu pendant l'attente après timeout");
                    running = false;
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                LogLevelManager.logError(COMPONENT_NAME, "Erreur dans la boucle du consommateur Kafka: " + e.getMessage());
                LogLevelManager.logTrace(COMPONENT_NAME, "Stacktrace de l'erreur: " + e.getClass().getSimpleName());

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                    LogLevelManager.logInfo(COMPONENT_NAME, "Thread interrompu pendant l'attente après erreur");
                    running = false;
                    Thread.currentThread().interrupt();
                }
            }
        }

        try {
            consumer.close();
            LogLevelManager.logInfo(COMPONENT_NAME, "Consommateur Kafka arrêté (Total messages traités: "
                    + totalMessagesProcessed + ")");
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de la fermeture du consommateur: " + e.getMessage());
        }
    }

    private void processTagData(TagData data) {
        EntityManager em = emf.createEntityManager();

        LogLevelManager.logTrace(COMPONENT_NAME, "Traitement TagData - ID: " + data.tagId()
                + ", Nom: " + data.tagName() + ", Valeur: " + data.value());

        try {
            em.getTransaction().begin();

            Tag tag = em.find(Tag.class, data.tagId());
            if (tag != null) {
                LogLevelManager.logTrace(COMPONENT_NAME, "Tag trouvé en base: " + tag.getName()
                        + " (ID: " + tag.getId() + ")");

                // Mise à jour de la valeur du tag
                updateTagValue(tag, data.value());
                tag.setvStamp(LocalDateTime.now());
                em.merge(tag);

                LogLevelManager.logTrace(COMPONENT_NAME, "Tag mis à jour: " + tag.getName() + " = " + data.value());

                // === LOGIQUE DE PERSISTANCE CONDITIONNELLE VERS SQL SERVER ===
                if (tag.getPersistenceEnable() != null && tag.getPersistenceEnable()) {
                    try {
                        // Récupérer l'entrée de persistance pour ce tag
                        Persistence persistenceConfig = em.createQuery(
                                "SELECT p FROM Persistence p WHERE p.tag.id = :tagId", Persistence.class)
                                .setParameter("tagId", tag.getId())
                                .getSingleResult();

                        // Vérifier la méthode et l'activation de la persistance
                        if (persistenceConfig.getMethod() != null && persistenceConfig.getMethod() == 1
                                && persistenceConfig.getActivate() != null && persistenceConfig.getActivate()) {

                            // Création de l'entrée d'historique
                            PersStandard history = new PersStandard();
                            history.setTag(data.tagId());

                            if (tag.getMachine() != null && tag.getMachine().getCompany() != null) {
                                history.setCompany(tag.getMachine().getCompany().getId().intValue());
                                LogLevelManager.logTrace(COMPONENT_NAME, "Company ID associée: " + tag.getMachine().getCompany().getId());
                            } else {
                                LogLevelManager.logDebug(COMPONENT_NAME, "Aucune company associée au tag: " + tag.getName());
                            }

                            updateHistoryValue(history, data.value());
                            history.setvStamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(data.timestamp()), ZoneId.systemDefault()));
                            em.persist(history);

                            LogLevelManager.logTrace(COMPONENT_NAME, "Entrée historique SQL Server créée pour tag: " + tag.getName());
                        } else {
                            LogLevelManager.logDebug(COMPONENT_NAME, "Persistance SQL Server non activée ou méthode incorrecte pour tag " + tag.getName()
                                    + " (PersistenceEnable: " + tag.getPersistenceEnable()
                                    + ", Method: " + persistenceConfig.getMethod()
                                    + ", Activate: " + persistenceConfig.getActivate() + ")");
                        }
                    } catch (NoResultException nre) {
                        LogLevelManager.logDebug(COMPONENT_NAME, "Aucune configuration de persistance SQL Server trouvée pour le tag " + tag.getName() + ", persistance ignorée.");
                    } catch (Exception e) {
                        LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de la vérification de la configuration de persistance SQL Server pour tag " + tag.getName() + ": " + e.getMessage());
                    }
                } else {
                    LogLevelManager.logDebug(COMPONENT_NAME, "Persistance SQL Server désactivée au niveau du tag pour " + tag.getName() + " (persistenceEnable: " + tag.getPersistenceEnable() + ")");
                }
                // === FIN LOGIQUE DE PERSISTANCE CONDITIONNELLE VERS SQL SERVER ===

                // === ÉCRITURE SYSTÉMATIQUE VERS INFLUXDB (pour l'instant, sans condition spécifique au tag) ===
                // Vous pouvez ajouter des conditions ici si vous voulez contrôler la persistance InfluxDB par tag.
                if (influxDBWriterService != null) {
                    // Pour améliorer, vous pourriez enrichir TagData avec machine_id et machine_name
                    // Exemple: new TagData(tag.getId(), tag.getName(), data.value(), data.timestamp(), tag.getMachine().getId(), tag.getMachine().getName());
                    influxDBWriterService.writeTagData(data); // Écrit toujours vers InfluxDB
                } else {
                    LogLevelManager.logWarn(COMPONENT_NAME, "InfluxDBWriterService non disponible. Les données ne seront pas écrites dans InfluxDB.");
                }
                // === FIN ÉCRITURE VERS INFLUXDB ===

            } else {
                LogLevelManager.logError(COMPONENT_NAME, "Tag avec ID " + data.tagId() + " non trouvé en base de données");
            }

            em.getTransaction().commit();
            LogLevelManager.logTrace(COMPONENT_NAME, "Transaction commitée avec succès pour tag ID: " + data.tagId());

        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de la transaction pour tag ID " + data.tagId()
                    + ": " + e.getMessage());

            if (em.getTransaction().isActive()) {
                try {
                    em.getTransaction().rollback();
                    LogLevelManager.logDebug(COMPONENT_NAME, "Transaction rollback effectué pour tag ID: " + data.tagId());
                } catch (Exception rollbackEx) {
                    LogLevelManager.logError(COMPONENT_NAME, "Erreur lors du rollback: " + rollbackEx.getMessage());
                }
            }

            LogLevelManager.logTrace(COMPONENT_NAME, "Détail de l'erreur: " + e.getClass().getSimpleName());

        } finally {
            try {
                em.close();
            } catch (Exception e) {
                LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de la fermeture de l'EntityManager: " + e.getMessage());
            }
        }
    }

    private void updateTagValue(Tag tag, Object value) {
        LogLevelManager.logTrace(COMPONENT_NAME, "Mise à jour valeur tag " + tag.getName()
                + " avec type: " + (value != null ? value.getClass().getSimpleName() : "null"));

        if (value instanceof Number num) {
            if (value instanceof Float || value instanceof Double) {
                tag.setvFloat(num.floatValue());
                LogLevelManager.logTrace(COMPONENT_NAME, "Valeur Float assignée: " + num.floatValue());
            } else {
                tag.setvInt(num.intValue());
                LogLevelManager.logTrace(COMPONENT_NAME, "Valeur Int assignée: " + num.intValue());
            }
        } else if (value instanceof Boolean bool) {
            tag.setvBool(bool);
            LogLevelManager.logTrace(COMPONENT_NAME, "Valeur Boolean assignée: " + bool);
        } else {
            String strValue = value != null ? value.toString() : null;
            tag.setvStr(strValue);
            LogLevelManager.logTrace(COMPONENT_NAME, "Valeur String assignée: " + strValue);
        }
    }

    private void updateHistoryValue(PersStandard history, Object value) {
        LogLevelManager.logTrace(COMPONENT_NAME, "Mise à jour valeur historique avec type: "
                + (value != null ? value.getClass().getSimpleName() : "null"));

        if (value instanceof Number num) {
            if (value instanceof Float || value instanceof Double) {
                history.setvFloat(num.floatValue());
            } else {
                history.setvInt(num.intValue());
            }
        } else if (value instanceof Boolean bool) {
            history.setvBool(bool);
        } else {
            history.setvStr(value != null ? value.toString() : null);
        }
    }

    public void stop() {
        LogLevelManager.logInfo(COMPONENT_NAME, "Arrêt demandé pour le consommateur Kafka");
        this.running = false;
        if (influxDBWriterService != null) {
            influxDBWriterService.close(); // Fermer la connexion InfluxDB à l'arrêt
        }
    }
}
