package ru.sportlife.mcp.schedule;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import ru.sportlife.mcp.service.VisitorService;

class VisitorSnapshotSchedulerTest {

    @Test
    void captureVisitorsDelegatesToService() {
        VisitorService visitorService = mock(VisitorService.class);
        VisitorSnapshotScheduler scheduler = new VisitorSnapshotScheduler(visitorService);

        scheduler.captureVisitors();

        verify(visitorService).captureCurrentVisitors();
    }
}
