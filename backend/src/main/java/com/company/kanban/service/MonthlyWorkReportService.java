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
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MonthlyWorkReportService {
    public static final ZoneId COMPANY_ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    private final MonthlyWorkReportRepository reports;
    private final UserRepository users;
    private final TaskRepository tasks;
    private final SnapshotBatchRepository batches;
    private final TaskSnapshotRepository snapshots;
    private final AuthorizationService authorization;

    public MonthlyWorkReportService(MonthlyWorkReportRepository reports, UserRepository users, TaskRepository tasks,
                                    SnapshotBatchRepository batches, TaskSnapshotRepository snapshots, AuthorizationService authorization) {
        this.reports=reports; this.users=users; this.tasks=tasks; this.batches=batches; this.snapshots=snapshots; this.authorization=authorization;
    }

    @Transactional(readOnly = true)
    public MonthlyWorkReportResponse view(User current, Long targetId, int year, int month) {
        YearMonth period = period(year, month);
        User target = users.findById(targetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        authorization.requireStaffViewerAccess(current, target);
        MonthlyWorkReport report = reports.findByUserIdAndYearAndMonth(targetId, year, month).orElse(null);
        return response(target, period, report);
    }

    @Transactional
    public MonthlyWorkReportResponse saveDraft(User current, int year, int month, MonthlyWorkReportRequest request) {
        YearMonth period = period(year, month);
        MonthlyWorkReport report = reports.findByUserIdAndYearAndMonth(current.getId(), year, month).orElseGet(() -> new MonthlyWorkReport(current, year, month));
        if (report.getStatus() == MonthlyWorkReportStatus.SUBMITTED) throw new ResponseStatusException(HttpStatus.CONFLICT, "Submitted reports are read-only");
        validate(request);
        report.update(value(request == null ? null : request.monthlySummary()), value(request == null ? null : request.keyAchievements()), value(request == null ? null : request.blockers()), value(request == null ? null : request.nextMonthPlan()));
        reports.save(report);
        return response(current, period, report);
    }

    @Transactional
    public MonthlyWorkReportResponse submit(User current, int year, int month) {
        YearMonth period = period(year, month);
        MonthlyWorkReport report = reports.findByUserIdAndYearAndMonth(current.getId(), year, month).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Save a draft before submitting"));
        if (report.getStatus() != MonthlyWorkReportStatus.SUBMITTED) { report.submit(); reports.save(report); }
        return response(current, period, report);
    }

    @Transactional(readOnly = true)
    public MonthlyTeamReportResponse team(User current, int year, int month, Long requestedDepartmentId) {
        YearMonth period = period(year, month);
        Long departmentId = current.getRole() == Role.MANAGER ? current.getDepartment().getId() : requestedDepartmentId;
        if (departmentId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "departmentId is required");
        authorization.requireDepartmentAccess(current, departmentId);
        List<User> staff = users.findByDepartmentIdOrderByNameAsc(departmentId).stream().filter(u -> u.getRole() == Role.STAFF).toList();
        List<MonthlyTeamReportResponse.EmployeeSummary> rows = staff.stream().map(u -> {
            MonthlyWorkReportResponse r = response(u, period, reports.findByUserIdAndYearAndMonth(u.getId(), year, month).orElse(null));
            return new MonthlyTeamReportResponse.EmployeeSummary(u.getId(), u.getName(), r.report().status(), r.overview().completedCount(), r.overview().ongoingCount(), r.overview().activeWorkload(), r.report().monthlySummary(), r.report().keyAchievements(), r.report().blockers(), r.report().nextMonthPlan());
        }).toList();
        return new MonthlyTeamReportResponse(year, month, period.atDay(1), period.atEndOfMonth(), departmentId, staff.isEmpty() ? departmentName(current, departmentId) : staff.get(0).getDepartment().getName(),
                new MonthlyTeamReportResponse.Summary(rows.size(), (int) rows.stream().filter(r -> "SUBMITTED".equals(r.status())).count(), rows.stream().mapToInt(MonthlyTeamReportResponse.EmployeeSummary::completedCount).sum(), rows.stream().mapToInt(MonthlyTeamReportResponse.EmployeeSummary::ongoingCount).sum()), rows);
    }

    @Transactional(readOnly = true)
    public byte[] pdf(User current, Long targetId, int year, int month) {
        MonthlyWorkReportResponse r = view(current, targetId, year, month);
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4); doc.addPage(page);
            try (PDPageContentStream c = new PDPageContentStream(doc, page)) {
                float y=790; c.setNonStrokingColor(23,43,77);
                y=line(c,"FlowOps",r.employee().userName()+" · Monthly Work Report",50,y,18,true); y-=8;
                y=line(c,"Employee: "+r.employee().userName(),"Department: "+r.employee().departmentName()+" · "+monthName(r.period().month())+" "+r.period().year(),50,y,10,false); y-=14;
                y=section(c,"Monthly Summary",r.report().monthlySummary(),50,y); y=section(c,"Key Achievements",r.report().keyAchievements(),50,y); y=section(c,"Challenges / Blockers",r.report().blockers(),50,y); y=section(c,"Next Month Plan",r.report().nextMonthPlan(),50,y);
                y=section(c,"Task Summary","Completed: "+r.overview().completedCount()+" · Ongoing / carry-over: "+r.overview().ongoingCount()+" · Active workload: "+r.overview().activeWorkload(),50,y);
                y=list(c,"Completed Tasks",r.completedTasks(),50,y); list(c,"Ongoing / Carry-over Tasks",r.ongoingTasks(),50,y);
            }
            doc.save(out); return out.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("Unable to generate monthly report PDF", e); }
    }

    @Transactional(readOnly = true)
    public byte[] teamPdf(User current, int year, int month, Long departmentId) {
        MonthlyTeamReportResponse team = team(current, year, month, departmentId);
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<PdfLine> lines = new ArrayList<>();
            lines.add(new PdfLine(team.departmentName()+" Monthly Report",18,true));
            lines.add(new PdfLine(monthName(month)+" "+year,10,false));
            lines.add(new PdfLine("",8,false));
            lines.add(new PdfLine("Department Summary",12,true));
            lines.add(new PdfLine("Employees: "+team.summary().employees()+" · Submitted: "+team.summary().submitted()+" / "+team.summary().employees()+" · Completed: "+team.summary().completedTasks()+" · Ongoing: "+team.summary().ongoingTasks(),10,false));
            for (var employee: team.employees()) {
                MonthlyWorkReportResponse detail = view(current, employee.userId(), year, month);
                lines.add(new PdfLine("",8,false));
                lines.add(new PdfLine(employee.userName()+" · "+employee.status(),12,true));
                lines.add(new PdfLine("Completed: "+employee.completedCount()+" · Ongoing: "+employee.ongoingCount()+" · Active workload: "+employee.activeWorkload(),10,false));
                addPdfSection(lines,"Monthly Summary",detail.report().monthlySummary());
                addPdfSection(lines,"Key Achievements",detail.report().keyAchievements());
                addPdfSection(lines,"Challenges / Blockers",detail.report().blockers());
                addPdfSection(lines,"Next Month Plan",detail.report().nextMonthPlan());
                addPdfTasks(lines,"Completed Tasks",detail.completedTasks());
                addPdfTasks(lines,"Ongoing / Carry-over Tasks",detail.ongoingTasks());
            }
            renderLines(doc, lines);
            doc.save(out); return out.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("Unable to generate department monthly PDF", e); }
    }

    private void addPdfSection(List<PdfLine> lines,String title,String text){lines.add(new PdfLine(title,10,true)); String value=value(text); if(value.isBlank()) value="No update provided."; for(String part:value.split("\\R",-1)) lines.add(new PdfLine(part,9,false));}
    private void addPdfTasks(List<PdfLine> lines,String title,List<MonthlyWorkReportResponse.TaskItem> tasks){lines.add(new PdfLine(title,10,true)); if(tasks.isEmpty()){lines.add(new PdfLine("None",9,false)); return;} for(var task:tasks) lines.add(new PdfLine("- "+task.title()+" · "+task.projectName()+" · workload "+(task.workload()==null?"—":task.workload()),9,false));}
    private void renderLines(PDDocument doc,List<PdfLine> lines)throws java.io.IOException { PDPageContentStream stream=null; float y=790; try { for(PdfLine line:lines){if(stream==null||y<55){if(stream!=null)stream.close(); PDPage page=new PDPage(PDRectangle.A4); doc.addPage(page); stream=new PDPageContentStream(doc,page); y=790;} stream.beginText(); stream.setFont(line.bold()?PDType1Font.HELVETICA_BOLD:PDType1Font.HELVETICA,line.size()); stream.newLineAtOffset(50,y); stream.showText(line.text().length()>120?line.text().substring(0,120):line.text()); stream.endText(); y-=line.bold()?16:13; }} finally {if(stream!=null)stream.close();}}
    private record PdfLine(String text,float size,boolean bold){}

    private MonthlyWorkReportResponse response(User user, YearMonth period, MonthlyWorkReport report) {
        TaskData data = aggregate(user, period);
        MonthlyWorkReportResponse.Narrative narrative = report == null
                ? new MonthlyWorkReportResponse.Narrative("DRAFT", "", "", "", "", null)
                : new MonthlyWorkReportResponse.Narrative(report.getStatus().name(), value(report.getMonthlySummary()), value(report.getKeyAchievements()), value(report.getBlockers()), value(report.getNextMonthPlan()), report.getSubmittedAt());
        String dept = user.getDepartment() == null ? "" : user.getDepartment().getName();
        return new MonthlyWorkReportResponse(new MonthlyWorkReportResponse.Period(period.getYear(),period.getMonthValue(),period.atDay(1),period.atEndOfMonth()), new MonthlyWorkReportResponse.Employee(user.getId(),user.getName(),user.getRole().name(),user.getDepartment()==null?null:user.getDepartment().getId(),dept), narrative,
                new MonthlyWorkReportResponse.Overview(data.completed.size(), data.ongoing.size(), data.ongoing.stream().mapToInt(t -> t.workload()==null?0:t.workload()).sum(), (int)data.projects()), data.completed, data.ongoing);
    }

    private TaskData aggregate(User user, YearMonth period) {
        LocalDate start=period.atDay(1), end=period.atEndOfMonth(); LocalDate today=LocalDate.now(COMPANY_ZONE);
        Map<Long,List<TaskSnapshot>> history = new HashMap<>();
        for (SnapshotBatch b : batches.findBySnapshotDateBetweenAndStatusOrderBySnapshotDateAsc(start,end,SnapshotBatchStatus.COMPLETED)) {
            for (TaskSnapshot s : snapshots.findByBatchIdOrderByPositionAsc(b.getId())) history.computeIfAbsent(s.getTaskId(), ignored -> new ArrayList<>()).add(s);
        }
        List<MonthlyWorkReportResponse.TaskItem> completed=new ArrayList<>(), ongoing=new ArrayList<>();
        for (Task t : tasks.findDetailedByAssigneeId(user.getId())) {
            LocalDate completion = t.getCompletedAt()==null?null:t.getCompletedAt().toLocalDate();
            List<TaskSnapshot> track=history.getOrDefault(t.getId(),List.of());
            if (completion == null) for (TaskSnapshot s:track) if (s.getStatus()==TaskStatus.DONE) { completion = batches.findById(s.getBatch().getId()).map(SnapshotBatch::getSnapshotDate).orElse(null); break; }
            if (completion != null && !completion.isBefore(start) && !completion.isAfter(end)) {
                completed.add(item(t, "DONE", completion)); continue;
            }
            boolean active;
            if (end.isBefore(today) && !track.isEmpty()) active = track.get(track.size()-1).getStatus()!=TaskStatus.DONE;
            else active = t.getStatus()!=TaskStatus.DONE && !t.getCreatedAt().toLocalDate().isAfter(end);
            if (active) ongoing.add(item(t, t.getStatus()==null?"ACTIVE":t.getStatus().name(), null));
        }
        return new TaskData(completed,ongoing);
    }

    private MonthlyWorkReportResponse.TaskItem item(Task t,String status,LocalDate done) { String project=t.getColumn()!=null&&t.getColumn().getBoard()!=null?t.getColumn().getBoard().getName():"General Tasks"; return new MonthlyWorkReportResponse.TaskItem(t.getId(),t.getTitle(),project,status,t.getWorkload(),done); }
    private YearMonth period(int year,int month){try{return YearMonth.of(year,month);}catch(DateTimeException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid year or month");}}
    private void validate(MonthlyWorkReportRequest r){if(r==null)return; for(String s:List.of(r.monthlySummary(),r.keyAchievements(),r.blockers(),r.nextMonthPlan())) if(s!=null&&s.length()>10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Monthly report text is too long");}
    private String value(String s){return s==null?"":s;}
    private String departmentName(User current,Long id){return current.getDepartment()!=null&&Objects.equals(current.getDepartment().getId(),id)?current.getDepartment().getName():users.findByDepartmentIdOrderByNameAsc(id).stream().findFirst().map(u->u.getDepartment().getName()).orElse("Department");}
    private String monthName(int month){return Month.of(month).getDisplayName(TextStyle.FULL,Locale.ENGLISH);}
    private record TaskData(List<MonthlyWorkReportResponse.TaskItem> completed,List<MonthlyWorkReportResponse.TaskItem> ongoing){long projects(){return java.util.stream.Stream.concat(completed.stream(),ongoing.stream()).map(MonthlyWorkReportResponse.TaskItem::projectName).filter(p->!"General Tasks".equals(p)).distinct().count();}}
    private float line(PDPageContentStream c,String a,String b,float x,float y,float size,boolean bold)throws java.io.IOException{c.beginText();c.setFont(bold?PDType1Font.HELVETICA_BOLD:PDType1Font.HELVETICA,size);c.newLineAtOffset(x,y);c.showText(a);c.endText();y-=size+4;c.beginText();c.setFont(PDType1Font.HELVETICA,10);c.newLineAtOffset(x,y);c.showText(b);c.endText();return y-12;}
    private float section(PDPageContentStream c,String title,String text,float x,float y)throws java.io.IOException{c.beginText();c.setFont(PDType1Font.HELVETICA_BOLD,11);c.newLineAtOffset(x,y);c.showText(title);c.endText();y-=16;String v=value(text);for(String part:v.split("\\R",-1)){c.beginText();c.setFont(PDType1Font.HELVETICA,9);c.newLineAtOffset(x,y);c.showText(part.length()>115?part.substring(0,115):part);c.endText();y-=12;}return y-8;}
    private float list(PDPageContentStream c,String title,List<MonthlyWorkReportResponse.TaskItem> rows,float x,float y)throws java.io.IOException{c.beginText();c.setFont(PDType1Font.HELVETICA_BOLD,11);c.newLineAtOffset(x,y);c.showText(title);c.endText();y-=15;for(var t:rows){c.beginText();c.setFont(PDType1Font.HELVETICA,9);c.newLineAtOffset(x,y);c.showText("• "+t.title()+" · "+t.projectName()+" · workload "+(t.workload()==null?"—":t.workload()));c.endText();y-=12;}return y-6;}
}
