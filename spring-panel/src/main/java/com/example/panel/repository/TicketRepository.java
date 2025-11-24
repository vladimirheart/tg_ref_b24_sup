package com.example.panel.repository;

import com.example.panel.entity.Ticket;
import com.example.panel.entity.TicketId;
import com.example.panel.service.db.projection.TicketAnalyticsProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, TicketId> {

    Optional<Ticket> findByIdTicketId(String ticketId);

    @Query("SELECT new com.example.panel.service.db.projection.TicketAnalyticsProjection(m.business, m.city, t.status, COUNT(t)) " +
            "FROM Ticket t JOIN Message m ON m.ticketId = t.id.ticketId GROUP BY m.business, m.city, t.status")
    List<TicketAnalyticsProjection> aggregateTicketSummary();

    @Query("SELECT t FROM Ticket t WHERE t.channel.id = :channelId")
    List<Ticket> findAllByChannelId(@Param("channelId") Long channelId);
}