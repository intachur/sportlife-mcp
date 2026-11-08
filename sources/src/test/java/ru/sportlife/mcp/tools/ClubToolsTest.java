package ru.sportlife.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ru.sportlife.mcp.client.SportlifeClient;
import ru.sportlife.mcp.config.SportlifeProperties;
import ru.sportlife.mcp.model.Activity;
import ru.sportlife.mcp.model.Club;
import ru.sportlife.mcp.model.ClubCard;
import ru.sportlife.mcp.model.Contract;
import ru.sportlife.mcp.model.Masseur;
import ru.sportlife.mcp.model.VisitorPoint;
import ru.sportlife.mcp.model.VisitorStatistics;
import ru.sportlife.mcp.service.ClubEventService;
import ru.sportlife.mcp.service.VisitorService;

class ClubToolsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SportlifeClient client;
    private VisitorService visitorService;
    private ClubEventService clubEventService;
    private ClubTools tools;

    @BeforeEach
    void setUp() {
        client = mock(SportlifeClient.class);
        visitorService = mock(VisitorService.class);
        clubEventService = mock(ClubEventService.class);
        SportlifeProperties properties = new SportlifeProperties(
                "https://sync.sportlifeclub.ru",
                "token",
                "6",
                "ios",
                "3.9.10",
                "Europe/Moscow",
                Duration.ofSeconds(5),
                Duration.ofSeconds(15));
        tools = new ClubTools(client, properties, visitorService, clubEventService, objectMapper);
    }

    @Test
    void getCurrentVisitorsReturnsClubPeople() throws Exception {
        when(visitorService.captureCurrentVisitors()).thenReturn(new Club("6", "Меркурий", "b", 116));

        JsonNode result = objectMapper.readTree(tools.getCurrentVisitors());

        assertThat(result.path("clubName").asText()).isEqualTo("Меркурий");
        assertThat(result.path("visitors").asInt()).isEqualTo(116);
    }

    @Test
    void getCurrentVisitorsReportsError() throws Exception {
        when(visitorService.captureCurrentVisitors())
                .thenThrow(new IllegalStateException("Club 6 not found, available clubs: [1=Аптекарский]"));

        JsonNode result = objectMapper.readTree(tools.getCurrentVisitors());

        assertThat(result.path("error").asText()).contains("1=Аптекарский");
    }

    @Test
    void getContractRemainingDaysReturnsActiveContract() throws Exception {
        when(client.getClubCard()).thenReturn(new ClubCard("ООО \"ГалаСпорт\"", List.of(
                new Contract("1294850", "Полный", "active",
                        "2026-08-11T00:00:00.000", "2027-08-20T00:00:00.000", 281, 291))));

        JsonNode result = objectMapper.readTree(tools.getContractRemainingDays());

        assertThat(result.path("contractRemainDays").asInt()).isEqualTo(281);
        assertThat(result.path("finishDate").asText()).isEqualTo("2027-08-20T00:00:00.000");
    }

    @Test
    void getContractRemainingDaysReportsMissingContract() throws Exception {
        when(client.getClubCard()).thenReturn(new ClubCard("ООО \"ГалаСпорт\"", List.of()));

        JsonNode result = objectMapper.readTree(tools.getContractRemainingDays());

        assertThat(result.path("error").asText()).contains("No active club contract");
    }

    @Test
    void getTodayClubEventsReturnsActivities() throws Exception {
        when(clubEventService.todayEvents()).thenReturn(List.of(
                new Activity("1", "Йога", "2026-11-03T10:00:00.000", "2026-11-03T11:00:00.000",
                        "Зал №1", "Иванов Иван")));

        JsonNode result = objectMapper.readTree(tools.getTodayClubEvents());

        assertThat(result.path("events")).hasSize(1);
        assertThat(result.path("events").get(0).path("name").asText()).isEqualTo("Йога");
        assertThat(result.path("events").get(0).path("hall").asText()).isEqualTo("Зал №1");
    }

    @Test
    void getTodayClubEventsReturnsEmptyList() throws Exception {
        when(clubEventService.todayEvents()).thenReturn(List.of());

        JsonNode result = objectMapper.readTree(tools.getTodayClubEvents());

        assertThat(result.path("events")).isEmpty();
    }

    @Test
    void getBodyCareSpecialistsReturnsSpecialists() throws Exception {
        when(client.getMasseurs()).thenReturn(List.of(
                new Masseur("12475", "Смирнова Марина", "Меркурий",
                        "Кабинет по уходу за телом", "2026-11-04T00:00:00.000")));

        JsonNode result = objectMapper.readTree(tools.getBodyCareSpecialists());

        assertThat(result.path("specialists")).hasSize(1);
        assertThat(result.path("specialists").get(0).path("name").asText()).isEqualTo("Смирнова Марина");
    }

    @Test
    void getVisitorStatisticsReturnsAggregatesAndPoints() throws Exception {
        OffsetDateTime from = OffsetDateTime.of(2026, 11, 3, 9, 0, 0, 0, ZoneOffset.ofHours(3));
        OffsetDateTime to = from.plusHours(1);
        when(visitorService.statistics("hour")).thenReturn(new VisitorStatistics(
                "6", "hour", from, to, 2, 120.0, 100, 140, 100, 140,
                List.of(new VisitorPoint(from, 100), new VisitorPoint(to, 140))));

        JsonNode result = objectMapper.readTree(tools.getVisitorStatistics("hour"));

        assertThat(result.path("period").asText()).isEqualTo("hour");
        assertThat(result.path("count").asInt()).isEqualTo(2);
        assertThat(result.path("average").asDouble()).isEqualTo(120.0);
        assertThat(result.path("min").asInt()).isEqualTo(100);
        assertThat(result.path("max").asInt()).isEqualTo(140);
        assertThat(result.path("points")).hasSize(2);
    }

    @Test
    void getVisitorStatisticsReportsUnsupportedPeriod() throws Exception {
        when(visitorService.statistics("month"))
                .thenThrow(new IllegalArgumentException("Unsupported period: month"));

        JsonNode result = objectMapper.readTree(tools.getVisitorStatistics("month"));

        assertThat(result.path("error").asText()).contains("Unsupported period");
    }
}
