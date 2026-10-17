package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class UiEventOutboxAppendSnapshotPostgresqlTest {

    private static final List<String> RAW_IDS = List.of(
        " T-EXACT ", "T/slash", "T?#[]&=+", "Обращение-ёж", "T%2F%25", "null");
    private static final List<String> PUBLISHERS = List.of(
        "append", "reopened", "client-edit", "operator-edit", "feedback", "closed", "auto-closed");

    static Stream<Arguments> uniqueCases() {
        return RAW_IDS.stream().flatMap(id -> PUBLISHERS.stream().map(publisher -> Arguments.of(id, publisher)));
    }

    static Stream<Arguments> directLateCases() {
        return RAW_IDS.stream().flatMap(id -> Stream.of("missing", "duplicate")
            .map(mutation -> Arguments.of(id, "append", mutation)));
    }

    static Stream<Arguments> wrapperLateCases() {
        return PUBLISHERS.stream().flatMap(publisher -> Stream.of("missing", "duplicate")
            .map(mutation -> Arguments.of(" Поздний/ID%? ", publisher, mutation)));
    }

    static Stream<Arguments> initialDeniedCases() {
        return RAW_IDS.stream().flatMap(id -> Stream.of("missing", "duplicate")
            .map(condition -> Arguments.of(id, condition)));
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
            Arguments.of(null, "T-VALID"), Arguments.of("", "T-VALID"),
            Arguments.of(" ", "T-VALID"), Arguments.of("\t\n", "T-VALID"),
            Arguments.of("custom", null), Arguments.of("custom", ""),
            Arguments.of("custom", " "), Arguments.of("custom", "\t\n"));
    }

    @ParameterizedTest
    @MethodSource("uniqueCases")
    void allPublishersPreserveExactIdAndPayload(String rawId, String publisher) {
        Fixture f = new Fixture();
        f.parent(1L, rawId, 10L);
        if (!rawId.equals(rawId.trim())) {
            f.parent(2L, rawId.trim(), 20L);
        }
        f.parent(3L, "T-UNRELATED", null);
        List<Map<String, Object>> parents = f.parents();
        OffsetDateTime before = OffsetDateTime.now().minusSeconds(1);

        f.publish(publisher, rawId);

        assertThat(f.parents()).isEqualTo(parents);
        assertThat(f.attempts).isEqualTo(1);
        assertThat(f.affected).isEqualTo(1);
        Map<String, Object> row = f.onlyEvent();
        assertThat(row.get("ticket_id")).isEqualTo(rawId);
        assertThat(row.get("channel_id")).isEqualTo(99L);
        assertThat(row.get("event_type")).isEqualTo(eventType(publisher));
        assertThat(row.get("message_text")).isEqualTo("feedback".equals(publisher) ? null : "payload");
        assertThat(row.get("message_type")).isEqualTo("append".equals(publisher) ? "photo" : null);
        assertThat(row.get("attachment")).isEqualTo("append".equals(publisher) ? "storage/key" : null);
        Integer expectedRating = switch (publisher) {
            case "feedback" -> Integer.valueOf(5);
            case "append" -> Integer.valueOf(4);
            default -> null;
        };
        assertThat(row.get("rating")).isEqualTo(expectedRating);
        assertThat((Long) row.get("id")).isPositive();
        assertThat(f.args[0]).isInstanceOf(Long.class).isEqualTo(row.get("id"));
        assertThat(f.args[1]).isEqualTo(eventType(publisher));
        assertThat(f.args[2]).isEqualTo(rawId);
        assertThat(f.args[3]).isInstanceOf(Long.class).isEqualTo(99L);
        assertThat(f.args[8]).isInstanceOf(OffsetDateTime.class);
        OffsetDateTime created = (OffsetDateTime) f.args[8];
        assertThat(created).isBetween(before, OffsetDateTime.now().plusSeconds(1));
        assertThat(((java.sql.Timestamp) row.get("created_at")).toInstant())
            .isBetween(created.toInstant().minusMillis(1), created.toInstant().plusMillis(1));
        // The first nine legacy bindings are unchanged; the guarded SELECT adds one raw ID.
        if (f.args.length != 9) {
            assertThat(f.args).hasSize(10);
            assertThat(f.args[9]).isEqualTo(rawId);
        }
    }

    @ParameterizedTest
    @MethodSource("directLateCases")
    void lateParentInvalidationUsesInsertSnapshotForExactRawId(String rawId, String publisher, String mutation) {
        assertLateDenied(rawId, publisher, mutation);
    }

    @ParameterizedTest
    @MethodSource("wrapperLateCases")
    void lateParentInvalidationIsDeniedForEveryPublicPublisher(String rawId, String publisher, String mutation) {
        assertLateDenied(rawId, publisher, mutation);
    }

    private void assertLateDenied(String rawId, String publisher, String mutation) {
        Fixture f = new Fixture();
        f.parent(1L, rawId, 10L);
        f.parent(3L, rawId.trim().equals(rawId) ? "T-SIBLING" : rawId.trim(), 20L);
        f.seedEvent();
        List<Map<String, Object>> events = f.events();
        f.beforeInsert = () -> f.invalidate(rawId, mutation);

        f.publish(publisher, rawId);

        assertThat(f.attempts).isEqualTo(1);
        assertThat(f.events()).isEqualTo(events);
        assertThat(f.affected).isZero();
        assertThat(f.parents()).isEqualTo(f.parentsAtInsert);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE ticket_id = ?", Integer.class, rawId))
            .isEqualTo("missing".equals(mutation) ? 0 : 2);
    }

    @ParameterizedTest
    @MethodSource("initialDeniedCases")
    void initialMissingOrDuplicateParentNeverAttemptsInsert(String rawId, String condition) {
        Fixture f = new Fixture();
        if ("duplicate".equals(condition)) {
            f.parent(1L, rawId, 10L);
            f.parent(2L, rawId, null);
        }
        if (!rawId.equals(rawId.trim())) {
            f.parent(3L, rawId.trim(), 20L);
        }
        f.seedEvent();
        List<Map<String, Object>> parents = f.parents();
        List<Map<String, Object>> events = f.events();

        f.publish("append", rawId);

        assertThat(f.attempts).isZero();
        assertThat(f.parents()).isEqualTo(parents);
        assertThat(f.events()).isEqualTo(events);
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void blankEventOrTicketDoesNotQueryParentsOrInsert(String eventType, String ticketId) {
        Fixture f = new Fixture();
        // No tickets table means any accidental guard SQL would fail closed.
        f.jdbc.execute("DROP TABLE tickets");

        f.service.append(eventType, ticketId, null, null, null, null, null);

        verifyNoInteractions(f.guard);
        assertThat(f.attempts).isZero();
        assertThat(f.events()).isEmpty();
    }

    @Test
    void nullableNumericAndPayloadFieldsUseDefaultJdbcStringType() {
        Fixture f = new Fixture();
        f.parent(1L, "T-NULLS", null);

        f.service.append("  custom  ", "T-NULLS", null, null, null, null, null);

        Map<String, Object> row = f.onlyEvent();
        assertThat(f.affected).isEqualTo(1);
        assertThat(row.get("event_type")).isEqualTo("custom");
        for (String column : List.of("channel_id", "message_text", "message_type", "attachment", "rating")) {
            assertThat(row.get(column)).as(column).isNull();
        }
        assertThat(f.args[3]).isNull();
        assertThat(f.args[7]).isNull();
    }

    @Test
    void blankOptionalStringsKeepExistingTrimToNullBehavior() {
        Fixture f = new Fixture();
        f.parent(1L, "T-BLANK", 10L);

        f.service.append("custom", "T-BLANK", 99L, " \t ", "\n", "", -7);

        Map<String, Object> row = f.onlyEvent();
        assertThat(row.get("message_text")).isNull();
        assertThat(row.get("message_type")).isNull();
        assertThat(row.get("attachment")).isNull();
        assertThat(row.get("rating")).isEqualTo(-7);
    }

    @Test
    void preliminaryJdbcFailureRemainsFailClosed() {
        Fixture f = new Fixture();
        f.jdbc.execute("DROP TABLE tickets");

        f.publish("append", "T-FAIL");

        assertThat(f.attempts).isZero();
        assertThat(f.events()).isEmpty();
    }

    @Test
    void insertFailureStillPropagatesWithoutChangingParent() {
        Fixture f = new Fixture();
        f.parent(1L, "T-FAIL", 10L);
        List<Map<String, Object>> parents = f.parents();
        f.beforeInsert = () -> f.jdbc.execute("ALTER TABLE ui_event_outbox ADD CONSTRAINT reject_event CHECK (rating = 999)");

        assertThatThrownBy(() -> f.publish("append", "T-FAIL")).isInstanceOf(DataAccessException.class);

        assertThat(f.attempts).isEqualTo(1);
        assertThat(f.events()).isEmpty();
        assertThat(f.parents()).isEqualTo(parents);
    }

    @Test
    void missingOutboxTableFailureStillPropagatesAfterGuard() {
        Fixture f = new Fixture();
        f.parent(1L, "T-FAIL", 10L);
        f.beforeInsert = () -> f.jdbc.execute("DROP TABLE ui_event_outbox");

        assertThatThrownBy(() -> f.publish("feedback", "T-FAIL")).isInstanceOf(DataAccessException.class);

        assertThat(f.attempts).isEqualTo(1);
        assertThat(f.parents()).hasSize(1);
    }

    @Test
    void unrelatedLateDuplicateDoesNotDenyUniqueTarget() {
        Fixture f = new Fixture();
        f.parent(1L, "T-TARGET", null);
        f.parent(2L, "T-OTHER", 20L);
        f.beforeInsert = () -> f.parent(3L, "T-OTHER", null);

        f.publish("append", "T-TARGET");

        assertThat(f.affected).isEqualTo(1);
        assertThat(f.onlyEvent().get("ticket_id")).isEqualTo("T-TARGET");
        assertThat(f.parents()).isEqualTo(f.parentsAtInsert);
    }

    static Stream<Arguments> afterInsertCases() {
        return Stream.of(Arguments.of("missing"), Arguments.of("duplicate"));
    }

    @ParameterizedTest
    @MethodSource("afterInsertCases")
    void mutationAfterCommittedInsertDoesNotRetroactivelyRemoveEvent(String mutation) {
        Fixture f = new Fixture();
        f.parent(1L, "T-AFTER", 10L);
        f.afterInsert = () -> f.invalidate("T-AFTER", mutation);

        f.publish("append", "T-AFTER");

        assertThat(f.affected).isEqualTo(1);
        assertThat(f.onlyEvent().get("ticket_id")).isEqualTo("T-AFTER");
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE ticket_id = 'T-AFTER'", Integer.class))
            .isEqualTo("missing".equals(mutation) ? 0 : 2);
    }

    private static String eventType(String publisher) {
        return switch (publisher) {
            case "append" -> "custom";
            case "reopened" -> "ticket_reopened";
            case "client-edit" -> "client_message_edited";
            case "operator-edit" -> "operator_message_edited";
            case "feedback" -> "feedback_created";
            case "closed" -> "ticket_closed";
            case "auto-closed" -> "ticket_closed_auto";
            default -> throw new IllegalArgumentException(publisher);
        };
    }

    private static final class Fixture {
        private final JdbcTemplate jdbc =
            PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("ui_append_snapshot");
        private final JdbcTemplate observed = spy(jdbc);
        private final LegacyTicketIdJdbcGuard guard = spy(new LegacyTicketIdJdbcGuard(jdbc));
        private final UiEventOutboxAppendService service;
        private Runnable beforeInsert = () -> { };
        private Runnable afterInsert = () -> { };
        private int attempts;
        private int affected = -1;
        private Object[] args;
        private List<Map<String, Object>> parentsAtInsert;

        private Fixture() {
            // Minimal V1/V7-compatible types, composite/unique/NOT NULL keys; no full channel FK/Flyway chain.
            jdbc.execute("""
                CREATE TABLE tickets(
                    user_id BIGINT NOT NULL, ticket_id VARCHAR(255) NOT NULL, channel_id BIGINT,
                    is_general BOOLEAN NOT NULL DEFAULT FALSE, business_id BIGINT, ticket_public_id TEXT,
                    PRIMARY KEY(user_id, ticket_id), UNIQUE(ticket_id, channel_id))
                """);
            jdbc.execute("""
                CREATE TABLE ui_event_outbox(
                    id BIGINT PRIMARY KEY, event_type TEXT NOT NULL, ticket_id VARCHAR(255) NOT NULL,
                    channel_id BIGINT, message_text TEXT, message_type TEXT, attachment TEXT, rating INTEGER,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    ticket_user_id BIGINT, ticket_public_id TEXT, ticket_scope_kind TEXT, ticket_business_id BIGINT)
                """);
            doAnswer(call -> {
                attempts++;
                String sql = (String) call.getRawArguments()[0];
                args = Arrays.copyOf((Object[]) call.getRawArguments()[1],
                    ((Object[]) call.getRawArguments()[1]).length);
                beforeInsert.run(); // Separate autocommitted connection, immediately before the real INSERT.
                parentsAtInsert = parents();
                affected = jdbc.update(sql, args);
                afterInsert.run();
                return affected;
            }).when(observed).update(anyString(), any(Object[].class));
            service = new UiEventOutboxAppendService(observed, guard);
        }

        private void parent(long userId, String ticketId, Long channelId) {
            jdbc.update("INSERT INTO tickets(user_id, ticket_id, channel_id) VALUES (?, ?, ?)",
                userId, ticketId, channelId);
        }

        private void invalidate(String rawId, String mutation) {
            if ("missing".equals(mutation)) {
                jdbc.update("DELETE FROM tickets WHERE ticket_id = ?", rawId);
            } else {
                // NULL channel avoids a false pass from restricting count to the event/parent channel.
                parent(9L, rawId, null);
            }
        }

        private void seedEvent() {
            jdbc.update("""
                INSERT INTO ui_event_outbox(id, event_type, ticket_id, created_at)
                VALUES (17, 'existing', 'T-EXISTING', CURRENT_TIMESTAMP)
                """);
        }

        private List<Map<String, Object>> parents() {
            return jdbc.queryForList("SELECT user_id, ticket_id, channel_id FROM tickets ORDER BY user_id, ticket_id");
        }

        private List<Map<String, Object>> events() {
            return jdbc.queryForList("SELECT * FROM ui_event_outbox ORDER BY id");
        }

        private Map<String, Object> onlyEvent() {
            List<Map<String, Object>> rows = events();
            assertThat(rows).hasSize(1);
            return rows.get(0);
        }

        private void publish(String publisher, String rawId) {
            switch (publisher) {
                case "append" -> service.append("  custom  ", rawId, 99L,
                    "  payload  ", "  photo  ", "  storage/key  ", 4);
                case "reopened" -> service.publishTicketReopened(rawId, 99L, "  payload  ");
                case "client-edit" -> service.publishClientMessageEdited(rawId, 99L, "  payload  ");
                case "operator-edit" -> service.publishOperatorMessageEdited(rawId, 99L, "  payload  ");
                case "feedback" -> service.publishFeedbackCreated(rawId, 99L, 5);
                case "closed" -> service.publishTicketClosed(rawId, 99L, "  payload  ", false);
                case "auto-closed" -> service.publishTicketClosed(rawId, 99L, "  payload  ", true);
                default -> throw new IllegalArgumentException(publisher);
            }
        }
    }
}
