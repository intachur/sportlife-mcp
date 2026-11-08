package ru.sportlife.mcp.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ru.sportlife.mcp.client.SportlifeClient;
import ru.sportlife.mcp.config.SportlifeProperties;
import ru.sportlife.mcp.model.Club;
import ru.sportlife.mcp.model.VisitorPoint;
import ru.sportlife.mcp.model.VisitorStatistics;
import ru.sportlife.mcp.repository.VisitorSnapshotRepository;

@Service
public class VisitorService {

    private static final Logger log = LoggerFactory.getLogger(VisitorService.class);

    private final SportlifeClient client;
    private final SportlifeProperties properties;
    private final VisitorSnapshotRepository repository;

    public VisitorService(SportlifeClient client, SportlifeProperties properties, VisitorSnapshotRepository repository) {
        this.client = client;
        this.properties = properties;
        this.repository = repository;
    }

    public Club currentClub() {
        List<Club> clubs = client.getClubs();
        return clubs.stream()
                .filter(item -> item.id().equals(properties.clubId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Club " + properties.clubId() + " not found, available clubs: " + describe(clubs)));
    }

    public Club captureCurrentVisitors() {
        Club club = currentClub();
        try {
            repository.save(club.id(), club.people(), OffsetDateTime.now(zone()));
        } catch (RuntimeException e) {
            log.warn("Failed to persist visitor snapshot for club {}", club.id(), e);
        }
        return club;
    }

    public VisitorStatistics statistics(String period) {
        Duration window = window(period);
        OffsetDateTime to = OffsetDateTime.now(zone());
        OffsetDateTime from = to.minus(window);
        List<VisitorPoint> points = repository.findBetween(properties.clubId(), from, to);

        int count = points.size();
        return new VisitorStatistics(
                properties.clubId(),
                period,
                from,
                to,
                count,
                points.stream().mapToInt(VisitorPoint::visitors).average().orElse(0),
                points.stream().mapToInt(VisitorPoint::visitors).min().orElse(0),
                points.stream().mapToInt(VisitorPoint::visitors).max().orElse(0),
                count == 0 ? null : points.getFirst().visitors(),
                count == 0 ? null : points.getLast().visitors(),
                points);
    }

    private Duration window(String period) {
        return switch (period) {
            case "hour" -> Duration.ofHours(1);
            case "day" -> Duration.ofDays(1);
            case "week" -> Duration.ofDays(7);
            default -> throw new IllegalArgumentException(
                    "Unsupported period: " + period + ", expected one of: hour, day, week");
        };
    }

    private ZoneId zone() {
        return ZoneId.of(properties.zoneId());
    }

    private String describe(List<Club> clubs) {
        return clubs.stream()
                .map(club -> club.id() + "=" + club.name())
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
