package fr.ses10doigts;

import org.apache.commons.configuration.PropertiesConfiguration;

import java.awt.event.ActionEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class SshTunnelDaemon {
    public static void main(String[] args) throws Exception {
        PropertiesConfiguration config = new PropertiesConfiguration("application.properties");

        String host = config.getString("ssh.host");
        int port = config.getInt("ssh.port");
        String user = config.getString("ssh.user");
        String password = config.getString("ssh.password");

        int checkInterval = config.getInt("check.interval", 10);
        int reconnectDelay = config.getInt("reconnect.delay", 5);

        List<TunnelConfig> tunnels = new ArrayList<>();
        Iterator<String> keys = config.getKeys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.startsWith("tunnel.") && key.endsWith(".localPort")) {
                String prefix = key.substring(0, key.lastIndexOf('.'));
                int localPort = config.getInt(prefix + ".localPort");
                String remoteHost = config.getString(prefix + ".remoteHost");
                int remotePort = config.getInt(prefix + ".remotePort");
                tunnels.add(new TunnelConfig(localPort, remoteHost, remotePort));
            }
        }

        if (tunnels.isEmpty()) {
            System.err.println("⚠️ Aucun tunnel défini dans application.properties");
            return;
        }

        LogWindow logWindow = new LogWindow();
        SshTunnelManager manager = new SshTunnelManager(host, port, user, password, tunnels);

        TrayManager tray = new TrayManager(
                (ActionEvent e) -> {
                    logWindow.log(dateNow()+" : Déconnexion manuelle demandée.");
                    manager.disconnect();
                },
                (ActionEvent e) -> {
                    logWindow.log(dateNow()+" : Reconnexion manuelle demandée.");
                    manager.reconnect();
                },
                (ActionEvent e) -> {
                    logWindow.log(dateNow()+" : Arrêt du daemon.");
                    System.exit(0);
                },
                (ActionEvent e) -> {
                    logWindow.log(dateNow()+" : Redémarrage Tor.");
                    manager.restartTor();
                },
                logWindow
        );
        tray.init();

        while (true) {
            try {
                if (!manager.isConnected() && !manager.getIsManualDisconnect()) {
                    logWindow.log(dateNow()+" : Tentative de connexion...");
                    manager.connect();
                    logWindow.log(dateNow()+" : Connecté à " + host + ":" + port);
                }
                tray.updateStatus(manager.isConnected(), tunnels);

            } catch (Exception e) {
                logWindow.log(dateNow()+" : Erreur : " + e.getMessage());
                manager.disconnect();
                tray.updateStatus(false, tunnels);

            }finally {
                Thread.sleep(checkInterval * 1000L);
            }
        }
    }

    private static String dateNow(){
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
        return now.format(formatter);
    }
}