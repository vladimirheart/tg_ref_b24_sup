package com.example.panel.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogReplyTargetServiceTest {

    @Test
    void logOutgoingMessageBindsOffsetDateTimeForPostgresqlTimestamp() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard = mock(LegacyTicketIdJdbcGuard.class);
        when(legacyTicketIdJdbcGuard.hasUniqueTicket("T-REPLY")).thenReturn(true);
        DialogReplyTargetService service = new DialogReplyTargetService(
                jdbcTemplate,
                mock(ChatAttachmentMetadataService.class),
                legacyTicketIdJdbcGuard
        );

        String returnedTimestamp = service.logOutgoingMessage(
                new DialogReplyTarget(42L, 1L),
                "T-REPLY",
                "edited operator reply",
                "text",
                1001L,
                null,
                "operator"
        );

        assertThat(jdbcTemplate.lastArgs).hasSize(9);
        assertThat(jdbcTemplate.lastArgs[3]).isInstanceOf(OffsetDateTime.class);
        assertThat(returnedTimestamp).isEqualTo(jdbcTemplate.lastArgs[3].toString());
    }

    @Test
    void touchTicketActivityBindsOffsetDateTimeForPostgresqlTimestamp() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard = mock(LegacyTicketIdJdbcGuard.class);
        when(legacyTicketIdJdbcGuard.hasUniqueTicket("T-ACTIVITY")).thenReturn(true);
        DialogReplyTargetService service = new DialogReplyTargetService(
                jdbcTemplate,
                mock(ChatAttachmentMetadataService.class),
                legacyTicketIdJdbcGuard
        );

        service.touchTicketActivity("T-ACTIVITY", "admin");

        assertThat(jdbcTemplate.lastArgs).hasSize(3);
        assertThat(jdbcTemplate.lastArgs[0]).isInstanceOf(OffsetDateTime.class);
        assertThat(jdbcTemplate.lastArgs[1]).isEqualTo("admin");
        assertThat(jdbcTemplate.lastArgs[2]).isEqualTo("T-ACTIVITY");
    }

    @Test
    void doesNotReadOrWriteForAmbiguousLegacyTicketId() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard = mock(LegacyTicketIdJdbcGuard.class);
        DialogReplyTargetService service = new DialogReplyTargetService(
                jdbcTemplate,
                mock(ChatAttachmentMetadataService.class),
                legacyTicketIdJdbcGuard
        );

        assertThat(service.loadReplyTarget("T-AMB")).isEmpty();
        assertThat(service.hasWebFormSession("T-AMB")).isFalse();
        assertThat(service.markOperatorMessageEdited("T-AMB", 101L, "edited")).isZero();
        assertThat(service.markOperatorMessageDeleted("T-AMB", 101L)).isZero();
        service.touchTicketActivity("T-AMB", "operator");

        assertThat(jdbcTemplate.lastArgs).isNull();
    }

    private static final class CapturingJdbcTemplate extends JdbcTemplate {
        private Object[] lastArgs;

        @Override
        public int update(String sql, Object... args) {
            this.lastArgs = args;
            return 1;
        }
    }
}
