package com.example.panel.service;

import com.example.panel.model.dialog.ChatMessageDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DialogWorkspaceHistorySliceServiceTest {

    private final DialogWorkspaceHistorySliceService service = new DialogWorkspaceHistorySliceService();

    @Test
    void sliceBuildsPagedWindowAndNextCursor() {
        List<ChatMessageDto> history = List.of(
                chatMessage("client", "m1", "2026-05-01T10:00:00Z", 1L),
                chatMessage("operator", "m2", "2026-05-01T10:01:00Z", 2L),
                chatMessage("client", "m3", "2026-05-01T10:02:00Z", 3L)
        );

        DialogWorkspaceHistorySliceService.HistorySlice slice = service.slice(history, 1, 1);

        assertThat(slice.safeCursor()).isEqualTo(1);
        assertThat(slice.pagedHistory()).hasSize(1);
        assertThat(slice.nextCursor()).isEqualTo(2);
        assertThat(slice.hasMore()).isTrue();
    }

    private ChatMessageDto chatMessage(String sender, String message, String timestamp, Long telegramMessageId) {
        return new ChatMessageDto(
                sender,
                message,
                null,
                timestamp,
                "text",
                null,
                null,
                null,
                telegramMessageId,
                null,
                null,
                null,
                null,
                null
        );
    }
}
