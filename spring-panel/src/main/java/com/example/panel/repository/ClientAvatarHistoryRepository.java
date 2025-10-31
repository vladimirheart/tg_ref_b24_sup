package com.example.panel.repository;

import com.example.panel.entity.ClientAvatarHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientAvatarHistoryRepository extends JpaRepository<ClientAvatarHistory, Long> {

    List<ClientAvatarHistory> findByUserIdOrderByLastSeenAtDesc(Long userId);
}
