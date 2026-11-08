package ru.sportlife.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ru.sportlife.mcp.client.SportlifeClient;
import ru.sportlife.mcp.config.SportlifeProperties;
import ru.sportlife.mcp.model.Activity;
import ru.sportlife.mcp.model.ClubEventRecord;
import ru.sportlife.mcp.repository.ClubEventRepository;

class ClubEventServiceTest {

    private SportlifeClient client;
    private ClubEventRepository repository;
    private ClubEventService service;

    @BeforeEach
    void setUp() {
        client = mock(SportlifeClient.class);
        repository = mock(ClubEventRepository.class);
        SportlifeProperties properties = new SportlifeProperties(
                "https://sync.sportlifeclub.ru",
                "token",
                "6",
                "ios",
                "3.9.10",
                "Europe/Moscow",
                Duration.ofSeconds(5),
                Duration.ofSeconds(15));
        service = new ClubEventService(client, properties, repository);
    }

    @Test
    void todayEventsPersistsMappedRecords() {
        when(client.getActivities(any(LocalDate.class))).thenReturn(List.of(
                new Activity("1", "Йога", "2026-11-03T10:00:00.000", "2026-11-03T11:00:00.000",
                        "Зал №1", "Иванов Иван")));

        List<Activity> activities = service.todayEvents();

        assertThat(activities).hasSize(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClubEventRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).upsertAll(eq("6"), captor.capture());
        ClubEventRecord record = captor.getValue().getFirst();
        assertThat(record.externalId()).isEqualTo("1");
        assertThat(record.name()).isEqualTo("Йога");
        assertThat(record.startsAt()).isEqualTo(OffsetDateTime.of(2026, 11, 3, 10, 0, 0, 0, ZoneOffset.ofHours(3)));
        assertThat(record.endsAt()).isEqualTo(OffsetDateTime.of(2026, 11, 3, 11, 0, 0, 0, ZoneOffset.ofHours(3)));
    }

    @Test
    void todayEventsReturnsActivitiesWhenPersistenceFails() {
        when(client.getActivities(any(LocalDate.class))).thenReturn(List.of(
                new Activity("1", "Йога", "2026-11-03T10:00:00.000", "2026-11-03T11:00:00.000",
                        "Зал №1", "Иванов Иван")));
        doThrow(new IllegalStateException("db down"))
                .when(repository).upsertAll(eq("6"), any());

        assertThat(service.todayEvents()).hasSize(1);
    }
}
