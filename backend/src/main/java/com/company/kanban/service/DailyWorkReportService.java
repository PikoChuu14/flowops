package com.company.kanban.service;

import com.company.kanban.dto.*;
import com.company.kanban.entity.*;
import com.company.kanban.repository.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.awt.Color;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DailyWorkReportService {
    public static final ZoneId COMPANY_ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    private final DailyWorkReportRepository reportRepository; private final UserRepository userRepository;
    private final SnapshotBatchRepository batchRepository; private final TaskSnapshotRepository snapshotRepository;
    private final TaskRepository taskRepository;
    private final AuthorizationService authorizationService;
    private final NotificationService notificationService;
    public DailyWorkReportService(DailyWorkReportRepository r, UserRepository u, SnapshotBatchRepository b, TaskSnapshotRepository s, TaskRepository t, AuthorizationService a, NotificationService n) { reportRepository=r; userRepository=u; batchRepository=b; snapshotRepository=s; taskRepository=t; authorizationService=a; notificationService=n; }
    public LocalDate today() { return LocalDate.now(COMPANY_ZONE); }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> availability(User current, YearMonth month, Long requestedDepartmentId) {
        Long departmentId = current.getRole() == Role.MANAGER ? current.getDepartment().getId() : requestedDepartmentId;
        Set<Long> staffIds = current.getRole() == Role.STAFF ? Set.of(current.getId()) :
                userRepository.findByDepartmentIdOrderByNameAsc(departmentId).stream().filter(u -> u.getRole() == Role.STAFF).map(User::getId).collect(Collectors.toSet());
        LocalDate start = month.atDay(1), end = month.atEndOfMonth();
        return reportRepository.findByReportDateBetween(start, end).stream().filter(r -> staffIds.contains(r.getUser().getId())).collect(Collectors.groupingBy(DailyWorkReport::getReportDate, LinkedHashMap::new, Collectors.toList())).entrySet().stream().map(e -> { Map<String,Object> row = new LinkedHashMap<>(); row.put("date", e.getKey()); row.put("hasReports", true); row.put("submitted", e.getValue().stream().filter(r -> r.getStatus() == DailyWorkReportStatus.SUBMITTED).count()); row.put("draft", e.getValue().stream().filter(r -> r.getStatus() == DailyWorkReportStatus.DRAFT).count()); return row; }).toList();
    }

    @Transactional
    public DailyWorkReport saveDraft(User current, LocalDate date, DailyWorkReportRequest request) {
        if (!date.equals(today())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Staff can only edit today's report");
        DailyWorkReport report = reportRepository.findByUserIdAndReportDate(current.getId(), date).orElseGet(() -> new DailyWorkReport(current, date));
        if (report.getStatus() == DailyWorkReportStatus.SUBMITTED) throw new ResponseStatusException(HttpStatus.CONFLICT, "Submitted reports are read-only");
        report.update(request == null ? "" : request.workSummary(), request == null ? null : request.blockers(), request == null ? null : request.nextDayPlan());
        return reportRepository.save(report);
    }
    @Transactional
    public DailyWorkReport submit(User current, LocalDate date) {
        if (!date.equals(today())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only today's report can be submitted");
        DailyWorkReport report = reportRepository.findByUserIdAndReportDate(current.getId(), date).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Save a draft before submitting"));
        if (report.getStatus() == DailyWorkReportStatus.SUBMITTED) return report;
        report.submit();
        DailyWorkReport saved = reportRepository.save(report);
        notificationService.notifyDailyReportSubmitted(saved, current);
        return saved;
    }
    @Transactional(readOnly = true)
    public DailyReportViewResponse view(User current, Long targetId, LocalDate date) {
        User target = userRepository.findById(targetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        authorizationService.requireStaffViewerAccess(current, target);
        return build(target, date);
    }
    @Transactional(readOnly = true)
    public List<Map<String,Object>> teamStatus(User current, LocalDate date) {
        if (current == null || (current.getRole()!=Role.ADMIN && current.getRole()!=Role.MANAGER)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Management access required");
        List<User> users = current.getRole()==Role.ADMIN ? userRepository.findAll() : userRepository.findByDepartmentIdOrderByNameAsc(current.getDepartment().getId());
        return users.stream().filter(u -> u.getRole()==Role.STAFF).map(u -> { var r=reportRepository.findByUserIdAndReportDate(u.getId(),date); Map<String,Object> m=new LinkedHashMap<>(); m.put("userId",u.getId()); m.put("userName",u.getName()); m.put("departmentName",u.getDepartment().getName()); m.put("status",r.map(x->x.getStatus().name()).orElse("NOT_STARTED")); return m; }).toList();
    }
    @Transactional(readOnly = true)
    public TeamDailyReportsResponse teamReports(User current, LocalDate date, Long requestedDepartmentId) {
        if (current == null || (current.getRole() != Role.ADMIN && current.getRole() != Role.MANAGER))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Management access required");
        Long departmentId = current.getRole() == Role.MANAGER ? current.getDepartment().getId() : requestedDepartmentId;
        if (departmentId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "departmentId is required");
        if (current.getRole() == Role.MANAGER && !departmentId.equals(current.getDepartment().getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this department");
        var department = current.getRole() == Role.MANAGER ? current.getDepartment() :
                userRepository.findAll().stream().map(User::getDepartment).filter(d -> d.getId().equals(departmentId)).findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));
        var reports = userRepository.findByDepartmentIdOrderByNameAsc(departmentId).stream()
                .filter(u -> u.getRole() == Role.STAFF)
                .map(u -> {
                    var detail = build(u, date);
                    var narrative = detail.report();
                    var right = detail.rightHandState();
                    var activity = teamActivity(detail);
                    return new TeamDailyReportsResponse.ReportCard(u.getId(), u.getName(), narrative.reportStatus(),
                            narrative.workSummary(), narrative.blockers(), narrative.nextDayPlan(), narrative.submittedAt(),
                            activity.completed.size(), activity.review.size(), right == null ? 0 : right.doingCount(),
                            right == null ? 0 : right.activeWorkload(),
                            countOrNull(detail.startOfDay(), 0), countOrNull(detail.startOfDay(), 1), countOrNull(detail.startOfDay(), 2),
                            countOrNull(detail.startOfDay(), 3), workloadOrNull(detail.startOfDay()),
                            countOrNull(right, 0), countOrNull(right, 1), countOrNull(right, 2),
                            countOrNull(right, 3), workloadOrNull(right), detail.rightHandSource(), detail.comparisonLabel(),
                            titles(activity.completed), titles(activity.review), titles(activity.active));
                }).toList();
        return new TeamDailyReportsResponse(date, department.getId(), department.getName(),
                (int) reports.stream().filter(r -> "SUBMITTED".equals(r.status())).count(),
                (int) reports.stream().filter(r -> "DRAFT".equals(r.status())).count(),
                (int) reports.stream().filter(r -> "NOT_STARTED".equals(r.status())).count(), reports);
    }
    private DailyReportViewResponse build(User user, LocalDate date) {
        var report=reportRepository.findByUserIdAndReportDate(user.getId(),date);
        var employee=new DailyReportViewResponse.Employee(user.getId(),user.getName(),user.getRole().name(),user.getDepartment().getId(),user.getDepartment().getName());
        var narrative=new DailyReportViewResponse.Report(date,report.map(r->r.getStatus().name()).orElse("NOT_STARTED"),report.map(DailyWorkReport::getWorkSummary).orElse(""),report.map(DailyWorkReport::getBlockers).orElse(""),report.map(DailyWorkReport::getNextDayPlan).orElse(""),report.flatMap(r->Optional.ofNullable(r.getSubmittedAt())).orElse(null));
        var start=readSnapshot(date,SnapshotType.START_OF_DAY,user.getId()); var end=readSnapshot(date,SnapshotType.END_OF_DAY,user.getId());
        List<TaskSnapshot> startTasks=start.tasks, endTasks=end.tasks;
        Map<Long,TaskSnapshot> starts=startTasks.stream().collect(Collectors.toMap(TaskSnapshot::getTaskId,x->x,(a,b)->a));
        List<DailyReportViewResponse.TaskItem> rightTasks;
        Map<Long, Task> liveById = Map.of();
        DailyReportViewResponse.SnapshotSummary rightSummary;
        String rightSource;
        String comparisonLabel;
        if (end.batch != null) {
            rightTasks = endTasks.stream().map(this::item).toList();
            rightSummary = summary(end.batch, endTasks);
            rightSource = "END_OF_DAY";
            comparisonLabel = "Start of Day -> End of Day";
        } else if (date.equals(today())) {
            List<Task> liveTasks = taskRepository.findByAssigneeIdOrderByStatusAscPositionAsc(user.getId()).stream()
                    .filter(task -> task.getStatus() != TaskStatus.DONE || wasUpdatedOn(task, date))
                    .toList();
            liveById = liveTasks.stream().collect(Collectors.toMap(Task::getId, x -> x, (a, b) -> a));
            rightTasks = liveTasks.stream().map(this::item).toList();
            rightSummary = summary(liveTasks);
            rightSource = "CURRENT";
            comparisonLabel = "Start of Day -> Current";
        } else if (start.batch != null) {
            rightTasks = startTasks.stream().map(this::item).toList();
            rightSummary = summary(start.batch, startTasks);
            rightSource = "START_OF_DAY_ONLY";
            comparisonLabel = "Start of Day only - End-of-day snapshot unavailable";
        } else {
            rightTasks = List.of();
            rightSummary = null;
            rightSource = "UNAVAILABLE";
            comparisonLabel = "Snapshot activity unavailable";
        }
        List<DailyReportViewResponse.TaskItem> completed=new ArrayList<>(), review=new ArrayList<>(), active=new ArrayList<>();
        for (var task : rightTasks) {
            var before = starts.get(task.taskId());
            if ("DONE".equals(task.status()) && completedToday(task, before, liveById, rightSource, date)) completed.add(task);
            if ("REVIEW".equals(task.status())) review.add(task);
            if ("DOING".equals(task.status()) || "DRAFT".equals(task.status())) active.add(task);
        }
        Comparator<DailyReportViewResponse.TaskItem> managementOrder = Comparator.comparingInt(t -> statusRank(t.status()));
        review.sort(managementOrder); active.sort(managementOrder); completed.sort(managementOrder);
        return new DailyReportViewResponse(employee,narrative,summary(start.batch,startTasks),summary(end.batch,endTasks),rightSummary,rightSource,comparisonLabel,completed,review,active,start.note,end.note);
    }
    private Integer countOrNull(DailyReportViewResponse.SnapshotSummary s, int status) {
        if (s == null) return null;
        return switch (status) { case 0 -> s.draftCount(); case 1 -> s.doingCount(); case 2 -> s.reviewCount(); default -> s.doneCount(); };
    }
    private Integer workloadOrNull(DailyReportViewResponse.SnapshotSummary s) { return s == null ? null : s.activeWorkload(); }
    private record TeamActivity(List<DailyReportViewResponse.TaskItem> completed, List<DailyReportViewResponse.TaskItem> review, List<DailyReportViewResponse.TaskItem> active) {}
    private TeamActivity teamActivity(DailyReportViewResponse detail) { return new TeamActivity(detail.completedTasks(), detail.reviewTasks(), detail.activeTasks()); }
    private List<String> titles(List<DailyReportViewResponse.TaskItem> tasks) { return tasks.stream().map(DailyReportViewResponse.TaskItem::title).toList(); }
    private DailyReportViewResponse.TaskItem item(TaskSnapshot t){return new DailyReportViewResponse.TaskItem(t.getTaskId(),t.getTitle(),t.getStatus().name(),t.getWorkload(),t.getBoardName()==null?"General Task":t.getBoardName());}
    private DailyReportViewResponse.TaskItem item(Task t){return new DailyReportViewResponse.TaskItem(t.getId(),t.getTitle(),t.getStatus().name(),t.getWorkload(),t.isGeneralTask()?"General Task":t.getColumn().getBoard().getName());}
    private DailyReportViewResponse.SnapshotSummary summary(SnapshotBatch b,List<TaskSnapshot> tasks){ if(b==null)return null; return new DailyReportViewResponse.SnapshotSummary(count(tasks,TaskStatus.DRAFT),count(tasks,TaskStatus.DOING),count(tasks,TaskStatus.REVIEW),count(tasks,TaskStatus.DONE),tasks.stream().filter(t->t.getStatus()!=TaskStatus.DONE).mapToInt(t->t.getWorkload()==null?0:t.getWorkload()).sum(),b.getCapturedAt(),b.isRecovered()); }
    private DailyReportViewResponse.SnapshotSummary summary(List<Task> tasks){ return new DailyReportViewResponse.SnapshotSummary((int)tasks.stream().filter(t->t.getStatus()==TaskStatus.DRAFT).count(),(int)tasks.stream().filter(t->t.getStatus()==TaskStatus.DOING).count(),(int)tasks.stream().filter(t->t.getStatus()==TaskStatus.REVIEW).count(),(int)tasks.stream().filter(t->t.getStatus()==TaskStatus.DONE).count(),tasks.stream().filter(t->t.getStatus()!=TaskStatus.DONE).mapToInt(t->t.getWorkload()==null?0:t.getWorkload()).sum(),LocalDateTime.now(COMPANY_ZONE),false); }
    private boolean completedToday(DailyReportViewResponse.TaskItem task, TaskSnapshot before, Map<Long, Task> liveById, String rightSource, LocalDate date) {
        if (before != null) return before.getStatus() != TaskStatus.DONE;
        if ("END_OF_DAY".equals(rightSource)) return true;
        Task live = liveById.get(task.taskId());
        return live != null && wasUpdatedOn(live, date);
    }
    private boolean wasUpdatedOn(Task task, LocalDate date) { return task.getUpdatedAt() != null && task.getUpdatedAt().toLocalDate().equals(date); }
    private int statusRank(String status) { return switch (status) { case "REVIEW" -> 0; case "DOING" -> 1; case "DRAFT" -> 2; default -> 3; }; }
    private int count(List<TaskSnapshot> t,TaskStatus s){return (int)t.stream().filter(x->x.getStatus()==s).count();}
    private SnapshotData readSnapshot(LocalDate date, SnapshotType type, Long userId){ var b=batchRepository.findBySnapshotDateAndSnapshotType(date,type).filter(x->x.getStatus()==SnapshotBatchStatus.COMPLETED).orElse(null); if(b==null)return new SnapshotData(null,List.of(),type==SnapshotType.START_OF_DAY?"Start-of-day snapshot is not available.":"End-of-day snapshot is not available."); return new SnapshotData(b,snapshotRepository.findByBatchIdAndAssigneeIdOrderByStatusAscPositionAsc(b.getId(),userId),b.isRecovered()?(type==SnapshotType.START_OF_DAY?"Recovered start-of-day snapshot":"Recovered end-of-day snapshot"):null); }
    private record SnapshotData(SnapshotBatch batch,List<TaskSnapshot> tasks,String note){}

    @Transactional(readOnly = true)
    public WeeklyReportResponse weeklyReports(User current, LocalDate requestedWeekStart, Long requestedDepartmentId) {
        DepartmentScope scope = departmentScope(current, requestedDepartmentId);
        LocalDate start = requestedWeekStart == null ? today().with(java.time.DayOfWeek.MONDAY) : requestedWeekStart;
        start = start.with(java.time.DayOfWeek.MONDAY);
        LocalDate end = start.plusDays(6);
        List<User> staff = userRepository.findByDepartmentIdOrderByNameAsc(scope.departmentId()).stream()
                .filter(u -> u.getRole() == Role.STAFF).toList();
        List<WeeklyReportResponse.Day> days = new ArrayList<>();
        Set<Long> completed = new HashSet<>();
        Set<Long> reviewed = new HashSet<>();
        List<Integer> workloads = new ArrayList<>();
        int submitted = 0, drafts = 0, notStarted = 0;
        Map<Long, Integer> firstStartLoads = new HashMap<>(), latestEndLoads = new HashMap<>();
        boolean weekEndCurrent = false;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            List<WeeklyReportResponse.StaffDay> dayStaff = new ArrayList<>();
            for (User user : staff) {
                DailyReportViewResponse detail = build(user, date);
                var report = detail.report();
                if ("SUBMITTED".equals(report.reportStatus())) submitted++;
                else if ("DRAFT".equals(report.reportStatus())) drafts++;
                else notStarted++;
                var right = detail.rightHandState();
                if (right != null && (!"START_OF_DAY_ONLY".equals(detail.rightHandSource()) && !"UNAVAILABLE".equals(detail.rightHandSource()))) {
                    workloads.add(right.activeWorkload());
                }
                if (detail.startOfDay() != null) firstStartLoads.putIfAbsent(user.getId(), detail.startOfDay().activeWorkload());
                if (right != null && ("END_OF_DAY".equals(detail.rightHandSource()) || "CURRENT".equals(detail.rightHandSource()))) {
                    latestEndLoads.put(user.getId(), right.activeWorkload());
                    weekEndCurrent = "CURRENT".equals(detail.rightHandSource());
                }
                List<WeeklyReportResponse.Task> tasks = weeklyTasks(detail);
                int dayCompleted = 0, dayReview = 0;
                for (var task : detail.completedTasks()) if (completed.add(task.taskId())) dayCompleted++;
                for (var task : detail.reviewTasks()) {
                    TaskSnapshot before = readSnapshot(date, SnapshotType.START_OF_DAY, user.getId()).tasks().stream()
                            .filter(t -> Objects.equals(t.getTaskId(), task.taskId())).findFirst().orElse(null);
                    if (before != null && before.getStatus() != TaskStatus.REVIEW && reviewed.add(task.taskId())) dayReview++;
                }
                dayStaff.add(new WeeklyReportResponse.StaffDay(user.getId(), user.getName(), report.reportStatus(),
                        report.workSummary(), report.blockers(), report.nextDayPlan(), report.submittedAt(),
                        snapshot(detail.startOfDay()), snapshot(right), detail.rightHandSource(), tasks, dayCompleted, dayReview));
            }
            days.add(new WeeklyReportResponse.Day(date, dayStaff));
        }
        Integer startWorkload = firstStartLoads.isEmpty() ? null : firstStartLoads.values().stream().mapToInt(Integer::intValue).sum();
        Integer endWorkload = latestEndLoads.isEmpty() ? null : latestEndLoads.values().stream().mapToInt(Integer::intValue).sum();
        return new WeeklyReportResponse(start, end, scope.departmentId(), scope.departmentName(),
                new WeeklyReportResponse.Overview(staff.size(), submitted, drafts, notStarted, completed.size(), reviewed.size(),
                        workloads.isEmpty() ? null : (int) Math.round(workloads.stream().mapToInt(Integer::intValue).average().orElse(0)),
                        startWorkload, endWorkload, weekEndCurrent), days);
    }

    private DepartmentScope departmentScope(User current, Long requestedDepartmentId) {
        if (current == null || (current.getRole() != Role.ADMIN && current.getRole() != Role.MANAGER))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Management access required");
        Long departmentId = current.getRole() == Role.MANAGER ? current.getDepartment().getId() : requestedDepartmentId;
        if (departmentId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "departmentId is required");
        if (current.getRole() == Role.MANAGER && !departmentId.equals(current.getDepartment().getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this department");
        Department department = current.getRole() == Role.MANAGER ? current.getDepartment() : userRepository.findAll().stream()
                .map(User::getDepartment).filter(d -> d.getId().equals(departmentId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));
        return new DepartmentScope(department.getId(), department.getName());
    }
    private record DepartmentScope(Long departmentId, String departmentName) {}
    private WeeklyReportResponse.Snapshot snapshot(DailyReportViewResponse.SnapshotSummary s) {
        return s == null ? null : new WeeklyReportResponse.Snapshot(s.draftCount(), s.doingCount(), s.reviewCount(), s.doneCount(), s.activeWorkload());
    }
    private List<WeeklyReportResponse.Task> weeklyTasks(DailyReportViewResponse detail) {
        List<DailyReportViewResponse.TaskItem> source = new ArrayList<>();
        source.addAll(detail.reviewTasks()); source.addAll(detail.completedTasks()); source.addAll(detail.activeTasks());
        source.sort(Comparator.comparingInt(t -> statusRank(t.status())));
        return source.stream().limit(4).map(t -> new WeeklyReportResponse.Task(t.taskId(), t.title(), t.status(), t.workload(), t.boardName())).toList();
    }

    @Transactional(readOnly = true)
    public byte[] weeklyPdf(User current, LocalDate weekStart, Long departmentId) {
        WeeklyReportResponse report = weeklyReports(current, weekStart, departmentId);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<PDPage> pages = new ArrayList<>(); PDPageContentStream stream = null; float y = 0;
            for (WeeklyReportResponse.Day day : report.days()) {
                for (WeeklyReportResponse.StaffDay staff : day.staff()) {
                    List<String> lines = weeklyPdfLines(day.date(), staff);
                    if (stream == null || y - (lines.size() * 10 + 24) < 58) {
                        if (stream != null) stream.close();
                        PDPage page = new PDPage(PDRectangle.A4); document.addPage(page); pages.add(page);
                        stream = new PDPageContentStream(document, page); y = page.getMediaBox().getHeight() - 42;
                        if (pages.size() == 1) { text(stream, "FLOWOPS", 42, y, 9, true); y -= 18; text(stream, "Weekly Report", 42, y, 17, true); y -= 17; text(stream, report.departmentName() + " - " + report.weekStart() + " to " + report.weekEnd(), 42, y, 9, false); y -= 18; line(stream, 42, y, 553); y -= 16; text(stream, weeklyOverviewLine(report.overview()), 42, y, 7.6f, false); y -= 18; }
                    }
                    line(stream, 42, y, 553); y -= 13; text(stream, day.date().getDayOfWeek().name() + " - " + day.date(), 42, y, 10, true); y -= 13;
                    for (String line : lines) { text(stream, line, 50, y, line.endsWith("SUMMARY") || line.endsWith("BLOCKER") || line.endsWith("NEXT") || line.equals("TASKS") ? 8.2f : 8, line.endsWith("SUMMARY") || line.endsWith("BLOCKER") || line.endsWith("NEXT") || line.equals("TASKS")); y -= 10; }
                    y -= 5;
                }
            }
            if (stream != null) stream.close(); document.save(out); return out.toByteArray();
        } catch (Exception e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to generate weekly PDF", e); }
    }
    private List<String> weeklyPdfLines(LocalDate date, WeeklyReportResponse.StaffDay s) { List<String> l = new ArrayList<>(); l.add(s.userName() + " - " + s.status()); if ("NOT_STARTED".equals(s.status())) { l.add("No report submitted."); return l; } l.add("SUMMARY"); l.add(compact(s.workSummary(), 125)); l.add("BLOCKER"); l.add(compact(s.blockers(), 105)); l.add("NEXT"); l.add(compact(s.nextDayPlan(), 105)); l.add("TASKS"); for (var t : s.tasks()) l.add("[" + t.status() + "] " + compact(t.title(), 68)); l.add("Completed " + s.completedCount() + " | Review " + s.reviewCount() + " | " + metricLine(s.start(), s.end(), s.rightHandSource())); return l; }
    private String metricLine(WeeklyReportResponse.Snapshot a, WeeklyReportResponse.Snapshot b, String source) { if (a == null && b == null) return "Snapshot unavailable"; if (b == null) return "Start snapshot available"; return String.format("Draft %d->%d | Doing %d->%d | Review %d->%d | Done %d->%d | Workload %d->%d%s", a == null ? 0 : a.draft(), b.draft(), a == null ? 0 : a.doing(), b.doing(), a == null ? 0 : a.review(), b.review(), a == null ? 0 : a.done(), b.done(), a == null ? 0 : a.workload(), b.workload(), "CURRENT".equals(source) ? " current" : ""); }
    private String weeklyOverviewLine(WeeklyReportResponse.Overview o) { return String.format("Staff %d | Submitted %d | Draft %d | Not started %d | Completed %d | Moved to review %d | Avg workload %s | Week workload %s -> %s%s", o.staff(), o.dailyReportsSubmitted(), o.draftReports(), o.notStarted(), o.tasksCompleted(), o.tasksMovedToReview(), value(o.averageActiveWorkload()), value(o.weekStartWorkload()), value(o.weekEndWorkload()), o.weekEndCurrent() ? " current" : ""); }
    private String value(Integer value) { return value == null ? "—" : value.toString(); }

    @Transactional(readOnly = true)
    public byte[] pdf(User current, Long userId, LocalDate date) {
        DailyReportViewResponse v=view(current,userId,date);
        try (PDDocument document=new PDDocument(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            PDPage page=new PDPage(PDRectangle.A4); document.addPage(page); float y=page.getMediaBox().getHeight()-48;
            try(PDPageContentStream c=new PDPageContentStream(document,page)){ c.setNonStrokingColor(new Color(18,59,86)); text(c,"FLOWOPS",48,y,16,true); y-=25; text(c,"Daily Work Report",48,y,22,true); y-=28; text(c,v.employee().userName()+" - "+v.employee().departmentName()+" - "+v.report().reportDate(),48,y,10,false); y-=25; line(c,48,y,547); y-=20; y=section(c,"Daily Summary",v.report().workSummary(),y); y=section(c,"Issues / Blockers",v.report().blockers(),y); y=section(c,"Next Working Day Plan",v.report().nextDayPlan(),y); y=activity(c,v,y); y=taskSection(c,"Completed Today",v.completedTasks(),y); y=taskSection(c,"In Review",v.reviewTasks(),y); taskSection(c,"Still Active",v.activeTasks(),y); }
            document.save(out); return out.toByteArray();
        } catch(Exception e){ throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Unable to generate PDF",e); }
    }
    @Transactional(readOnly = true)
    public byte[] teamPdf(User current, LocalDate date, Long requestedDepartmentId) {
        TeamDailyReportsResponse scope = teamReports(current, date, requestedDepartmentId);
        List<User> staff = userRepository.findByDepartmentIdOrderByNameAsc(scope.departmentId()).stream()
                .filter(u -> u.getRole() == Role.STAFF).toList();
        List<TeamPdfBlock> blocks = staff.stream().map(u -> teamPdfBlock(u, date)).toList();
        try (PDDocument document = new PDDocument()) {
            List<PDPage> pages = new ArrayList<>();
            PDPageContentStream stream = null;
            float y = 0;
            for (TeamPdfBlock block : blocks) {
                List<String> lines = block.lines();
                float required = teamBlockHeight(lines) + 4;
                if (stream == null || y - required < 58) {
                    if (stream != null) stream.close();
                    PDPage page = new PDPage(PDRectangle.A4);
                    document.addPage(page); pages.add(page);
                    stream = new PDPageContentStream(document, page);
                    y = page.getMediaBox().getHeight() - 42;
                    if (pages.size() == 1) {
                        stream.setNonStrokingColor(new Color(18, 59, 86));
                        text(stream, "FLOWOPS", 42, y, 9, true); y -= 18;
                        text(stream, "Department Daily Report", 42, y, 17, true); y -= 17;
                        text(stream, scope.departmentName() + " - " + date, 42, y, 9, false); y -= 18;
                        line(stream, 42, y, 553); y -= 16;
                        String summary = String.format("Staff %d | Submitted %d | Draft Reports %d | Not Started %d | Done Today %d | Review %d | Active Workload %d",
                                scope.reports().size(), scope.submittedCount(), scope.draftCount(), scope.notStartedCount(),
                                blocks.stream().mapToInt(TeamPdfBlock::done).sum(), blocks.stream().mapToInt(TeamPdfBlock::review).sum(), blocks.stream().mapToInt(TeamPdfBlock::workload).sum());
                        text(stream, summary, 42, y, 7.6f, false); y -= 18;
                    }
                }
                y = drawTeamBlock(stream, block, y);
            }
            if (stream != null) stream.close();
            String generated = "Generated " + LocalDateTime.now(COMPANY_ZONE).format(DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm", Locale.ENGLISH));
            for (int i = 0; i < pages.size(); i++) {
                try (PDPageContentStream footer = new PDPageContentStream(document, pages.get(i), PDPageContentStream.AppendMode.APPEND, true)) {
                    text(footer, "FlowOps - " + generated + " - Page " + (i + 1) + " of " + pages.size(), 42, 28, 7, false);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(); document.save(out); return out.toByteArray();
        } catch (Exception e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to generate team PDF", e); }
    }
    private TeamPdfBlock teamPdfBlock(User user, LocalDate date) {
        DailyReportViewResponse detail = build(user, date);
        var report = detail.report(); var right = detail.rightHandState();
        List<String> lines = new ArrayList<>();
        lines.add(user.getName() + " - " + report.reportStatus());
        if ("NOT_STARTED".equals(report.reportStatus())) { lines.add("No daily report submitted."); }
        else {
            lines.add("Summary"); lines.add(compact(report.workSummary(), 210));
            lines.add("Blocker"); lines.add(compact(report.blockers(), 150));
            lines.add("Next"); lines.add(compact(report.nextDayPlan(), 150));
        }
        addTeamTaskLines(lines, detail);
        lines.add(detail.comparisonLabel());
        lines.add(metrics(detail.startOfDay(), right, detail.rightHandSource()));
        return new TeamPdfBlock(lines, detail.completedTasks().size(), right == null ? 0 : right.reviewCount(), right == null ? 0 : right.activeWorkload());
    }
    private void addTeamTaskLines(List<String> lines, DailyReportViewResponse detail) {
        List<DailyReportViewResponse.TaskItem> current = new ArrayList<>();
        current.addAll(detail.reviewTasks()); current.addAll(detail.activeTasks());
        current.sort(Comparator.comparingInt(t -> statusRank(t.status())));
        int total = current.size() + detail.completedTasks().size();
        int shown = 0;
        if (!current.isEmpty()) {
            lines.add("Tasks");
            for (var task : current) {
                if (shown == 4) break;
                lines.add("[" + task.status() + "] " + compact(task.title(), 78)); shown++;
            }
        }
        if (!detail.completedTasks().isEmpty() && shown < 6) {
            lines.add("Completed Today");
            for (var task : detail.completedTasks()) {
                if (shown == 6) break;
                lines.add("[DONE] " + compact(task.title(), 78)); shown++;
            }
        }
        if (total > shown) lines.add("+" + (total - shown) + " more tasks");
    }
    private String metrics(DailyReportViewResponse.SnapshotSummary start, DailyReportViewResponse.SnapshotSummary right, String source) {
        if (start == null && right == null) return "Snapshot activity unavailable";
        if ("START_OF_DAY_ONLY".equals(source)) return "Start: " + summaryLine(start);
        if (start == null) return "Start unavailable | " + summaryLine(right);
        if (right == null) return "End-of-day snapshot unavailable";
        return String.format("Draft %d -> %d | Doing %d -> %d | Review %d -> %d | Done %d -> %d | Workload %d -> %d", start.draftCount(), right.draftCount(), start.doingCount(), right.doingCount(), start.reviewCount(), right.reviewCount(), start.doneCount(), right.doneCount(), start.activeWorkload(), right.activeWorkload());
    }
    private float drawTeamBlock(PDPageContentStream c, TeamPdfBlock block, float y) throws Exception {
        line(c, 42, y, 553); y -= 13;
        text(c, block.lines().get(0), 42, y, 10.5f, true); y -= 13;
        for (String value : block.lines().subList(1, block.lines().size())) {
            boolean label = value.equals("Summary") || value.equals("Blocker") || value.equals("Next") || value.equals("Tasks") || value.equals("Completed Today") || value.startsWith("Start of Day");
            for (String wrapped : wrap(value, 105)) { text(c, wrapped, label ? 48 : 58, y, label ? 8.5f : 8.2f, label); y -= label ? 10 : 10; }
        }
        return y - 5;
    }
    private float teamBlockHeight(List<String> lines) {
        float height = 18;
        for (String value : lines.subList(1, lines.size())) height += wrap(value, 105).size() * 10;
        return height + 5;
    }
    private String compact(String value, int max) { String clean = value == null || value.isBlank() ? "No update provided." : value.replaceAll("\\s+", " ").trim(); return clean.length() <= max ? clean : clean.substring(0, Math.max(0, max - 1)).trim() + "…"; }
    private record TeamPdfBlock(List<String> lines, int done, int review, int workload) {}
    private float section(PDPageContentStream c,String title,String body,float y)throws Exception{ text(c,title,48,y,12,true); y-=17; for(String line:wrap(body==null||body.isBlank()?"No update provided.":body,92)){text(c,line,48,y,10,false);y-=14;} return y-10; }
    private float activity(PDPageContentStream c, DailyReportViewResponse v, float y) throws Exception {
        text(c, "Work Activity", 48, y, 12, true); y -= 18;
        text(c, v.comparisonLabel(), 48, y, 10, true); y -= 14;
        text(c, "Start: " + summaryLine(v.startOfDay()), 48, y, 10, false); y -= 14;
        if (!"START_OF_DAY_ONLY".equals(v.rightHandSource())) {
            text(c, ("CURRENT".equals(v.rightHandSource()) ? "Current: " : "End: ") + summaryLine(v.rightHandState()), 48, y, 10, false); y -= 14;
        }
        return y - 10;
    }
    private String summaryLine(DailyReportViewResponse.SnapshotSummary s){return s==null?"Not available":String.format("Draft %d | Doing %d | Review %d | Done %d | Active workload %d",s.draftCount(),s.doingCount(),s.reviewCount(),s.doneCount(),s.activeWorkload());}
    private float taskSection(PDPageContentStream c,String title,List<DailyReportViewResponse.TaskItem> tasks,float y)throws Exception{ text(c,title,48,y,12,true);y-=17; if(tasks.isEmpty()){text(c,"None",48,y,10,false);return y-24;} for(var t:tasks){text(c,"- "+t.title()+" ("+t.boardName()+")",48,y,10,false);y-=14;}return y-10; }
    private void text(PDPageContentStream c,String s,float x,float y,float size,boolean bold)throws Exception{c.beginText();c.setFont(bold?PDType1Font.HELVETICA_BOLD:PDType1Font.HELVETICA,size);c.newLineAtOffset(x,y);c.showText(s==null?"":s.replaceAll("[^\\x20-\\x7E]",""));c.endText();}
    private void line(PDPageContentStream c,float x,float y,float x2)throws Exception{c.setStrokingColor(new Color(49,139,157));c.moveTo(x,y);c.lineTo(x2,y);c.stroke();}
    private List<String> wrap(String value,int width){List<String> r=new ArrayList<>();for(String p:value.replace("\r","").split("\n")){String[] w=p.split(" ");String cur="";for(String x:w){if(cur.length()+x.length()+1>width){r.add(cur);cur=x;}else cur=cur.isBlank()?x:cur+" "+x;}if(!cur.isBlank())r.add(cur);}return r;}
}
