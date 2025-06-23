package org.dobi.kafka.manager;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class KafkaManagerService {

    public KafkaManagerService() {
        System.out.println("Service de supervision Kafka initialisé.");
    }

    public void checkStatus() {
        System.out.println("Vérification du statut de Kafka et Zookeeper...");
        
        // Cette approche est dépendante de l'OS (Windows dans ce cas)
        // Elle cherche si un processus Java contient "zookeeper" ou "kafka" dans son nom.
        boolean isZookeeperRunning = isProcessRunning("zookeeper");
        boolean isKafkaRunning = isProcessRunning("kafka");

        System.out.println("  -> Statut Zookeeper: " + (isZookeeperRunning ? "Démarré" : "Arrêté"));
        System.out.println("  -> Statut Kafka: " + (isKafkaRunning ? "Démarré" : "Arrêté"));
    }
    
    /**
     * Méthode simple pour vérifier si un processus contenant le mot-clé est en cours.
     * Note: Ceci est une implémentation basique pour Windows.
     */
    private boolean isProcessRunning(String processName) {
        try {
            Process process = Runtime.getRuntime().exec("jps -l");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().contains(processName.toLowerCase())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            System.err.println("Impossible de vérifier les processus Java en cours: " + e.getMessage());
            return false;
        }
    }
}
