package com.example.supportbot.repository;

import com.example.supportbot.entity.Feedback;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findByTicketIdIn(List<String> ticketIds);
}
