package ru.sportlife.mcp.model;

import java.util.List;

public record MasseurDetail(
        String id,
        String name,
        String description,
        String price,
        MasseurResume resume,
        List<MasseurScheduleDay> schedule) {
}
