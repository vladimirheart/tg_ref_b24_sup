package com.example.panel.repository;

import com.example.panel.entity.IncidentRoute;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRouteRepository extends JpaRepository<IncidentRoute, Long> {

    List<IncidentRoute> findByIncidentIdOrderByCreatedAtAscIdAsc(Long incidentId);
}
