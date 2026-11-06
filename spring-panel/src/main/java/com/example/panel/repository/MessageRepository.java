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

    @Query(value = """
        SELECT m.* FROM messages m JOIN tickets t ON t.ticket_id=m.ticket_id AND t.user_id=m.user_id AND t.channel_id=m.channel_id
         WHERE m.user_id=:userId AND t.channel_id=:channelId
           AND 1=(SELECT COUNT(*) FROM tickets candidate WHERE candidate.ticket_id=m.ticket_id)
         ORDER BY m.created_at DESC, m.group_msg_id DESC LIMIT :limit
        """, nativeQuery = true)
    List<Message> findScopedRecent(@Param("userId") Long userId, @Param("channelId") Long channelId, @Param("limit") int limit);

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
