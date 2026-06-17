package fr.ses10doigts.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Une opération CI/CD nommée, composée d'une séquence de steps.
 *
 * Exemple dans playbook.json :
 * {
 *   "description": "Build CoursesCrawler en local",
 *   "steps": [
 *     { "type": "local", "command": "..." }
 *   ]
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlaybookOperation {

    /** Description humaine de l'opération (affichée dans listOperations) */
    private String description;

    /** Séquence de steps à exécuter dans l'ordre */
    private List<PlaybookStep> steps = new ArrayList<>();

    public String getDescription()                      { return description; }
    public void setDescription(String description)      { this.description = description; }

    public List<PlaybookStep> getSteps()                { return steps; }
    public void setSteps(List<PlaybookStep> steps)      { this.steps = steps; }
}
