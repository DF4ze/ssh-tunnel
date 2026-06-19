package fr.ses10doigts.mcp;

import fr.ses10doigts.config.GatewayProperties;
import fr.ses10doigts.service.MaintenanceService;
import fr.ses10doigts.service.SshTunnelService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Outils MCP exposés à Claude via Spring AI (Streamable HTTP).
 *
 * Cette classe est la couche d'adaptation entre le protocole MCP et les services
 * métier existants. Elle ne contient pas de logique — uniquement des délégations
 * avec des descriptions claires pour que Claude sache quand et comment utiliser
 * chaque outil.
 */
@Component
public class McpGatewayTools {

    private final SshTunnelService sshService;
    private final MaintenanceService maintenanceService;
    private final GatewayProperties gatewayProperties;

    public McpGatewayTools(SshTunnelService sshService, MaintenanceService maintenanceService,
                           GatewayProperties gatewayProperties) {
        this.sshService = sshService;
        this.maintenanceService = maintenanceService;
        this.gatewayProperties = gatewayProperties;
    }

    // ─── Statut & connexion ───────────────────────────────────────────────────

    @Tool(description = """
            Retourne le statut complet de la gateway SSH :
            - connexion SSH (connecté/déconnecté, déconnexion manuelle)
            - liste des tunnels port-forwarding actifs (localPort → remoteHost:remotePort)
            - liste des services systemd gérables (whitelist)
            Appeler en premier pour diagnostiquer l'état général du serveur.
            """)
    public String getStatus() {
        boolean connected = sshService.isConnected();
        StringBuilder sb = new StringBuilder();
        sb.append("Connexion SSH : ").append(connected ? "✅ Connecté" : "❌ Déconnecté").append("\n");
        sb.append("Déconnexion manuelle : ").append(sshService.isManualDisconnect() ? "oui" : "non").append("\n\n");

        sb.append("Tunnels actifs :\n");
        sshService.getTunnels().forEach(t ->
            sb.append("  • localhost:").append(t.getLocalPort())
              .append(" → ").append(t.getRemoteHost()).append(":").append(t.getRemotePort()).append("\n")
        );

        sb.append("\nServices gérables : ").append(maintenanceService.getManagedServices());
        return sb.toString();
    }

    @Tool(description = """
            Force une reconnexion SSH vers le serveur distant.
            Utile si la connexion est tombée et que la reconnexion automatique n'a pas encore eu lieu,
            ou si la gateway était en mode déconnexion manuelle.
            """)
    public String reconnect() {
        sshService.reconnect();
        return "Reconnexion SSH demandée. La connexion sera rétablie dans quelques secondes.";
    }

    // ─── Services systemd ─────────────────────────────────────────────────────

    @Tool(description = """
            Retourne l'état courant (is-active) de tous les services gérés en une seule passe.
            Utile pour un aperçu rapide de la santé de tous les services.
            """)
    public String getAllServicesStatus() {
        try {
            return maintenanceService.getAllServicesStatus();
        } catch (Exception e) {
            return "Erreur : " + e.getMessage();
        }
    }

    @Tool(description = """
            Retourne les N dernières lignes de journal (journalctl) d'un service systemd.
            Utile pour diagnostiquer des erreurs ou vérifier l'activité récente d'un service.
            Le service doit être dans la liste des services gérables (whitelist).
            """)
    public String getServiceLogs(
            @ToolParam(description = "Nom du service systemd (ex: tor, mariadb)") String serviceName,
            @ToolParam(description = "Nombre de lignes à retourner (défaut: 100, max recommandé: 200)") int lines) {
        try {
            return maintenanceService.getServiceLogs(serviceName, lines);
        } catch (Exception e) {
            return "Erreur : " + e.getMessage();
        }
    }

    // ─── Transfert de fichiers ────────────────────────────────────────────────

    @Tool(description = """
            Transfère un fichier de la machine locale vers le serveur distant via SFTP.
            Réutilise la connexion SSH existante — aucune configuration supplémentaire.
            Les répertoires distants parents sont créés automatiquement si absents.

            Retours structurés :
              ✅ Upload réussi — avec nom, destination, taille, durée et vitesse
              ❌ [CONNEXION]  — session SSH down ; appeler reconnect() puis réessayer
              ❌ [SOURCE]     — fichier local introuvable ou chemin invalide
              ❌ [SFTP]       — erreur distante (permission refusée, disque plein, etc.)

            Usage typique : déployer un JAR après mvn package.
              localPath  = D:\\Documents\\Spring\\monapp\\target\\monapp-2.1.0.jar
              remotePath = /opt/apps/monapp/monapp-2.1.0.jar
            """)
    public String uploadFile(
            @ToolParam(description = "Chemin absolu du fichier local à envoyer") String localPath,
            @ToolParam(description = "Chemin absolu de destination sur le serveur distant") String remotePath) {
        try {
            return sshService.uploadFile(localPath, remotePath);
        } catch (IllegalStateException e) {
            return "❌ [CONNEXION] " + e.getMessage() + " — appeler reconnect() puis réessayer.";
        } catch (IllegalArgumentException e) {
            return "❌ [SOURCE] " + e.getMessage();
        } catch (Exception e) {
            return "❌ [SFTP] Erreur de transfert : " + e.getMessage();
        }
    }

    // ─── Redémarrage du gateway ───────────────────────────────────────────────

    @Tool(description = """
            Redémarre le gateway lui-même.
            Écrit un fichier trigger puis appelle System.exit(0) — le bat de lancement détecte
            le trigger et relance automatiquement le JAR.
            La connexion MCP sera coupée ~3-5 secondes puis rétablie automatiquement.
            À utiliser après un déploiement d'une nouvelle version du JAR, ou si le gateway
            se comporte anormalement et nécessite un redémarrage propre.
            """)
    public String restartGateway() {
        try {
            Path trigger = Path.of(gatewayProperties.getRestartTriggerPath());
            Files.writeString(trigger, "restart requested at " + LocalDateTime.now(), StandardCharsets.UTF_8);
            // Laisser ~1s pour que la réponse MCP soit envoyée avant l'arrêt
            new Thread(() -> {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                System.exit(0);
            }, "restart-thread").start();
            return "🔄 Redémarrage en cours. Le gateway s'arrête et redémarre automatiquement.\n"
                 + "La connexion MCP sera brièvement interrompue puis rétablie.";
        } catch (Exception e) {
            return "❌ Impossible de déclencher le redémarrage : " + e.getMessage();
        }
    }

    // ─── Commandes arbitraires ────────────────────────────────────────────────

    @Tool(description = """
            Exécute une commande shell arbitraire sur le serveur distant via SSH.
            Exemples utiles : df -h, free -m, ps aux | grep java, uptime, uname -a,
            ss -tlnp, cat /var/log/syslog | tail -50, etc.
            Ne pas utiliser pour des commandes déjà couvertes par les autres outils
            (préférer getAllServicesStatus/getServiceLogs pour les services systemd).
            """)
    public String execCommand(
            @ToolParam(description = "Commande shell à exécuter sur le serveur distant") String command) {
        try {
            return sshService.executeCommand(command);
        } catch (Exception e) {
            return "Erreur : " + e.getMessage();
        }
    }
}
