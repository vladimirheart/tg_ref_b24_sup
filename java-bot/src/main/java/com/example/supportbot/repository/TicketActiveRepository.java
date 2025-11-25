package com.example.supportbot.repository;

import com.example.supportbot.entity.TicketActive;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketActiveRepository extends JpaRepository<TicketActive, String> {
}
