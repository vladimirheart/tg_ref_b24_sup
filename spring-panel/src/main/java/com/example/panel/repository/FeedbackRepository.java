package com.example.panel.repository;

import com.example.panel.entity.Feedback;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByUserId(Long userId);

    List<Feedback> findByTicketIdIn(List<String> ticketIds);

    boolean existsByTicketId(String ticketId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Feedback> findFirstByTicketIdOrderByTimestampDesc(String ticketId);
}
