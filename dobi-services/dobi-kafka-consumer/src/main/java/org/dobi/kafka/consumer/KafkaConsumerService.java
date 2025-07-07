package org.dobi.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.TypedQuery;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.dobi.core.websocket.TagWebSocketController;
import org.dobi.dto.TagData;
import org.dobi.entities.Alarms;
import org.dobi.entities.PersStandardLimits;
import org.dobi.entities.Tag;
import org.dobi.influxdb.InfluxDBWriterService;
import org.dobi.logging.LogLevelManager;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class KafkaConsumerService implements Runnable {

    private static final String COMPONENT_NAME = "KAFKA-CONSUMER";

    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityManagerFactory emf;
    private volatile boolean running = true;

    private final InfluxDBWriterService influxDBWriterService;
    private final TagWebSocketController tagWebSocketController;
    private final SimpMessagingTemplate messagingTemplate;

    public record AlarmDto(String alarmId, String timestamp, String message, String priority, String deviceName, boolean acknowledged) {

    }

    public KafkaConsumerService(
            String bootstrapServers, String groupId, String topic, EntityManagerFactory emf,
            InfluxDBWriterService influxDBWriterService,
            TagWebSocketController tagWebSocketController,
            SimpMessagingTemplate messagingTemplate) {
        this.emf = emf;
        this.influxDBWriterService = influxDBWriterService;
        this.tagWebSocketController = tagWebSocketController;
        this.messagingTemplate = messagingTemplate;

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(Collections.singletonList(topic));
    }

    @Override
    public void run() {
        while (running) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        TagData tagData = objectMapper.readValue(record.value(), TagData.class);
                        processTagData(tagData);
                    } catch (Exception e) {
                        LogLevelManager.logError(COMPONENT_NAME, "Erreur de désérialisation du message Kafka: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                LogLevelManager.logError(COMPONENT_NAME, "Erreur dans la boucle du consommateur Kafka: " + e.getMessage());
            }
        }
        consumer.close();
    }

    private void processTagData(TagData data) {
        if (data == null) {
            return;
        }

        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            Tag tag = em.find(Tag.class, (int) data.tagId());
            if (tag != null) {
                updateTagValue(tag, data.value());
                tag.setvStamp(LocalDateTime.now());
                em.merge(tag);

                if (Boolean.TRUE.equals(tag.getAlarmEnable())) {
                    checkLimitsAndGenerateAlarms(em, tag, data);
                }

                tagWebSocketController.sendTagUpdate(data);
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            // CORRIGÉ : Appel à logError avec la bonne signature (2 arguments)
            LogLevelManager.logError(COMPONENT_NAME, "Erreur dans processTagData pour tag " + data.tagName() + ": " + e.getMessage());
        } finally {
            em.close();
        }
    }

    private void checkLimitsAndGenerateAlarms(EntityManager em, Tag tag, TagData data) {
        if (!(data.value() instanceof Number)) {
            return;
        }

        double currentValue = ((Number) data.value()).doubleValue();

        TypedQuery<PersStandardLimits> query = em.createQuery(
                "SELECT l FROM PersStandardLimits l JOIN FETCH l.comparator WHERE l.tag = :tag",
                PersStandardLimits.class
        );
        query.setParameter("tag", tag);
        List<PersStandardLimits> limits = query.getResultList();

        for (PersStandardLimits limit : limits) {
            if (limit.getValue() == null) {
                continue;
            }

            boolean isTriggered = false;
            String comparatorSymbol = limit.getComparator().getSymbol();
            double limitValue = limit.getValue(); // limit.getValue() retourne un Double, qui est unboxed en double

            switch (comparatorSymbol) {
                case ">":
                    isTriggered = currentValue > limitValue;
                    break;
                case "<":
                    isTriggered = currentValue < limitValue;
                    break;
                case ">=":
                    isTriggered = currentValue >= limitValue;
                    break;
                case "<=":
                    isTriggered = currentValue <= limitValue;
                    break;
                case "==":
                    isTriggered = currentValue == limitValue;
                    break;
            }

            if (isTriggered) {
                Alarms alarmDefinition = tag.getAlarm();
                if (alarmDefinition != null) {
                    AlarmDto alarmToBroadcast = new AlarmDto(
                            UUID.randomUUID().toString(),
                            Instant.now().toString(),
                            alarmDefinition.getName() + String.format(" (valeur: %.2f, limite: %.2f)", currentValue, limitValue),
                            alarmDefinition.getClass1().getClass1(),
                            tag.getMachine().getName(),
                            false
                    );

                    messagingTemplate.convertAndSend("/topic/alarms/updates", alarmToBroadcast);
                    LogLevelManager.logWarn(COMPONENT_NAME, "ALARME DÉCLENCHÉE: " + alarmToBroadcast.message());
                }
            }
        }
    }

    private void updateTagValue(Tag tag, Object value) {
        if (value instanceof Number num) {
            // CORRIGÉ : On caste explicitement en float pour correspondre au type Float de l'entité Tag
            if (value instanceof Float || value instanceof Double) {
                tag.setvFloat(num.doubleValue());
            } else {
                tag.setvInt(num.intValue());
            }
        } else if (value instanceof Boolean bool) {
            tag.setvBool(bool);
        } else {
            tag.setvStr(value != null ? value.toString() : null);
        }
    }

    public void stop() {
        this.running = false;
    }
}
