package com.example.panel.repository;

import com.example.panel.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByUserId(Long userId);

    List<Feedback> findByTicketIdIn(List<String> ticketIds);

    boolean existsByTicketId(String ticketId);

    Optional<Feedback> findFirstByTicketIdOrderByTimestampDesc(String ticketId);
}
