package com.example.supportbot.repository;

import com.example.supportbot.entity.ChatHistory;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    Optional<ChatHistory> findTopByTicketIdAndSenderInOrderByIdDesc(String ticketId, Collection<String> senders);
}
