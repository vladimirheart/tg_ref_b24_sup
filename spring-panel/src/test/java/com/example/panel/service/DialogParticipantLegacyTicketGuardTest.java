package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DialogParticipantLegacyTicketGuardTest {

    @Test
    void rejectsReadsAndMutationsForAmbiguousLegacyTicketId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate usersJdbcTemplate = mock(JdbcTemplate.class);
        LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard = mock(LegacyTicketIdJdbcGuard.class);
        DialogParticipantService service = new DialogParticipantService(
                jdbcTemplate,
                usersJdbcTemplate,
                legacyTicketIdJdbcGuard
        );

        assertThat(service.ticketExists("T-AMB")).isFalse();
        assertThat(service.loadParticipants("T-AMB")).isEmpty();
        assertThat(service.addParticipant("T-AMB", "operator", "lead")).isFalse();
        assertThat(service.removeParticipant("T-AMB", "operator")).isFalse();

        verifyNoInteractions(jdbcTemplate, usersJdbcTemplate);
    }
}
