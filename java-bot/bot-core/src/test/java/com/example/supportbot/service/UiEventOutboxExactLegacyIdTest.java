package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiEventOutboxExactLegacyIdTest {

    @Test
    void ticketCreatedKeepsPaddedAndPlainLegacyIdsSeparate() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        UiEventOutboxService outbox = new UiEventOutboxService(jdbc);
        Channel channel = new Channel();
        channel.setId(41L);

        outbox.publishTicketCreated(" T-EXACT ", channel, " preview ");
        outbox.publishTicketCreated("T-EXACT", channel, "preview");

        assertEquals(2, jdbc.rows.size());
        assertEquals(" T-EXACT ", jdbc.rows.get(0)[2]);
        assertEquals("T-EXACT", jdbc.rows.get(1)[2]);
        assertEquals("ticket_created", jdbc.rows.get(0)[1]);
        assertEquals(41L, jdbc.rows.get(0)[3]);
        assertEquals("preview", jdbc.rows.get(0)[4]);
    }

    @Test
    void allSevenEventTypesPreserveTheOriginalTicketId() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        UiEventOutboxService outbox = new UiEventOutboxService(jdbc);
        Channel channel = new Channel();
        channel.setId(41L);
        String exactId = " T-EXACT ";

        outbox.publishTicketCreated(exactId, channel, "created");
        outbox.publishClientMessage(exactId, channel, " message ", " text ", " /path ");
        outbox.publishClientMessageEdited(exactId, 41L, " edited ");
        outbox.publishFeedbackCreated(exactId, channel, 5);
        outbox.publishTicketClosed(exactId, channel, " closed ", false);
        outbox.publishTicketClosed(exactId, channel, " auto ", true);
        outbox.publishTicketReopened(exactId, channel, " reopened ");

        assertEquals(7, jdbc.rows.size());
        assertEquals(List.of(
            "ticket_created", "client_message_created", "client_message_edited",
            "feedback_created", "ticket_closed", "ticket_closed_auto", "ticket_reopened"
        ), jdbc.rows.stream().map(row -> (String) row[1]).toList());
        for (Object[] row : jdbc.rows) {
            assertEquals(9, row.length);
            assertEquals(exactId, row[2]);
            assertEquals(41L, row[3]);
            assertTrue(row[0] instanceof Long);
            assertTrue(row[8] instanceof String);
        }
        assertEquals("message", jdbc.rows.get(1)[4]);
        assertEquals("text", jdbc.rows.get(1)[5]);
        assertEquals("/path", jdbc.rows.get(1)[6]);
        assertEquals("edited", jdbc.rows.get(2)[4]);
        assertEquals(5, jdbc.rows.get(3)[7]);
    }

    @Test
    void nullEmptyAndWhitespaceOnlyIdsNeverReachJdbc() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        UiEventOutboxService outbox = new UiEventOutboxService(jdbc);
        for (String invalid : new String[]{null, "", "   ", "\t\n"}) {
            outbox.publishTicketCreated(invalid, null, "preview");
            outbox.publishClientMessage(invalid, null, "message", "text", null);
            outbox.publishClientMessageEdited(invalid, 41L, "edited");
            outbox.publishFeedbackCreated(invalid, null, 5);
            outbox.publishTicketClosed(invalid, null, "closed", false);
            outbox.publishTicketReopened(invalid, null, "reopened");
        }
        assertEquals(0, jdbc.attempts);
        assertTrue(jdbc.rows.isEmpty());
    }

    @Test
    void failedOutboxWriteDoesNotBreakTheMainFlow() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        jdbc.failWrites = true;
        UiEventOutboxService outbox = new UiEventOutboxService(jdbc);

        assertDoesNotThrow(() -> outbox.publishTicketCreated(" T-EXACT ", null, "preview"));
        assertEquals(1, jdbc.attempts);
        assertTrue(jdbc.rows.isEmpty());
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<Object[]> rows = new ArrayList<>();
        private int attempts;
        private boolean failWrites;

        @Override
        public int update(String sql, Object... args) {
            attempts++;
            assertTrue(sql.contains("INSERT INTO ui_event_outbox("));
            if (failWrites) {
                throw new IllegalStateException("Synthetic outbox write failure");
            }
            rows.add(args.clone());
            return 1;
        }
    }
}
