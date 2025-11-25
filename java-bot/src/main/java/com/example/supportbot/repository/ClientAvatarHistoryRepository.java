package com.example.supportbot.repository;

import com.example.supportbot.entity.ClientAvatarHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientAvatarHistoryRepository extends JpaRepository<ClientAvatarHistory, Long> {
}
