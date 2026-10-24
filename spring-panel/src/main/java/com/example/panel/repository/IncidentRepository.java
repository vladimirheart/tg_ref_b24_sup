package com.example.panel.repository;

import com.example.panel.entity.Incident;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Incident i where i.id = :id")
    Optional<Incident> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Incident i where i.signalType = :type and i.signalKey = :key "
        + "and i.status not in ('resolved', 'closed') order by i.updatedAt desc, i.id desc")
    List<Incident> findActiveSignalForUpdate(@Param("type") String type, @Param("key") String key);

    Optional<Incident> findByIncidentKey(String incidentKey);

    List<Incident> findTop200ByOrderByUpdatedAtDescIdDesc();

    List<Incident> findByIdInOrderByUpdatedAtDescIdDesc(Collection<Long> ids);

    List<Incident> findByStatusOrderByUpdatedAtDescIdDesc(String status);

    List<Incident> findBySignalTypeOrderByUpdatedAtDescIdDesc(String signalType);

    List<Incident> findBySignalTypeAndSignalKeyOrderByUpdatedAtDescIdDesc(String signalType, String signalKey);

    long countByStatusNotIn(Collection<String> statuses);

    long countBySeverityAndStatusNotIn(String severity, Collection<String> statuses);

    long countByCreatedAtBeforeAndStatusNotIn(OffsetDateTime threshold, Collection<String> statuses);

    long countByCreatedAtGreaterThanEqual(OffsetDateTime threshold);

    long countByResolvedAtGreaterThanEqual(OffsetDateTime threshold);

    List<Incident> findByCreatedAtGreaterThanEqual(OffsetDateTime threshold);

    List<Incident> findTop100BySeverityAndStatusNotInOrderByCreatedAtAscIdAsc(
        String severity,
        Collection<String> statuses
    );

    List<Incident> findTop100ByCreatedAtBeforeAndStatusNotInOrderByCreatedAtAscIdAsc(
        OffsetDateTime threshold,
        Collection<String> statuses
    );
}
