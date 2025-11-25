package com.example.supportbot.repository;

import com.example.supportbot.entity.TaskHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskHistoryRepository extends JpaRepository<TaskHistory, Long> {
    List<TaskHistory> findByTaskIdOrderByAtDesc(Long taskId);
}
