package com.example.supportbot.repository;

import com.example.supportbot.entity.ChannelNotification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelNotificationRepository extends JpaRepository<ChannelNotification, Long> {
}
