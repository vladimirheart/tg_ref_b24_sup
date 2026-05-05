package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingRuleCandidateContextServiceTest {

    private final SlaRoutingRuleCandidateContextService service = new SlaRoutingRuleCandidateContextService();

    @Test
    void buildNormalizesCandidateFields() {
        SlaRoutingRuleCandidateContextService.CandidateContext context = service.build(Map.of(
                "ticket_id", "T-1",
                "channel", "Telegram",
                "business", "Retail",
                "categories", List.of("Billing", "VIP"),
                "client_status", "Gold",
                "unread_count", "3",
                "rating", 5,
                "minutes_left", "15",
                "sla_state", "warning",
                "request_number", "Inc-77"
        ));

        assertEquals("telegram", context.channel());
        assertEquals("retail", context.business());
        assertTrue(context.categories().contains("billing"));
        assertEquals("gold", context.clientStatus());
        assertEquals(3, context.unreadCount());
        assertEquals(5, context.rating());
        assertEquals(15L, context.minutesLeft());
        assertEquals("at_risk", context.slaState());
        assertEquals("Inc-77", context.requestNumber());
        assertEquals("T-1", context.ticketId());
    }
}
