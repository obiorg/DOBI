package org.dobi.app;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.io.OutputStream;
import java.io.IOException;

/**
 * Un OutputStream qui écrit son contenu dans une JTextArea.
 * Utilisé pour rediriger System.out et System.err vers l'interface graphique.
 */
public class TextAreaOutputStream extends OutputStream {

    private final JTextArea textArea;
    private final StringBuilder sb = new StringBuilder();

    public TextAreaOutputStream(final JTextArea textArea) {
        this.textArea = textArea;
    }

    @Override
    public void flush() {
        // Pas nécessaire pour ce type de stream
    }

    @Override
    public void close() {
        // Pas nécessaire pour ce type de stream
    }

    @Override
    public void write(int b) throws IOException {
        if (b == '\r') return; // On ignore les retours chariot
        
        if (b == '\n') {
            final String text = sb.toString() + "\n";
            SwingUtilities.invokeLater(() -> textArea.append(text));
            sb.setLength(0);
        } else {
            sb.append((char) b);
        }
    }
}
