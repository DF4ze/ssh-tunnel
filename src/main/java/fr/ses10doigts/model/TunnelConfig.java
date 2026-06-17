package fr.ses10doigts.model;

/**
 * Configuration d'un tunnel SSH (port forwarding local → remote).
 * Doit avoir un constructeur no-arg et des setters pour @ConfigurationProperties.
 */
public class TunnelConfig {

    private int localPort;
    private String remoteHost;
    private int remotePort;

    public TunnelConfig() {}

    public TunnelConfig(int localPort, String remoteHost, int remotePort) {
        this.localPort = localPort;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    public int getLocalPort() { return localPort; }
    public void setLocalPort(int localPort) { this.localPort = localPort; }

    public String getRemoteHost() { return remoteHost; }
    public void setRemoteHost(String remoteHost) { this.remoteHost = remoteHost; }

    public int getRemotePort() { return remotePort; }
    public void setRemotePort(int remotePort) { this.remotePort = remotePort; }

    @Override
    public String toString() {
        return "localhost:" + localPort + " -> " + remoteHost + ":" + remotePort;
    }
}
