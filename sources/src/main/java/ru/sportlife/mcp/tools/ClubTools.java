package ru.sportlife.mcp.tools;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

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

@Component
public class ClubTools {

    private static final Logger log = LoggerFactory.getLogger(ClubTools.class);

    private final SportlifeClient client;
    private final SportlifeProperties properties;
    private final VisitorService visitorService;
    private final ClubEventService clubEventService;
    private final ObjectMapper objectMapper;

    public ClubTools(
            SportlifeClient client,
            SportlifeProperties properties,
            VisitorService visitorService,
            ClubEventService clubEventService,
            ObjectMapper objectMapper) {
        this.client = client;
        this.properties = properties;
        this.visitorService = visitorService;
        this.clubEventService = clubEventService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "get_current_visitors",
            description = "Current number of visitors inside the club right now")
    public String getCurrentVisitors() {
        try {
            Club club = visitorService.captureCurrentVisitors();

            ObjectNode result = objectMapper.createObjectNode();
            result.put("clubId", club.id());
            result.put("clubName", club.name());
            result.put("visitors", club.people());
            result.put("checkedAt", OffsetDateTime.now(zone()).toString());
            return result.toString();
        } catch (RuntimeException e) {
            return error("get_current_visitors", e);
        }
    }

    @Tool(name = "get_contract_remaining_days",
            description = "Remaining days of the active club contract")
    public String getContractRemainingDays() {
        try {
            ClubCard card = client.getClubCard();
            Contract contract = card.currentContracts().stream()
                    .filter(item -> "active".equals(item.status()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No active club contract found"));

            ObjectNode result = objectMapper.createObjectNode();
            result.put("contractId", contract.id());
            result.put("tariff", contract.name());
            result.put("status", contract.status());
            result.put("finishDate", contract.finishDate());
            result.put("contractRemainDays", contract.contractRemainDays());
            result.put("remainDays", contract.remainDays());
            return result.toString();
        } catch (RuntimeException e) {
            return error("get_contract_remaining_days", e);
        }
    }

    @Tool(name = "get_today_club_events",
            description = "Club activities and events scheduled for today")
    public String getTodayClubEvents() {
        try {
            LocalDate today = LocalDate.now(zone());
            List<Activity> activities = clubEventService.todayEvents(today);

            ObjectNode result = objectMapper.createObjectNode();
            result.put("date", today.toString());
            result.put("clubId", properties.clubId());
            ArrayNode events = result.putArray("events");
            for (Activity activity : activities) {
                ObjectNode event = events.addObject();
                event.put("name", activity.name());
                event.put("start", activity.date());
                event.put("finish", activity.dateFinish());
                event.put("hall", activity.hallName());
                event.put("trainer", activity.trainerName());
            }
            return result.toString();
        } catch (RuntimeException e) {
            return error("get_today_club_events", e);
        }
    }

    @Tool(name = "get_body_care_specialists",
            description = "Availability of body care specialists in the club")
    public String getBodyCareSpecialists() {
        try {
            List<Masseur> masseurs = client.getMasseurs();

            ObjectNode result = objectMapper.createObjectNode();
            result.put("clubId", properties.clubId());
            ArrayNode specialists = result.putArray("specialists");
            for (Masseur masseur : masseurs) {
                ObjectNode specialist = specialists.addObject();
                specialist.put("name", masseur.name());
                specialist.put("hall", masseur.hallName());
                specialist.put("availableDate", masseur.availableDate());
            }
            return result.toString();
        } catch (RuntimeException e) {
            return error("get_body_care_specialists", e);
        }
    }

    @Tool(name = "get_visitor_statistics",
            description = "Visitor count statistics for the last hour, day or week")
    public String getVisitorStatistics(
            @ToolParam(description = "Interval: hour, day or week") String period) {
        try {
            VisitorStatistics statistics = visitorService.statistics(period);

            ObjectNode result = objectMapper.createObjectNode();
            result.put("clubId", statistics.clubId());
            result.put("period", statistics.period());
            result.put("from", statistics.from().toString());
            result.put("to", statistics.to().toString());
            result.put("count", statistics.count());
            result.put("average", Math.round(statistics.average() * 10) / 10.0);
            result.put("min", statistics.min());
            result.put("max", statistics.max());
            if (statistics.first() == null) {
                result.putNull("first");
            } else {
                result.put("first", statistics.first());
            }
            if (statistics.last() == null) {
                result.putNull("last");
            } else {
                result.put("last", statistics.last());
            }
            ArrayNode points = result.putArray("points");
            for (VisitorPoint point : statistics.points()) {
                ObjectNode item = points.addObject();
                item.put("capturedAt", point.capturedAt().toString());
                item.put("visitors", point.visitors());
            }
            return result.toString();
        } catch (RuntimeException e) {
            return error("get_visitor_statistics", e);
        }
    }

    private ZoneId zone() {
        return ZoneId.of(properties.zoneId());
    }

    private String error(String tool, RuntimeException e) {
        log.warn("MCP tool {} failed", tool, e);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        return result.toString();
    }
}
