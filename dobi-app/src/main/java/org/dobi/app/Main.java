package org.dobi.app;

import org.dobi.kafka.consumer.KafkaConsumerService;
import org.dobi.manager.MachineManagerService;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

public class Main {

    private static MachineManagerService collectorService;
    private static KafkaConsumerService persistenceService;

    public static void main(String[] args) {
        // Lancer l'interface graphique sur le bon thread (Event Dispatch Thread)
        SwingUtilities.invokeLater(Main::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        // VÃ©rifier si le SystemTray est supportÃ©
        if (!SystemTray.isSupported()) {
            System.err.println("Le SystemTray n'est pas supportÃ© sur ce systÃ¨me.");
            // Lancer en mode console dÃ©gradÃ© si pas de tray
            startServices(); 
            return;
        }

        // CrÃ©er la fenÃªtre principale mais ne pas l'afficher tout de suite
        MainFrame mainFrame = new MainFrame();

        // Configurer le TrayIcon
        PopupMenu popup = new PopupMenu();
        TrayIcon trayIcon = new TrayIcon(createImage("obi-signet-dim.png", "DOBI Tray Icon"));
        SystemTray tray = SystemTray.getSystemTray();

        MenuItem displayItem = new MenuItem("Afficher/Masquer");
        displayItem.addActionListener(e -> mainFrame.setVisible(!mainFrame.isVisible()));
        popup.add(displayItem);

        popup.addSeparator();

        MenuItem exitItem = new MenuItem("Quitter");
        exitItem.addActionListener(e -> {
            stopServices();
            tray.remove(trayIcon);
            System.exit(0);
        });
        popup.add(exitItem);

        trayIcon.setPopupMenu(popup);
        trayIcon.setToolTip("DOBI Service");

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.err.println("Impossible d'ajouter l'icÃ´ne au SystemTray.");
            return;
        }
        
        System.out.println("Application DOBI dÃ©marrÃ©e en arriÃ¨re-plan.");
        System.out.println("Double-cliquez ou utilisez le menu pour afficher la console.");

        trayIcon.addActionListener(e -> mainFrame.setVisible(!mainFrame.isVisible()));

        // DÃ©marrer les services en arriÃ¨re-plan pour ne pas geler l'interface
        startServices();
    }
    
    private static void startServices() {
        new Thread(() -> {
            collectorService = new MachineManagerService();
            persistenceService = new KafkaConsumerService(
                collectorService.getAppProperty("kafka.bootstrap.servers"), 
                "dobi-persistence-group", 
                collectorService.getAppProperty("kafka.topic.tags.data"), 
                collectorService.getEmf()
            );
            
            new Thread(persistenceService).start();
            collectorService.start();
        }).start();
    }
    
    private static void stopServices() {
        if(collectorService != null) collectorService.stop();
        if(persistenceService != null) persistenceService.stop();
    }

    private static Image createImage(String path, String description) {
        URL imageURL = Main.class.getClassLoader().getResource(path);
        if (imageURL == null) {
            System.err.println("Ressource non trouvÃ©e: " + path);
            return null;
        } else {
            return (new ImageIcon(imageURL, description)).getImage();
        }
    }
}

