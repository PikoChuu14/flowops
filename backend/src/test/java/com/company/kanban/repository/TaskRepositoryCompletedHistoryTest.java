package com.company.kanban.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.time.LocalDateTime;

@SpringBootTest
@ActiveProfiles("test")
class TaskRepositoryCompletedHistoryTest {
    @Autowired TaskRepository tasks;

    @Test
    void completedHistoryQuerySupportsEmptyOptionalFilters() {
        assertNotNull(tasks.findCompletedHistory(LocalDateTime.of(1, 1, 1, 0, 0), LocalDateTime.of(9999, 12, 31, 23, 59, 59), 0L, 0L, true, PageRequest.of(0, 20)));
    }
}
