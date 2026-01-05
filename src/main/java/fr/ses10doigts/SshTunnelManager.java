package fr.ses10doigts;

import com.jcraft.jsch.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class SshTunnelManager {
    private Session session;
    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final List<TunnelConfig> tunnels;
    private boolean isManualDisconnect = false;

    public boolean getIsManualDisconnect(){
        return isManualDisconnect;
    }

    public SshTunnelManager(String host, int port, String user, String password, List<TunnelConfig> tunnels) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        this.tunnels = tunnels;
    }

    public void connect() throws Exception {
        if (session != null && session.isConnected()) {
            return;
        }
        JSch jsch = new JSch();
        session = jsch.getSession(user, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");

        System.out.println("Connexion SSH à " + host + "...");
        session.connect();

        for (TunnelConfig tunnel : tunnels) {
            session.setPortForwardingL(
                    tunnel.getLocalPort(),
                    tunnel.getRemoteHost(),
                    tunnel.getRemotePort()
            );
            System.out.println("Tunnel actif : localhost:" + tunnel.getLocalPort() +
                    " -> " + tunnel.getRemoteHost() + ":" + tunnel.getRemotePort());
        }
    }

    public boolean isConnected() {
        return session != null && session.isConnected();
    }

    public void reconnect() {
        isManualDisconnect = false;
        if (session != null && session.isConnected()) {
            session.disconnect();
            System.out.println("Connexion SSH fermée.");
        }
    }
    public void disconnect() {
        isManualDisconnect = true;
        if (session != null && session.isConnected()) {
            session.disconnect();
            System.out.println("Connexion SSH fermée.");
        }
    }

    public void restartTor()  {
        try {
            System.out.println("Redémarrage Tor");
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("sudo systemctl restart tor.service"); // la commande à exécuter
            OutputStream out = channel.getOutputStream();
            InputStream in = channel.getInputStream();

            channel.connect();

            // envoyer le mot de passe suivi d’un retour à la ligne
            out.write(("V!veLaV!e31!Oklm" + "\n").getBytes());
            out.flush();
        } catch (Exception e) {
            System.out.println("Error restarting Tor : "+ e.getMessage());
        }
    }
}