package com.company.kanban.service;

import com.company.kanban.dto.*;
import com.company.kanban.entity.*;
import com.company.kanban.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
public class InterdepartmentRequestService {
    private final InterdepartmentRequestRepository requests; private final DepartmentRepository departments; private final UserRepository users; private final NotificationService notifications;
    public InterdepartmentRequestService(InterdepartmentRequestRepository requests, DepartmentRepository departments, UserRepository users, NotificationService notifications){this.requests=requests;this.departments=departments;this.users=users;this.notifications=notifications;}
    @Transactional
    public InterdepartmentRequestResponse create(CreateInterdepartmentRequest input, User current){
        if (current == null || current.getDepartment()==null || (current.getRole()!=Role.ADMIN && !"RDD".equalsIgnoreCase(current.getDepartment().getName()))) throw forbidden();
        String targetName = input.targetDepartment()==null || input.targetDepartment().isBlank() ? "PPC" : input.targetDepartment().trim();
        if (current.getRole()!=Role.ADMIN && !"PPC".equalsIgnoreCase(targetName)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"RDD requests can only target PPC");
        Department target=departments.findByNameIgnoreCase(targetName).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Target department not found"));
        InterdepartmentRequest saved=requests.save(new InterdepartmentRequest(current.getDepartment(),target,current,input.title().trim(),input.description().trim(),input.priority(),input.neededBy()));
        history(saved,null,InterdepartmentRequestStatus.REQUESTED,current,null); notifications.notifyRequestCreated(saved,current); return toResponse(saved);
    }
    @Transactional(readOnly=true)
    public Page<InterdepartmentRequestResponse> list(User current, String view, Pageable pageable){
        String normalizedView = "archived".equalsIgnoreCase(view) ? "archived" : "history".equalsIgnoreCase(view) ? "history" : "active";
        // Archived rows remain readable as History for compatibility; no destructive migration is needed.
        List<InterdepartmentRequest> filtered = visible(current).stream()
                .filter(request -> "history".equals(normalizedView) || "archived".equals(normalizedView) ? terminal(request) : !terminal(request) && request.getArchivedAt() == null)
                .sorted(Comparator.comparing(InterdepartmentRequest::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int from = Math.min((int) pageable.getOffset(), filtered.size());
        int to = Math.min(from + pageable.getPageSize(), filtered.size());
        return new org.springframework.data.domain.PageImpl<>(filtered.subList(from, to), pageable, filtered.size()).map(this::toResponse);
    }
    @Transactional
    public InterdepartmentRequestResponse update(Long id, UpdateInterdepartmentRequestStatus input, User current){
        InterdepartmentRequest r=load(id); requireVisible(current,r);
        InterdepartmentRequestStatus from=r.getStatus(), to=input.status();
        if(!canChangeStatus(current,r,to)) throw forbidden();
        if(!valid(from,to)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid request status transition");
        if(to==InterdepartmentRequestStatus.REJECTED && (input.rejectionReason()==null || input.rejectionReason().isBlank())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Rejection reason is required");
        r.setStatus(to); if(to==InterdepartmentRequestStatus.ACKNOWLEDGED && r.getAcknowledgedAt()==null) r.setAcknowledgedAt(LocalDateTime.now()); if(to==InterdepartmentRequestStatus.COMPLETED) r.setCompletedAt(LocalDateTime.now()); if(to==InterdepartmentRequestStatus.REJECTED) r.setRejectionReason(input.rejectionReason().trim());
        requests.save(r); history(r,from,to,current,input.note()); notifications.notifyRequestStatusChanged(r,current); return toResponse(r);
    }
    @Transactional
    public InterdepartmentRequestResponse archive(Long id, User current){
        InterdepartmentRequest r=load(id); requireVisible(current,r);
        if(!terminal(r)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Only completed or rejected requests can be archived");
        r.setArchivedAt(LocalDateTime.now()); return toResponse(requests.save(r));
    }
    private List<InterdepartmentRequest> visible(User u){ if(u==null) return List.of(); if(u.getRole()==Role.ADMIN) return requests.findAll(); if(u.getDepartment()==null)return List.of(); if("PPC".equalsIgnoreCase(u.getDepartment().getName())) return requests.findByTargetDepartmentIdOrderByCreatedAtDesc(u.getDepartment().getId()).stream().filter(r->r.getAssignedTo()==null || Objects.equals(r.getAssignedTo().getId(),u.getId()) || u.getRole()==Role.MANAGER).toList(); if("RDD".equalsIgnoreCase(u.getDepartment().getName())) return requests.findByRequestingDepartmentIdOrderByCreatedAtDesc(u.getDepartment().getId()); return requests.findByCreatedByIdOrderByCreatedAtDesc(u.getId()); }
    private InterdepartmentRequest load(Long id){return requests.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Request not found"));}
    private void requireVisible(User u, InterdepartmentRequest r){if(u==null||u.getRole()!=Role.ADMIN && !Objects.equals(u.getDepartment().getId(),r.getRequestingDepartment().getId()) && !Objects.equals(u.getDepartment().getId(),r.getTargetDepartment().getId())) throw forbidden(); if("PPC".equalsIgnoreCase(r.getRequestingDepartment().getName()) && u.getRole()!=Role.ADMIN && Objects.equals(u.getDepartment().getId(),r.getRequestingDepartment().getId()) && !Objects.equals(r.getCreatedBy().getId(),u.getId())) throw forbidden();}
    private boolean canChangeStatus(User u, InterdepartmentRequest r, InterdepartmentRequestStatus to){
        if(u==null) return false;
        if(u.getRole()==Role.ADMIN) return true;
        boolean ppcHandler=u.getDepartment()!=null && Objects.equals(u.getDepartment().getId(),r.getTargetDepartment().getId()) && "PPC".equalsIgnoreCase(r.getTargetDepartment().getName());
        if(!ppcHandler) return false;
        if(u.getRole()==Role.MANAGER) return true;
        return u.getRole()==Role.STAFF && (to==InterdepartmentRequestStatus.IN_PROGRESS || to==InterdepartmentRequestStatus.COMPLETED);
    }
    private boolean valid(InterdepartmentRequestStatus from, InterdepartmentRequestStatus to){return (from==InterdepartmentRequestStatus.REQUESTED && (to==InterdepartmentRequestStatus.ACKNOWLEDGED||to==InterdepartmentRequestStatus.REJECTED)) || (from==InterdepartmentRequestStatus.ACKNOWLEDGED && (to==InterdepartmentRequestStatus.IN_PROGRESS||to==InterdepartmentRequestStatus.REJECTED)) || (from==InterdepartmentRequestStatus.IN_PROGRESS && to==InterdepartmentRequestStatus.COMPLETED);}
    private boolean terminal(InterdepartmentRequest request){return request.getStatus()==InterdepartmentRequestStatus.COMPLETED || request.getStatus()==InterdepartmentRequestStatus.REJECTED;}
    private void history(InterdepartmentRequest r, InterdepartmentRequestStatus from, InterdepartmentRequestStatus to, User actor,String note){r.getStatusHistory().add(new InterdepartmentRequestStatusHistory(r,from,to,actor,note)); requests.save(r);}
    private ResponseStatusException forbidden(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"You do not have permission to access this request");}
    private InterdepartmentRequestResponse toResponse(InterdepartmentRequest r){return new InterdepartmentRequestResponse(r.getId(),r.getRequestingDepartment().getName(),r.getTargetDepartment().getName(),r.getCreatedBy().getId(),r.getCreatedBy().getName(),r.getAssignedTo()==null?null:r.getAssignedTo().getId(),r.getAssignedTo()==null?null:r.getAssignedTo().getName(),r.getTitle(),r.getDescription(),r.getPriority(),r.getNeededBy(),r.getStatus(),r.getCreatedAt(),r.getUpdatedAt(),r.getAcknowledgedAt(),r.getCompletedAt(),r.getRejectionReason(),r.getLinkedTaskId(),r.getArchivedAt(),r.getStatusHistory().stream().map(h->new InterdepartmentRequestResponse.StatusHistory(h.getFromStatus(),h.getToStatus(),h.getChangedBy().getId(),h.getChangedBy().getName(),h.getChangedAt(),h.getNote())).toList());}
}
