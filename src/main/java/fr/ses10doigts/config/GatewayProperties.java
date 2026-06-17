package fr.ses10doigts.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Propriétés de la gateway lues depuis application.properties (préfixe "gateway").
 */
@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    /** Mot de passe sudo pour les commandes systemctl nécessitant des privilèges */
    private String sudoPassword;

    /**
     * Liste des services systemd autorisés à être gérés via l'API.
     * Whitelist de sécurité : seuls ces services peuvent être restartés/consultés.
     */
    private List<String> managedServices = new ArrayList<>();

    /**
     * Chemin du fichier trigger pour le redémarrage du gateway.
     * Quand ce fichier est créé, gateway-watcher.ps1 tue et relance le process.
     */
    private String restartTriggerPath = "D:\\Installs\\Ssh-tunnel\\restart.trigger";

    public String getSudoPassword() { return sudoPassword; }
    public void setSudoPassword(String sudoPassword) { this.sudoPassword = sudoPassword; }

    public List<String> getManagedServices() { return managedServices; }
    public void setManagedServices(List<String> managedServices) { this.managedServices = managedServices; }

    public String getRestartTriggerPath() { return restartTriggerPath; }
    public void setRestartTriggerPath(String restartTriggerPath) { this.restartTriggerPath = restartTriggerPath; }
}
