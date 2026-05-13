package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.panel.entity.RmsLicenseMonitor;
import com.example.panel.repository.RmsLicenseMonitorRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RmsLicenseMonitoringServiceTest {

    @Test
    void createMonitorRestoresPreviouslyHiddenRecord() {
        InMemoryRmsRepository repository = new InMemoryRmsRepository();
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC);

        RmsLicenseMonitor hidden = new RmsLicenseMonitor();
        hidden.setId(15L);
        hidden.setRmsAddress("https://rms.example.com");
        hidden.setCreatedAt(createdAt);
        hidden.setDeleted(true);
        hidden.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        repository.stored.add(hidden);

        RmsLicenseMonitoringService service = new RmsLicenseMonitoringService(
                repository,
                null,
                null,
                new ObjectMapper()
        );

        RmsLicenseMonitor restored = service.createMonitor(
                "rms.example.com",
                "admin",
                "secret",
                false,
                false,
                false
        );

        assertThat(repository.lastSaved).isNotNull();
        assertThat(repository.lastSaved.getId()).isEqualTo(15L);
        assertThat(repository.lastSaved.getDeleted()).isFalse();
        assertThat(repository.lastSaved.getDeletedAt()).isNull();
        assertThat(repository.lastSaved.getCreatedAt()).isEqualTo(createdAt);
        assertThat(repository.lastSaved.getEnabled()).isFalse();
        assertThat(repository.lastSaved.getLicenseMonitoringEnabled()).isFalse();
        assertThat(repository.lastSaved.getNetworkMonitoringEnabled()).isFalse();
        assertThat(repository.lastSaved.getRmsAddress()).isEqualTo("https://rms.example.com");
        assertThat(repository.lastSaved.getHost()).isEqualTo("rms.example.com");
        assertThat(repository.lastSaved.getPort()).isEqualTo(443);
        assertThat(restored.getDeleted()).isFalse();
    }

    @Test
    void deleteMonitorUsesSoftDelete() {
        InMemoryRmsRepository repository = new InMemoryRmsRepository();
        RmsLicenseMonitor active = new RmsLicenseMonitor();
        active.setId(44L);
        active.setRmsAddress("https://active.example.com");
        active.setDeleted(false);
        repository.stored.add(active);

        RmsLicenseMonitoringService service = new RmsLicenseMonitoringService(
                repository,
                null,
                null,
                new ObjectMapper()
        );

        service.deleteMonitor(44L);

        assertThat(repository.softDeletedIds).containsExactly(44L);
        assertThat(active.getDeleted()).isTrue();
        assertThat(active.getDeletedAt()).isNotNull();
    }

    private static final class InMemoryRmsRepository extends RmsLicenseMonitorRepository {
        private final List<RmsLicenseMonitor> stored = new ArrayList<>();
        private final List<Long> softDeletedIds = new ArrayList<>();
        private RmsLicenseMonitor lastSaved;

        private InMemoryRmsRepository() {
            super(null, null);
        }

        @Override
        public Optional<RmsLicenseMonitor> findAnyByRmsAddress(String rmsAddress) {
            return stored.stream()
                    .filter(item -> rmsAddress.equals(item.getRmsAddress()))
                    .findFirst();
        }

        @Override
        public Optional<RmsLicenseMonitor> findById(Long id) {
            return stored.stream()
                    .filter(item -> id.equals(item.getId()) && !Boolean.TRUE.equals(item.getDeleted()))
                    .findFirst();
        }

        @Override
        public boolean existsById(Long id) {
            return stored.stream()
                    .anyMatch(item -> id.equals(item.getId()) && !Boolean.TRUE.equals(item.getDeleted()));
        }

        @Override
        public void softDeleteById(Long id) {
            softDeletedIds.add(id);
            stored.stream()
                    .filter(item -> id.equals(item.getId()))
                    .findFirst()
                    .ifPresent(item -> {
                        item.setDeleted(true);
                        item.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
                    });
        }

        @Override
        public RmsLicenseMonitor save(RmsLicenseMonitor item) {
            lastSaved = item;
            if (item.getId() == null) {
                item.setId((long) (stored.size() + 1));
            }
            stored.removeIf(existing -> existing.getId() != null && existing.getId().equals(item.getId()));
            stored.add(item);
            return item;
        }
    }
}
