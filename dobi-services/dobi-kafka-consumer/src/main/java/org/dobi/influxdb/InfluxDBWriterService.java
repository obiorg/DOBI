package org.dobi.influxdb;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.dobi.dto.TagData;
import org.dobi.logging.LogLevelManager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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
        // Log les propriétés utilisées pour le diagnostic
        LogLevelManager.logInfo(COMPONENT_NAME, "Tentative de connexion à InfluxDB...");
        LogLevelManager.logDebug(COMPONENT_NAME, "Propriétés de connexion InfluxDB: "
                + "URL=" + (url != null ? url : "null")
                + ", Org=" + (org != null ? org : "null")
                + ", Bucket=" + (bucket != null ? bucket : "null")
                + ", Token=" + (token != null && !token.isEmpty() ? "******" : "NONE/null"));

        // Validation des propriétés avant de tenter la connexion
        if (url == null || url.isEmpty() || token == null || token.isEmpty() || org == null || org.isEmpty() || bucket == null || bucket == null) {
            LogLevelManager.logError(COMPONENT_NAME, "Propriétés de connexion InfluxDB manquantes ou invalides. Impossible d'initialiser le client.");
            this.influxDBClient = null;
            this.writeApi = null;
            return; // Sortir si les propriétés sont invalides
        }

        try {
            // Crée le client InfluxDB
            this.influxDBClient = InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);

            // AJOUTÉ: Vérification explicite si le client est null après la création
            if (this.influxDBClient == null) {
                LogLevelManager.logError(COMPONENT_NAME, "InfluxDBClientFactory.create a retourné un client null. Cela peut indiquer un problème avec l'URL ou les paramètres de connexion.");
                this.writeApi = null; // S'assurer que writeApi est null
                return; // Sortir
            }

            // Tente d'obtenir l'API d'écriture
            this.writeApi = this.influxDBClient.getWriteApiBlocking();

            // AJOUTÉ: Vérification explicite si writeApi est null après l'appel
            if (this.writeApi == null) {
                LogLevelManager.logError(COMPONENT_NAME, "influxDBClient.getWriteApiBlocking a retourné null. Le client InfluxDB n'a peut-être pas été correctement initialisé ou la connexion a échoué silencieusement.");
                return; // Sortir
            }

            LogLevelManager.logInfo(COMPONENT_NAME, "Connexion à InfluxDB établie avec succès.");

            // Test de connexion simple
            String healthStatus = this.influxDBClient.health().getStatus().toString();
            LogLevelManager.logInfo(COMPONENT_NAME, "InfluxDB Health Check: " + healthStatus);

        } catch (Exception e) {
            // Log l'exception détaillée
            LogLevelManager.logError(COMPONENT_NAME, "Échec de la connexion ou de l'initialisation d'InfluxDB: " + e.getMessage());
            // Imprime la stack trace complète pour un diagnostic plus précis
            e.printStackTrace();
            // Assurez-vous que les objets sont null en cas d'échec
            this.influxDBClient = null;
            this.writeApi = null;
        } finally {
            // AJOUTÉ: S'assurer de fermer le client si l'API d'écriture n'a pas pu être initialisée
            // mais que le client a été créé. Cela libère les ressources.
            if (this.influxDBClient != null && this.writeApi == null) {
                try {
                    this.influxDBClient.close();
                    LogLevelManager.logDebug(COMPONENT_NAME, "Client InfluxDB fermé après échec d'initialisation de WriteApi.");
                } catch (Exception closeEx) {
                    LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de la fermeture du client InfluxDB après échec d'initialisation de WriteApi: " + closeEx.getMessage());
                }
            }
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
            // Correction: Si la valeur convertie est null, ne pas ajouter le champ 'value'
            if (value == null) {
                LogLevelManager.logWarn(COMPONENT_NAME, "Valeur du tag '" + tagData.tagName() + "' non supportée ou non numérique après conversion. Point non écrit.");
                return; // Ne pas écrire le point si la valeur est null après conversion
            }

            Point point = Point.measurement("tag_values")
                    .addTag("tag_id", String.valueOf(tagData.tagId()))
                    .addTag("tag_name", tagData.tagName())
                    .time(tagData.timestamp(), WritePrecision.MS);

            // Correction: Utiliser addField avec le type correct
            if (value instanceof Double) {
                point.addField("value", (Double) value);
            } else if (value instanceof Boolean) {
                point.addField("value", (Boolean) value);
            } else if (value instanceof String) {
                // Si c'est une String (et non un nombre parsable), l'ajouter comme String
                point.addField("value", (String) value);
            } else {
                // Ce cas ne devrait pas se produire si convertToInfluxDBValue est correct,
                // mais c'est une sécurité.
                LogLevelManager.logWarn(COMPONENT_NAME, "Type de valeur inattendu après conversion pour InfluxDB: " + value.getClass().getName() + ". Tentative d'écriture en String.");
                point.addField("value", value.toString());
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
     * @return La valeur convertie (Double, Boolean, String) ou null si le type
     * n'est pas supporté.
     */
    private Object convertToInfluxDBValue(Object value) {
        if (value == null) {
            return null;
        }
        // Si la valeur est une instance de Number, la convertir en Double
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } // Si la valeur est une String, tenter de la convertir en Double.
        // Si non numérique, la retourner comme String.
        else if (value instanceof String) {
            try {
                // Tenter de parser comme un Double (pour les floats et les entiers)
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                // Si ce n'est pas un nombre valide, le laisser comme String
                return (String) value; // Retourne la String originale
            }
        } // Autres types
        else if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof LocalDateTime) {
            // Convertir LocalDateTime en timestamp Unix millisecondes
            return (double) ((LocalDateTime) value).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(); // Retourne un Double
        } else if (value instanceof java.util.Date) {
            // Convertir java.util.Date en timestamp Unix millisecondes
            return (double) ((java.util.Date) value).getTime(); // Retourne un Double
        }
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

    // Getter pour l'API d'écriture, utile pour le diagnostic
    public WriteApiBlocking getWriteApiBlocking() {
        return this.writeApi;
    }
}
