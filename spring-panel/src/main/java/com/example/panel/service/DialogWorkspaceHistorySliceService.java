package com.example.panel.service;

import com.example.panel.model.dialog.ChatMessageDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DialogWorkspaceHistorySliceService {

    public HistorySlice slice(List<ChatMessageDto> history, int resolvedCursor, int resolvedLimit) {
        List<ChatMessageDto> safeHistory = history == null ? List.of() : history;
        int safeCursor = Math.min(Math.max(resolvedCursor, 0), safeHistory.size());
        int endExclusive = Math.min(safeCursor + Math.max(resolvedLimit, 0), safeHistory.size());
        List<ChatMessageDto> pagedHistory = safeHistory.subList(safeCursor, endExclusive);
        boolean hasMore = endExclusive < safeHistory.size();
        Integer nextCursor = hasMore ? endExclusive : null;
        return new HistorySlice(safeCursor, pagedHistory, nextCursor, hasMore);
    }

    public record HistorySlice(int safeCursor,
                               List<ChatMessageDto> pagedHistory,
                               Integer nextCursor,
                               boolean hasMore) {
    }
}
