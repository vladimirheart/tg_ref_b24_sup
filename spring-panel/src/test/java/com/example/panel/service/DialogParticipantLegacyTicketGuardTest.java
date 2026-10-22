package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.example.panel.support.PostgresqlJdbcTestSupport;

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
    @Test
    void paddedUnknownIdDoesNotAliasPlainTicketForReadsOrMutations() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate usersJdbcTemplate = mock(JdbcTemplate.class);
        LegacyTicketIdJdbcGuard guard = mock(LegacyTicketIdJdbcGuard.class);
        when(guard.hasUniqueTicket("T-PLAIN")).thenReturn(true);
        DialogParticipantService service = new DialogParticipantService(jdbcTemplate, usersJdbcTemplate, guard);

        assertThat(service.ticketExists(" T-PLAIN ")).isFalse();
        assertThat(service.loadParticipants(" T-PLAIN ")).isEmpty();
        assertThat(service.addParticipant(" T-PLAIN ", "operator", "lead")).isFalse();
        assertThat(service.removeParticipant(" T-PLAIN ", "operator")).isFalse();
        verify(guard, org.mockito.Mockito.times(3)).hasUniqueTicket(" T-PLAIN ");
        verify(guard).hasUniqueNonGeneralTicket(" T-PLAIN ");
        verifyNoInteractions(jdbcTemplate, usersJdbcTemplate);
    }

    @Test
    void participantMutationsPreserveExactIdAndLeavePlainNeighbourUntouched() throws Exception {
        JdbcTemplate jdbcTemplate = PostgresqlJdbcTestSupport.freshJdbcTemplate("participant_exact_id");
        jdbcTemplate.execute("CREATE TABLE tickets(user_id BIGINT, ticket_id TEXT, PRIMARY KEY(user_id, ticket_id))");
        jdbcTemplate.execute("""
            CREATE TABLE ticket_participants(ticket_id TEXT, username TEXT, added_at TIMESTAMPTZ,
                added_by TEXT, PRIMARY KEY(ticket_id, username))
            """);
        jdbcTemplate.update("INSERT INTO tickets VALUES (1, ' T-EXACT '), (2, 'T-EXACT')");
        jdbcTemplate.execute("ALTER TABLE tickets ADD COLUMN business_id BIGINT, ADD COLUMN is_general BOOLEAN DEFAULT FALSE");
        jdbcTemplate.update("""
            INSERT INTO ticket_participants(ticket_id, username) VALUES ('T-EXACT', 'operator')
            """);
        DialogParticipantService service = new DialogParticipantService(
            jdbcTemplate, mock(JdbcTemplate.class), new LegacyTicketIdJdbcGuard(jdbcTemplate));

        assertThat(service.ticketExists(" T-EXACT ")).isTrue();
        assertThat(service.addParticipant(" T-EXACT ", "operator", "lead")).isTrue();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT added_by FROM ticket_participants WHERE ticket_id = ' T-EXACT '", String.class)).isEqualTo("lead");
        assertThat(service.removeParticipant(" T-EXACT ", "operator")).isTrue();
        assertThat(jdbcTemplate.queryForList("SELECT ticket_id FROM ticket_participants", String.class))
            .containsExactly("T-EXACT");
    }
}
