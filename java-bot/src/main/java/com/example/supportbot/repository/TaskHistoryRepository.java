package com.example.supportbot.repository;

import com.example.supportbot.entity.TaskLink;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskLinkRepository extends JpaRepository<TaskLink, Long> {
}
