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
import ru.sportlife.mcp.model.MasseurClub;
import ru.sportlife.mcp.model.MasseurDetail;
import ru.sportlife.mcp.model.MasseurScheduleDay;
import ru.sportlife.mcp.model.MasseurSlot;
import ru.sportlife.mcp.model.Trainer;
import ru.sportlife.mcp.model.TrainerActivity;
import ru.sportlife.mcp.model.TrainerDetail;
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
            description = "Club activities and events for a date (defaults to today)")
    public String getTodayClubEvents(
            @ToolParam(required = false, description = "Day in ISO format (yyyy-MM-dd). Omit for today")
                    String date) {
        try {
            LocalDate today = (date == null || date.isBlank()) ? LocalDate.now(zone()) : LocalDate.parse(date);
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
                specialist.put("id", masseur.id());
                specialist.put("name", masseur.name());
                specialist.put("hall", masseur.hallName());
                specialist.put("availableDate", masseur.availableDate());
            }
            return result.toString();
        } catch (RuntimeException e) {
            return error("get_body_care_specialists", e);
        }
    }

    @Tool(name = "get_specialist_schedule",
            description = "Body care specialist details and appointment slots by day")
    public String getSpecialistSchedule(
            @ToolParam(description = "Specialist id from get_body_care_specialists") String masseurId,
            @ToolParam(required = false, description = "Day in ISO format (yyyy-MM-dd). Omit to return all days")
                    String date) {
        try {
            MasseurDetail detail = client.getMasseurDetail(masseurId);

            ObjectNode result = objectMapper.createObjectNode();
            result.put("specialistId", detail.id());
            result.put("name", detail.name());
            result.put("description", detail.description());
            result.put("price", detail.price());
            result.put("specialization", detail.resume().specialization());
            result.put("education", detail.resume().education());
            result.put("experience", detail.resume().experience());
            ArrayNode schedule = result.putArray("schedule");
            for (MasseurScheduleDay day : detail.schedule()) {
                if (date != null && !date.isBlank() && !day.date().startsWith(date)) {
                    continue;
                }
                ObjectNode dayNode = schedule.addObject();
                dayNode.put("date", day.date());
                ArrayNode clubs = dayNode.putArray("clubs");
                for (MasseurClub club : day.clubs()) {
                    ObjectNode clubNode = clubs.addObject();
                    clubNode.put("club", club.clubName());
                    ArrayNode slots = clubNode.putArray("slots");
                    for (MasseurSlot slot : club.slots()) {
                        ObjectNode slotNode = slots.addObject();
                        slotNode.put("id", slot.id());
                        slotNode.put("time", slot.time());
                        slotNode.put("available", slot.available());
                    }
                }
            }
            return result.toString();
        } catch (RuntimeException e) {
            return error("get_specialist_schedule", e);
        }
    }

    @Tool(name = "get_trainers",
            description = "Club personal trainers with speciality and price")
    public String getTrainers() {
        try {
            List<Trainer> trainers = client.getTrainers();

            ObjectNode result = objectMapper.createObjectNode();
            result.put("clubId", properties.clubId());
            ArrayNode list = result.putArray("trainers");
            for (Trainer trainer : trainers) {
                ObjectNode item = list.addObject();
                item.put("id", trainer.id());
                item.put("name", trainer.name());
                item.put("speciality", trainer.speciality());
                item.put("price", trainer.price());
                item.put("rating", trainer.rating());
                item.put("type", trainer.type());
            }
            return result.toString();
        } catch (RuntimeException e) {
            return error("get_trainers", e);
        }
    }

    @Tool(name = "get_trainer",
            description = "Trainer details: specialisation, education, clubs and nearest activities")
    public String getTrainer(
            @ToolParam(description = "Trainer id from get_trainers") String trainerId) {
        try {
            TrainerDetail detail = client.getTrainer(trainerId);

            ObjectNode result = objectMapper.createObjectNode();
            result.put("trainerId", detail.id());
            result.put("name", detail.name());
            result.put("speciality", detail.speciality());
            result.put("price", detail.price());
            result.put("rating", detail.rating());
            result.put("type", detail.type());
            result.put("education", detail.education());
            result.put("experience", detail.experience());
            ArrayNode clubs = result.putArray("clubs");
            for (String club : detail.clubs()) {
                clubs.add(club);
            }
            ArrayNode activities = result.putArray("activities");
            for (TrainerActivity activity : detail.activities()) {
                ObjectNode item = activities.addObject();
                item.put("name", activity.name());
                item.put("start", activity.date());
                item.put("hall", activity.hall());
                item.put("club", activity.club());
            }
            return result.toString();
        } catch (RuntimeException e) {
            return error("get_trainer", e);
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
