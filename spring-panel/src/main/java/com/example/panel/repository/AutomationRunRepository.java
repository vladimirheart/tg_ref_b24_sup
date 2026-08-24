package com.example.panel.repository;

import com.example.panel.entity.AutomationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AutomationRunRepository extends JpaRepository<AutomationRun, Long> {

    List<AutomationRun> findTop20ByAutomationKeyAndActorIgnoreCaseOrderByStartedAtDesc(String automationKey, String actor);

    Optional<AutomationRun> findByIdAndAutomationKeyAndActorIgnoreCase(Long id, String automationKey, String actor);
}
