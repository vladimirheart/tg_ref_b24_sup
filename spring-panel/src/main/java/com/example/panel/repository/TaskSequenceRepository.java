package com.example.panel.repository;

import com.example.panel.entity.TaskSequence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskSequenceRepository extends JpaRepository<TaskSequence, Integer> {
}
