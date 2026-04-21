package com.example.panel.service;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.model.dialog.DialogPreviousHistoryPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DialogReadService {

    private static final Logger log = LoggerFactory.getLogger(DialogReadService.class);

    private final DialogService dialogService;
    private final DialogResponsibilityService dialogResponsibilityService;
    private final DialogConversationReadService dialogConversationReadService;
    private final PublicFormService publicFormService;

    public DialogReadService(DialogService dialogService,
                             DialogResponsibilityService dialogResponsibilityService,
                             DialogConversationReadService dialogConversationReadService,
                             PublicFormService publicFormService) {
        this.dialogService = dialogService;
        this.dialogResponsibilityService = dialogResponsibilityService;
        this.dialogConversationReadService = dialogConversationReadService;
        this.publicFormService = publicFormService;
    }

    public Map<String, Object> loadPublicFormMetrics(Long channelId) {
        return Map.of(
                "success", true,
                "metrics", publicFormService.loadMetricsSnapshot(channelId)
        );
    }

    public ResponseEntity<?> loadDetails(String ticketId, Long channelId, String operator) {
        dialogResponsibilityService.markDialogAsRead(ticketId, operator);
        Optional<DialogDetails> details = dialogService.loadDialogDetails(ticketId, channelId, operator);
        log.info("Dialog details requested for ticket {} (channelId={}): {}", ticketId, channelId,
                details.map(d -> "found").orElse("not found"));
        return details.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "error", "Диалог не найден")));
    }

    public Map<String, Object> loadHistory(String ticketId, Long channelId, String operator) {
        dialogResponsibilityService.markDialogAsRead(ticketId, operator);
        List<ChatMessageDto> history = dialogConversationReadService.loadHistory(ticketId, channelId);
        log.info("History requested for ticket {} (channelId={}): {} messages", ticketId, channelId, history.size());
        return Map.of(
                "success", true,
                "messages", history
        );
    }

    public ResponseEntity<?> loadPreviousHistory(String ticketId, Integer offset) {
        int resolvedOffset = offset != null ? Math.max(0, offset) : 0;
        Optional<DialogPreviousHistoryPage> page = dialogConversationReadService.loadPreviousDialogHistory(ticketId, resolvedOffset);
        if (page.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "batch", null,
                    "has_more", false,
                    "next_offset", resolvedOffset
            ));
        }
        DialogPreviousHistoryPage historyPage = page.get();
        return ResponseEntity.ok(mapWithNullableValues(
                "success", true,
                "batch", historyPage.batch(),
                "has_more", historyPage.hasMore(),
                "next_offset", historyPage.nextOffset()
        ));
    }

    private Map<String, Object> mapWithNullableValues(Object... keyValues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (keyValues == null) {
            return payload;
        }
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            Object rawKey = keyValues[index];
            if (rawKey == null) {
                continue;
            }
            payload.put(String.valueOf(rawKey), keyValues[index + 1]);
        }
        return payload;
    }
}
