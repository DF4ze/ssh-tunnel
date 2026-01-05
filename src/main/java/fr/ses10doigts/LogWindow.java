package fr.ses10doigts;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;

public class LogWindow {
    private JFrame frame;
    private JTextArea textArea;

    public LogWindow() {
        frame = new JFrame("SSH Tunnel Logs");
        frame.setSize(600, 400);
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE); // Ne ferme pas l'appli

        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font("Consolas", Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(textArea);
        frame.add(scrollPane);

        // Auto-scroll
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