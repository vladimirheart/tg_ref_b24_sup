package com.example.panel.repository;

import com.example.panel.entity.Message;
import com.example.panel.service.db.projection.ClientAnalyticsProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findFirstByTicketId(String ticketId);

    @Query("SELECT new com.example.panel.service.db.projection.ClientAnalyticsProjection(m.username, MAX(ch.timestamp), COUNT(DISTINCT m.ticketId)) " +
            "FROM Message m LEFT JOIN ChatHistory ch ON ch.ticketId = m.ticketId GROUP BY m.username")
    List<ClientAnalyticsProjection> aggregateClientSummary();
}
