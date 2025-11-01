package com.example.panel.repository;

import com.example.panel.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdentityOrderByCreatedAtDesc(String userIdentity);

    long countByUserIdentityAndIsReadFalse(String userIdentity);

    Optional<Notification> findByIdAndUserIdentity(Long id, String userIdentity);
}
