package fr.ses10doigts;

import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;

public class TrayManager {
    private TrayIcon trayIcon;
    private final PopupMenu popupMenu;
    private final Menu tunnelsMenu;
    private final ActionListener disconnectAction;
    private final ActionListener reconnectAction;
    private final ActionListener quitAction;
    private final ActionListener reloadTorAction;
    private final LogWindow logWindow;

    public TrayManager(ActionListener disconnectAction, ActionListener reconnectAction, ActionListener quitAction, ActionListener reloadTorAction, LogWindow logWindow) {
        this.disconnectAction = disconnectAction;
        this.reconnectAction = reconnectAction;
        this.quitAction = quitAction;
        this.reloadTorAction = reloadTorAction;
        this.popupMenu = new PopupMenu();
        this.tunnelsMenu = new Menu("Tunnels");
        this.logWindow = logWindow;
    }

    public void init() throws Exception {
        if (!SystemTray.isSupported()) {
            System.err.println("⚠️ SystemTray non supporté sur cette machine");
            return;
        }

        Image image = Toolkit.getDefaultToolkit().createImage(
                TrayManager.class.getResource("/icon.png")
        );
        trayIcon = new TrayIcon(image, "SSH Tunnel Daemon");
        trayIcon.setImageAutoSize(true);

        popupMenu.add(tunnelsMenu);

        MenuItem logsItem = new MenuItem("Afficher les logs");
        logsItem.addActionListener(e -> logWindow.showWindow());
        popupMenu.add(logsItem);

        MenuItem disconnectItem = new MenuItem("Déconnecter");
        disconnectItem.addActionListener(disconnectAction);
        popupMenu.add(disconnectItem);

        MenuItem reconnectItem = new MenuItem("Reconnecter");
        reconnectItem.addActionListener(reconnectAction);
        popupMenu.add(reconnectItem);

        MenuItem reloadTorItem = new MenuItem("Restart Tor");
        reloadTorItem.addActionListener(reloadTorAction);
        popupMenu.add(reloadTorItem);

        MenuItem quitItem = new MenuItem("Quitter");
        quitItem.addActionListener(quitAction);
        popupMenu.add(quitItem);

        trayIcon.setPopupMenu(popupMenu);
        SystemTray.getSystemTray().add(trayIcon);
    }

    public void updateStatus(boolean connected, List<TunnelConfig> tunnels) {
        if (trayIcon == null) return;

        trayIcon.setToolTip("SSH Tunnel Daemon - " + (connected ? "✅ Connecté" : "❌ Déconnecté"));

        tunnelsMenu.removeAll();
        for (TunnelConfig tunnel : tunnels) {
            tunnelsMenu.add("localhost:" + tunnel.getLocalPort() +
                    " → " + tunnel.getRemoteHost() + ":" + tunnel.getRemotePort());
        }

        String iconFile = connected ? "/icon-green.png" : "/icon-red.png";
        Image image = Toolkit.getDefaultToolkit().createImage(
                TrayManager.class.getResource(iconFile)
        );
        trayIcon.setImage(image);
    }
}