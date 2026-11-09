package ru.sportlife.mcp.model;

import java.util.List;

public record MasseurClub(
        String clubName,
        List<MasseurSlot> slots) {
}
