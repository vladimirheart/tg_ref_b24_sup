package com.example.supportbot.repository;

import com.example.supportbot.entity.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByReadIsNullOrReadFalseOrderByCreatedAtAsc();
}
