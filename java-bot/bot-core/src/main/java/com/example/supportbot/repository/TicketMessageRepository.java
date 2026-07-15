package com.example.supportbot.repository;

import com.example.supportbot.entity.TicketMessage;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {
    Optional<TicketMessage> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    List<TicketMessage> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<TicketMessage> findByTicketId(String ticketId);

    @Query("""
            SELECT COUNT(m)
              FROM TicketMessage m
             WHERE m.createdDate = :createdDate
               AND (
                    m.createdAt < :createdAt
                    OR (m.createdAt = :createdAt AND m.id <= :messageId)
               )
            """)
    long countClientSequenceForDay(@Param("createdDate") LocalDate createdDate,
                                   @Param("createdAt") OffsetDateTime createdAt,
                                   @Param("messageId") Long messageId);
}
