package ru.sportlife.mcp.schedule;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ru.sportlife.mcp.service.VisitorService;

@Component
public class VisitorSnapshotScheduler {

    private final VisitorService visitorService;

    public VisitorSnapshotScheduler(VisitorService visitorService) {
        this.visitorService = visitorService;
    }

    @Scheduled(cron = "0 0 9-22 * * *", zone = "Europe/Moscow")
    public void captureVisitors() {
        visitorService.captureCurrentVisitors();
    }
}
