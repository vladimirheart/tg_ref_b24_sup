package com.example.supportbot.repository;

import com.example.supportbot.entity.TicketMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {
    Optional<TicketMessage> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    List<TicketMessage> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);
}
