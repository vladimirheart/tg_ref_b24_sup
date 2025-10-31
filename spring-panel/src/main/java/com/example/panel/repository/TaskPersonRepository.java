package com.example.panel.repository;

import com.example.panel.entity.TaskPerson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskPersonRepository extends JpaRepository<TaskPerson, Long> {

    List<TaskPerson> findByTaskId(Long taskId);
}
