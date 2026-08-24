package com.company.kanban.service;

import com.company.kanban.dto.MonthlyWorkReportRequest;
import com.company.kanban.entity.*;
import com.company.kanban.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MonthlyWorkReportServiceTest {
    private MonthlyWorkReportRepository reports; private UserRepository users; private TaskRepository tasks; private SnapshotBatchRepository batches; private TaskSnapshotRepository snapshots; private AuthorizationService authorization; private MonthlyWorkReportService service; private User staff;
    @BeforeEach void setup() {
        reports=mock(MonthlyWorkReportRepository.class); users=mock(UserRepository.class); tasks=mock(TaskRepository.class); batches=mock(SnapshotBatchRepository.class); snapshots=mock(TaskSnapshotRepository.class); authorization=mock(AuthorizationService.class);
        Department d=new Department("PPC"); ReflectionTestUtils.setField(d,"id",10L); staff=new User("Staff","staff@test","x",Role.STAFF,d); ReflectionTestUtils.setField(staff,"id",20L);
        service=new MonthlyWorkReportService(reports,users,tasks,batches,snapshots,authorization); when(tasks.findDetailedByAssigneeId(20L)).thenReturn(List.of()); when(batches.findBySnapshotDateBetweenAndStatusOrderBySnapshotDateAsc(any(),any(),any())).thenReturn(List.of());
        when(users.findById(20L)).thenReturn(Optional.of(staff));
    }
    @Test void rejectsInvalidMonth(){assertThrows(ResponseStatusException.class,()->service.view(staff,20L,2026,13));}
    @Test void staffSaveUsesAuthenticatedUserAndReturnsDraft(){when(reports.findByUserIdAndYearAndMonth(20L,2026,8)).thenReturn(Optional.empty()); when(reports.save(any())).thenAnswer(i->i.getArgument(0)); var result=service.saveDraft(staff,2026,8,new MonthlyWorkReportRequest("Summary","Wins","Blocker","Plan")); assertEquals(20L,result.employee().userId()); assertEquals("Summary",result.report().monthlySummary()); assertEquals("DRAFT",result.report().status()); verify(reports).save(any(MonthlyWorkReport.class));}
    @Test void completedAtDrivesCompletedAggregationAndOngoingExcludesDone(){Task done=task(1L,"Done",TaskStatus.DONE,3); done.setCompletedAt(LocalDateTime.of(2026,8,12,10,0)); Task doing=task(2L,"Doing",TaskStatus.DOING,4); when(tasks.findDetailedByAssigneeId(20L)).thenReturn(List.of(done,doing)); var result=service.view(staff,20L,2026,8); assertEquals(List.of("Done"),result.completedTasks().stream().map(x->x.title()).toList()); assertEquals(List.of("Doing"),result.ongoingTasks().stream().map(x->x.title()).toList()); assertEquals(4,result.overview().activeWorkload());}
    @Test void authorizationIsRequiredForOtherEmployee(){User other=new User("Other","other@test","x",Role.STAFF,staff.getDepartment()); ReflectionTestUtils.setField(other,"id",21L); when(users.findById(21L)).thenReturn(Optional.of(other)); doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN)).when(authorization).requireStaffViewerAccess(staff,other); assertThrows(ResponseStatusException.class,()->service.view(staff,21L,2026,8)); verify(authorization).requireStaffViewerAccess(staff,other);}
    @Test void generatesJulySamplePdfWhenOutputPathIsProvided() throws Exception {
        String output=System.getProperty("sample.pdf.output"); if(output==null||output.isBlank()) return;
        Board board=new Board("Production Planning Q3","Sample board",staff.getDepartment()); ReflectionTestUtils.setField(board,"id",30L);
        KanbanColumn doneColumn=new KanbanColumn("Done",4,board); ReflectionTestUtils.setField(doneColumn,"id",31L);
        KanbanColumn doingColumn=new KanbanColumn("In Progress",2,board); ReflectionTestUtils.setField(doingColumn,"id",32L);
        Task completed=new Task("Complete July production schedule","Finalise the July operating schedule and circulate the approved version.",Priority.HIGH,null,1,doneColumn,staff); ReflectionTestUtils.setField(completed,"id",301L); completed.setStatus(TaskStatus.DONE); completed.setWorkload(4); completed.setCompletedAt(LocalDateTime.of(2026,7,18,16,30));
        Task ongoing=new Task("Prepare August capacity handover","Document carry-over capacity risks for the next planning cycle.",Priority.MEDIUM,null,1,doingColumn,staff); ReflectionTestUtils.setField(ongoing,"id",302L); ongoing.setStatus(TaskStatus.DOING); ongoing.setWorkload(3); ReflectionTestUtils.setField(ongoing,"createdAt",LocalDateTime.of(2026,7,6,9,0));
        Task general=new Task("Supplier follow-up","Confirm the July raw-material delivery position.",Priority.MEDIUM,null,1,null,staff); ReflectionTestUtils.setField(general,"id",303L); general.setDepartment(staff.getDepartment()); general.setStatus(TaskStatus.REVIEW); general.setWorkload(2); ReflectionTestUtils.setField(general,"createdAt",LocalDateTime.of(2026,7,10,9,0));
        when(users.findById(20L)).thenReturn(Optional.of(staff)); when(tasks.findDetailedByAssigneeId(20L)).thenReturn(List.of(completed,ongoing,general));
        MonthlyWorkReport report=new MonthlyWorkReport(staff,2026,7); report.update("July focused on stabilising the production plan and improving cross-team visibility.","Published the approved production schedule and closed the key material coordination gap.","Supplier confirmation arrived late, creating a short planning window.","Carry forward the capacity handover and confirm August material readiness."); report.submit(); when(reports.findByUserIdAndYearAndMonth(20L,2026,7)).thenReturn(Optional.of(report));
        Path path=Path.of(output); Files.createDirectories(path.getParent()); Files.write(path,service.pdf(staff,20L,2026,7)); assertTrue(Files.size(path)>1000);
    }
    private Task task(Long id,String title,TaskStatus status,int workload){Task t=new Task(title,"",Priority.MEDIUM,null,1,null,staff); ReflectionTestUtils.setField(t,"id",id); t.setDepartment(staff.getDepartment()); t.setStatus(status); t.setWorkload(workload); ReflectionTestUtils.setField(t,"createdAt",LocalDateTime.of(2026,8,1,8,0)); return t;}
}
