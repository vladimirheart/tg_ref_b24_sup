package com.example.panel.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.config.PanelDatabaseRuntimeMode;
import com.example.panel.storage.AttachmentService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

class ChatAttachmentMetadataAvailabilityServiceTest {

    @Test
    void reconcileSkipsSchemaAlterInExternalMode() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        when(jdbcTemplate.query(anyString(), any())).thenReturn(List.of());
        PanelDatabaseRuntimeMode runtimeMode = new PanelDatabaseRuntimeMode(
                new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/supportpanel")
        );

        ChatAttachmentMetadataAvailabilityService service =
                new ChatAttachmentMetadataAvailabilityService(jdbcTemplate, attachmentService, runtimeMode);

        service.reconcileAvailabilityStatuses();

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void reconcileKeepsLegacySchemaAlterInSqliteMode() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        when(jdbcTemplate.query(anyString(), any())).thenReturn(List.of());
        PanelDatabaseRuntimeMode runtimeMode = new PanelDatabaseRuntimeMode(new MockEnvironment());

        ChatAttachmentMetadataAvailabilityService service =
                new ChatAttachmentMetadataAvailabilityService(jdbcTemplate, attachmentService, runtimeMode);

        service.reconcileAvailabilityStatuses();

        verify(jdbcTemplate, times(1)).execute(anyString());
    }
}
