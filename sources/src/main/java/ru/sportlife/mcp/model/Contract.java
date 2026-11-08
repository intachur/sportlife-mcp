package ru.sportlife.mcp.model;

public record Contract(
        String id,
        String name,
        String status,
        String startDate,
        String finishDate,
        int contractRemainDays,
        int remainDays) {
}
