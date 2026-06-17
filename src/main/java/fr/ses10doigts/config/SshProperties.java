package fr.ses10doigts.config;

import fr.ses10doigts.model.TunnelConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Propriétés SSH lues depuis application.properties (préfixe "ssh").
 */
@Component
@ConfigurationProperties(prefix = "ssh")
public class SshProperties {

    private String host;
    private int port = 22;
    private String user;
    private String password;
    /** Intervalle de vérification de la connexion en secondes */
    private int checkInterval = 10;
    private List<TunnelConfig> tunnels = new ArrayList<>();

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public int getCheckInterval() { return checkInterval; }
    public void setCheckInterval(int checkInterval) { this.checkInterval = checkInterval; }

    public List<TunnelConfig> getTunnels() { return tunnels; }
    public void setTunnels(List<TunnelConfig> tunnels) { this.tunnels = tunnels; }
}
