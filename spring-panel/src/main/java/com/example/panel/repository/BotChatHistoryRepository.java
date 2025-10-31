package com.example.panel.repository;

import com.example.panel.entity.BotChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BotChatHistoryRepository extends JpaRepository<BotChatHistory, Long> {

    List<BotChatHistory> findByUserUserIdOrderByTimestampAsc(Long userId);
}
