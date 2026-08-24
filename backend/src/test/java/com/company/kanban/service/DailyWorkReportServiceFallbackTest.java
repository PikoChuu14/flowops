package com.company.kanban.service;

import com.company.kanban.entity.*;
import com.company.kanban.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DailyWorkReportServiceFallbackTest {
    private DailyWorkReportRepository reports;
    private UserRepository users;
    private SnapshotBatchRepository batches;
    private TaskSnapshotRepository snapshots;
    private TaskRepository tasks;
    private DailyWorkReportService service;
    private User staff;
    private Board board;

    @BeforeEach
    void setUp() {
        reports = mock(DailyWorkReportRepository.class);
        users = mock(UserRepository.class);
        batches = mock(SnapshotBatchRepository.class);
        snapshots = mock(TaskSnapshotRepository.class);
        tasks = mock(TaskRepository.class);
        Department department = new Department("Production");
        ReflectionTestUtils.setField(department, "id", 10L);
        staff = new User("Hana", "hana@example.test", "x", Role.STAFF, department);
        ReflectionTestUtils.setField(staff, "id", 20L);
        board = new Board("Prototype", "", department);
        ReflectionTestUtils.setField(board, "id", 30L);
        service = new DailyWorkReportService(reports, users, batches, snapshots, tasks, mock(AuthorizationService.class), mock(NotificationService.class));
        when(users.findById(20L)).thenReturn(Optional.of(staff));
        when(reports.findByUserIdAndReportDate(eq(20L), any())).thenReturn(Optional.empty());
    }

    @Test
    void todayUsesCurrentTasksWhenEndSnapshotIsMissing() {
        LocalDate date = service.today();
        SnapshotBatch start = batch(date, SnapshotType.START_OF_DAY, 1L);
        when(batches.findBySnapshotDateAndSnapshotType(date, SnapshotType.START_OF_DAY)).thenReturn(Optional.of(start));
        when(batches.findBySnapshotDateAndSnapshotType(date, SnapshotType.END_OF_DAY)).thenReturn(Optional.empty());
        when(snapshots.findByBatchIdAndAssigneeIdOrderByStatusAscPositionAsc(1L, 20L)).thenReturn(List.of(
                snapshot(start, task(101L, "Task A", TaskStatus.DRAFT, 2, date.minusDays(1))),
                snapshot(start, task(102L, "Task B", TaskStatus.DOING, 3, date.minusDays(1)))
        ));
        when(tasks.findByAssigneeIdOrderByStatusAscPositionAsc(20L)).thenReturn(List.of(
                task(101L, "Task A", TaskStatus.DOING, 2, date),
                task(102L, "Task B", TaskStatus.REVIEW, 3, date),
                task(103L, "Task C", TaskStatus.DRAFT, 1, date),
                generalTask(104L, "Supplier follow-up", TaskStatus.DOING, 2, date)
        ));

        var result = service.view(staff, 20L, date);

        assertEquals("CURRENT", result.rightHandSource());
        assertEquals("Start of Day -> Current", result.comparisonLabel());
        assertEquals(1, result.rightHandState().draftCount());
        assertEquals(2, result.rightHandState().doingCount());
        assertEquals(1, result.rightHandState().reviewCount());
        assertEquals(8, result.rightHandState().activeWorkload());
        assertEquals(List.of("Task B"), result.reviewTasks().stream().map(x -> x.title()).toList());
        assertEquals(List.of("Task A", "Supplier follow-up", "Task C"), result.activeTasks().stream().map(x -> x.title()).toList());
        assertEquals("General Task", result.activeTasks().stream()
                .filter(x -> x.title().equals("Supplier follow-up")).findFirst().orElseThrow().boardName());
    }

    @Test
    void endSnapshotOverridesLiveTasksForTheSameDate() {
        LocalDate date = service.today();
        SnapshotBatch start = batch(date, SnapshotType.START_OF_DAY, 1L);
        SnapshotBatch end = batch(date, SnapshotType.END_OF_DAY, 2L);
        when(batches.findBySnapshotDateAndSnapshotType(date, SnapshotType.START_OF_DAY)).thenReturn(Optional.of(start));
        when(batches.findBySnapshotDateAndSnapshotType(date, SnapshotType.END_OF_DAY)).thenReturn(Optional.of(end));
        when(snapshots.findByBatchIdAndAssigneeIdOrderByStatusAscPositionAsc(1L, 20L)).thenReturn(List.of(
                snapshot(start, task(101L, "Task A", TaskStatus.DOING, 3, date.minusDays(1)))
        ));
        when(snapshots.findByBatchIdAndAssigneeIdOrderByStatusAscPositionAsc(2L, 20L)).thenReturn(List.of(
                snapshot(end, task(101L, "Task A", TaskStatus.DONE, 3, date))
        ));

        var result = service.view(staff, 20L, date);

        assertEquals("END_OF_DAY", result.rightHandSource());
        assertEquals("Start of Day -> End of Day", result.comparisonLabel());
        assertEquals(1, result.completedTasks().size());
        assertEquals(1, result.rightHandState().doneCount());
        verifyNoInteractions(tasks);
    }

    @Test
    void pastDateWithoutEndSnapshotNeverUsesLiveTasks() {
        LocalDate date = service.today().minusDays(1);
        SnapshotBatch start = batch(date, SnapshotType.START_OF_DAY, 1L);
        when(batches.findBySnapshotDateAndSnapshotType(date, SnapshotType.START_OF_DAY)).thenReturn(Optional.of(start));
        when(batches.findBySnapshotDateAndSnapshotType(date, SnapshotType.END_OF_DAY)).thenReturn(Optional.empty());
        when(snapshots.findByBatchIdAndAssigneeIdOrderByStatusAscPositionAsc(1L, 20L)).thenReturn(List.of(
                snapshot(start, task(101L, "Historical task", TaskStatus.DOING, 3, date))
        ));

        var result = service.view(staff, 20L, date);

        assertEquals("START_OF_DAY_ONLY", result.rightHandSource());
        assertTrue(result.comparisonLabel().contains("End-of-day snapshot unavailable"));
        assertEquals(1, result.rightHandState().doingCount());
        verifyNoInteractions(tasks);
    }

    @Test
    void generatesCompactTodayFallbackPdf() throws Exception {
        LocalDate date = service.today();
        User manager = new User("Manager", "manager@example.test", "x", Role.MANAGER, staff.getDepartment());
        ReflectionTestUtils.setField(manager, "id", 21L);
        SnapshotBatch start = batch(date, SnapshotType.START_OF_DAY, 1L);
        when(users.findByDepartmentIdOrderByNameAsc(10L)).thenReturn(List.of(staff));
        when(batches.findBySnapshotDateAndSnapshotType(date, SnapshotType.START_OF_DAY)).thenReturn(Optional.of(start));
        when(batches.findBySnapshotDateAndSnapshotType(date, SnapshotType.END_OF_DAY)).thenReturn(Optional.empty());
        when(snapshots.findByBatchIdAndAssigneeIdOrderByStatusAscPositionAsc(1L, 20L)).thenReturn(List.of(
                snapshot(start, task(101L, "Prepare drawing note", TaskStatus.DRAFT, 2, date.minusDays(1))),
                snapshot(start, task(102L, "Prototype test", TaskStatus.DOING, 3, date.minusDays(1)))
        ));
        when(tasks.findByAssigneeIdOrderByStatusAscPositionAsc(20L)).thenReturn(List.of(
                task(101L, "Prepare drawing note", TaskStatus.DOING, 2, date),
                task(102L, "Prototype test", TaskStatus.REVIEW, 3, date),
                task(103L, "Update CAD design", TaskStatus.DONE, 2, date),
                task(104L, "Supplier dimension check", TaskStatus.DRAFT, 1, date)
        ));
        DailyWorkReport report = new DailyWorkReport(staff, date);
        report.update("Completed prototype testing and updated CAD dimensions.", "Awaiting production feedback.", "Submit revised prototype.");
        report.submit();
        when(reports.findByUserIdAndReportDate(20L, date)).thenReturn(Optional.of(report));

        byte[] bytes = service.teamPdf(manager, date, null);
        assertTrue(bytes.length > 1_000);
        String qaOutput = System.getProperty("pdf.qa.output");
        if (qaOutput != null && !qaOutput.isBlank()) {
            Path output = Path.of(qaOutput);
            Files.createDirectories(output.getParent());
            Files.write(output, bytes);
        }
        try (PDDocument pdf = PDDocument.load(bytes)) {
            assertEquals(1, pdf.getNumberOfPages());
        }
    }

    private SnapshotBatch batch(LocalDate date, SnapshotType type, Long id) {
        SnapshotBatch batch = new SnapshotBatch(date, type, date.atTime(type == SnapshotType.START_OF_DAY ? 8 : 17, 0), false);
        ReflectionTestUtils.setField(batch, "id", id);
        batch.complete(date.atTime(type == SnapshotType.START_OF_DAY ? 8 : 17, 0), 1);
        return batch;
    }

    private Task task(Long id, String title, TaskStatus status, int workload, LocalDate updatedDate) {
        KanbanColumn column = new KanbanColumn(status.name(), status.ordinal(), board);
        ReflectionTestUtils.setField(column, "id", 40L + status.ordinal());
        Task task = new Task(title, "", Priority.MEDIUM, null, status.ordinal(), column, staff);
        ReflectionTestUtils.setField(task, "id", id);
        ReflectionTestUtils.setField(task, "updatedAt", updatedDate.atTime(12, 0));
        task.setStatus(status);
        task.setWorkload(workload);
        return task;
    }

    private TaskSnapshot snapshot(SnapshotBatch batch, Task task) {
        return new TaskSnapshot(batch, task);
    }

    private Task generalTask(Long id, String title, TaskStatus status, int workload, LocalDate updatedDate) {
        Task task = new Task(title, "", Priority.MEDIUM, null, 1, null, staff);
        task.setDepartment(staff.getDepartment());
        task.setCreatedBy(staff);
        task.setStatus(status);
        task.setWorkload(workload);
        ReflectionTestUtils.setField(task, "id", id);
        ReflectionTestUtils.setField(task, "updatedAt", updatedDate.atTime(12, 0));
        return task;
    }
}
