package ru.sportlife.mcp.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ru.sportlife.mcp.tools.ClubTools;

@Configuration
public class McpToolConfig {

    @Bean
    ToolCallbackProvider clubToolCallbackProvider(ClubTools clubTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(clubTools)
                .build();
    }
}
