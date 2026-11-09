package ru.sportlife.mcp.model;

import java.util.List;

public record MasseurScheduleDay(
        String date,
        List<MasseurClub> clubs) {
}
