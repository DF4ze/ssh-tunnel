package fr.ses10doigts.mcp;

import fr.ses10doigts.service.LocalAccessService;
import fr.ses10doigts.service.LocalPermissionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Outils MCP pour accès à la machine locale.
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  LECTURE (toujours autorisée, aucune permission requise)            │
 * │  • readLocalFile    — lit un fichier                                │
 * │  • listLocalDir     — liste un répertoire                           │
 * │  • getLocalSystemInfo — CPU, RAM, disques                           │
 * │  • getLocalSafeList — affiche les commandes/chemins approuvés       │
 * ├─────────────────────────────────────────────────────────────────────┤
 * │  ÉCRITURE / EXÉCUTION (nécessitent une approbation utilisateur)     │
 * │  • execLocalCommand — commande shell locale                         │
 * │  • writeLocalFile   — écriture de fichier local                     │
 * │                                                                     │
 * │  WORKFLOW OBLIGATOIRE pour ces outils :                             │
 * │  1. Appeler avec approvedScope = "" → si PERMISSION_REQUIRED :      │
 * │  2. Demander à l'utilisateur dans la conversation                   │
 * │  3. Rappeler avec approvedScope = "ONCE" | "SESSION" | "PERMANENT"  │
 * └─────────────────────────────────────────────────────────────────────┘
 */
@Component
public class LocalAccessTools {

    private static final String PERMISSION_REQUIRED = "PERMISSION_REQUIRED";

    private final LocalAccessService      localService;
    private final LocalPermissionService  permService;

    public LocalAccessTools(LocalAccessService localService, LocalPermissionService permService) {
        this.localService = localService;
        this.permService  = permService;
    }

    // ─── Lecture (toujours autorisée) ────────────────────────────────────────

    @Tool(description = """
            Lit le contenu d'un fichier sur la machine locale de l'utilisateur.
            Lecture seule — aucune permission requise.
            Limite : 512 KB. Pour les fichiers plus volumineux, utiliser execLocalCommand (head, tail, grep…).

            ⚠️ RÈGLE D'UTILISATION (Claude-Cowork) :
            N'utiliser QUE pour des fichiers HORS du workspace monté Claude-Cowork (ex: D:\\Installs\\, C:\\Users\\..., etc.).
            Pour tout fichier dans le workspace monté (ex: D:\\Documents\\Spring\\), Claude dispose de son outil
            natif Read — plus direct, sans workflow de permission. Ce tool existe pour les chemins
            normalement inaccessibles à Claude-Cowork (config système, dossiers d'installation, logs Windows…).
            """)
    public String readLocalFile(
            @ToolParam(description = "Chemin absolu du fichier (ex: C:\\Users\\user\\project\\pom.xml ou /home/user/project/pom.xml)") String path) {
        try {
            return localService.readFile(path);
        } catch (Exception e) {
            return "❌ Erreur : " + e.getMessage();
        }
    }

    @Tool(description = """
            Liste le contenu d'un répertoire local (fichiers et sous-dossiers avec taille).
            Lecture seule — aucune permission requise.

            ⚠️ RÈGLE D'UTILISATION (Claude-Cowork) :
            N'utiliser QUE pour des répertoires HORS du workspace monté Claude-Cowork.
            Pour les dossiers dans le workspace (ex: D:\\Documents\\Spring\\), préférer la commande
            Bash native (ls) ou Glob — plus rapide, sans passer par ce tool.
            """)
    public String listLocalDir(
            @ToolParam(description = "Chemin absolu du répertoire (ex: D:\\Documents\\Spring ou /home/user/project)") String path) {
        try {
            return localService.listDir(path);
        } catch (Exception e) {
            return "❌ Erreur : " + e.getMessage();
        }
    }

    @Tool(description = """
            Retourne les informations système de la machine locale :
            OS, architecture, JVM, nombre de CPUs, charge CPU, RAM JVM, espace disque par volume.
            Lecture seule — aucune permission requise.
            """)
    public String getLocalSystemInfo() {
        return localService.getSystemInfo();
    }

    @Tool(description = """
            Affiche la safe list locale actuelle :
            - commandes approuvées de façon permanente (persistent dans local-safe.json)
            - commandes approuvées pour la session en cours (in-memory)
            - chemins de fichiers approuvés (idem, deux niveaux)
            Utile pour auditer ou expliquer pourquoi une commande s'exécute sans demande.
            """)
    public String getLocalSafeList() {
        return permService.getSummary();
    }

    // ─── Exécution locale (permission requise si non approuvée) ─────────────

    @Tool(description = """
            Exécute une commande shell sur la MACHINE LOCALE de l'utilisateur (pas le serveur distant).

            ⚠️ RÈGLE D'UTILISATION (Claude-Cowork) :
            Ce tool est RÉSERVÉ aux actions de maintenance système qui dépassent le périmètre
            habituel de Claude-Cowork : accès à D:\\Installs\\, C:\\Windows\\, registre Windows,
            schtasks, services Windows, chemins hors workspace, etc.
            Pour toute commande opérant uniquement sur le workspace monté (D:\\Documents\\Spring\\),
            utiliser l'outil Bash natif de Claude-Cowork — il n'impose pas de workflow de permission.

            Cas d'usage typiques via ce tool : mkdir hors workspace, copy de JAR vers D:\\Installs\\,
            schtasks, powershell sur des ressources système, ipconfig, netstat, sc query, etc.

            ╔══ WORKFLOW OBLIGATOIRE ══════════════════════════════════════════╗
            ║ 1. Premier appel : approvedScope = ""                           ║
            ║    → Si la réponse commence par PERMISSION_REQUIRED :           ║
            ║ 2. Demander EXPLICITEMENT à l'utilisateur dans la conversation :║
            ║    "Autoriser la commande locale suivante ?                     ║
            ║     → [commande]                                                ║
            ║     Répondre : une fois | session | toujours"                   ║
            ║ 3. Second appel avec la réponse traduite :                      ║
            ║    une fois → ONCE  |  session → SESSION  |  toujours → PERMANENT║
            ╚═════════════════════════════════════════════════════════════════╝

            Si la commande est déjà dans la safe list, elle s'exécute directement à l'étape 1.
            Timeout : 30 secondes. Sortie max : 50 000 caractères.
            """)
    public String execLocalCommand(
            @ToolParam(description = "Commande shell à exécuter localement") String command,
            @ToolParam(description = "Scope d'approbation : laisser vide pour vérification, ONCE, SESSION ou PERMANENT", required = false) String approvedScope) {
        try {
            boolean alreadySafe = permService.isCommandSafe(command);
            boolean hasScope    = approvedScope != null && !approvedScope.isBlank();

            if (!alreadySafe && !hasScope) {
                return PERMISSION_REQUIRED + ": commande='" + command + "'\n"
                     + "⚠️ Cette commande n'est pas dans la safe list.\n"
                     + "Demandez à l'utilisateur dans la conversation :\n"
                     + "  \"Autoriser localement : " + command + " ? (une fois / session / toujours)\"\n"
                     + "Puis rappeler avec approvedScope = ONCE | SESSION | PERMANENT selon la réponse.";
            }

            if (hasScope) {
                permService.approveCommand(command, approvedScope);
            }
            return localService.execCommand(command);

        } catch (IllegalArgumentException e) {
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            return "❌ Erreur d'exécution : " + e.getMessage();
        }
    }

    // ─── Écriture locale (permission requise si chemin non approuvé) ─────────

    @Tool(description = """
            Écrit (ou écrase) un fichier sur la machine locale de l'utilisateur.
            ATTENTION : l'écriture est immédiate et irréversible.

            ⚠️ RÈGLE D'UTILISATION (Claude-Cowork) :
            N'utiliser QUE pour des fichiers HORS du workspace monté Claude-Cowork.
            Pour écrire dans le workspace (ex: D:\\Documents\\Spring\\), utiliser les outils
            natifs Write/Edit de Claude-Cowork — plus directs, sans workflow de permission.
            Ce tool est fait pour les dossiers système hors périmètre : D:\\Installs\\,
            C:\\ProgramData\\, fichiers de config Windows, etc.

            ╔══ WORKFLOW OBLIGATOIRE ══════════════════════════════════════════╗
            ║ 1. Premier appel : approvedScope = ""                           ║
            ║    → Si la réponse commence par PERMISSION_REQUIRED :           ║
            ║ 2. Demander EXPLICITEMENT à l'utilisateur dans la conversation :║
            ║    "Autoriser l'écriture dans ce fichier ?                      ║
            ║     → [chemin]                                                  ║
            ║     Répondre : une fois | session | toujours"                   ║
            ║ 3. Second appel avec approvedScope = ONCE | SESSION | PERMANENT ║
            ╚═════════════════════════════════════════════════════════════════╝

            Si le chemin est déjà dans la safe list, l'écriture s'effectue directement.
            """)
    public String writeLocalFile(
            @ToolParam(description = "Chemin absolu du fichier à écrire ou créer") String path,
            @ToolParam(description = "Contenu complet à écrire dans le fichier") String content,
            @ToolParam(description = "Scope d'approbation : laisser vide pour vérification, ONCE, SESSION ou PERMANENT", required = false) String approvedScope) {
        try {
            boolean alreadySafe = permService.isPathSafeForWrite(path);
            boolean hasScope    = approvedScope != null && !approvedScope.isBlank();

            if (!alreadySafe && !hasScope) {
                return PERMISSION_REQUIRED + ": chemin='" + path + "'\n"
                     + "⚠️ Ce chemin n'est pas dans la safe list d'écriture.\n"
                     + "Demandez à l'utilisateur dans la conversation :\n"
                     + "  \"Autoriser l'écriture dans : " + path + " ? (une fois / session / toujours)\"\n"
                     + "Puis rappeler avec approvedScope = ONCE | SESSION | PERMANENT selon la réponse.";
            }

            if (hasScope) {
                permService.approvePath(path, approvedScope);
            }
            return localService.writeFile(path, content);

        } catch (IllegalArgumentException e) {
            return "❌ " + e.getMessage();
        } catch (Exception e) {
            return "❌ Erreur d'écriture : " + e.getMessage();
        }
    }
}
