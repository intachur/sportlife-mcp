package ru.sportlife.mcp.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import ru.sportlife.mcp.client.SportlifeClient;

class SportlifeHealthIndicatorTest {

    private final SportlifeClient client = mock(SportlifeClient.class);
    private final SportlifeHealthIndicator indicator = new SportlifeHealthIndicator(client);

    @Test
    void reportsUpWhenApiAvailable() {
        when(client.ping()).thenReturn(12);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails()).containsEntry("clubs", 12);
    }

    @Test
    void reportsDownWhenApiFails() {
        when(client.ping()).thenThrow(new IllegalStateException("boom"));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }
}
