package com.company.kanban.repository;
import com.company.kanban.entity.InterdepartmentRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InterdepartmentRequestRepository extends JpaRepository<InterdepartmentRequest, Long> {
    List<InterdepartmentRequest> findByCreatedByIdOrderByCreatedAtDesc(Long userId);
    List<InterdepartmentRequest> findByTargetDepartmentIdOrderByCreatedAtDesc(Long departmentId);
    List<InterdepartmentRequest> findByRequestingDepartmentIdOrderByCreatedAtDesc(Long departmentId);
    Page<InterdepartmentRequest> findByIdInOrderByUpdatedAtDesc(List<Long> ids, Pageable pageable);
}
