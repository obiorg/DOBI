package org.dobi.app;

import org.dobi.manager.MachineManagerService;

public class Main {
    public static void main(String[] args) {
        System.out.println("Lancement de l'application DOBI...");
        
        // Initialisation du service principal
        MachineManagerService service = new MachineManagerService();
        
        // Création d'un "shutdown hook".
        // Ce bloc de code sera exécuté lorsque l'application s'arrêtera (ex: Ctrl+C)
        // pour s'assurer que les connexions sont correctement fermées.
        Thread shutdownHook = new Thread(() -> {
            System.out.println("Début de la procédure d'arrêt...");
            service.stop();
            System.out.println("Application DOBI arrêtée proprement.");
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        // Démarrage du service de collecte
        try {
            service.start();
            System.out.println("Service démarré. L'application tourne en tâche de fond.");
            System.out.println("Appuyez sur Ctrl+C pour arrêter.");
        } catch (Exception e) {
            System.err.println("Une erreur critique est survenue au démarrage. L'application va s'arrêter.");
            e.printStackTrace();
            service.stop(); // Tente un arrêt même en cas d'erreur
        }
    }
}
