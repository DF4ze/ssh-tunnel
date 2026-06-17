package fr.ses10doigts.mcp;

import fr.ses10doigts.service.PlaybookService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Outils MCP pour le CI/CD via le playbook.
 *
 * Workflow typique depuis n'importe quelle conversation Claude :
 *
 *   1. listOperations()           → voir les opérations disponibles
 *   2. executeOperation("build:X") → lancer en arrière-plan
 *   3. checkOperation("build:X")   → suivre l'avancement / voir le résultat
 *
 * Les opérations sont définies dans playbook.json (rechargé au redémarrage du gateway).
 * Pour ajouter/modifier une opération : éditer playbook.json + redémarrer le gateway.
 */
@Component
public class PlaybookTools {

    private final PlaybookService playbookService;

    public PlaybookTools(PlaybookService playbookService) {
        this.playbookService = playbookService;
    }

    @Tool(description = """
            Liste toutes les opérations CI/CD configurées dans le playbook.
            Retourne le nom, la description et le nombre de steps de chaque opération.
            Indique aussi si une opération est actuellement en cours d'exécution.
            À appeler en premier pour connaître les opérations disponibles.
            """)
    public String listOperations() {
        return playbookService.listOperations();
    }

    @Tool(description = """
            Lance une opération CI/CD en arrière-plan (build, deploy, ou les deux).
            L'opération doit être définie dans playbook.json — utiliser listOperations() pour connaître les noms.
            Retourne immédiatement un message de confirmation avec le chemin du log.
            L'exécution est asynchrone : les steps s'enchaînent sans bloquer.
            Appeler ensuite checkOperation(name) pour suivre l'avancement.

            Exemples de noms d'opérations : "build:mon-projet", "deploy:mon-projet", "build-deploy:mon-projet"
            """)
    public String executeOperation(
            @ToolParam(description = "Nom exact de l'opération (tel que défini dans le playbook)") String name) {
        return playbookService.executeOperation(name);
    }

    @Tool(description = """
            Retourne le statut courant d'une opération CI/CD + les dernières lignes de log.
            Statuts possibles : RUNNING (en cours), SUCCESS (terminé OK), FAILED (échec).
            En cas d'échec, les dernières lignes de log contiennent le détail de l'erreur.
            À appeler après executeOperation() pour suivre l'avancement ou voir le résultat final.
            """)
    public String checkOperation(
            @ToolParam(description = "Nom exact de l'opération à vérifier") String name,
            @ToolParam(description = "Nombre de lignes de log à retourner (défaut: 50, max recommandé: 100)", required = false) Integer tailLines) {
        return playbookService.checkOperation(name, tailLines != null ? tailLines : 50);
    }
}
