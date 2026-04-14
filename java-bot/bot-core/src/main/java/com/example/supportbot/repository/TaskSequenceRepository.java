package com.example.supportbot.repository;

import com.example.supportbot.entity.TaskSequence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskSequenceRepository extends JpaRepository<TaskSequence, Integer> {
}
