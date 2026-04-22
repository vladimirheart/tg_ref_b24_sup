package com.example.panel.repository;

import com.example.panel.entity.RmsLicenseMonitor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RmsLicenseMonitorRepository extends JpaRepository<RmsLicenseMonitor, Long> {

    List<RmsLicenseMonitor> findAllByOrderByRmsAddressAscIdAsc();

    Optional<RmsLicenseMonitor> findByRmsAddress(String rmsAddress);
}
