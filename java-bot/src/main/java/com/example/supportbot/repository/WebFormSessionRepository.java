package com.example.supportbot.repository;

import com.example.supportbot.entity.WebFormSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebFormSessionRepository extends JpaRepository<WebFormSession, Long> {
}
