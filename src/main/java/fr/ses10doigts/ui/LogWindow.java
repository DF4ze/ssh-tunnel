package fr.ses10doigts.ui;

import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;

/**
 * Fenêtre de logs Swing (cachée par défaut, accessible via le systray).
 * Inchangée fonctionnellement par rapport à v1.
 */
@Component
public class LogWindow {

    private final JFrame frame;
    private final JTextArea textArea;

    public LogWindow() {
        frame = new JFrame("SSH Tunnel Gateway - Logs");
        frame.setSize(700, 450);
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font("Consolas", Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(textArea);
        frame.add(scrollPane);

        DefaultCaret caret = (DefaultCaret) textArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
    }

    public void showWindow() {
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
    }

    public void hideWindow() {
        SwingUtilities.invokeLater(() -> frame.setVisible(false));
    }

    public void log(String message) {
        SwingUtilities.invokeLater(() -> textArea.append(message + "\n"));
    }
}
