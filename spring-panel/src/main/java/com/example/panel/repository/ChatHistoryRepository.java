package com.example.panel.repository;

import com.example.panel.entity.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    List<ChatHistory> findByTicketIdOrderByTimestampAsc(String ticketId);
}
