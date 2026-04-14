package com.example.supportbot.repository;

import com.example.supportbot.entity.TicketActive;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketActiveRepository extends JpaRepository<TicketActive, String> {
    List<TicketActive> findByUserInOrderByLastSeenDesc(List<String> users);
}
