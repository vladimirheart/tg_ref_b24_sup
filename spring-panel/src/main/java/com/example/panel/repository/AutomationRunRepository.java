package com.example.panel.repository;

import com.example.panel.entity.AutomationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutomationRunRepository extends JpaRepository<AutomationRun, Long> {

    List<AutomationRun> findTop20ByAutomationKeyOrderByStartedAtDesc(String automationKey);
}
