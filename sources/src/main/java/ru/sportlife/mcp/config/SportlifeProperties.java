package ru.sportlife.mcp.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sportlife")
public record SportlifeProperties(
        String baseUrl,
        String authToken,
        String clubId,
        String platform,
        String appVersion,
        String zoneId,
        Duration connectTimeout,
        Duration readTimeout) {
}
