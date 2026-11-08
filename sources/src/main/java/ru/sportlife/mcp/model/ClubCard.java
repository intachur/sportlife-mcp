package ru.sportlife.mcp.model;

import java.util.List;

public record ClubCard(String companyName, List<Contract> currentContracts) {
}
