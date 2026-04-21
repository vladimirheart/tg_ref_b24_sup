package com.example.panel.service;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.model.dialog.DialogListItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialogDetailsReadServiceTest {

    @Test
    void composesDialogDetailsFromLookupAndConversationSlices() {
        DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);
        DialogConversationReadService dialogConversationReadService = mock(DialogConversationReadService.class);
        DialogDetailsReadService service = new DialogDetailsReadService(dialogLookupReadService, dialogConversationReadService);

        DialogListItem summary = new DialogListItem(
                "T-500",
                500L,
                42L,
                "client42",
                "Клиент",
                "Retail",
                7L,
                "Telegram",
                "Москва",
                "Офис",
                "Проблема",
                "2026-04-21T12:00:00Z",
                "open",
                false,
                null,
                "operator",
                "21.04.2026",
                "12:00:00",
                null,
                "client",
                "2026-04-21T12:01:00Z",
                0,
                null,
                null
        );
        List<ChatMessageDto> history = List.of(new ChatMessageDto(
                "client",
                "Сообщение клиента",
                null,
                "2026-04-21T12:01:00Z",
                "text",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        when(dialogLookupReadService.findDialog("T-500", "operator")).thenReturn(Optional.of(summary));
        when(dialogConversationReadService.loadHistory("T-500", 7L)).thenReturn(history);
        when(dialogConversationReadService.loadTicketCategories("T-500")).thenReturn(List.of("billing", "support"));

        Optional<DialogDetails> details = service.loadDialogDetails("T-500", 7L, "operator");

        assertThat(details).isPresent();
        assertThat(details.orElseThrow().summary().ticketId()).isEqualTo("T-500");
        assertThat(details.orElseThrow().history()).hasSize(1);
        assertThat(details.orElseThrow().categories()).containsExactly("billing", "support");
        verify(dialogLookupReadService).findDialog("T-500", "operator");
        verify(dialogConversationReadService).loadHistory("T-500", 7L);
        verify(dialogConversationReadService).loadTicketCategories("T-500");
    }
}
