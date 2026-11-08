package ru.sportlife.mcp.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import ru.sportlife.mcp.model.VisitorPoint;

@Repository
public class VisitorSnapshotRepository {

    private final JdbcTemplate jdbcTemplate;

    public VisitorSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(String clubId, int visitors, OffsetDateTime capturedAt) {
        jdbcTemplate.update(
                "INSERT INTO visitor_snapshot (club_id, visitors, captured_at) VALUES (?, ?, ?)",
                clubId, visitors, capturedAt);
    }

    public List<VisitorPoint> findBetween(String clubId, OffsetDateTime from, OffsetDateTime to) {
        return jdbcTemplate.query(
                """
                        SELECT captured_at, visitors
                        FROM visitor_snapshot
                        WHERE club_id = ? AND captured_at >= ? AND captured_at <= ?
                        ORDER BY captured_at
                        """,
                (rs, rowNum) -> new VisitorPoint(
                        rs.getObject("captured_at", OffsetDateTime.class),
                        rs.getInt("visitors")),
                clubId, from, to);
    }
}
