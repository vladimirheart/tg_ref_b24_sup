package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlaEscalationWebhookNotifierTest {

    @Test
    void findsOnlyCriticalOpenUnassignedDialogs() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());
        Instant now = Instant.now();

        DialogListItem critical = dialog("T-1", now.minusSeconds(23 * 60 * 60).toString(), "open", null);
        DialogListItem assigned = dialog("T-2", now.minusSeconds(23 * 60 * 60).toString(), "open", "operator");
        DialogListItem closed = dialog("T-3", now.minusSeconds(26 * 60 * 60).toString(), "resolved", null);
        DialogListItem fresh = dialog("T-4", now.minusSeconds(2 * 60 * 60).toString(), "open", null);

        List<Map<String, Object>> candidates = notifier.findEscalationCandidates(
                List.of(critical, assigned, closed, fresh),
                24 * 60,
                60
        );

        assertEquals(1, candidates.size());
        assertEquals("T-1", candidates.get(0).get("ticket_id"));
    }

    private DialogListItem dialog(String ticketId, String createdAt, String status, String responsible) {
        return new DialogListItem(
                ticketId,
                100L,
                1L,
                "client",
                "Client",
                "biz",
                10L,
                "Telegram",
                "Moscow",
                "HQ",
                "Issue",
                createdAt,
                status,
                null,
                null,
                responsible,
                null,
                null,
                null,
                "user",
                createdAt,
                0,
                null,
                null
        );
    }
}
