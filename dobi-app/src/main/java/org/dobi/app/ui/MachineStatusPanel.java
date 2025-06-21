package org.dobi.app.ui;

import org.dobi.entities.Machine;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MachineStatusPanel extends JPanel {
    private final Map<Long, JLabel> statusLabels = new ConcurrentHashMap<>();
    public MachineStatusPanel(List<Machine> machines) {
        super(new GridBagLayout());
        setBorder(new TitledBorder("Statut des Machines"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;
        int gridY = 0;
        for (Machine machine : machines) {
            gbc.gridx = 0; gbc.gridy = gridY; gbc.weightx = 0.5;
            add(new JLabel(machine.getName()), gbc);
            gbc.gridx = 1;
            JLabel statusLabel = new JLabel("Initialisation...");
            statusLabel.setForeground(Color.GRAY);
            add(statusLabel, gbc);
            statusLabels.put(machine.getId(), statusLabel);
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
            }
        });
    }
}
