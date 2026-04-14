package com.example.supportbot.repository;

import com.example.supportbot.entity.TaskPerson;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskPersonRepository extends JpaRepository<TaskPerson, Long> {
    List<TaskPerson> findByTaskId(Long taskId);
}
