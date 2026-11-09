package ru.sportlife.mcp.model;

import java.util.List;

public record TrainerDetail(
        String id,
        String name,
        String speciality,
        String education,
        String experience,
        int price,
        String rating,
        String type,
        List<String> clubs,
        List<TrainerActivity> activities) {
}
