package com.example.panel.repository;

import com.example.panel.entity.IncidentWatcher;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentWatcherRepository extends JpaRepository<IncidentWatcher, Long> {

    List<IncidentWatcher> findByIncidentIdOrderByWatcherIdentityAsc(Long incidentId);

    void deleteByIncidentId(Long incidentId);
}
