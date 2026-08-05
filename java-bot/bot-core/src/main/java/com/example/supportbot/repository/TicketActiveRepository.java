package com.example.supportbot.repository;

import com.example.supportbot.entity.TicketActive;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketActiveRepository extends JpaRepository<TicketActive, String> {
    List<TicketActive> findByUserInOrderByLastSeenDesc(List<String> users);

    @Query(value = """
            SELECT ta.ticket_id, ta.user_identity, ta.last_seen
              FROM ticket_active ta
              JOIN tickets t
                ON t.ticket_id = ta.ticket_id
             WHERE ta.user_identity IN (:users)
               AND (:channelId IS NULL OR t.channel_id = :channelId)
             ORDER BY ta.last_seen DESC
            """, nativeQuery = true)
    List<TicketActive> findByUserInOrderByLastSeenDescAndChannelId(@Param("users") List<String> users,
                                                                   @Param("channelId") Long channelId);
}
