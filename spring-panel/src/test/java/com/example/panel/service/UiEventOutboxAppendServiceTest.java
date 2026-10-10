package com.example.panel.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class UiEventOutboxAppendServiceTest {

    @Test
    void appendSkipsAmbiguousLegacyTicketIdBeforeOutboxInsert() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard = mock(LegacyTicketIdJdbcGuard.class);
        UiEventOutboxAppendService service = new UiEventOutboxAppendService(jdbcTemplate, legacyTicketIdJdbcGuard);

        service.append("client_message_edited", "T-DUPLICATE", 1L, "edited", null, null, null);

        verify(legacyTicketIdJdbcGuard).hasUniqueTicket("T-DUPLICATE");
        verifyNoInteractions(jdbcTemplate);
    }
}
