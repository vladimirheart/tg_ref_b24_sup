package com.example.panel.repository;

import com.example.panel.entity.TaskAnalyticsView;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskAnalyticsViewRepository extends JpaRepository<TaskAnalyticsView, Long> {

    List<TaskAnalyticsView> findByOwnerIdentityOrderByUpdatedAtDescIdDesc(String ownerIdentity);

    Optional<TaskAnalyticsView> findByIdAndOwnerIdentity(Long id, String ownerIdentity);

    Optional<TaskAnalyticsView> findByOwnerIdentityAndNameIgnoreCase(String ownerIdentity, String name);
}
