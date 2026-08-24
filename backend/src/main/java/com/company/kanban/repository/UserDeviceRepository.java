package com.company.kanban.repository;

import com.company.kanban.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    List<UserDevice> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<UserDevice> findByIdAndUserId(Long id, Long userId);
    Optional<UserDevice> findByTokenIdentifier(String tokenIdentifier);
}
