package com.example.panel.repository;

import com.example.panel.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.OffsetDateTime;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    List<Task> findTop50ByOrderByCreatedAtDesc();

    long countByCreatedAtBetween(OffsetDateTime from, OffsetDateTime to);
}
