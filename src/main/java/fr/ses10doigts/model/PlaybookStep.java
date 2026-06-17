package fr.ses10doigts.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Un step dans une opération CI/CD.
 *
 * Types supportés :
 *   local   — commande shell exécutée sur la machine locale (Windows)
 *   remote  — commande shell exécutée sur le serveur distant via SSH
 *   upload  — transfert d'un fichier local vers le serveur distant via SFTP
 *
 * Champs selon le type :
 *   local  : command
 *   remote : command
 *   upload : localPath, remotePath
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlaybookStep {

    /** Type du step : "local", "remote", "upload" */
    private String type;

    /** Commande shell (pour types "local" et "remote") */
    private String command;

    /** Chemin local source (pour type "upload") */
    private String localPath;

    /** Chemin distant destination (pour type "upload") */
    private String remotePath;

    /** Description facultative du step (affichée dans les logs) */
    private String description;

    public String getType()                     { return type; }
    public void setType(String type)            { this.type = type; }

    public String getCommand()                  { return command; }
    public void setCommand(String command)      { this.command = command; }

    public String getLocalPath()                { return localPath; }
    public void setLocalPath(String localPath)  { this.localPath = localPath; }

    public String getRemotePath()               { return remotePath; }
    public void setRemotePath(String remotePath){ this.remotePath = remotePath; }

    public String getDescription()              { return description; }
    public void setDescription(String desc)     { this.description = desc; }

    @Override
    public String toString() {
        return switch (type == null ? "" : type) {
            case "upload" -> "upload " + localPath + " → " + remotePath;
            default       -> "[" + type + "] " + command;
        };
    }
}
