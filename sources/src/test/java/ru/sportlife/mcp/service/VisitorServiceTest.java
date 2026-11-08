package ru.sportlife.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ru.sportlife.mcp.client.SportlifeClient;
import ru.sportlife.mcp.config.SportlifeProperties;
import ru.sportlife.mcp.model.Club;
import ru.sportlife.mcp.model.VisitorPoint;
import ru.sportlife.mcp.model.VisitorStatistics;
import ru.sportlife.mcp.repository.VisitorSnapshotRepository;

class VisitorServiceTest {

    private SportlifeClient client;
    private VisitorSnapshotRepository repository;
    private VisitorService service;

    @BeforeEach
    void setUp() {
        client = mock(SportlifeClient.class);
        repository = mock(VisitorSnapshotRepository.class);
        SportlifeProperties properties = new SportlifeProperties(
                "https://sync.sportlifeclub.ru",
                "token",
                "6",
                "ios",
                "3.9.10",
                "Europe/Moscow",
                Duration.ofSeconds(5),
                Duration.ofSeconds(15));
        service = new VisitorService(client, properties, repository);
    }

    @Test
    void captureCurrentVisitorsPersistsSnapshot() {
        when(client.getClubs()).thenReturn(List.of(new Club("6", "Меркурий", "b", 116)));

        Club club = service.captureCurrentVisitors();

        assertThat(club.people()).isEqualTo(116);
        verify(repository).save(eq("6"), eq(116), any(OffsetDateTime.class));
    }

    @Test
    void captureCurrentVisitorsReturnsClubWhenPersistenceFails() {
        when(client.getClubs()).thenReturn(List.of(new Club("6", "Меркурий", "b", 116)));
        doThrow(new IllegalStateException("db down"))
                .when(repository).save(eq("6"), eq(116), any(OffsetDateTime.class));

        assertThat(service.captureCurrentVisitors().people()).isEqualTo(116);
    }

    @Test
    void captureCurrentVisitorsReportsMissingClub() {
        when(client.getClubs()).thenReturn(List.of(new Club("1", "Аптекарский", "a", 56)));

        assertThatThrownBy(() -> service.captureCurrentVisitors())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1=Аптекарский");
    }

    @Test
    void statisticsAggregatesPoints() {
        OffsetDateTime from = OffsetDateTime.of(2026, 11, 3, 9, 0, 0, 0, ZoneOffset.ofHours(3));
        OffsetDateTime to = from.plusHours(1);
        when(repository.findBetween(eq("6"), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(new VisitorPoint(from, 100), new VisitorPoint(to, 140)));

        VisitorStatistics statistics = service.statistics("hour");

        assertThat(statistics.count()).isEqualTo(2);
        assertThat(statistics.average()).isEqualTo(120.0);
        assertThat(statistics.min()).isEqualTo(100);
        assertThat(statistics.max()).isEqualTo(140);
        assertThat(statistics.first()).isEqualTo(100);
        assertThat(statistics.last()).isEqualTo(140);
    }

    @Test
    void statisticsReturnsEmptyAggregatesWhenNoData() {
        when(repository.findBetween(eq("6"), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        VisitorStatistics statistics = service.statistics("day");

        assertThat(statistics.count()).isZero();
        assertThat(statistics.average()).isZero();
        assertThat(statistics.first()).isNull();
        assertThat(statistics.last()).isNull();
    }

    @Test
    void statisticsRejectsUnsupportedPeriod() {
        assertThatThrownBy(() -> service.statistics("month"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported period");
    }
}
