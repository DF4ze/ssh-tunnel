package fr.ses10doigts.service;

import fr.ses10doigts.config.GatewayProperties;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service de maintenance : exécute des actions systemd sur le serveur distant
 * via SshTunnelService, avec whitelist des services autorisés.
 */
@Service
public class MaintenanceService {

    private final SshTunnelService sshService;
    private final GatewayProperties gatewayProps;

    public MaintenanceService(SshTunnelService sshService, GatewayProperties gatewayProps) {
        this.sshService = sshService;
        this.gatewayProps = gatewayProps;
    }

    /**
     * Retourne la liste des services gérables (whitelist depuis application.properties).
     */
    public List<String> getManagedServices() {
        return gatewayProps.getManagedServices();
    }

    /**
     * Vérifie que le service est dans la whitelist avant toute action.
     */
    private void assertServiceAllowed(String serviceName) {
        if (!gatewayProps.getManagedServices().contains(serviceName)) {
            throw new IllegalArgumentException(
                "Service '" + serviceName + "' non autorisé. Services gérables : "
                + gatewayProps.getManagedServices()
            );
        }
    }



    /**
     * journalctl -n 100 -u <service> --no-pager
     */
    public String getServiceLogs(String serviceName, int lines) throws Exception {
        assertServiceAllowed(serviceName);
        return sshService.executeCommand(
            "journalctl -n " + lines + " -u " + serviceName + " --no-pager"
        );
    }

    /**
     * Statut de tous les services gérés en une passe.
     */
    public String getAllServicesStatus() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (String service : gatewayProps.getManagedServices()) {
            try {
                sb.append("=== ").append(service).append(" ===\n");
                sb.append(sshService.executeCommand("systemctl is-active " + service));
                sb.append("\n\n");
            } catch (Exception e) {
                sb.append("Erreur : ").append(e.getMessage()).append("\n\n");
            }
        }
        return sb.toString().trim();
    }
}
