package org.dobi.app;

import javax.swing.*;
import java.awt.*;
import java.io.PrintStream;

public class MainFrame extends JFrame {

    public MainFrame() {
        setTitle("DOBI - Console de Supervision");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE); // Cacher la fenêtre au lieu de quitter
        setSize(800, 600);
        setLocationRelativeTo(null); // Centrer la fenêtre

        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        // Rediriger System.out et System.err vers notre JTextArea
        PrintStream printStream = new PrintStream(new TextAreaOutputStream(textArea));
        System.setOut(printStream);
        System.setErr(printStream);

        JScrollPane scrollPane = new JScrollPane(textArea);
        getContentPane().add(scrollPane, BorderLayout.CENTER);
    }
}
