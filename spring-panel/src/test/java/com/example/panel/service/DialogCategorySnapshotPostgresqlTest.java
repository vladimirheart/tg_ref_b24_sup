package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class DialogCategorySnapshotPostgresqlTest {
    private static final LocalDateTime OLD_TIME = LocalDateTime.parse("2000-01-01T00:00:00");
    private record Fixture(JdbcTemplate jdbc, LegacyTicketIdJdbcGuard guard,
                           DialogResponsibilityService responsibility, DialogTicketLifecycleService service) { }

    private Fixture fixture(String owner, boolean existing) {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("category_snapshot");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ticket_categories (
                    id BIGSERIAL PRIMARY KEY,
                    ticket_id TEXT NOT NULL,
                    category TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT ux_ticket_categories_ticket_category UNIQUE(ticket_id, category)
                );
                """);
        jdbc.update("INSERT INTO tickets VALUES (42,?)", owner);
        if (existing) category(jdbc, owner, "Old");
        if (!owner.equals(owner.trim())) {
            jdbc.update("INSERT INTO tickets VALUES (88,?)", owner.trim());
            category(jdbc, owner.trim(), "Sibling");
        }
        var guard = spy(new LegacyTicketIdJdbcGuard(jdbc));
        var responsibility = mock(DialogResponsibilityService.class);
        return new Fixture(jdbc, guard, responsibility, new DialogTicketLifecycleService(jdbc, responsibility, guard));
    }

    private void category(JdbcTemplate jdbc, String owner, String category) {
        jdbc.update("INSERT INTO ticket_categories(ticket_id,category,created_at,updated_at) VALUES (?,?,?,?)",
                owner, category, OLD_TIME, OLD_TIME);
    }

    private void invalidate(Fixture fixture, String owner, String condition) {
        if ("duplicate".equals(condition)) {
            fixture.jdbc().update("INSERT INTO tickets VALUES (99,?)", owner);
        } else if ("missing".equals(condition)) {
            fixture.jdbc().update("DELETE FROM tickets WHERE ticket_id=?", owner);
        } else {
            throw new IllegalArgumentException(condition);
        }
    }

    static Stream<Arguments> lateProofs() {
        return Stream.of("T", " T ").flatMap(owner -> Stream.of("duplicate", "missing")
                .flatMap(condition -> Stream.of(false, true).map(empty -> Arguments.of(owner, condition, empty))));
    }

    @ParameterizedTest
    @MethodSource("lateProofs")
    void staleProofCannotClearOrReplaceCategoriesForInvalidParent(String owner, String condition, boolean empty) {
        Fixture fixture = fixture(owner, true);
        var before = fixture.jdbc().queryForList("SELECT * FROM ticket_categories ORDER BY id");
        var proofs = new AtomicInteger();
        doAnswer(call -> {
            boolean unique = (boolean) call.callRealMethod();
            assertThat(unique).isTrue();
            proofs.incrementAndGet();
            invalidate(fixture, owner, condition);
            return unique;
        }).when(fixture.guard()).hasUniqueTicket(owner);

        fixture.service().setTicketCategories(owner, empty ? List.of() : List.of(" First ", "Second"));

        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_categories ORDER BY id")).isEqualTo(before);
        assertThat(proofs).hasValue(1);
        verifyNoInteractions(fixture.responsibility());
    }

    static Stream<Arguments> betweenStatements() {
        return Stream.of("T", " T ").flatMap(owner -> Stream.of("duplicate", "missing")
                .flatMap(condition -> Stream.of(false, true).map(existing -> Arguments.of(owner, condition, existing))));
    }

    @ParameterizedTest
    @MethodSource("betweenStatements")
    void parentInvalidatedAfterDeleteCannotReceiveNewCategory(String owner, String condition, boolean existing) {
        Fixture fixture = fixture(owner, existing);
        var sibling = fixture.jdbc().queryForList("SELECT * FROM ticket_categories WHERE ticket_id<>?", owner);
        JdbcTemplate intercepted = spy(fixture.jdbc());
        var deletes = new AtomicInteger();
        var inserted = new ArrayList<Integer>();
        doAnswer(call -> {
            String sql = call.getArgument(0);
            int changed = (int) call.callRealMethod();
            if (sql.stripLeading().startsWith("DELETE FROM ticket_categories")) {
                assertThat(changed).isEqualTo(existing ? 1 : 0);
                deletes.incrementAndGet();
                // DELETE уже зафиксирован; откат не является scope этого guard.
                invalidate(fixture, owner, condition);
            } else if (sql.stripLeading().startsWith("INSERT INTO ticket_categories")) {
                inserted.add(changed);
            }
            return changed;
        }).when(intercepted).update(anyString(), any(Object[].class));
        var service = new DialogTicketLifecycleService(intercepted, fixture.responsibility(), fixture.guard());

        service.setTicketCategories(owner, List.of(" First ", "Second"));

        assertThat(deletes).hasValue(1);
        assertThat(inserted).containsExactly(0, 0);
        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_categories WHERE ticket_id=?", owner)).isEmpty();
        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_categories WHERE ticket_id<>?", owner)).isEqualTo(sibling);
        verifyNoInteractions(fixture.responsibility());
    }

    static Stream<Arguments> midLoop() {
        return Stream.of("T", " T ").flatMap(owner -> Stream.of("duplicate", "missing")
                .map(condition -> Arguments.of(owner, condition)));
    }

    @ParameterizedTest
    @MethodSource("midLoop")
    void laterInsertsRecheckParentWithoutRollingBackAlreadyCommittedFirstCategory(String owner, String condition) {
        Fixture fixture = fixture(owner, true);
        JdbcTemplate intercepted = spy(fixture.jdbc());
        var inserted = new ArrayList<Integer>();
        doAnswer(call -> {
            String sql = call.getArgument(0);
            int changed = (int) call.callRealMethod();
            if (sql.stripLeading().startsWith("INSERT INTO ticket_categories")) {
                inserted.add(changed);
                if (inserted.size() == 1) {
                    assertThat(changed).isEqualTo(1);
                    invalidate(fixture, owner, condition);
                }
            }
            return changed;
        }).when(intercepted).update(anyString(), any(Object[].class));
        var service = new DialogTicketLifecycleService(intercepted, fixture.responsibility(), fixture.guard());

        service.setTicketCategories(owner, List.of(" First ", "Second", "Third"));

        assertThat(inserted).containsExactly(1, 0, 0);
        assertThat(fixture.jdbc().queryForList("SELECT category FROM ticket_categories WHERE ticket_id=? ORDER BY id",
                String.class, owner)).containsExactly("First");
        assertThat(fixture.jdbc().queryForObject("SELECT created_at=updated_at FROM ticket_categories WHERE ticket_id=?",
                Boolean.class, owner)).isTrue();
        verifyNoInteractions(fixture.responsibility());
    }

    static Stream<String> exactIds() {
        return Stream.of("T", " T ", "T/100", " T/100?&%#+ Ю ", "T%2F+", " null ");
    }

    private List<String> input(String state) {
        return switch (state) {
            case "rich" -> Arrays.asList(null, "", " ", " Billing ", "Billing", " billing ",
                    " Ю/Кат?+% ", "Ю/Кат?+%", "first");
            case "null" -> null;
            case "empty" -> List.of();
            case "blank" -> Arrays.asList(null, "", " ");
            default -> throw new IllegalArgumentException(state);
        };
    }

    static Stream<Arguments> validReplacements() {
        return exactIds().flatMap(owner -> Stream.of(false, true)
                .flatMap(existing -> Stream.of("rich", "null", "empty", "blank")
                        .map(state -> Arguments.of(owner, existing, state))));
    }

    @ParameterizedTest
    @MethodSource("validReplacements")
    void validReplacementKeepsExactIdCaseDistinctOrderClearSemanticsAndTimestamps(String owner, boolean existing, String state) {
        Fixture fixture = fixture(owner, existing);
        var parents = fixture.jdbc().queryForList("SELECT * FROM tickets ORDER BY user_id");
        var sibling = fixture.jdbc().queryForList("SELECT * FROM ticket_categories WHERE ticket_id<>?", owner);
        List<String> expected = "rich".equals(state) ? List.of("Billing", "billing", "Ю/Кат?+%", "first") : List.of();

        fixture.service().setTicketCategories(owner, input(state));

        assertThat(fixture.jdbc().queryForList("SELECT category FROM ticket_categories WHERE ticket_id=? ORDER BY id",
                String.class, owner)).containsExactlyElementsOf(expected);
        assertThat(fixture.jdbc().queryForList("SELECT ticket_id FROM ticket_categories WHERE ticket_id=? ORDER BY id",
                String.class, owner)).containsExactlyElementsOf(expected.stream().map(category -> owner).toList());
        for (boolean timestamp : fixture.jdbc().queryForList("""
                SELECT created_at=updated_at AND created_at BETWEEN
                       CURRENT_TIMESTAMP - INTERVAL '1 minute' AND CURRENT_TIMESTAMP + INTERVAL '1 minute'
                  FROM ticket_categories WHERE ticket_id=? ORDER BY id
                """, Boolean.class, owner)) {
            assertThat(timestamp).isTrue();
        }
        var firstIds = fixture.jdbc().queryForList("SELECT id FROM ticket_categories WHERE ticket_id=? ORDER BY id", Long.class, owner);
        fixture.service().setTicketCategories(owner, input(state));
        var secondIds = fixture.jdbc().queryForList("SELECT id FROM ticket_categories WHERE ticket_id=? ORDER BY id", Long.class, owner);
        if (!expected.isEmpty()) assertThat(secondIds.get(0)).isGreaterThan(firstIds.get(firstIds.size() - 1));
        assertThat(fixture.jdbc().queryForList("SELECT category FROM ticket_categories WHERE ticket_id=? ORDER BY id",
                String.class, owner)).containsExactlyElementsOf(expected);
        assertThat(fixture.jdbc().queryForList("SELECT * FROM tickets ORDER BY user_id")).isEqualTo(parents);
        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_categories WHERE ticket_id<>?", owner)).isEqualTo(sibling);
        verifyNoInteractions(fixture.responsibility());
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "missing", " T ", "duplicate"})
    void unresolvedExactIdCannotClearOrAppendCategory(String requested) {
        Fixture fixture = fixture("T", true);
        fixture.jdbc().update("INSERT INTO tickets VALUES (98,'duplicate'),(99,'duplicate')");
        var before = fixture.jdbc().queryForList("SELECT * FROM ticket_categories ORDER BY id");

        fixture.service().setTicketCategories(requested, List.of("new"));
        fixture.service().setTicketCategories(requested, List.of());

        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_categories ORDER BY id")).isEqualTo(before);
        verifyNoInteractions(fixture.responsibility());
    }

    static Stream<Arguments> uniqueOwnerReplacement() {
        return Stream.of("T", " T ").flatMap(owner -> Stream.of("proof", "delete").map(phase -> Arguments.of(owner, phase)));
    }

    @ParameterizedTest
    @MethodSource("uniqueOwnerReplacement")
    void compatibilityGateStillChecksOnlyUniquenessAndDoesNotFreezeCompositeOwner(String owner, String phase) {
        Fixture fixture = fixture(owner, true);
        Runnable replace = () -> {
            fixture.jdbc().update("DELETE FROM tickets WHERE ticket_id=?", owner);
            fixture.jdbc().update("INSERT INTO tickets VALUES (99,?)", owner);
        };
        JdbcTemplate intercepted = spy(fixture.jdbc());
        if ("proof".equals(phase)) {
            doAnswer(call -> {
                boolean unique = (boolean) call.callRealMethod();
                assertThat(unique).isTrue();
                replace.run();
                return unique;
            }).when(fixture.guard()).hasUniqueTicket(owner);
        } else {
            doAnswer(call -> {
                int changed = (int) call.callRealMethod();
                String sql = call.getArgument(0);
                if (sql.stripLeading().startsWith("DELETE FROM ticket_categories")) replace.run();
                return changed;
            }).when(intercepted).update(anyString(), any(Object[].class));
        }
        var service = new DialogTicketLifecycleService(intercepted, fixture.responsibility(), fixture.guard());

        service.setTicketCategories(owner, List.of("first", "second"));

        assertThat(fixture.jdbc().queryForList("SELECT category FROM ticket_categories WHERE ticket_id=? ORDER BY id",
                String.class, owner)).containsExactly("first", "second");
        assertThat(fixture.jdbc().queryForObject("SELECT user_id FROM tickets WHERE ticket_id=?", Long.class, owner)).isEqualTo(99L);
        verifyNoInteractions(fixture.responsibility());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ticket_categories", "tickets"})
    void missingSchemaKeepsExistingDataAccessFallback(String missing) {
        Fixture fixture = fixture("T", true);
        fixture.jdbc().execute("DROP TABLE " + missing);
        fixture.service().setTicketCategories("T", List.of("new"));
        if ("tickets".equals(missing)) {
            assertThat(fixture.jdbc().queryForList("SELECT category FROM ticket_categories", String.class)).containsExactly("Old");
        }
        verifyNoInteractions(fixture.responsibility());
    }

    @Test
    void duplicateCategoryRaceStillStopsLoopWithoutRollingBackEarlierStatements() {
        Fixture fixture = fixture("T", true);
        JdbcTemplate intercepted = spy(fixture.jdbc());
        var attempts = new AtomicInteger();
        doAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.stripLeading().startsWith("INSERT INTO ticket_categories")) attempts.incrementAndGet();
            int changed = (int) call.callRealMethod();
            if (sql.stripLeading().startsWith("INSERT INTO ticket_categories") && attempts.get() == 1) {
                category(fixture.jdbc(), "T", "Second");
            }
            return changed;
        }).when(intercepted).update(anyString(), any(Object[].class));
        var service = new DialogTicketLifecycleService(intercepted, fixture.responsibility(), fixture.guard());

        service.setTicketCategories("T", List.of("First", "Second", "Third"));

        assertThat(attempts).hasValue(2);
        assertThat(fixture.jdbc().queryForList("SELECT category FROM ticket_categories ORDER BY id", String.class))
                .containsExactly("First", "Second");
        LocalDateTime concurrentTimestamp = fixture.jdbc().queryForObject(
                "SELECT created_at FROM ticket_categories WHERE category='Second'",
                (rs, n) -> rs.getObject(1, LocalDateTime.class));
        assertThat(concurrentTimestamp).isEqualTo(OLD_TIME);
        verifyNoInteractions(fixture.responsibility());
    }
}
