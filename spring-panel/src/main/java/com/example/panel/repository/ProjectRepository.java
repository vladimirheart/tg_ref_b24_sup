package com.example.panel.repository;

import com.example.panel.entity.Project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByProjectKeyIgnoreCase(String projectKey);

    List<Project> findAllByArchivedAtIsNullOrderByNameAsc();
}
