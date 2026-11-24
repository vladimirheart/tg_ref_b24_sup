package com.example.panel.repository;

import com.example.panel.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.OffsetDateTime;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from Task t where t.id = :id")
    java.util.Optional<Task> findByIdForContentUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    List<Task> findTop50ByOrderByCreatedAtDesc();

    List<Task> findByAssigneeIgnoreCaseOrderByIdAsc(String assignee);

    long countByCreatedAtBetween(OffsetDateTime from, OffsetDateTime to);
}
