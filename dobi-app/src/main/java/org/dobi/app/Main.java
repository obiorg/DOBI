package org.dobi.app;

import org.dobi.manager.MachineManagerService;
import org.dobi.kafka.consumer.KafkaConsumerService;

public class Main {
    public static void main(String[] args) {
        System.out.println("Lancement de l'application DOBI...");
        
        // Initialisation du service principal
        MachineManagerService collectorService = new MachineManagerService();
        // TODO: Déplacer la logique de création de l'EMF dans un service partagé
        KafkaConsumerService persistenceService = new KafkaConsumerService(collectorService.getAppProperty("kafka.bootstrap.servers"), "dobi-persistence-group", collectorService.getAppProperty("kafka.topic.tags.data"), collectorService.getEmf());
        new Thread(persistenceService).start();
        
        // CrÃ©ation d'un "shutdown hook".
        // Ce bloc de code sera exÃ©cutÃ© lorsque l'application s'arrÃªtera (ex: Ctrl+C)
        // pour s'assurer que les connexions sont correctement fermÃ©es.
        Thread shutdownHook = new Thread(() -> {
            System.out.println("DÃ©but de la procÃ©dure d'arrÃªt...");
            collectorService.stop();
            persistenceService.stop();
            System.out.println("Application DOBI arrÃªtÃ©e proprement.");
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        // DÃ©marrage du service de collecte
        try {
            collectorService.start();
            System.out.println("Service dÃ©marrÃ©. L'application tourne en tÃ¢che de fond.");
            System.out.println("Appuyez sur Ctrl+C pour arrÃªter.");
        } catch (Exception e) {
            System.err.println("Une erreur critique est survenue au dÃ©marrage. L'application va s'arrÃªter.");
            e.printStackTrace();
            collectorService.stop();
            persistenceService.stop(); // Tente un arrÃªt mÃªme en cas d'erreur
        }
    }
}

