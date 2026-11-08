package ru.sportlife.mcp.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ru.sportlife.mcp.client.SportlifeClient;
import ru.sportlife.mcp.config.SportlifeProperties;
import ru.sportlife.mcp.model.Activity;
import ru.sportlife.mcp.model.ClubEventRecord;
import ru.sportlife.mcp.repository.ClubEventRepository;

@Service
public class ClubEventService {

    private static final Logger log = LoggerFactory.getLogger(ClubEventService.class);

    private final SportlifeClient client;
    private final SportlifeProperties properties;
    private final ClubEventRepository repository;

    public ClubEventService(SportlifeClient client, SportlifeProperties properties, ClubEventRepository repository) {
        this.client = client;
        this.properties = properties;
        this.repository = repository;
    }

    public List<Activity> todayEvents(LocalDate date) {
        List<Activity> activities = client.getActivities(date);
        try {
            repository.upsertAll(properties.clubId(), activities.stream()
                    .map(activity -> toRecord(activity, date))
                    .toList());
        } catch (RuntimeException e) {
            log.warn("Failed to persist club events for club {} on {}", properties.clubId(), date, e);
        }
        return activities;
    }

    private ClubEventRecord toRecord(Activity activity, LocalDate date) {
        ZoneId zone = zone();
        return new ClubEventRecord(
                activity.id(),
                activity.name(),
                LocalDateTime.parse(activity.date()).atZone(zone).toOffsetDateTime(),
                LocalDateTime.parse(activity.dateFinish()).atZone(zone).toOffsetDateTime(),
                activity.hallName(),
                activity.trainerName(),
                date);
    }

    private ZoneId zone() {
        return ZoneId.of(properties.zoneId());
    }
}
