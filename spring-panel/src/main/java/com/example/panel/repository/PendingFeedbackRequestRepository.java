package com.example.panel.repository;

import com.example.panel.entity.PendingFeedbackRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PendingFeedbackRequestRepository extends JpaRepository<PendingFeedbackRequest, Long> {

    List<PendingFeedbackRequest> findByTicketId(String ticketId);
}
