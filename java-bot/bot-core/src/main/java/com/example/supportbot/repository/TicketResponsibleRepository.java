package com.example.supportbot.repository;

import com.example.supportbot.entity.TicketResponsible;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketResponsibleRepository extends JpaRepository<TicketResponsible, String> {
}
