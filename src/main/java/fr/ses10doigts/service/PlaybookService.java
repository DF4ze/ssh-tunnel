package fr.ses10doigts.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.ses10doigts.config.PlaybookProperties;
import fr.ses10doigts.model.PlaybookOperation;
import fr.ses10doigts.model.PlaybookStep;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service CI/CD — charge le playbook.json et orchestre l'exécution des opérations.
 *
 * Chaque opération s'exécute dans un thread dédié (non-bloquant).
 * Les logs et le status sont écrits dans :
 *   {outputDir}/{nom-opération-sanitisé}/output.log
 *   {outputDir}/{nom-opération-sanitisé}/status.txt
 *
 * Status possibles dans status.txt : RUNNING, SUCCESS, FAILED
 */
@Service
public class PlaybookService {

    private static final Logger log = LoggerFactory.getLogger(PlaybookService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PlaybookProperties   props;
    private final SshTunnelService     sshService;
    private final ObjectMapper         mapper = new ObjectMapper();

    /** Opérations chargées depuis playbook.json */
    private Map<String, PlaybookOperation> operations = new LinkedHashMap<>();

    /** Opérations en cours d'exécution (évite les lancements en double) */
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    public PlaybookService(PlaybookProperties props, SshTunnelService sshService) {
        this.props      = props;
        this.sshService = sshService;
    }

    // ─── Chargement ──────────────────────────────────────────────────────────

    @PostConstruct
    public void load() {
        File f = new File(props.getPath());
        if (!f.exists()) {
            log.warn("Playbook introuvable : {}. Aucune opération CI/CD disponible.", props.getPath());
            return;
        }
        try {
            PlaybookFile pf = mapper.readValue(f, PlaybookFile.class);
            operations = pf.operations != null ? pf.operations : new LinkedHashMap<>();
            log.info("Playbook chargé : {} opération(s) depuis {}", operations.size(), props.getPath());
        } catch (Exception e) {
            log.error("Erreur de chargement du playbook {} : {}", props.getPath(), e.getMessage());
        }
    }

    // ─── API publique ─────────────────────────────────────────────────────────

    /**
     * Liste les opérations disponibles avec leur description et nombre de steps.
     */
    public String listOperations() {
        if (operations.isEmpty()) {
            return "⚠️ Aucune opération configurée. Vérifiez " + props.getPath();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("📋 Opérations CI/CD disponibles\n");
        sb.append("════════════════════════════════\n");
        operations.forEach((name, op) -> {
            String status = running.contains(name) ? " 🔄 EN COURS" : "";
            sb.append("  • ").append(name).append(status).append("\n");
            if (op.getDescription() != null) {
                sb.append("    ").append(op.getDescription()).append("\n");
            }
            sb.append("    ").append(op.getSteps().size()).append(" step(s)\n");
        });
        return sb.toString().trim();
    }

    /**
     * Lance une opération en arrière-plan.
     * Retourne immédiatement avec un message de confirmation ou d'erreur.
     */
    public String executeOperation(String name) {
        PlaybookOperation op = operations.get(name);
        if (op == null) {
            return "❌ Opération inconnue : '" + name + "'\n"
                 + "Opérations disponibles : " + String.join(", ", operations.keySet());
        }
        if (running.contains(name)) {
            return "⚠️ L'opération '" + name + "' est déjà en cours. "
                 + "Utilisez checkOperation(\"" + name + "\") pour suivre l'avancement.";
        }

        Path outDir = operationDir(name);
        try {
            Files.createDirectories(outDir);
            writeStatus(outDir, "RUNNING");
            // Vider le log précédent
            Files.writeString(outDir.resolve("output.log"), "", StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "❌ Impossible de créer le répertoire de log : " + e.getMessage();
        }

        running.add(name);
        Thread thread = new Thread(() -> runOperation(name, op, outDir), "playbook-" + sanitize(name));
        thread.setDaemon(true);
        thread.start();

        return "🚀 Opération '" + name + "' lancée en arrière-plan.\n"
             + "Logs : " + outDir.resolve("output.log") + "\n"
             + "→ Appelez checkOperation(\"" + name + "\") pour voir l'avancement.";
    }

    /**
     * Retourne le status courant d'une opération + les N dernières lignes de log.
     */
    public String checkOperation(String name, int tailLines) {
        Path outDir = operationDir(name);
        Path statusFile = outDir.resolve("status.txt");
        Path logFile    = outDir.resolve("output.log");

        if (!Files.exists(statusFile)) {
            if (!operations.containsKey(name)) {
                return "❌ Opération inconnue : '" + name + "'";
            }
            return "⏸ L'opération '" + name + "' n'a jamais été lancée dans cette session.";
        }

        try {
            String status = Files.readString(statusFile, StandardCharsets.UTF_8).trim();
            String icon   = switch (status) {
                case "SUCCESS" -> "✅";
                case "FAILED"  -> "❌";
                case "RUNNING" -> "🔄";
                default        -> "❓";
            };

            StringBuilder sb = new StringBuilder();
            sb.append(icon).append(" Statut : ").append(status).append("\n");
            sb.append("─────────────────────────────────\n");

            if (Files.exists(logFile)) {
                List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
                int from = Math.max(0, lines.size() - tailLines);
                if (from > 0) sb.append("[… ").append(from).append(" lignes omises]\n");
                lines.subList(from, lines.size()).forEach(l -> sb.append(l).append("\n"));
            }

            return sb.toString().trim();
        } catch (IOException e) {
            return "❌ Erreur de lecture des logs : " + e.getMessage();
        }
    }

    // ─── Exécution en arrière-plan ────────────────────────────────────────────

    private void runOperation(String name, PlaybookOperation op, Path outDir) {
        Path logFile = outDir.resolve("output.log");
        try (PrintWriter out = new PrintWriter(new FileWriter(logFile.toFile(), StandardCharsets.UTF_8, true))) {

            out.println(ts() + " ═══ Début : " + name + " ═══");
            if (op.getDescription() != null) {
                out.println(ts() + " Description : " + op.getDescription());
            }
            out.println(ts() + " Steps : " + op.getSteps().size());
            out.println();
            out.flush();

            for (int i = 0; i < op.getSteps().size(); i++) {
                PlaybookStep step = op.getSteps().get(i);
                out.println(ts() + " ── Step " + (i + 1) + "/" + op.getSteps().size() + " : " + step);
                out.flush();

                try {
                    String result = executeStep(step, out);
                    out.println(result);
                    out.println();
                    out.flush();
                } catch (Exception e) {
                    out.println("❌ ERREUR : " + e.getMessage());
                    out.flush();
                    writeStatus(outDir, "FAILED");
                    log.error("Playbook '{}' - échec step {} : {}", name, i + 1, e.getMessage());
                    return;
                }
            }

            out.println();
            out.println(ts() + " ═══ Terminé avec succès : " + name + " ═══");
            out.flush();
            writeStatus(outDir, "SUCCESS");
            log.info("Playbook '{}' terminé avec succès.", name);

        } catch (IOException e) {
            log.error("Playbook '{}' - erreur d'écriture des logs : {}", name, e.getMessage());
            writeStatus(outDir, "FAILED");
        } finally {
            running.remove(name);
        }
    }

    private String executeStep(PlaybookStep step, PrintWriter logOut) throws Exception {
        return switch (step.getType()) {
            case "local"  -> executeLocal(step.getCommand(), logOut);
            case "remote" -> executeRemote(step.getCommand());
            case "upload" -> sshService.uploadFile(step.getLocalPath(), step.getRemotePath());
            default       -> throw new IllegalArgumentException("Type de step inconnu : '" + step.getType() + "'");
        };
    }

    /**
     * Exécute une commande locale sans timeout (pour les builds Maven longs).
     * Redirige stdout+stderr vers le fichier de log en temps réel.
     */
    private String executeLocal(String command, PrintWriter logOut) throws IOException, InterruptedException {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        ProcessBuilder pb = isWindows
                ? new ProcessBuilder("cmd.exe", "/c", command)
                : new ProcessBuilder("/bin/sh", "-c", command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // Lire stdout en temps réel et écrire dans le log
        byte[] buffer = new byte[4096];
        var inputStream = process.getInputStream();
        StringBuilder fullOutput = new StringBuilder();

        Thread reader = new Thread(() -> {
            try {
                int n;
                while ((n = inputStream.read(buffer)) != -1) {
                    String chunk = new String(buffer, 0, n, isWindows ? "Cp1252" : "UTF-8");
                    fullOutput.append(chunk);
                    logOut.print(chunk);
                    logOut.flush();
                }
            } catch (IOException ignored) {}
        });
        reader.setDaemon(true);
        reader.start();

        int exitCode = process.waitFor();
        reader.join(5000); // attendre que le reader finisse

        if (exitCode != 0) {
            throw new RuntimeException("Exit code " + exitCode + " — voir logs ci-dessus");
        }
        return "✅ Commande locale terminée (exit 0)";
    }

    private String executeRemote(String command) throws Exception {
        return sshService.executeCommand(command);
    }

    // ─── Utilitaires ─────────────────────────────────────────────────────────

    private Path operationDir(String name) {
        return Path.of(props.getOutputDir(), sanitize(name));
    }

    private void writeStatus(Path outDir, String status) {
        try {
            Files.writeString(outDir.resolve("status.txt"), status, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Impossible d'écrire le status '{}' : {}", status, e.getMessage());
        }
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "-");
    }

    private String ts() {
        return "[" + LocalDateTime.now().format(TS) + "]";
    }

    // ─── Structure JSON du playbook.json ─────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlaybookFile {
        public Map<String, PlaybookOperation> operations;
    }
}
