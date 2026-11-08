package ru.sportlife.mcp.model;

public record Activity(
        String id,
        String name,
        String date,
        String dateFinish,
        String hallName,
        String trainerName) {
}
