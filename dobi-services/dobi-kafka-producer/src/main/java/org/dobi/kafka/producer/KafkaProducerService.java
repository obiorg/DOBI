package org.dobi.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.dobi.dto.TagData;

import java.util.Properties;

public class KafkaProducerService {
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String topic = "dobi.tags.data";

    public KafkaProducerService(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        
        this.producer = new KafkaProducer<>(props);
        System.out.println("Producteur Kafka initialise pour les serveurs: " + bootstrapServers);
    }

    public void sendTagData(TagData data) {
        try {
            String jsonValue = objectMapper.writeValueAsString(data);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, String.valueOf(data.tagId()), jsonValue);
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    System.err.println("Erreur d'envoi a Kafka pour le tag " + data.tagId());
                    exception.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        if (producer != null) {
            producer.flush();
            producer.close();
            System.out.println("Producteur Kafka ferme.");
        }
    }
}
