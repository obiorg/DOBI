package org.dobi.kafka.manager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class KafkaManagerService {

    public KafkaManagerService() {
        System.out.println("Service de supervision Kafka initialisé.");
    }

    public void checkStatus() {
        System.out.println("Vérification du statut de Kafka et Zookeeper...");
        
        List<String> runningProcesses = getRunningJavaProcesses();

        boolean isZookeeperRunning = isProcessInList(runningProcesses, "zookeeper");
        boolean isKafkaRunning = isProcessInList(runningProcesses, "kafka");

        System.out.println("  -> Statut Zookeeper: " + (isZookeeperRunning ? "Démarré" : "Arrêté"));
        System.out.println("  -> Statut Kafka: " + (isKafkaRunning ? "Démarré" : "Arrêté"));
    }
    
    /**
     * Exécute la commande 'jps -l' et retourne une liste des processus Java en cours.
     */
    private List<String> getRunningJavaProcesses() {
        List<String> processes = new ArrayList<>();
        try {
            // 'jps -l' liste les processus Java avec le nom complet du jar ou de la classe main.
            Process process = Runtime.getRuntime().exec("jps -l");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                processes.add(line.toLowerCase());
            }
            process.waitFor();
        } catch (Exception e) {
            System.err.println("Impossible de vérifier les processus Java en cours. Assurez-vous que le JDK est dans le PATH. Erreur: " + e.getMessage());
        }
        return processes;
    }

    /**
     * Vérifie si un mot-clé est présent dans la liste des processus.
     */
    private boolean isProcessInList(List<String> processList, String keyword) {
        for (String process : processList) {
            // On cherche si la ligne contient, par exemple, 'zookeeper' ou 'kafka.Kafka'
            if (process.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
