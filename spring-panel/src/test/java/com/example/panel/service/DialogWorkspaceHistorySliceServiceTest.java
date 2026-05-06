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
                new ChatMessageDto("client", "m1", null, "2026-05-01T10:00:00Z", "text", null, 1L, null, null, null, null, null),
                new ChatMessageDto("operator", "m2", null, "2026-05-01T10:01:00Z", "text", null, 2L, null, null, null, null, null),
                new ChatMessageDto("client", "m3", null, "2026-05-01T10:02:00Z", "text", null, 3L, null, null, null, null, null)
        );

        DialogWorkspaceHistorySliceService.HistorySlice slice = service.slice(history, 1, 1);

        assertThat(slice.safeCursor()).isEqualTo(1);
        assertThat(slice.pagedHistory()).hasSize(1);
        assertThat(slice.nextCursor()).isEqualTo(2);
        assertThat(slice.hasMore()).isTrue();
    }
}
