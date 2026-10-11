package com.example.panel.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.ArgumentCaptor;

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
    @Test
    void appendPersistsTheSameExactIdThatPassedTheGuard() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        LegacyTicketIdJdbcGuard guard = mock(LegacyTicketIdJdbcGuard.class);
        when(guard.hasUniqueTicket(" T-EXACT ")).thenReturn(true);
        UiEventOutboxAppendService service = new UiEventOutboxAppendService(jdbcTemplate, guard);

        service.append("client_message_edited", " T-EXACT ", 1L, "edited", null, null, null);

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), args.capture());
        assertThat(args.getValue()[2]).isEqualTo(" T-EXACT ");
        verify(guard).hasUniqueTicket(" T-EXACT ");
    }
}
