package ru.sportlife.mcp.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ClubEventRecord(
        String externalId,
        String name,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String hall,
        String trainer,
        LocalDate eventDate) {
}
