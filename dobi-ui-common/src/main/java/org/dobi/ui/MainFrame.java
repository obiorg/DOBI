package org.dobi.ui;

import org.dobi.ui.TextAreaOutputStream;
import org.dobi.entities.Machine;
import javax.swing.*;
import java.awt.*;
import java.io.PrintStream;
import java.util.List;

public class MainFrame extends JFrame {
    private final MachineStatusPanel statusPanel;
    public MainFrame(List<Machine> machines) {
        setTitle("DOBI - Console de Supervision");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        statusPanel = new MachineStatusPanel(machines);
        JScrollPane statusScrollPane = new JScrollPane(statusPanel);
        statusScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        JTextArea logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        System.setOut(new PrintStream(new TextAreaOutputStream(logTextArea)));
        System.setErr(new PrintStream(new TextAreaOutputStream(logTextArea)));
        JScrollPane logScrollPane = new JScrollPane(logTextArea);
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, statusScrollPane, logScrollPane);
        splitPane.setResizeWeight(0.4);
        getContentPane().add(splitPane, BorderLayout.CENTER);
    }
    public MachineStatusPanel getStatusPanel() {
        return statusPanel;
    }
}
