package fr.ses10doigts.controller;

import fr.ses10doigts.model.TunnelConfig;
import fr.ses10doigts.service.MaintenanceService;
import fr.ses10doigts.service.SshTunnelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API REST de maintenance - écoute sur 127.0.0.1 uniquement (voir application.properties).
 * Tous les endpoints sont sous /api/.
 */
@RestController
@RequestMapping("/api")
public class GatewayController {

    private final SshTunnelService sshService;
    private final MaintenanceService maintenanceService;

    public GatewayController(SshTunnelService sshService, MaintenanceService maintenanceService) {
        this.sshService = sshService;
        this.maintenanceService = maintenanceService;
    }

    // ─── Statut général ───────────────────────────────────────────────────────

    /**
     * GET /api/status
     * Retourne l'état de la connexion SSH, les tunnels et l'heure serveur.
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("connected", sshService.isConnected());
        status.put("manualDisconnect", sshService.isManualDisconnect());
        status.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        List<Map<String, Object>> tunnelList = sshService.getTunnels().stream()
            .map(t -> {
                Map<String, Object> tm = new LinkedHashMap<>();
                tm.put("localPort", t.getLocalPort());
                tm.put("remoteHost", t.getRemoteHost());
                tm.put("remotePort", t.getRemotePort());
                return tm;
            })
            .toList();
        status.put("tunnels", tunnelList);
        status.put("managedServices", maintenanceService.getManagedServices());
        return status;
    }

    // ─── Tunnels ──────────────────────────────────────────────────────────────

    /**
     * GET /api/tunnels
     * Liste des tunnels port-forwarding configurés.
     */
    @GetMapping("/tunnels")
    public List<TunnelConfig> getTunnels() {
        return sshService.getTunnels();
    }

    // ─── Connexion SSH ────────────────────────────────────────────────────────

    /**
     * POST /api/connect
     * Force une reconnexion SSH.
     */
    @PostMapping("/connect")
    public ResponseEntity<Map<String, String>> connect() {
        sshService.reconnect();
        return ok("Reconnexion demandée");
    }

    /**
     * POST /api/disconnect
     * Déconnecte la session SSH (mode manuel, pas de reconnexion auto).
     */
    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, String>> disconnect() {
        sshService.disconnect();
        return ok("Déconnexion effectuée");
    }

    // ─── Services systemd ─────────────────────────────────────────────────────

    /**
     * GET /api/services
     * Statut de tous les services gérés.
     */
    @GetMapping("/services")
    public ResponseEntity<Map<String, Object>> getAllServices() {
        try {
            String output = maintenanceService.getAllServicesStatus();
            return ResponseEntity.ok(Map.of("result", output));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    /**
     * GET /api/service/{name}/logs?lines=100
     * journalctl -n <lines> -u <service>
     */
    @GetMapping("/service/{name}/logs")
    public ResponseEntity<Map<String, Object>> getServiceLogs(
            @PathVariable String name,
            @RequestParam(defaultValue = "100") int lines) {
        try {
            String output = maintenanceService.getServiceLogs(name, lines);
            return ResponseEntity.ok(Map.of("service", name, "lines", lines, "result", output));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    /**
     * POST /api/exec
     * Exécute une commande SSH arbitraire.
     * Body JSON : { "command": "df -h" }
     */
    @PostMapping("/exec")
    public ResponseEntity<Map<String, Object>> exec(@RequestBody Map<String, String> body) {
        String command = body.get("command");
        if (command == null || command.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Paramètre 'command' manquant"));
        }
        try {
            String output = sshService.executeCommand(command);
            return ResponseEntity.ok(Map.of("command", command, "result", output));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, String>> ok(String message) {
        return ResponseEntity.ok(Map.of("message", message));
    }

    private ResponseEntity<Map<String, Object>> error(String message) {
        return ResponseEntity.internalServerError().body(Map.of("error", message));
    }
}
