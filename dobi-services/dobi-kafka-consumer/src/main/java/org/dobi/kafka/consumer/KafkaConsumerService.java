package org.dobi.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
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

    public KafkaConsumerService(String bootstrapServers, String groupId, String topic, EntityManagerFactory emf) {
        this.emf = emf;
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(Collections.singletonList(topic));
        System.out.println("Consommateur Kafka initialisÃ© pour le topic '" + topic + "'");
    }

    @Override
    public void run() {
        while (running) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            for (ConsumerRecord<String, String> record : records) {
                try {
                    TagData tagData = objectMapper.readValue(record.value(), TagData.class);
                    processTagData(tagData);
                } catch (Exception e) {
                    System.err.println("Erreur de dÃ©sÃ©rialisation du message Kafka: " + e.getMessage());
                }
            }
        }
        consumer.close();
        System.out.println("Consommateur Kafka arrÃªtÃ©.");
    }

        private void processTagData(TagData data) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();

            Tag tag = em.find(Tag.class, data.tagId());
            if (tag != null) {
                // 1. Mettre à jour la valeur "live" dans la table Tag
                updateTagValue(tag, data.value());
                tag.setvStamp(LocalDateTime.now());
                em.merge(tag);

                // 2. Créer un enregistrement d'historique
                PersStandard history = new PersStandard();
                history.setTag(data.tagId());
                // CORRECTION: On récupère l'ID de la company depuis l'objet Tag
                history.setCompany(tag.getMachine().getCompany().getId().intValue());
                updateHistoryValue(history, data.value());
                history.setvStamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(data.timestamp()), ZoneId.systemDefault()));
                em.persist(history);
            }

            em.getTransaction().commit();
            System.out.println("Tag " + data.tagName() + " mis à jour avec la valeur " + data.value());
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            e.printStackTrace();
        } finally {
            em.close();
        }
    }

    private void updateTagValue(Tag tag, Object value) {
        if (value instanceof Number) {
            if (value instanceof Float || value instanceof Double) tag.setvFloat(((Number) value).floatValue());
            else tag.setvInt(((Number) value).intValue());
        } else if (value instanceof Boolean) {
            tag.setvBool((Boolean) value);
        } else {
            tag.setvStr(value.toString());
        }
    }

    private void updateHistoryValue(PersStandard history, Object value) {
        if (value instanceof Number) {
            if (value instanceof Float || value instanceof Double) history.setvFloat(((Number) value).floatValue());
            else history.setvInt(((Number) value).intValue());
        } else if (value instanceof Boolean) {
            history.setvBool((Boolean) value);
        } else {
            history.setvStr(value.toString());
        }
    }

    public void stop() {
        this.running = false;
    }
}

