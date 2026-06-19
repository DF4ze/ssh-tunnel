package fr.ses10doigts.ui;

import fr.ses10doigts.config.GatewayProperties;
import fr.ses10doigts.model.TunnelConfig;
import fr.ses10doigts.service.MaintenanceService;
import fr.ses10doigts.service.SshTunnelService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Gestion de l'icône systray Windows.
 * Initialisé via ApplicationReadyEvent (Spring Boot est prêt avant de toucher AWT).
 */
@Component
public class TrayManager {

    private final SshTunnelService sshService;
    private final MaintenanceService maintenanceService;
    private final LogWindow logWindow;
    private final GatewayProperties gatewayProperties;

    private TrayIcon trayIcon;
    private final PopupMenu popupMenu = new PopupMenu();
    private final Menu tunnelsMenu = new Menu("Tunnels");

    // Icônes pré-chargées au démarrage (ImageIO = synchrone, pas d'image vide)
    private Image iconDefault;
    private Image iconGreen;
    private Image iconRed;

    public TrayManager(SshTunnelService sshService, MaintenanceService maintenanceService,
                       LogWindow logWindow, GatewayProperties gatewayProperties) {
        this.sshService = sshService;
        this.maintenanceService = maintenanceService;
        this.logWindow = logWindow;
        this.gatewayProperties = gatewayProperties;
    }

    /**
     * Initialisé une fois que Spring Boot et le contexte sont prêts.
     * On injecte aussi le logCallback ici pour éviter la dépendance circulaire.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        // Brancher le callback de log sur la LogWindow
        sshService.setLogCallback(msg -> logWindow.log(dateNow() + " : " + msg));

        if (!SystemTray.isSupported()) {
            System.err.println("SystemTray non supporté");
            return;
        }

        // Pré-chargement synchrone des icônes (évite l'icône vide au changement d'état)
        iconDefault = loadImage("/icon.png");
        iconGreen   = loadImage("/icon-green.png");
        iconRed     = loadImage("/icon-red.png");

        trayIcon = new TrayIcon(iconDefault != null ? iconDefault : new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB), "SSH Tunnel Gateway");
        trayIcon.setImageAutoSize(true);

        popupMenu.add(tunnelsMenu);

        MenuItem logsItem = new MenuItem("Afficher les logs");
        logsItem.addActionListener(e -> logWindow.showWindow());
        popupMenu.add(logsItem);

        MenuItem disconnectItem = new MenuItem("Déconnecter");
        disconnectItem.addActionListener(e -> {
            logWindow.log(dateNow() + " : Déconnexion manuelle demandée.");
            sshService.disconnect();
            updateStatus();
        });
        popupMenu.add(disconnectItem);

        MenuItem reconnectItem = new MenuItem("Reconnecter");
        reconnectItem.addActionListener(e -> {
            logWindow.log(dateNow() + " : Reconnexion manuelle demandée.");
            sshService.reconnect();
        });
        popupMenu.add(reconnectItem);

        MenuItem restartTorItem = new MenuItem("Restart Tor");
        restartTorItem.addActionListener(e -> {
            logWindow.log(dateNow() + " : Restart Tor demandé...");
            new Thread(() -> {
                try {
                    String result = sshService.executeCommand("systemctl restart tor", gatewayProperties.getSudoPassword());
                    logWindow.log(dateNow() + " : Tor restarted. " + (result.isEmpty() ? "OK" : result));
                } catch (Exception ex) {
                    logWindow.log(dateNow() + " : Erreur restart Tor : " + ex.getMessage());
                }
            }).start();
        });
        popupMenu.add(restartTorItem);

        popupMenu.addSeparator();

        MenuItem restartGatewayItem = new MenuItem("Redémarrer le gateway");
        restartGatewayItem.addActionListener(e -> {
            logWindow.log(dateNow() + " : Redémarrage du gateway demandé via systray...");
            try {
                Path trigger = Path.of(gatewayProperties.getRestartTriggerPath());
                Files.writeString(trigger, "restart requested at " + LocalDateTime.now(), StandardCharsets.UTF_8);
                logWindow.log(dateNow() + " : Trigger écrit — redémarrage dans 1 seconde.");
                new Thread(() -> {
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                    System.exit(0);
                }, "restart-thread").start();
            } catch (Exception ex) {
                logWindow.log(dateNow() + " : ❌ Impossible de redémarrer : " + ex.getMessage());
            }
        });
        popupMenu.add(restartGatewayItem);

        MenuItem quitItem = new MenuItem("Quitter");
        quitItem.addActionListener(e -> {
            logWindow.log(dateNow() + " : Arrêt de la gateway.");
            System.exit(0);
        });
        popupMenu.add(quitItem);

        trayIcon.setPopupMenu(popupMenu);

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            System.err.println("Impossible d'ajouter l'icône systray : " + e.getMessage());
        }

        // Mise à jour périodique du statut dans le systray
        new Thread(this::statusUpdateLoop, "tray-status-updater").start();
    }

    private void statusUpdateLoop() {
        while (true) {
            try {
                updateStatus();
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void updateStatus() {
        if (trayIcon == null) return;
        boolean connected = sshService.isConnected();
        List<TunnelConfig> tunnels = sshService.getTunnels();

        // Toutes les opérations AWT doivent s'exécuter sur l'EDT
        // (évite la corruption graphique du sous-menu Tunnels lors d'un removeAll() concurrent)
        EventQueue.invokeLater(() -> {
            trayIcon.setToolTip("SSH Tunnel Gateway - " + (connected ? "✅ Connecté" : "❌ Déconnecté"));

            tunnelsMenu.removeAll();
            for (TunnelConfig tunnel : tunnels) {
                tunnelsMenu.add("localhost:" + tunnel.getLocalPort()
                    + " → " + tunnel.getRemoteHost() + ":" + tunnel.getRemotePort());
            }

            Image icon = connected ? iconGreen : iconRed;
            if (icon != null) trayIcon.setImage(icon);
        });
    }

    private Image loadImage(String resource) {
        try {
            var url = TrayManager.class.getResource(resource);
            if (url == null) { System.err.println("Ressource introuvable : " + resource); return null; }
            return ImageIO.read(url);
        } catch (IOException e) {
            System.err.println("Erreur chargement image " + resource + " : " + e.getMessage());
            return null;
        }
    }

    private String dateNow() {
        return java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
    }
}
