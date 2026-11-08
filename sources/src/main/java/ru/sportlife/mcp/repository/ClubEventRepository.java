package ru.sportlife.mcp.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import ru.sportlife.mcp.model.ClubEventRecord;

@Repository
public class ClubEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public ClubEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertAll(String clubId, List<ClubEventRecord> events) {
        jdbcTemplate.batchUpdate(
                """
                        INSERT INTO club_event
                            (club_id, external_id, name, starts_at, ends_at, hall, trainer, event_date)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (club_id, external_id, starts_at) DO UPDATE SET
                            name = EXCLUDED.name,
                            ends_at = EXCLUDED.ends_at,
                            hall = EXCLUDED.hall,
                            trainer = EXCLUDED.trainer,
                            event_date = EXCLUDED.event_date
                        """,
                events.stream()
                        .map(event -> new Object[]{
                                clubId,
                                event.externalId(),
                                event.name(),
                                event.startsAt(),
                                event.endsAt(),
                                event.hall(),
                                event.trainer(),
                                event.eventDate()})
                        .toList());
    }
}
