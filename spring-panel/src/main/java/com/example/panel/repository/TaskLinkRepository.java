package com.example.panel.repository;

import com.example.panel.entity.TaskLink;
import com.example.panel.entity.TaskLinkId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskLinkRepository extends JpaRepository<TaskLink, TaskLinkId> {

    List<TaskLink> findByIdTicketId(String ticketId);
}
