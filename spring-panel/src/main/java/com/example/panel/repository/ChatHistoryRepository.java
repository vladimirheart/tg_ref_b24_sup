package com.example.panel.repository;

import com.example.panel.entity.ChatHistory;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    List<ChatHistory> findByTicketIdOrderByTimestampAsc(String ticketId);

    Optional<ChatHistory> findTopByTicketIdAndSenderInOrderByIdDesc(String ticketId, Collection<String> senders);
}
