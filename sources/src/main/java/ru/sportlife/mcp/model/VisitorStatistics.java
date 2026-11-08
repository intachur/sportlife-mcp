package ru.sportlife.mcp.model;

import java.time.OffsetDateTime;
import java.util.List;

public record VisitorStatistics(
        String clubId,
        String period,
        OffsetDateTime from,
        OffsetDateTime to,
        int count,
        double average,
        int min,
        int max,
        Integer first,
        Integer last,
        List<VisitorPoint> points) {
}
