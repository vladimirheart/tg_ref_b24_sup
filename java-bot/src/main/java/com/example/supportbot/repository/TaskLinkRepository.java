package com.example.supportbot.repository;

import com.example.supportbot.entity.TaskLink;
import com.example.supportbot.entity.TaskLinkId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskLinkRepository extends JpaRepository<TaskLink, TaskLinkId> {
    List<TaskLink> findByIdTaskId(Long taskId);
    List<TaskLink> findByIdTicketId(String ticketId);
}
