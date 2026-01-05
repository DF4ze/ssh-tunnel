package fr.ses10doigts;

public class TunnelConfig {
    private final int localPort;
    private final String remoteHost;
    private final int remotePort;

    public TunnelConfig(int localPort, String remoteHost, int remotePort) {
        this.localPort = localPort;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    public int getLocalPort() {
        return localPort;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public int getRemotePort() {
        return remotePort;
    }
}