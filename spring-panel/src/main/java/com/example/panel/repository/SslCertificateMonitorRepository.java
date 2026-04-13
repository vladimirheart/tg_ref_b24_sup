package com.example.panel.repository;

import com.example.panel.entity.SslCertificateMonitor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SslCertificateMonitorRepository extends JpaRepository<SslCertificateMonitor, Long> {

    List<SslCertificateMonitor> findAllByOrderBySiteNameAscIdAsc();

    List<SslCertificateMonitor> findByEnabledTrueOrderBySiteNameAscIdAsc();

    Optional<SslCertificateMonitor> findByEndpointUrl(String endpointUrl);
}
