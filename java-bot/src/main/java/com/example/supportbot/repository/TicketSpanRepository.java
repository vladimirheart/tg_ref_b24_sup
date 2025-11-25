package com.example.supportbot.repository;

import com.example.supportbot.entity.TicketSpan;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketSpanRepository extends JpaRepository<TicketSpan, Long> {

    Optional<TicketSpan> findTopByTicketIdOrderBySpanNumberDesc(String ticketId);

    Optional<TicketSpan> findFirstByTicketIdAndEndedAtIsNullOrderBySpanNumberDesc(String ticketId);
}
