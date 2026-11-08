package ru.sportlife.mcp.model;

import java.time.OffsetDateTime;

public record VisitorPoint(OffsetDateTime capturedAt, int visitors) {
}
