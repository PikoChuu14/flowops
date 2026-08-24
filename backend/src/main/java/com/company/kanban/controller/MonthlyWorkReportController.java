package com.company.kanban.controller;

import com.company.kanban.dto.*;
import com.company.kanban.entity.User;
import com.company.kanban.service.MonthlyWorkReportService;
import org.springframework.format.annotation.NumberFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports/monthly")
public class MonthlyWorkReportController {
    private final MonthlyWorkReportService service;
    public MonthlyWorkReportController(MonthlyWorkReportService service){this.service=service;}
    @GetMapping("/me") public MonthlyWorkReportResponse me(@AuthenticationPrincipal User u,@RequestParam int year,@RequestParam int month){return service.view(u,u.getId(),year,month);}
    @PutMapping("/me") public MonthlyWorkReportResponse save(@AuthenticationPrincipal User u,@RequestParam int year,@RequestParam int month,@RequestBody MonthlyWorkReportRequest r){return service.saveDraft(u,year,month,r);}
    @PostMapping("/me/submit") public MonthlyWorkReportResponse submit(@AuthenticationPrincipal User u,@RequestParam int year,@RequestParam int month){return service.submit(u,year,month);}
    @GetMapping("/users/{userId}") public MonthlyWorkReportResponse user(@AuthenticationPrincipal User u,@PathVariable Long userId,@RequestParam int year,@RequestParam int month){return service.view(u,userId,year,month);}
    @GetMapping("/me/pdf") public ResponseEntity<byte[]> mePdf(@AuthenticationPrincipal User u,@RequestParam int year,@RequestParam int month){return pdf(u,u.getId(),year,month);}
    @GetMapping("/users/{userId}/pdf") public ResponseEntity<byte[]> userPdf(@AuthenticationPrincipal User u,@PathVariable Long userId,@RequestParam int year,@RequestParam int month){return pdf(u,userId,year,month);}
    @GetMapping("/team") public MonthlyTeamReportResponse team(@AuthenticationPrincipal User u,@RequestParam int year,@RequestParam int month,@RequestParam(required=false) Long departmentId){return service.team(u,year,month,departmentId);}
    @GetMapping("/team/pdf") public ResponseEntity<byte[]> teamPdf(@AuthenticationPrincipal User u,@RequestParam int year,@RequestParam int month,@RequestParam(required=false) Long departmentId){var t=service.team(u,year,month,departmentId);String name="FlowOps-"+t.departmentName().replaceAll("[^A-Za-z0-9]+","-")+"-"+year+"-"+month+"-Monthly-Report.pdf";return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+name+"\"").body(service.teamPdf(u,year,month,departmentId));}
    private ResponseEntity<byte[]> pdf(User u,Long id,int year,int month){var r=service.view(u,id,year,month);String name="FlowOps-"+r.employee().userName().replaceAll("[^A-Za-z0-9]+","-")+"-"+year+"-"+month+"-Monthly-Report.pdf";return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+name+"\"").body(service.pdf(u,id,year,month));}
}
