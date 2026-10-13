package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.storage.AttachmentService;
import com.example.panel.storage.AttachmentStorageKeyResolver;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

class DialogQuickMediaAttachmentUrlTest {
    static Stream<Arguments> locators() {
        return Stream.of("T-PLAIN", " T ", "T/100", " T/100?&%#+ Ю ", "T%2F+", " null ", "T\\opaque")
                .flatMap(owner -> Stream.of("stored.png", "photo+%2F Ю.png", "folder/photo.png",
                        "photo?#& name.png", "folder\\photo.png").map(filename -> Arguments.of(owner, filename)));
    }

    @ParameterizedTest
    @MethodSource("locators")
    void successfulQuickMediaReplyKeepsExactLocatorAndExistingSideEffects(String owner, String filename) throws Exception {
        var replies = mock(DialogReplyService.class);
        var ai = mock(DialogAiAssistantService.class);
        var notifications = mock(NotificationService.class);
        var attachments = mock(AttachmentService.class);
        var authentication = mock(Authentication.class);
        var receipt = mock(AttachmentService.TicketAttachmentUpload.class);
        var metadata = new AttachmentService.AttachmentUploadMetadata("photo.png", filename, "image/png", 3L,
                OffsetDateTime.parse("2026-10-13T13:00:00Z"));
        when(receipt.metadata()).thenReturn(metadata);
        when(attachments.prepareTicketAttachment(eq(authentication), eq(owner), any(MultipartFile.class)))
                .thenReturn(receipt);
        when(replies.sendMediaReply(eq(owner), any(MultipartFile.class), eq(" caption "), eq(123L),
                eq("operator"), eq(filename), eq("photo.png"), eq("image/png"), eq(3L)))
                .thenAnswer(call -> {
                    assertThat(call.getArgument(1, MultipartFile.class).getBytes()).containsExactly(9, 8, 7);
                    return new DialogReplyService.DialogMediaReplyResult(true, null, "2026-10-13T13:00:01Z",
                            456L, filename, "image", " caption ", "operator");
                });
        when(notifications.buildDialogUrl(owner)).thenReturn("/dialogs?ticketId=controlled");
        var quick = new DialogQuickActionService(mock(DialogTicketLifecycleService.class),
                mock(DialogLookupReadService.class), mock(DialogResponsibilityService.class),
                mock(DialogParticipantService.class), replies, mock(DialogNotificationService.class),
                ai, notifications, attachments);

        var response = quick.sendMediaReply(owner,
                new MockMultipartFile("file", "photo.png", "image/png", new byte[] {9, 8, 7}),
                " caption ", 123L, "operator", authentication);

        assertThat(response.keySet()).isEqualTo(Set.of("success", "timestamp", "telegramMessageId", "responsible",
                "attachment", "messageType", "message"));
        assertThat(response).containsEntry("success", true).containsEntry("timestamp", "2026-10-13T13:00:01Z")
                .containsEntry("telegramMessageId", 456L).containsEntry("responsible", "operator")
                .containsEntry("messageType", "image").containsEntry("message", " caption ");
        String url = (String) response.get("attachment");
        assertThat(url).isEqualTo(expectedUrl(owner, filename));
        assertThat(URI.create(url).getRawFragment()).isNull();
        var reference = AttachmentStorageKeyResolver.parseLegacyDialogReference(url);
        assertThat(reference).isNotNull();
        assertThat(reference.ticketId()).isEqualTo(owner);
        assertThat(reference.reference()).isEqualTo(filename);
        verify(receipt).retain();
        verify(receipt, never()).rollback();
        verify(ai).clearProcessing(owner, "operator_reply_media", null);
        verify(ai).registerOperatorReply(owner, " caption ", "operator");
        verify(replies).sendMediaReply(eq(owner), any(MultipartFile.class), eq(" caption "), eq(123L),
                eq("operator"), eq(filename), eq("photo.png"), eq("image/png"), eq(3L));
        verify(notifications).notifyDialogParticipants(owner, "Новое медиа-сообщение в обращении " + owner,
                "/dialogs?ticketId=controlled", "operator");
    }

    private static String expectedUrl(String owner, String filename) {
        if (!owner.matches("[A-Za-z0-9._-]+") || filename.contains("/") || filename.contains("\\")
                || filename.contains("%")) {
            return "/api/attachments/tickets/by-ticket?ticketId="
                    + UriUtils.encodeQueryParam(owner, StandardCharsets.UTF_8).replace("+", "%2B")
                    + "&filename=" + UriUtils.encodeQueryParam(filename, StandardCharsets.UTF_8).replace("+", "%2B");
        }
        return "/api/attachments/tickets/" + UriUtils.encodePathSegment(owner, StandardCharsets.UTF_8)
                + "/" + UriUtils.encodePathSegment(filename, StandardCharsets.UTF_8);
    }
}
