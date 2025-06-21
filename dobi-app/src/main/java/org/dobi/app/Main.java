package org.dobi.app;

import org.dobi.app.ui.MainFrame;
import org.dobi.entities.Machine;
import org.dobi.kafka.consumer.KafkaConsumerService;
import org.dobi.manager.MachineManagerService;
import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.util.Collections;
import java.util.List;

public class Main {
    private static MachineManagerService collectorService;
    private static KafkaConsumerService persistenceService;
    private static MainFrame mainFrame;
    private static TrayIcon trayIcon;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } 
        catch (Exception e) { System.err.println("Impossible d'utiliser le Look and Feel du systeme."); }

        if (!SystemTray.isSupported()) {
            System.err.println("Le SystemTray n'est pas supporte sur ce systeme.");
            // Logique de secours sans TrayIcon
            List<Machine> machines = new MachineManagerService().getMachinesFromDb();
            mainFrame = new MainFrame(machines);
            mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            mainFrame.setVisible(true);
            startServices(mainFrame.getStatusPanel());
            return;
        }

        // 1. Charger les machines une seule fois pour construire l'UI
        List<Machine> machines = Collections.emptyList();
        try {
             machines = new MachineManagerService().getMachinesFromDb();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Erreur de connexion a la base de donnees. Verifiez persistence.xml.\n" + e.getMessage(), "Erreur Critique", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        
        mainFrame = new MainFrame(machines);

        // 2. Configurer le TrayIcon
        Image iconImage = createImage("obi-signet-dim.png");
        if (iconImage == null) { System.exit(1); }
        trayIcon = new TrayIcon(iconImage, "DOBI Service");
        
        PopupMenu popup = new PopupMenu();
        MenuItem displayItem = new MenuItem("Afficher/Masquer Console");
        displayItem.addActionListener(e -> mainFrame.setVisible(!mainFrame.isVisible()));
        popup.add(displayItem);
        popup.addSeparator();
        MenuItem exitItem = new MenuItem("Quitter");
        exitItem.addActionListener(e -> quitApplication());
        popup.add(exitItem);
        
        trayIcon.setPopupMenu(popup);
        trayIcon.addActionListener(e -> mainFrame.setVisible(!mainFrame.isVisible()));
        
        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) { System.err.println("Impossible d'ajouter l'icone au SystemTray."); return; }
        
        // 3. Démarrer les services
        startServices(mainFrame.getStatusPanel());
        trayIcon.displayMessage("DOBI Service", "L'application a demarre avec succes.", TrayIcon.MessageType.INFO);
    }
    
    private static void startServices(MachineStatusPanel statusPanel) {
        new Thread(() -> {
            collectorService = new MachineManagerService(statusPanel);
            collectorService.initializeKafka(); // Initialiser Kafka après avoir chargé les properties
            
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
    
    private static void quitApplication() {
        new Thread(() -> {
            trayIcon.displayMessage("DOBI Service", "Arret de l'application en cours...", TrayIcon.MessageType.INFO);
            if(collectorService != null) collectorService.stop();
            if(persistenceService != null) persistenceService.stop();
            System.out.println("Application DOBI arretee proprement.");
            SystemTray.getSystemTray().remove(trayIcon);
            System.exit(0);
        }).start();
    }

    private static Image createImage(String path) {
        URL imageURL = Main.class.getClassLoader().getResource(path);
        if (imageURL == null) {
            System.err.println("Ressource non trouvee: " + path);
            return null;
        }
        Image image = new ImageIcon(imageURL).getImage();
        Dimension trayIconSize = SystemTray.getSystemTray().getTrayIconSize();
        return image.getScaledInstance(trayIconSize.width, trayIconSize.height, Image.SCALE_SMOOTH);
    }
}
