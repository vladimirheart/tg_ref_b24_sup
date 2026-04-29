package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.panel.entity.IikoApiMonitor;
import com.example.panel.repository.IikoApiMonitorRepository;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IikoApiMonitoringServiceTest {

    @Test
    void createMonitorRejectsLocationSourceFlagForNonOrganizationsRequest() {
        IikoApiMonitoringService service = new IikoApiMonitoringService(
                new IikoApiMonitorRepository(null),
                new MonitoringCheckHistoryRepository(null),
                new ObjectMapper()
        );

        IikoApiMonitoringService.MonitorDraft draft = new IikoApiMonitoringService.MonitorDraft(
                "terminal groups",
                "https://api-ru.iiko.services",
                "key",
                "terminal_groups",
                new IikoApiMonitoringService.MonitorConfig(
                        List.of("org-id"),
                        null,
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null
                ),
                true,
                true
        );

        assertThatThrownBy(() -> service.createMonitor(draft))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("organizations");
    }

    @Test
    void createMonitorPersistsLocationSourceFlagForOrganizationsRequest() {
        IikoApiMonitorRepository repository = new IikoApiMonitorRepository(null) {
            private IikoApiMonitor saved;

            @Override
            public IikoApiMonitor save(IikoApiMonitor item) {
                if (item.getId() == null) {
                    item.setId(1L);
                }
                this.saved = item;
                return item;
            }

            @Override
            public Optional<IikoApiMonitor> findById(Long id) {
                return Optional.ofNullable(saved);
            }
        };
        IikoApiMonitoringService service = new IikoApiMonitoringService(
                repository,
                new MonitoringCheckHistoryRepository(null),
                new ObjectMapper()
        );

        IikoApiMonitoringService.MonitorDraft draft = new IikoApiMonitoringService.MonitorDraft(
                "organizations",
                "https://api-ru.iiko.services",
                "key",
                "organizations",
                new IikoApiMonitoringService.MonitorConfig(
                        List.of(),
                        null,
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        List.of(),
                        null
                ),
                true,
                true
        );

        IikoApiMonitor saved = service.createMonitor(draft);

        assertThat(saved.getLocationsSyncEnabled()).isTrue();
        assertThat(saved.getRequestType()).isEqualTo("organizations");
    }
}
