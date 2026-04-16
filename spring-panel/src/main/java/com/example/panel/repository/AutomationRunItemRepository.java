package com.example.panel.repository;

import com.example.panel.entity.AutomationRunItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutomationRunItemRepository extends JpaRepository<AutomationRunItem, Long> {

    List<AutomationRunItem> findByRunIdOrderByCreatedAtAsc(Long runId);
}
