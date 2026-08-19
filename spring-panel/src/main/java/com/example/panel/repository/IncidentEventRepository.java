package com.example.panel.repository;

import com.example.panel.entity.IncidentEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentEventRepository extends JpaRepository<IncidentEvent, Long> {

    List<IncidentEvent> findByIncidentIdOrderByCreatedAtAscIdAsc(Long incidentId);
}
