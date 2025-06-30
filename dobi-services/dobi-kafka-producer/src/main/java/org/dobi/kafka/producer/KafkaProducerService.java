package org.dobi.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.dobi.dto.TagData;

import java.util.Properties;

import org.dobi.logging.LogLevelManager;
import org.dobi.logging.LogLevelManager.LogLevel;

public class KafkaProducerService {
    
    private static final String COMPONENT_NAME = "KAFKA-PRODUCER";
    
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String topic;
    private long messagesSent = 0;
    private long errorsCount = 0;

    public KafkaProducerService(String bootstrapServers, String topic) {
        this.topic = topic;
        
        LogLevelManager.logInfo(COMPONENT_NAME, "Initialisation du producteur Kafka");
        LogLevelManager.logDebug(COMPONENT_NAME, "Configuration - Servers: " + bootstrapServers + ", Topic: " + topic);
        
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        
        this.producer = new KafkaProducer<>(props);
        LogLevelManager.logInfo(COMPONENT_NAME, "Producteur Kafka initialisé pour le topic '" + topic + "'");
    }

    public void sendTagData(TagData data) {
        try {
            String jsonValue = objectMapper.writeValueAsString(data);
            ProducerRecord<String, String> record = new ProducerRecord<>(this.topic, String.valueOf(data.tagId()), jsonValue);
            
            LogLevelManager.logTrace(COMPONENT_NAME, "Envoi message Kafka - Tag: " + data.tagName() + 
                                 " (ID: " + data.tagId() + "), Valeur: " + data.value());
            
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    errorsCount++;
                    LogLevelManager.logError(COMPONENT_NAME, "Erreur envoi message Kafka pour tag " + 
                                         data.tagName() + " (ID: " + data.tagId() + "): " + exception.getMessage());
                    LogLevelManager.logTrace(COMPONENT_NAME, "Détail erreur: " + exception.getClass().getSimpleName());
                } else {
                    messagesSent++;
                    LogLevelManager.logTrace(COMPONENT_NAME, "Message envoyé avec succès - Partition: " + 
                                         metadata.partition() + ", Offset: " + metadata.offset() + 
                                         " (Total envoyé: " + messagesSent + ")");
                }
            });
            
        } catch (Exception e) {
            errorsCount++;
            LogLevelManager.logError(COMPONENT_NAME, "Exception lors de l'envoi du message pour tag " + 
                                 data.tagName() + " (ID: " + data.tagId() + "): " + e.getMessage());
            LogLevelManager.logTrace(COMPONENT_NAME, "Données du tag en erreur: " + data);
        }
    }

    public void close() {
        LogLevelManager.logInfo(COMPONENT_NAME, "Fermeture du producteur Kafka...");
        
        if (producer != null) {
            try {
                LogLevelManager.logDebug(COMPONENT_NAME, "Flush des messages en attente...");
                producer.flush();
                
                LogLevelManager.logDebug(COMPONENT_NAME, "Fermeture du producteur...");
                producer.close();
                
                LogLevelManager.logInfo(COMPONENT_NAME, "Producteur Kafka fermé - Statistiques: " + 
                                     messagesSent + " messages envoyés, " + errorsCount + " erreurs");
            } catch (Exception e) {
                LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de la fermeture du producteur: " + e.getMessage());
            }
        } else {
            LogLevelManager.logDebug(COMPONENT_NAME, "Producteur déjà fermé");
        }
    }
    
    /**
     * Obtient les statistiques du producteur
     */
    public String getStatistics() {
        return String.format("Messages envoyés: %d, Erreurs: %d, Taux succès: %.2f%%", 
                           messagesSent, errorsCount, 
                           messagesSent > 0 ? (100.0 * messagesSent / (messagesSent + errorsCount)) : 0.0);
    }
    
    /**
     * Réinitialise les compteurs
     */
    public void resetCounters() {
        long oldSent = messagesSent;
        long oldErrors = errorsCount;
        messagesSent = 0;
        errorsCount = 0;
        LogLevelManager.logInfo(COMPONENT_NAME, "Compteurs réinitialisés (anciens: " + oldSent + " envoyés, " + oldErrors + " erreurs)");
    }
}
