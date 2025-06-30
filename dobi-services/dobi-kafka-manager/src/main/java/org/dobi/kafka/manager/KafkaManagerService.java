package org.dobi.kafka.manager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.dobi.logging.LogLevelManager;
import org.dobi.logging.LogLevelManager.LogLevel;

public class KafkaManagerService {

    private static final String COMPONENT_NAME = "KAFKA-MANAGER";

    public KafkaManagerService() {
        System.out.println("Service de supervision Kafka initialisé.");
    }

    public void checkStatus() {
        LogLevelManager.logInfo(COMPONENT_NAME, "Vérification du statut de Kafka et Zookeeper");

        List<String> runningProcesses = getRunningJavaProcesses();

        boolean isZookeeperRunning = isProcessInList(runningProcesses, "zookeeper");
        boolean isKafkaRunning = isProcessInList(runningProcesses, "kafka");

        LogLevelManager.logInfo(COMPONENT_NAME, "Statut Zookeeper: " + (isZookeeperRunning ? "Démarré" : "Arrêté"));
        LogLevelManager.logInfo(COMPONENT_NAME, "Statut Kafka: " + (isKafkaRunning ? "Démarré" : "Arrêté"));

        if (!isZookeeperRunning || !isKafkaRunning) {
            LogLevelManager.logError(COMPONENT_NAME, "Services manquants détectés - Zookeeper: "
                    + isZookeeperRunning + ", Kafka: " + isKafkaRunning);
        } else {
            LogLevelManager.logDebug(COMPONENT_NAME, "Tous les services Kafka sont opérationnels");
        }
    }

    /**
     * Exécute la commande 'jps -l' et retourne une liste des processus Java en
     * cours.
     */
    private List<String> getRunningJavaProcesses() {
        List<String> processes = new ArrayList<>();

        LogLevelManager.logDebug(COMPONENT_NAME, "Exécution de 'jps -l' pour lister les processus Java");

        try {
            Process process = Runtime.getRuntime().exec("jps -l");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            int processCount = 0;

            while ((line = reader.readLine()) != null) {
                processes.add(line.toLowerCase());
                processCount++;
                LogLevelManager.logTrace(COMPONENT_NAME, "Processus Java détecté: " + line);
            }

            int exitCode = process.waitFor();
            LogLevelManager.logDebug(COMPONENT_NAME, "Commande 'jps -l' terminée - Code: " + exitCode
                    + ", Processus trouvés: " + processCount);

        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Impossible de vérifier les processus Java: " + e.getMessage()
                    + ". Assurez-vous que le JDK est dans le PATH");
            LogLevelManager.logTrace(COMPONENT_NAME, "Erreur détaillée: " + e.getClass().getSimpleName());
        }

        return processes;
    }

    /**
     * Vérifie si un mot-clé est présent dans la liste des processus.
     */
    private boolean isProcessInList(List<String> processList, String keyword) {
        LogLevelManager.logTrace(COMPONENT_NAME, "Recherche du keyword '" + keyword + "' dans "
                + processList.size() + " processus");

        for (String process : processList) {
            // On cherche si la ligne contient, par exemple, 'zookeeper' ou 'kafka.Kafka'
            if (process.contains(keyword.toLowerCase())) {
                LogLevelManager.logTrace(COMPONENT_NAME, "Keyword '" + keyword + "' trouvé dans: " + process);
                return true;
            }
        }

        LogLevelManager.logTrace(COMPONENT_NAME, "Keyword '" + keyword + "' non trouvé");
        return false;
    }

    /**
     * Obtient un rapport détaillé des processus Java
     */
    public String getDetailedProcessReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Rapport Processus Java ===\n");

        LogLevelManager.logDebug(COMPONENT_NAME, "Génération du rapport détaillé des processus");

        List<String> processes = getRunningJavaProcesses();

        report.append("Nombre total de processus Java: ").append(processes.size()).append("\n\n");

        boolean foundKafka = false;
        boolean foundZookeeper = false;

        for (String process : processes) {
            report.append("- ").append(process).append("\n");

            if (process.contains("kafka")) {
                foundKafka = true;
            }
            if (process.contains("zookeeper")) {
                foundZookeeper = true;
            }
        }

        report.append("\n=== Analyse ===\n");
        report.append("Kafka détecté: ").append(foundKafka ? "OUI" : "NON").append("\n");
        report.append("Zookeeper détecté: ").append(foundZookeeper ? "OUI" : "NON").append("\n");

        if (foundKafka && foundZookeeper) {
            report.append("État global: OPÉRATIONNEL\n");
            LogLevelManager.logInfo(COMPONENT_NAME, "Rapport généré - État: OPÉRATIONNEL");
        } else {
            report.append("État global: PROBLÈME DÉTECTÉ\n");
            LogLevelManager.logError(COMPONENT_NAME, "Rapport généré - État: PROBLÈME DÉTECTÉ");
        }

        return report.toString();
    }
}
