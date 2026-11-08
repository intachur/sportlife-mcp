package ru.sportlife.mcp.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import ru.sportlife.mcp.client.SportlifeClient;

@Component("sportlife")
public class SportlifeHealthIndicator implements HealthIndicator {

    private final SportlifeClient client;

    public SportlifeHealthIndicator(SportlifeClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            int clubs = client.ping();
            return Health.up().withDetail("clubs", clubs).build();
        } catch (RuntimeException e) {
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}
