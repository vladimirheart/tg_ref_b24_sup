package com.example.panel.repository;

import com.example.panel.entity.Incident;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findByIncidentKey(String incidentKey);

    List<Incident> findTop200ByOrderByUpdatedAtDescIdDesc();

    List<Incident> findByIdInOrderByUpdatedAtDescIdDesc(Collection<Long> ids);

    List<Incident> findByStatusOrderByUpdatedAtDescIdDesc(String status);

    List<Incident> findBySignalTypeOrderByUpdatedAtDescIdDesc(String signalType);

    List<Incident> findBySignalTypeAndSignalKeyOrderByUpdatedAtDescIdDesc(String signalType, String signalKey);
}
