package com.example.panel.repository;

import com.example.panel.entity.ApplicationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationRecordRepository extends JpaRepository<ApplicationRecord, Long> {

    List<ApplicationRecord> findByUserUserId(Long userId);
}