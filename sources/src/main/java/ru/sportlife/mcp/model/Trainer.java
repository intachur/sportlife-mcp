package ru.sportlife.mcp.model;

public record Trainer(
        String id,
        String name,
        String speciality,
        int price,
        String rating,
        String type) {
}
