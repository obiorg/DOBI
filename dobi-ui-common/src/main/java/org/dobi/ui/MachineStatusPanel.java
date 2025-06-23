package org.dobi.ui;

import org.dobi.entities.Machine;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MachineStatusPanel extends JPanel {
    private final Map<Long, JLabel> statusLabels = new ConcurrentHashMap<>();
    private final Map<Long, JButton> restartButtons = new ConcurrentHashMap<>();

    public MachineStatusPanel(List<Machine> machines) {
        super(new GridBagLayout());
        setBorder(new TitledBorder("Statut des Machines"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Entêtes de colonnes
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.4; add(new JLabel("Machine"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.4; add(new JLabel("Statut"), gbc);
        gbc.gridx = 2; gbc.weightx = 0.2; add(new JLabel("Actions"), gbc);

        int gridY = 1;
        for (Machine machine : machines) {
            // Nom de la machine
            gbc.gridx = 0; gbc.gridy = gridY;
            add(new JLabel(machine.getName()), gbc);

            // Label de statut
            gbc.gridx = 1;
            JLabel statusLabel = new JLabel("Initialisation...");
            statusLabel.setForeground(Color.GRAY);
            add(statusLabel, gbc);
            statusLabels.put(machine.getId(), statusLabel);

            // Bouton de redémarrage
            gbc.gridx = 2;
            JButton restartButton = new JButton("Redémarrer");
            restartButton.setEnabled(false); // Désactivé au départ
            add(restartButton, gbc);
            restartButtons.put(machine.getId(), restartButton);

            gridY++;
        }
        
        gbc.gridy = gridY; gbc.weighty = 1.0; add(new JPanel(), gbc);
    }

    public void updateMachineStatus(long machineId, String status, Color color) {
        SwingUtilities.invokeLater(() -> {
            JLabel label = statusLabels.get(machineId);
            if (label != null) {
                label.setText(status);
                label.setForeground(color);
                // Activer le bouton une fois la tentative de connexion terminée
                JButton button = restartButtons.get(machineId);
                if(button != null) button.setEnabled(true);
            }
        });
    }

    public JButton getRestartButton(long machineId) {
        return restartButtons.get(machineId);
    }
}
