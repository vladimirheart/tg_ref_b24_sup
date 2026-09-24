package com.example.panel.repository;

import com.example.panel.entity.TaskEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskEventRepository extends JpaRepository<TaskEvent, Long> {

    List<TaskEvent> findTop100ByTask_IdOrderByOccurredAtDescIdDesc(Long taskId);
}
