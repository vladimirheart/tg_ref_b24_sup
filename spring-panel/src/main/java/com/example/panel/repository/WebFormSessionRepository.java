package com.example.panel.repository;

import com.example.panel.entity.WebFormSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WebFormSessionRepository extends JpaRepository<WebFormSession, Long> {

    Optional<WebFormSession> findByToken(String token);
}
