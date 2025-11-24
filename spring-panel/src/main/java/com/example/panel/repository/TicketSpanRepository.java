package com.example.panel.repository;

import com.example.panel.entity.TicketSpan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketSpanRepository extends JpaRepository<TicketSpan, Long> {

    List<TicketSpan> findByTicketIdOrderBySpanNoAsc(String ticketId);
}