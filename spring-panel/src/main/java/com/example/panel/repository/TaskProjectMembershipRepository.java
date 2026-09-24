package com.example.panel.repository;

import com.example.panel.entity.TaskProjectMembership;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskProjectMembershipRepository extends JpaRepository<TaskProjectMembership, Long> {

    List<TaskProjectMembership> findByTask_IdOrderByAddedAtAsc(Long taskId);
}
