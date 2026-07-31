package com.example.panel.service;

import com.example.panel.storage.AttachmentService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatAttachmentMetadataAvailabilityServiceTest {

    @Test
    void reconcileAvailabilityStatusesSkipsCorruptedMetadataWithoutFailingStartup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        ChatAttachmentMetadataAvailabilityService service =
                new ChatAttachmentMetadataAvailabilityService(jdbcTemplate, attachmentService);

        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenThrow(new DataIntegrityViolationException("database disk image is malformed"));

        assertThatCode(service::reconcileAvailabilityStatuses).doesNotThrowAnyException();
        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any());
    }
}
