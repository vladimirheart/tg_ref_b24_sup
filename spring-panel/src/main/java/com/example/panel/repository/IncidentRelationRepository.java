package com.example.panel.repository;

import com.example.panel.entity.IncidentRelation;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRelationRepository extends JpaRepository<IncidentRelation, Long> {

    List<IncidentRelation> findByIncidentIdOrderByPrimaryRelationDescCreatedAtAscIdAsc(Long incidentId);

    List<IncidentRelation> findByRelationTypeAndRelationKeyOrderByCreatedAtDescIdDesc(String relationType, String relationKey);

    List<IncidentRelation> findByRelationTypeAndRelationKeyInOrderByCreatedAtDescIdDesc(String relationType, Collection<String> relationKeys);

    void deleteByIncidentId(Long incidentId);
}
