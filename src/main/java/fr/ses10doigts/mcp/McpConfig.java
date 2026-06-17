package fr.ses10doigts.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// LocalAccessTools est dans le même package — pas d'import nécessaire

/**
 * Enregistre les outils MCP de la gateway auprès de Spring AI.
 * Spring AI auto-découvre ce bean et l'expose via l'endpoint /mcp.
 */
@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider gatewayTools(McpGatewayTools mcpGatewayTools,
                                             LocalAccessTools localAccessTools,
                                             PlaybookTools playbookTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(mcpGatewayTools, localAccessTools, playbookTools)
                .build();
    }
}
