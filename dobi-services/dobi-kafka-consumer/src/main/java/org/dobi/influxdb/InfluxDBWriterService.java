package org.dobi.influxdb;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.dobi.dto.TagData;
import org.dobi.logging.LogLevelManager;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class InfluxDBWriterService {

    private static final String COMPONENT_NAME = "INFLUXDB-WRITER";

    private final String url;
    private final String token;
    private final String org;
    private final String bucket;
    private InfluxDBClient influxDBClient;
    private WriteApiBlocking writeApi;

    public InfluxDBWriterService(String url, String token, String org, String bucket) {
        this.url = url;
        this.token = token;
        this.org = org;
        this.bucket = bucket;
        LogLevelManager.logInfo(COMPONENT_NAME, "InfluxDBWriterService initialisé avec URL: " + url + ", Org: " + org + ", Bucket: " + bucket);
    }

    /**
     * Initialise la connexion au client InfluxDB. Cette méthode doit être
     * appelée après la construction du service.
     */
    public void initialize() {
        try {
            LogLevelManager.logInfo(COMPONENT_NAME, "Tentative de connexion à InfluxDB...");
            influxDBClient = InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
            writeApi = influxDBClient.getWriteApiBlocking();
            LogLevelManager.logInfo(COMPONENT_NAME, "Connexion à InfluxDB établie avec succès.");

            // Test de connexion simple
            String health = influxDBClient.health().getStatus().toString();
            LogLevelManager.logInfo(COMPONENT_NAME, "InfluxDB Health Check: " + health);

        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Échec de la connexion à InfluxDB: " + e.getMessage());
            influxDBClient = null;
            writeApi = null;
        }
    }

    /**
     * Écrit les données d'un tag dans InfluxDB.
     *
     * @param tagData Les données du tag à écrire.
     */
    public void writeTagData(TagData tagData) {
        if (writeApi == null) {
            LogLevelManager.logError(COMPONENT_NAME, "WriteApi InfluxDB non initialisé. Les données ne seront pas écrites.");
            return;
        }

        try {
            Object value = convertToInfluxDBValue(tagData.value());
            if (value == null) {
                LogLevelManager.logWarn(COMPONENT_NAME, "Valeur du tag '" + tagData.tagName() + "' non supportée pour InfluxDB. Ignorée.");
                return;
            }

            Point point = Point.measurement("tag_values")
                    .addTag("tag_id", String.valueOf(tagData.tagId()))
                    .addTag("tag_name", tagData.tagName())
                    // Ajoutez ici d'autres tags si TagData est enrichi (ex: machine_id, machine_name)
                    // .addTag("machine_id", String.valueOf(tagData.getMachineId())) // Exemple
                    // .addTag("machine_name", tagData.getMachineName()) // Exemple
                    .time(tagData.timestamp(), WritePrecision.MS);

            // Correction: Utiliser addField avec le type correct
            if (value instanceof Long) {
                point.addField("value", (Long) value);
            } else if (value instanceof Double) {
                point.addField("value", (Double) value);
            } else if (value instanceof Boolean) {
                point.addField("value", (Boolean) value);
            } else if (value instanceof String) {
                point.addField("value", (String) value);
            } else {
                // Fallback si le type n'est pas explicitement géré ci-dessus (ne devrait pas arriver si convertToInfluxDBValue est correct)
                LogLevelManager.logWarn(COMPONENT_NAME, "Type de valeur inattendu après conversion pour InfluxDB: " + value.getClass().getName());
                point.addField("value", value.toString()); // Convertir en String comme dernier recours
            }

            writeApi.writePoint(point);
            LogLevelManager.logTrace(COMPONENT_NAME, "Donnée écrite dans InfluxDB: " + tagData.tagName() + " = " + tagData.value());

        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de l'écriture des données de tag dans InfluxDB pour " + tagData.tagName() + ": " + e.getMessage());
        }
    }

    /**
     * Convertit un objet Java en un type compatible avec InfluxDB. InfluxDB
     * supporte les types : Long, Double, Boolean, String.
     *
     * @param value La valeur à convertir.
     * @return La valeur convertie ou null si le type n'est pas supporté.
     */
    private Object convertToInfluxDBValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            // InfluxDB stocke les entiers comme Long, les flottants comme Double
            if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
                return ((Number) value).longValue();
            } else if (value instanceof Float || value instanceof Double) {
                return ((Number) value).doubleValue();
            }
        } else if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return (String) value;
        } else if (value instanceof LocalDateTime) {
            // Convertir LocalDateTime en timestamp Unix millisecondes
            return ((LocalDateTime) value).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } else if (value instanceof java.util.Date) {
            return ((java.util.Date) value).getTime();
        }
        // Correction: Utiliser la méthode log de LogLevelManager avec le niveau WARN
        LogLevelManager.logWarn(COMPONENT_NAME, "Type de valeur non supporté pour InfluxDB: " + value.getClass().getName());
        return null;
    }

    /**
     * Ferme la connexion InfluxDB.
     */
    public void close() {
        if (influxDBClient != null) {
            try {
                influxDBClient.close();
                LogLevelManager.logInfo(COMPONENT_NAME, "Connexion InfluxDB fermée.");
            } catch (Exception e) {
                LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de la fermeture de la connexion InfluxDB: " + e.getMessage());
            }
        }
    }
}
