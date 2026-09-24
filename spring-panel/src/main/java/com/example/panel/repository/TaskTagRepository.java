package com.example.panel.repository;

import com.example.panel.entity.TaskTag;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskTagRepository extends JpaRepository<TaskTag, Long> {

    List<TaskTag> findByTask_IdOrderByAddedAtAsc(Long taskId);
}
