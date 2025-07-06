package org.dobi.influxdb;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.query.FluxTable;
import com.influxdb.query.FluxRecord;
import org.dobi.logging.LogLevelManager;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service pour la lecture des données depuis InfluxDB. Il permet de récupérer
 * l'historique des tags et des agrégations.
 */
@Service
public class InfluxDBReaderService {

    private static final String COMPONENT_NAME = "INFLUXDB-READER";

    private final String url;
    private final String token;
    private final String org;
    private final String bucket;
    private InfluxDBClient influxDBClient; // Reste private

    public InfluxDBReaderService(String url, String token, String org, String bucket) {
        this.url = url;
        this.token = token;
        this.org = org;
        this.bucket = bucket;
        LogLevelManager.logInfo(COMPONENT_NAME, "InfluxDBReaderService initialisé avec URL: " + url + ", Org: " + org + ", Bucket: " + bucket);
    }

    /**
     * Initialise la connexion au client InfluxDB pour la lecture. Cette méthode
     * doit être appelée après la construction du service.
     */
    public void initialize() {
        LogLevelManager.logInfo(COMPONENT_NAME, "Tentative d'initialisation du client de lecture InfluxDB...");
        if (url == null || url.isEmpty() || token == null || token.isEmpty() || org == null || org.isEmpty() || bucket == null || bucket.isEmpty()) {
            LogLevelManager.logError(COMPONENT_NAME, "Propriétés de connexion InfluxDB manquantes ou invalides pour le service de lecture. Impossible d'initialiser.");
            this.influxDBClient = null;
            return;
        }

        try {
            this.influxDBClient = InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
            if (this.influxDBClient == null) {
                throw new IllegalStateException("InfluxDBClientFactory.create a retourné un client null pour le service de lecture.");
            }
            LogLevelManager.logInfo(COMPONENT_NAME, "Client de lecture InfluxDB initialisé avec succès.");
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Échec de l'initialisation du client de lecture InfluxDB: " + e.getMessage());
            e.printStackTrace();
            this.influxDBClient = null;
        }
    }

    /**
     * Récupère l'historique des valeurs pour un tag donné.
     *
     * @param tagName Le nom du tag.
     * @param start La période de début (ex: "-1h", "2023-01-01T00:00:00Z").
     * @param stop La période de fin (optionnel, par défaut "now()").
     * @return Une liste de Map, chaque Map représentant un point de donnée
     * (timestamp, value).
     */
    public List<Map<String, Object>> getTagHistory(String tagName, String start, String stop) {
        List<Map<String, Object>> history = new ArrayList<>();
        if (influxDBClient == null) {
            LogLevelManager.logError(COMPONENT_NAME, "Client InfluxDB non initialisé. Impossible de récupérer l'historique.");
            return history;
        }

        String fluxQuery = String.format(
                "from(bucket: \"%s\") "
                + "|> range(start: %s%s) "
                + "|> filter(fn: (r) => r._measurement == \"tag_values\" and r.tag_name == \"%s\") "
                + "|> filter(fn: (r) => r._field == \"value\") "
                + "|> yield(name: \"value\")",
                bucket,
                start,
                (stop != null && !stop.isEmpty() ? ", stop: " + stop : ""),
                tagName
        );

        LogLevelManager.logDebug(COMPONENT_NAME, "Exécution de la requête Flux: " + fluxQuery);

        try {
            List<FluxTable> tables = influxDBClient.getQueryApi().query(fluxQuery, org);

            for (FluxTable fluxTable : tables) {
                for (FluxRecord fluxRecord : fluxTable.getRecords()) {
                    Map<String, Object> recordMap = new HashMap<>();
                    recordMap.put("time", fluxRecord.getTime());
                    recordMap.put("value", fluxRecord.getValueByKey("_value"));
                    history.add(recordMap);
                }
            }
            LogLevelManager.logInfo(COMPONENT_NAME, "Historique récupéré pour tag '" + tagName + "': " + history.size() + " points.");
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de la récupération de l'historique pour tag '" + tagName + "': " + e.getMessage());
            e.printStackTrace();
        }
        return history;
    }

    /**
     * Récupère la dernière valeur connue pour un tag.
     *
     * @param tagName Le nom du tag.
     * @return Une Map contenant la dernière valeur et son timestamp, ou null si
     * non trouvé.
     */
    public Map<String, Object> getLastTagValue(String tagName) {
        if (influxDBClient == null) {
            LogLevelManager.logError(COMPONENT_NAME, "Client InfluxDB non initialisé. Impossible de récupérer la dernière valeur.");
            return null;
        }

        String fluxQuery = String.format(
                "from(bucket: \"%s\") "
                + "|> range(start: -1d) "
                + // Cherche sur la dernière journée pour la dernière valeur
                "|> filter(fn: (r) => r._measurement == \"tag_values\" and r.tag_name == \"%s\") "
                + "|> filter(fn: (r) => r._field == \"value\") "
                + "|> last() "
                + // Prend la dernière valeur
                "|> yield(name: \"last_value\")",
                bucket,
                tagName
        );

        LogLevelManager.logDebug(COMPONENT_NAME, "Exécution de la requête Flux pour dernière valeur: " + fluxQuery);

        try {
            List<FluxTable> tables = influxDBClient.getQueryApi().query(fluxQuery, org);
            if (!tables.isEmpty() && !tables.get(0).getRecords().isEmpty()) {
                FluxRecord record = tables.get(0).getRecords().get(0);
                Map<String, Object> result = new HashMap<>();
                result.put("time", record.getTime());
                result.put("value", record.getValueByKey("_value"));
                LogLevelManager.logInfo(COMPONENT_NAME, "Dernière valeur récupérée pour tag '" + tagName + "': " + result.get("value") + " à " + result.get("time"));
                return result;
            }
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de la récupération de la dernière valeur pour tag '" + tagName + "': " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Récupère les tags disponibles dans le bucket.
     *
     * @return Une liste de noms de tags uniques.
     */
    public List<String> getAvailableTagNames() {
        List<String> tagNames = new ArrayList<>();
        if (influxDBClient == null) {
            LogLevelManager.logError(COMPONENT_NAME, "Client InfluxDB non initialisé. Impossible de récupérer les noms de tags.");
            return tagNames;
        }

        String fluxQuery = String.format(
                "import \"influxdata/influxdb/schema\" "
                + "schema.tagValues(bucket: \"%s\", tag: \"tag_name\")",
                bucket
        );

        LogLevelManager.logDebug(COMPONENT_NAME, "Exécution de la requête Flux pour les noms de tags: " + fluxQuery);

        try {
            List<FluxTable> tables = influxDBClient.getQueryApi().query(fluxQuery, org);
            for (FluxTable fluxTable : tables) {
                for (FluxRecord fluxRecord : fluxTable.getRecords()) {
                    Object value = fluxRecord.getValue();
                    if (value != null) {
                        tagNames.add(value.toString());
                    }
                }
            }
            LogLevelManager.logInfo(COMPONENT_NAME, "Noms de tags disponibles récupérés: " + tagNames.size());
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de la récupération des noms de tags disponibles: " + e.getMessage());
            e.printStackTrace();
        }
        return tagNames;
    }

    /**
     * Ferme la connexion InfluxDB.
     */
    public void close() {
        if (influxDBClient != null) {
            try {
                influxDBClient.close();
                LogLevelManager.logInfo(COMPONENT_NAME, "Connexion InfluxDB (lecture) fermée.");
            } catch (Exception e) {
                LogLevelManager.logError(COMPONENT_NAME, "Erreur lors de la fermeture de la connexion InfluxDB (lecture): " + e.getMessage());
            }
        }
    }

    /**
     * Getter pour le client InfluxDB.
     *
     * @return L'instance de InfluxDBClient ou null si non initialisé.
     */
    public InfluxDBClient getInfluxDBClient() { // AJOUTÉ
        return this.influxDBClient;
    }
}
