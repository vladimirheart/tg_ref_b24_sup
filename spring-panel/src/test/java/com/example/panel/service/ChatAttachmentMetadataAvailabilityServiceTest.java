package com.example.panel.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.storage.AttachmentObjectStorageService;
import com.example.panel.storage.AttachmentService;
import java.sql.ResultSet;
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

    @Test
    void reconcileUpdatesEachAttachmentRowByIdAfterBackfillCheck() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        AttachmentObjectStorageService attachmentObjectStorageService = mock(AttachmentObjectStorageService.class);

        when(attachmentObjectStorageService.providerLabel()).thenReturn("s3");
        when(attachmentObjectStorageService.backfillDialogAttachmentByStorageKey("ticket-1/file-1.jpg")).thenReturn(true);
        when(attachmentObjectStorageService.backfillDialogAttachmentByStorageKey("ticket-1/file-2.jpg")).thenReturn(false);
        when(attachmentService.hasTicketAttachmentByStorageKey("ticket-1/file-1.jpg")).thenReturn(true);
        when(attachmentService.hasTicketAttachmentByStorageKey("ticket-1/file-2.jpg")).thenReturn(false);

        org.mockito.Mockito.doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<Object> rowMapper = invocation.getArgument(1, RowMapper.class);

            ResultSet first = mock(ResultSet.class);
            when(first.getLong("id")).thenReturn(11L);
            when(first.getLong("chat_history_id")).thenReturn(101L);
            when(first.getString("storage_provider")).thenReturn("local_fs");
            when(first.getString("storage_key")).thenReturn("ticket-1/file-1.jpg");
            when(first.getString("legacy_attachment_ref")).thenReturn("attachments/ticket-1/file-1.jpg");
            when(first.getString("normalization_status")).thenReturn("normalized");

            ResultSet second = mock(ResultSet.class);
            when(second.getLong("id")).thenReturn(12L);
            when(second.getLong("chat_history_id")).thenReturn(101L);
            when(second.getString("storage_provider")).thenReturn("local_fs");
            when(second.getString("storage_key")).thenReturn("ticket-1/file-2.jpg");
            when(second.getString("legacy_attachment_ref")).thenReturn("attachments/ticket-1/file-2.jpg");
            when(second.getString("normalization_status")).thenReturn("normalized");

            return List.of(
                    rowMapper.mapRow(first, 0),
                    rowMapper.mapRow(second, 1)
            );
        }).when(jdbcTemplate).query(anyString(), ArgumentMatchers.<RowMapper<Object>>any());

        ChatAttachmentMetadataAvailabilityService service =
                new ChatAttachmentMetadataAvailabilityService(jdbcTemplate, attachmentService, attachmentObjectStorageService);

        service.reconcileAvailabilityStatuses();

        verify(attachmentObjectStorageService).backfillDialogAttachmentByStorageKey("ticket-1/file-1.jpg");
        verify(attachmentObjectStorageService).backfillDialogAttachmentByStorageKey("ticket-1/file-2.jpg");
        verify(jdbcTemplate).update(anyString(), eq("s3"), eq("normalized"), eq("available"), eq(11L));
        verify(jdbcTemplate).update(anyString(), eq("s3"), eq("normalized"), eq("missing"), eq(12L));
    }
}
