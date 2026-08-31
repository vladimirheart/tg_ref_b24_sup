package com.example.panel.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.storage.AttachmentObjectStorageService;
import java.io.InputStream;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

class ChatAttachmentMetadataAvailabilityServiceTest {

    @Test
    void reconcileDoesNotAttemptSchemaMutationDuringAvailabilityRefresh() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AttachmentObjectStorageService attachmentObjectStorageService = mock(AttachmentObjectStorageService.class);
        ChatAttachmentMetadataService chatAttachmentMetadataService = mock(ChatAttachmentMetadataService.class);
        doReturn(List.of()).when(jdbcTemplate)
                .query(anyString(), ArgumentMatchers.<RowMapper<Object>>any());

        ChatAttachmentMetadataAvailabilityService service =
                new ChatAttachmentMetadataAvailabilityService(
                        jdbcTemplate,
                        attachmentObjectStorageService,
                        chatAttachmentMetadataService
                );

        service.reconcileAvailabilityStatuses();

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void reconcileUpdatesEachAttachmentRowAfterConfirmedS3Probe() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AttachmentObjectStorageService attachmentObjectStorageService = mock(AttachmentObjectStorageService.class);
        ChatAttachmentMetadataService chatAttachmentMetadataService = mock(ChatAttachmentMetadataService.class);

        when(attachmentObjectStorageService.providerLabel()).thenReturn("s3");
        when(attachmentObjectStorageService.backfillDialogAttachmentByStorageKey("ticket-1/file-1.jpg")).thenReturn(true);
        when(attachmentObjectStorageService.backfillDialogAttachmentByStorageKey("ticket-1/file-2.jpg")).thenReturn(false);
        when(attachmentObjectStorageService.openDialogAttachmentByStorageKey("ticket-1/file-1.jpg"))
                .thenReturn(new AttachmentObjectStorageService.StoredBinary(
                        "ticket-1/file-1.jpg",
                        "image/jpeg",
                        10L,
                        InputStream.nullInputStream()
                ));
        when(attachmentObjectStorageService.openDialogAttachmentByStorageKey("ticket-1/file-2.jpg"))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());

        org.mockito.Mockito.doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            @SuppressWarnings("unchecked")
            RowMapper<Object> rowMapper = invocation.getArgument(1, RowMapper.class);
            if (sql.contains("FROM chat_history ch")) {
                return List.of();
            }

            ResultSet first = mock(ResultSet.class);
            when(first.getLong("id")).thenReturn(11L);
            when(first.getLong("chat_history_id")).thenReturn(101L);
            when(first.getString("storage_provider")).thenReturn("local_fs");
            when(first.getString("storage_key")).thenReturn("ticket-1/file-1.jpg");
            when(first.getString("legacy_attachment_ref")).thenReturn("attachments/ticket-1/file-1.jpg");
            when(first.getString("normalization_status")).thenReturn("normalized");

            ResultSet second = mock(ResultSet.class);
            when(second.getLong("id")).thenReturn(12L);
            when(second.getLong("chat_history_id")).thenReturn(102L);
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
                new ChatAttachmentMetadataAvailabilityService(
                        jdbcTemplate,
                        attachmentObjectStorageService,
                        chatAttachmentMetadataService
                );

        service.reconcileAvailabilityStatuses();

        verify(attachmentObjectStorageService).backfillDialogAttachmentByStorageKey("ticket-1/file-1.jpg");
        verify(attachmentObjectStorageService).backfillDialogAttachmentByStorageKey("ticket-1/file-2.jpg");
        verify(attachmentObjectStorageService).openDialogAttachmentByStorageKey("ticket-1/file-1.jpg");
        verify(attachmentObjectStorageService).openDialogAttachmentByStorageKey("ticket-1/file-2.jpg");
        verify(jdbcTemplate).update(anyString(), eq("s3"), eq("normalized"), eq("available"), eq(11L));
        verify(jdbcTemplate).update(anyString(), eq("s3"), eq("normalized"), eq("missing"), eq(12L));
    }

    @Test
    void reconcileKeepsExistingStatusWhenS3ProbeFailsTransiently() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AttachmentObjectStorageService attachmentObjectStorageService = mock(AttachmentObjectStorageService.class);
        ChatAttachmentMetadataService chatAttachmentMetadataService = mock(ChatAttachmentMetadataService.class);

        when(attachmentObjectStorageService.providerLabel()).thenReturn("s3");
        when(attachmentObjectStorageService.backfillDialogAttachmentByStorageKey("ticket-1/file-1.jpg")).thenReturn(false);
        when(attachmentObjectStorageService.openDialogAttachmentByStorageKey("ticket-1/file-1.jpg"))
                .thenThrow(S3Exception.builder().statusCode(503).message("temporary S3 outage").build());

        org.mockito.Mockito.doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            @SuppressWarnings("unchecked")
            RowMapper<Object> rowMapper = invocation.getArgument(1, RowMapper.class);
            if (sql.contains("FROM chat_history ch")) {
                return List.of();
            }

            ResultSet row = mock(ResultSet.class);
            when(row.getLong("id")).thenReturn(11L);
            when(row.getLong("chat_history_id")).thenReturn(101L);
            when(row.getString("storage_provider")).thenReturn("s3");
            when(row.getString("storage_key")).thenReturn("ticket-1/file-1.jpg");
            when(row.getString("legacy_attachment_ref")).thenReturn("attachments/ticket-1/file-1.jpg");
            when(row.getString("normalization_status")).thenReturn("normalized");

            return List.of(rowMapper.mapRow(row, 0));
        }).when(jdbcTemplate).query(anyString(), ArgumentMatchers.<RowMapper<Object>>any());

        ChatAttachmentMetadataAvailabilityService service =
                new ChatAttachmentMetadataAvailabilityService(
                        jdbcTemplate,
                        attachmentObjectStorageService,
                        chatAttachmentMetadataService
                );

        service.reconcileAvailabilityStatuses();

        verify(attachmentObjectStorageService).openDialogAttachmentByStorageKey("ticket-1/file-1.jpg");
        verify(jdbcTemplate, never()).update(anyString(), eq("s3"), eq("normalized"), eq("available"), eq(11L));
        verify(jdbcTemplate, never()).update(anyString(), eq("s3"), eq("normalized"), eq("missing"), eq(11L));
    }

    @Test
    void reconcileBackfillsMissingMetadataRowsBeforeAvailabilityRefresh() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AttachmentObjectStorageService attachmentObjectStorageService = mock(AttachmentObjectStorageService.class);
        ChatAttachmentMetadataService chatAttachmentMetadataService = mock(ChatAttachmentMetadataService.class);

        org.mockito.Mockito.doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            @SuppressWarnings("unchecked")
            RowMapper<Object> rowMapper = invocation.getArgument(1, RowMapper.class);

            if (sql.contains("FROM chat_history ch")) {
                ResultSet row = mock(ResultSet.class);
                when(row.getLong("id")).thenReturn(501L);
                when(row.getString("ticket_id")).thenReturn("ticket-501");
                when(row.getObject("channel_id")).thenReturn(77L);
                when(row.getLong("channel_id")).thenReturn(77L);
                when(row.getString("attachment")).thenReturn("C:\\legacy\\attachments\\ticket-501\\photo.jpg");
                when(row.getString("file_name")).thenReturn("photo.jpg");
                when(row.getString("message_type")).thenReturn("photo");
                return List.of(rowMapper.mapRow(row, 0));
            }
            return List.of();
        }).when(jdbcTemplate).query(anyString(), ArgumentMatchers.<RowMapper<Object>>any());

        ChatAttachmentMetadataAvailabilityService service =
                new ChatAttachmentMetadataAvailabilityService(
                        jdbcTemplate,
                        attachmentObjectStorageService,
                        chatAttachmentMetadataService
                );

        service.reconcileAvailabilityStatuses();

        verify(chatAttachmentMetadataService).upsertForChatHistory(
                501L,
                "ticket-501",
                77L,
                "C:\\legacy\\attachments\\ticket-501\\photo.jpg",
                "photo.jpg",
                null,
                null,
                "photo"
        );
    }
}
