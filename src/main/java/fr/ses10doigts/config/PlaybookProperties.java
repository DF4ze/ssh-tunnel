package fr.ses10doigts.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriétés du playbook CI/CD (préfixe "playbook" dans application.properties).
 *
 * playbook.path       : chemin absolu vers le fichier playbook.json
 * playbook.output-dir : répertoire où sont écrits les logs d'exécution
 */
@Component
@ConfigurationProperties(prefix = "playbook")
public class PlaybookProperties {

    /** Chemin du fichier playbook.json (relatif au répertoire du JAR, ou absolu). */
    private String path = "playbook.json";

    /** Répertoire de sortie pour les logs et status des opérations. */
    private String outputDir = "C:\\temp\\gateway-ops";

    public String getPath()                     { return path; }
    public void setPath(String path)            { this.path = path; }

    public String getOutputDir()                { return outputDir; }
    public void setOutputDir(String outputDir)  { this.outputDir = outputDir; }
}
