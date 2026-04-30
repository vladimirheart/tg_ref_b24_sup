package com.example.panel.service;

import com.example.panel.entity.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialogReplyServiceTest {

    @Test
    void sendReplyDelegatesToTargetTransportAndResponsibilityServices() {
        DialogReplyTargetService targetService = mock(DialogReplyTargetService.class);
        DialogReplyTransportService transportService = mock(DialogReplyTransportService.class);
        DialogResponsibilityService responsibilityService = mock(DialogResponsibilityService.class);
        DialogReplyService dialogReplyService = new DialogReplyService(targetService, transportService, responsibilityService);
        Channel channel = new Channel();
        channel.setId(10L);
        channel.setPlatform("telegram");
        channel.setToken("token");

        when(targetService.loadReplyTarget("T-900")).thenReturn(Optional.of(new DialogReplyTarget(123L, 10L)));
        when(transportService.loadChannel(10L)).thenReturn(Optional.of(channel));
        when(targetService.hasWebFormSession("T-900")).thenReturn(false);
        when(transportService.sendText(channel, 123L, "Принято", 45L))
                .thenReturn(new DialogReplyTransportService.DialogReplyTransportResult(null, 77L));
        when(targetService.logOutgoingMessage(any(), eq("T-900"), eq("Принято"), eq("operator_message"), eq(77L), eq(45L), eq("operator")))
                .thenReturn("2026-04-30T12:00:00Z");

        DialogReplyService.DialogReplyResult result = dialogReplyService.sendReply("T-900", "Принято", 45L, "operator");

        assertThat(result.success()).isTrue();
        assertThat(result.timestamp()).isEqualTo("2026-04-30T12:00:00Z");
        assertThat(result.telegramMessageId()).isEqualTo(77L);
        verify(targetService).touchTicketActivity("T-900", 123L);
        verify(responsibilityService).assignResponsibleIfMissing("T-900", "operator");
    }

    @Test
    void sendMediaReplyUsesTransportAndPersistsAttachmentMetadata() {
        DialogReplyTargetService targetService = mock(DialogReplyTargetService.class);
        DialogReplyTransportService transportService = mock(DialogReplyTransportService.class);
        DialogResponsibilityService responsibilityService = mock(DialogResponsibilityService.class);
        DialogReplyService dialogReplyService = new DialogReplyService(targetService, transportService, responsibilityService);
        Channel channel = new Channel();
        channel.setId(15L);
        channel.setPlatform("telegram");
        channel.setToken("token");
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", "png".getBytes());

        when(targetService.loadReplyTarget("T-901")).thenReturn(Optional.of(new DialogReplyTarget(200L, 15L)));
        when(transportService.loadChannel(15L)).thenReturn(Optional.of(channel));
        when(targetService.hasWebFormSession("T-901")).thenReturn(false);
        when(transportService.sendMedia(channel, 200L, file, "caption", "image.png"))
                .thenReturn(new DialogReplyTransportService.DialogReplyTransportResult(null, 88L));
        when(targetService.logOutgoingMediaMessage(any(), eq("T-901"), eq("caption"), eq("stored.bin"), eq("image"), eq(88L)))
                .thenReturn("2026-04-30T12:05:00Z");

        DialogReplyService.DialogMediaReplyResult result =
                dialogReplyService.sendMediaReply("T-901", file, "caption", "operator", "stored.bin", "image.png");

        assertThat(result.success()).isTrue();
        assertThat(result.telegramMessageId()).isEqualTo(88L);
        assertThat(result.messageType()).isEqualTo("image");
        verify(targetService).touchTicketActivity("T-901", 200L);
        verify(responsibilityService).assignResponsibleIfMissing("T-901", "operator");
    }

    @Test
    void sendReplyUsesWebFormFallbackWithoutTransport() {
        DialogReplyTargetService targetService = mock(DialogReplyTargetService.class);
        DialogReplyTransportService transportService = mock(DialogReplyTransportService.class);
        DialogResponsibilityService responsibilityService = mock(DialogResponsibilityService.class);
        DialogReplyService dialogReplyService = new DialogReplyService(targetService, transportService, responsibilityService);
        Channel channel = new Channel();
        channel.setId(20L);

        when(targetService.loadReplyTarget("T-902")).thenReturn(Optional.of(new DialogReplyTarget(321L, 20L)));
        when(transportService.loadChannel(20L)).thenReturn(Optional.of(channel));
        when(targetService.hasWebFormSession("T-902")).thenReturn(true);
        when(targetService.logOutgoingMessage(any(), eq("T-902"), eq("Текст"), eq("operator_message"), eq(null), eq(null), eq("operator")))
                .thenReturn("2026-04-30T12:10:00Z");

        DialogReplyService.DialogReplyResult result = dialogReplyService.sendReply("T-902", "Текст", null, "operator");

        assertThat(result.success()).isTrue();
        assertThat(result.telegramMessageId()).isNull();
        verify(transportService, never()).sendText(any(), any(), any(), any());
    }
}
