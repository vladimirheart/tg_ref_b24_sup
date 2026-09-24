package com.example.panel.repository;

import com.example.panel.entity.ProjectBoard;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectBoardRepository extends JpaRepository<ProjectBoard, Long> {

    Optional<ProjectBoard> findByScopeKeyIgnoreCase(String scopeKey);
}
