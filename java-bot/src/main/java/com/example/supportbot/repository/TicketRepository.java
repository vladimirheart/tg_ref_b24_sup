package com.example.supportbot.repository;

import com.example.supportbot.entity.Ticket;
import com.example.supportbot.entity.TicketId;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, TicketId> {
    Optional<Ticket> findTopByIdUserIdOrderByIdTicketIdDesc(Long userId);

    Optional<Ticket> findByIdTicketId(String ticketId);
}
