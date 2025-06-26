package org.dobi.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.dobi.dto.TagData;
import org.dobi.entities.PersStandard;
import org.dobi.entities.Tag;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Properties;

public class KafkaConsumerService implements Runnable {

    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityManagerFactory emf;
    private volatile boolean running = true;
    private final String bootstrapServers;

    public KafkaConsumerService(String bootstrapServers, String groupId, String topic, EntityManagerFactory emf) {
        this.emf = emf;
        this.bootstrapServers = bootstrapServers;
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000"); 

        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(Collections.singletonList(topic));
        System.out.println("Consommateur Kafka initialisé pour le topic '" + topic + "'");
    }

    @Override
    public void run() {
        while (running) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                
                // --- AJOUT DE LOGS DE DÉBOGAGE ---
                if (!records.isEmpty()) {
                    System.out.println("[KAFKA-CONSUMER] " + records.count() + " message(s) reçu(s). Traitement en cours...");
                    for (ConsumerRecord<String, String> record : records) {
                        try {
                            TagData tagData = objectMapper.readValue(record.value(), TagData.class);
                            processTagData(tagData);
                        } catch (Exception e) {
                            System.err.println("    -> Erreur de désérialisation du message Kafka: " + e.getMessage());
                        }
                    }
                }
                // --- FIN DES LOGS DE DÉBOGAGE ---

            } catch (TimeoutException te) {
                System.err.println("ERREUR KAFKA: Timeout de connexion au serveur Kafka (" + bootstrapServers + "). Vérifiez l'adresse, la configuration des listeners et le pare-feu.");
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            } catch (Exception e) {
                System.err.println("Erreur inattendue dans la boucle du consommateur Kafka: " + e.getMessage());
            }
        }
        consumer.close();
        System.out.println("Consommateur Kafka arrêté.");
    }

    private void processTagData(TagData data) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            Tag tag = em.find(Tag.class, data.tagId());
            if (tag != null) {
                updateTagValue(tag, data.value());
                tag.setvStamp(LocalDateTime.now());
                em.merge(tag);
                
                PersStandard history = new PersStandard();
                history.setTag(data.tagId());
                if (tag.getMachine() != null && tag.getMachine().getCompany() != null) {
                    history.setCompany(tag.getMachine().getCompany().getId().intValue());
                }
                updateHistoryValue(history, data.value());
                history.setvStamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(data.timestamp()), ZoneId.systemDefault()));
                em.persist(history);
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            e.printStackTrace();
        } finally {
            em.close();
        }
    }

    private void updateTagValue(Tag tag, Object value) {
        if (value instanceof Number num) {
            if (value instanceof Float || value instanceof Double) tag.setvFloat(num.floatValue());
            else tag.setvInt(num.intValue());
        } else if (value instanceof Boolean bool) {
            tag.setvBool(bool);
        } else {
            tag.setvStr(value.toString());
        }
    }

    private void updateHistoryValue(PersStandard history, Object value) {
        if (value instanceof Number num) {
            if (value instanceof Float || value instanceof Double) history.setvFloat(num.floatValue());
            else history.setvInt(num.intValue());
        } else if (value instanceof Boolean bool) {
            history.setvBool(bool);
        } else {
            history.setvStr(value.toString());
        }
    }

    public void stop() {
        this.running = false;
    }
}
