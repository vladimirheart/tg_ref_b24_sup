package com.example.panel.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.panel.storage.AttachmentObjectStorageService;
import com.example.panel.storage.AttachmentService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ChatAttachmentMetadataAvailabilityServiceTest {

    @Test
    void reconcileDoesNotAttemptSchemaMutationDuringAvailabilityRefresh() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        AttachmentObjectStorageService attachmentObjectStorageService = mock(AttachmentObjectStorageService.class);
        doReturn(List.of()).when(jdbcTemplate)
                .query(anyString(), ArgumentMatchers.<RowMapper<Object>>any());

        ChatAttachmentMetadataAvailabilityService service =
                new ChatAttachmentMetadataAvailabilityService(jdbcTemplate, attachmentService, attachmentObjectStorageService);

        service.reconcileAvailabilityStatuses();

        verify(jdbcTemplate, never()).execute(anyString());
    }
}
