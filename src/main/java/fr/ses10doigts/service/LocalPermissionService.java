package fr.ses10doigts.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

/**
 * Gestion de la safe list pour l'accès local.
 *
 * Trois niveaux de permission :
 *   ONCE      — exécution unique, rien n'est mémorisé
 *   SESSION   — mémorisé en mémoire jusqu'au redémarrage de l'application
 *   PERMANENT — mémorisé en mémoire + persisté dans local-safe.json
 *
 * Le fichier local-safe.json est chargé au démarrage et mis à jour à chaque
 * approbation PERMANENT.
 */
@Service
public class LocalPermissionService {

    private static final Logger log = LoggerFactory.getLogger(LocalPermissionService.class);

    @Value("${local.safe-list-path:local-safe.json}")
    private String safeListPath;

    // Approuvés définitivement (chargés depuis le fichier + nouveaux PERMANENT)
    private final Set<String> permanentSafeCommands = new HashSet<>();
    private final Set<String> permanentSafePaths    = new HashSet<>();

    // Approuvés pour la session en cours uniquement
    private final Set<String> sessionSafeCommands = new HashSet<>();
    private final Set<String> sessionSafePaths    = new HashSet<>();

    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    public void load() {
        File f = new File(safeListPath);
        if (!f.exists()) {
            log.info("Aucune safe list locale trouvée ({}). Elle sera créée à la première approbation PERMANENT.", safeListPath);
            return;
        }
        try {
            SafeList list = mapper.readValue(f, SafeList.class);
            if (list.commands != null) permanentSafeCommands.addAll(list.commands);
            if (list.paths    != null) permanentSafePaths.addAll(list.paths);
            log.info("Safe list locale chargée : {} commandes, {} chemins ({})",
                    permanentSafeCommands.size(), permanentSafePaths.size(), safeListPath);
        } catch (Exception e) {
            log.warn("Impossible de charger la safe list depuis {} : {}", safeListPath, e.getMessage());
        }
    }

    public boolean isCommandSafe(String command) {
        return permanentSafeCommands.contains(command) || sessionSafeCommands.contains(command);
    }

    public boolean isPathSafeForWrite(String path) {
        return permanentSafePaths.contains(path) || sessionSafePaths.contains(path);
    }

    /**
     * Enregistre une commande selon le scope fourni par l'utilisateur.
     *
     * @param command commande shell locale
     * @param scope   "ONCE" | "SESSION" | "PERMANENT"
     */
    public void approveCommand(String command, String scope) {
        switch (scope.toUpperCase().trim()) {
            case "ONCE"      -> { /* rien à mémoriser */ }
            case "SESSION"   -> sessionSafeCommands.add(command);
            case "PERMANENT" -> { permanentSafeCommands.add(command); persist(); }
            default -> throw new IllegalArgumentException(
                "Scope invalide : '" + scope + "'. Valeurs acceptées : ONCE, SESSION, PERMANENT");
        }
        log.info("Commande locale approuvée ({}) : {}", scope.toUpperCase(), command);
    }

    /**
     * Enregistre un chemin de fichier selon le scope fourni par l'utilisateur.
     *
     * @param path  chemin absolu du fichier
     * @param scope "ONCE" | "SESSION" | "PERMANENT"
     */
    public void approvePath(String path, String scope) {
        switch (scope.toUpperCase().trim()) {
            case "ONCE"      -> { /* rien à mémoriser */ }
            case "SESSION"   -> sessionSafePaths.add(path);
            case "PERMANENT" -> { permanentSafePaths.add(path); persist(); }
            default -> throw new IllegalArgumentException(
                "Scope invalide : '" + scope + "'. Valeurs acceptées : ONCE, SESSION, PERMANENT");
        }
        log.info("Chemin local approuvé ({}) : {}", scope.toUpperCase(), path);
    }

    /** Retourne un résumé lisible de la safe list courante. */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("📋 Safe list locale\n");
        sb.append("═══════════════════\n");
        sb.append("Commandes permanentes (").append(permanentSafeCommands.size()).append(") :\n");
        permanentSafeCommands.stream().sorted().forEach(c -> sb.append("  • ").append(c).append("\n"));
        sb.append("Commandes session (").append(sessionSafeCommands.size()).append(") :\n");
        sessionSafeCommands.stream().sorted().forEach(c -> sb.append("  • ").append(c).append("\n"));
        sb.append("Chemins permanents (").append(permanentSafePaths.size()).append(") :\n");
        permanentSafePaths.stream().sorted().forEach(p -> sb.append("  • ").append(p).append("\n"));
        sb.append("Chemins session (").append(sessionSafePaths.size()).append(") :\n");
        sessionSafePaths.stream().sorted().forEach(p -> sb.append("  • ").append(p).append("\n"));
        return sb.toString().trim();
    }

    private void persist() {
        try {
            SafeList list = new SafeList();
            list.commands = new ArrayList<>(permanentSafeCommands);
            list.paths    = new ArrayList<>(permanentSafePaths);
            Collections.sort(list.commands);
            Collections.sort(list.paths);
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(safeListPath), list);
            log.info("Safe list persistée dans {}", safeListPath);
        } catch (Exception e) {
            log.error("Impossible de persister la safe list : {}", e.getMessage());
        }
    }

    /** Structure JSON du fichier local-safe.json */
    public static class SafeList {
        public List<String> commands;
        public List<String> paths;
    }
}
