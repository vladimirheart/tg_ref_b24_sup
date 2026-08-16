package com.example.panel.repository;

import com.example.panel.entity.Message;
import com.example.panel.service.db.projection.ClientAnalyticsProjection;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findFirstByTicketId(String ticketId);

    Optional<Message> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    List<Message> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Message> findByTicketId(String ticketId);

    @Query("""
            SELECT COUNT(m)
              FROM Message m
             WHERE m.createdDate = :createdDate
               AND (
                    m.createdAt < :createdAt
                    OR (m.createdAt = :createdAt AND m.id <= :messageId)
               )
            """)
    long countClientSequenceForDay(@Param("createdDate") LocalDate createdDate,
                                   @Param("createdAt") OffsetDateTime createdAt,
                                   @Param("messageId") Long messageId);

    @Query("SELECT new com.example.panel.service.db.projection.ClientAnalyticsProjection(m.username, MAX(ch.timestamp), COUNT(DISTINCT m.ticketId)) " +
            "FROM Message m LEFT JOIN ChatHistory ch ON ch.ticketId = m.ticketId GROUP BY m.username")
    List<ClientAnalyticsProjection> aggregateClientSummary();
}
