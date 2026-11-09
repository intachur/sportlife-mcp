package ru.sportlife.mcp.client;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import ru.sportlife.mcp.config.SportlifeProperties;
import ru.sportlife.mcp.model.Activity;
import ru.sportlife.mcp.model.Club;
import ru.sportlife.mcp.model.ClubCard;
import ru.sportlife.mcp.model.Contract;
import ru.sportlife.mcp.model.Masseur;
import ru.sportlife.mcp.model.MasseurClub;
import ru.sportlife.mcp.model.MasseurDetail;
import ru.sportlife.mcp.model.MasseurResume;
import ru.sportlife.mcp.model.MasseurScheduleDay;
import ru.sportlife.mcp.model.MasseurSlot;
import ru.sportlife.mcp.model.Trainer;
import ru.sportlife.mcp.model.TrainerActivity;
import ru.sportlife.mcp.model.TrainerDetail;

@Component
public class SportlifeClient {

    private static final Logger log = LoggerFactory.getLogger(SportlifeClient.class);

    private final RestClient restClient;
    private final SportlifeProperties properties;
    private final ObjectMapper objectMapper;

    public SportlifeClient(RestClient restClient, SportlifeProperties properties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2))
    public List<Club> getClubs() {
        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/handh/api/v1/club")
                        .queryParam("timestamp", System.currentTimeMillis())
                        .build())
                .retrieve()
                .body(JsonNode.class);

        List<Club> clubs = new ArrayList<>();
        for (JsonNode node : requireSuccess(root, "/handh/api/v1/club").path("list")) {
            clubs.add(new Club(
                    node.path("id").asText(),
                    node.path("name").asText(),
                    node.path("address").asText(),
                    node.path("people").asInt()));
        }
        return clubs;
    }

    public int ping() {
        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/handh/api/v1/club")
                        .queryParam("timestamp", System.currentTimeMillis())
                        .build())
                .retrieve()
                .body(JsonNode.class);
        return requireSuccess(root, "/handh/api/v1/club").path("list").size();
    }

    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2))
    public ClubCard getClubCard() {
        JsonNode root = restClient.get()
                .uri("/handh/api/v1.8/club_card")
                .retrieve()
                .body(JsonNode.class);

        JsonNode data = requireSuccess(root, "/handh/api/v1.8/club_card").path("data");
        List<Contract> contracts = new ArrayList<>();
        for (JsonNode node : data.path("current_contracts")) {
            contracts.add(new Contract(
                    node.path("id").asText(),
                    node.path("name").asText(),
                    node.path("status").asText(),
                    node.path("start_date").asText(),
                    node.path("finish_date").asText(),
                    node.path("contract_remain_days").asInt(),
                    node.path("remain_days").asInt()));
        }
        return new ClubCard(data.path("company_name").asText(), contracts);
    }

    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2))
    public List<Activity> getActivities(LocalDate date) {
        JsonNode root = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/handh/api/v1.2/schedule")
                        .queryParam("type", "club")
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.createObjectNode()
                        .put("club_id", properties.clubId())
                        .toString()
                        .getBytes(StandardCharsets.UTF_8))
                .retrieve()
                .body(JsonNode.class);

        String day = date.toString();
        List<Activity> activities = new ArrayList<>();
        for (JsonNode node : requireSuccess(root, "/handh/api/v1.2/schedule").path("list")) {
            if (!node.path("date").asText().startsWith(day)) {
                continue;
            }
            for (JsonNode activity : node.path("activities")) {
                activities.add(new Activity(
                        activity.path("id").asText(),
                        activity.path("name").asText(),
                        activity.path("date").asText(),
                        activity.path("dateFinish").asText(),
                        activity.path("hall").path("name").asText(),
                        activity.path("trainer").path("name").asText()));
            }
        }
        return activities;
    }

    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2))
    public List<Masseur> getMasseurs() {
        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/handh/api/v1.7/masseurs")
                        .queryParam("limit", 30)
                        .queryParam("offset", 0)
                        .queryParam("query", "")
                        .queryParam("type", "club")
                        .queryParam("club_id", properties.clubId())
                        .build())
                .retrieve()
                .body(JsonNode.class);

        List<Masseur> masseurs = new ArrayList<>();
        for (JsonNode node : requireSuccess(root, "/handh/api/v1.7/masseurs").path("list")) {
            masseurs.add(new Masseur(
                    node.path("id").asText(),
                    node.path("name").asText(),
                    node.path("club_name").asText(),
                    node.path("hall_name").asText(),
                    node.path("date").asText()));
        }
        return masseurs;
    }

    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2))
    public MasseurDetail getMasseurDetail(String masseurId) {
        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/handh/api/v1.2/masseur")
                        .queryParam("masseur_id", masseurId)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        JsonNode data = requireSuccess(root, "/handh/api/v1.2/masseur");
        JsonNode resume = data.path("resume");
        List<MasseurScheduleDay> schedule = new ArrayList<>();
        for (JsonNode day : data.path("schedule")) {
            List<MasseurClub> clubs = new ArrayList<>();
            for (JsonNode club : day.path("schedule_club")) {
                List<MasseurSlot> slots = new ArrayList<>();
                for (JsonNode interval : club.path("intervals")) {
                    slots.add(new MasseurSlot(
                            interval.path("id").asText(),
                            interval.path("name").asText(),
                            interval.path("available").asBoolean()));
                }
                clubs.add(new MasseurClub(club.path("club_name").asText(), slots));
            }
            schedule.add(new MasseurScheduleDay(day.path("date").asText(), clubs));
        }
        return new MasseurDetail(
                data.path("id").asText(),
                data.path("name").asText(),
                data.path("description").asText(),
                data.path("price").asText(),
                new MasseurResume(
                        resume.path("specialization").asText(),
                        resume.path("education").asText(),
                        resume.path("expirience").asText()),
                schedule);
    }

    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2))
    public List<Trainer> getTrainers() {
        JsonNode root = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/handh/api/v1.5/trainer")
                        .queryParam("limit", 30)
                        .queryParam("offset", 0)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(trainerFilter().getBytes(StandardCharsets.UTF_8))
                .retrieve()
                .body(JsonNode.class);

        List<Trainer> trainers = new ArrayList<>();
        for (JsonNode node : requireSuccess(root, "/handh/api/v1.5/trainer").path("list")) {
            trainers.add(new Trainer(
                    node.path("id").asText(),
                    node.path("name").asText(),
                    node.path("main_speciality").asText(),
                    node.path("price").asInt(),
                    node.path("rating_fractional").asText(),
                    node.path("type").asText()));
        }
        return trainers;
    }

    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2))
    public TrainerDetail getTrainer(String trainerId) {
        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/handh/api/v1.5/trainer/{id}")
                        .build(trainerId))
                .retrieve()
                .body(JsonNode.class);

        JsonNode data = requireSuccess(root, "/handh/api/v1.5/trainer").path("data");
        List<String> clubs = new ArrayList<>();
        for (JsonNode club : data.path("clubs")) {
            clubs.add(club.path("name").asText());
        }
        List<TrainerActivity> activities = new ArrayList<>();
        for (JsonNode activity : data.path("nearest_activities")) {
            activities.add(new TrainerActivity(
                    activity.path("name").asText(),
                    activity.path("date").asText(),
                    activity.path("hall").path("name").asText(),
                    activity.path("hall").path("club").path("name").asText()));
        }
        return new TrainerDetail(
                data.path("id").asText(),
                data.path("name").asText(),
                data.path("cvSpeciality").asText(),
                data.path("cvEducation").asText(),
                data.path("cvExperience").asText(),
                data.path("price").asInt(),
                data.path("rating_fractional").asText(),
                data.path("type").asText(),
                clubs,
                activities);
    }

    private String trainerFilter() {
        ObjectNode filter = objectMapper.createObjectNode();
        filter.putArray("activity_group_typy_ids").add("3");
        filter.put("type", "personal");
        filter.put("query", "");
        filter.put("woman", true);
        filter.put("man", true);
        filter.putArray("club_ids").add(properties.clubId());
        return filter.toString();
    }

    private JsonNode requireSuccess(JsonNode root, String endpoint) {
        if (root == null) {
            log.warn("SportLife API returned empty response for {}", endpoint);
            throw new IllegalStateException("SportLife API returned empty response for " + endpoint);
        }
        int code = root.path("code").asInt();
        if (code != 1) {
            String message = root.path("message").asText();
            log.warn("SportLife API error for {}: code={}, message={}", endpoint, code, message);
            throw new IllegalStateException(
                    "SportLife API error for " + endpoint + ": code=" + code + ", message=" + message);
        }
        return root;
    }
}
