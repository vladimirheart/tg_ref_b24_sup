package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class DialogReplyTargetLookupPostgresqlTest {
    private record Fixture(JdbcTemplate jdbc, LegacyTicketIdJdbcGuard guard,
                           DialogReplyTargetService targets, ChatAttachmentMetadataService metadata) { }

    private Fixture fixture(String owner) {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("reply_target_lookup");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,channel_id BIGINT,PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("CREATE TABLE messages(user_id BIGINT,ticket_id TEXT,channel_id BIGINT,created_at TIMESTAMPTZ)");
        jdbc.update("INSERT INTO tickets VALUES (42,?,77)", owner);
        jdbc.update("INSERT INTO messages VALUES (42,?,77,CURRENT_TIMESTAMP)", owner);
        if (!owner.equals(owner.trim())) {
            jdbc.update("INSERT INTO tickets VALUES (88,?,88)", owner.trim());
            jdbc.update("INSERT INTO messages VALUES (88,?,88,CURRENT_TIMESTAMP)", owner.trim());
        }
        var guard = spy(new LegacyTicketIdJdbcGuard(jdbc));
        var metadata = mock(ChatAttachmentMetadataService.class);
        return new Fixture(jdbc, guard, new DialogReplyTargetService(jdbc, metadata, guard), metadata);
    }

    private void changeAfterUniqueProof(Fixture fixture, String owner, Runnable change) {
        doAnswer(call -> {
            boolean unique = (boolean) call.callRealMethod();
            assertThat(unique).as("real precheck before committed change").isTrue();
            // Committed interleaving before the target SELECT takes its own snapshot.
            change.run();
            return unique;
        }).when(fixture.guard()).hasUniqueTicket(owner);
    }

    private void addDuplicate(Fixture fixture, String owner, String variant) {
        long channelId = "same-channel".equals(variant) ? 77L : 99L;
        fixture.jdbc().update("INSERT INTO tickets VALUES (99,?,?)", owner, channelId);
        if ("matched-root".equals(variant)) {
            fixture.jdbc().update("INSERT INTO messages VALUES (99,?,?,CURRENT_TIMESTAMP)", owner, channelId);
        }
    }

    static Stream<Arguments> lateDuplicates() {
        return Stream.of("T", " T ")
                .flatMap(owner -> Stream.of("same-channel", "other-channel", "matched-root")
                        .map(variant -> Arguments.of(owner, variant)));
    }

    @ParameterizedTest
    @MethodSource("lateDuplicates")
    void lateDuplicateCannotResolveAnArbitraryRecipient(String owner, String variant) {
        Fixture fixture = fixture(owner);
        changeAfterUniqueProof(fixture, owner, () -> addDuplicate(fixture, owner, variant));

        assertThat(fixture.targets().loadReplyTarget(owner)).isEmpty();

        assertThat(fixture.jdbc().queryForObject(
                "SELECT COUNT(*) FROM tickets WHERE ticket_id=?", Integer.class, owner)).isEqualTo(2);
        verify(fixture.guard(), times(1)).hasUniqueTicket(owner);
        verifyNoInteractions(fixture.metadata());
    }

    static Stream<Arguments> changedRoots() {
        return Stream.of("T", " T ")
                .flatMap(owner -> Stream.of("missing-ticket", "missing-message", "user", "channel", "both")
                        .map(change -> Arguments.of(owner, change)));
    }

    @ParameterizedTest
    @MethodSource("changedRoots")
    void missingOrMismatchedCanonicalRootStillDoesNotResolve(String owner, String change) {
        Fixture fixture = fixture(owner);
        changeAfterUniqueProof(fixture, owner, () -> {
            switch (change) {
                case "missing-ticket" -> fixture.jdbc().update("DELETE FROM tickets WHERE ticket_id=?", owner);
                case "missing-message" -> fixture.jdbc().update("DELETE FROM messages WHERE ticket_id=?", owner);
                case "user" -> fixture.jdbc().update("UPDATE tickets SET user_id=99 WHERE ticket_id=?", owner);
                case "channel" -> fixture.jdbc().update("UPDATE tickets SET channel_id=99 WHERE ticket_id=?", owner);
                case "both" -> fixture.jdbc().update("UPDATE tickets SET user_id=99,channel_id=99 WHERE ticket_id=?", owner);
                default -> throw new IllegalArgumentException(change);
            }
        });

        assertThat(fixture.targets().loadReplyTarget(owner)).isEmpty();

        verify(fixture.guard(), times(1)).hasUniqueTicket(owner);
        verifyNoInteractions(fixture.metadata());
    }

    @ParameterizedTest
    @ValueSource(strings = {"T", " T ", "T/100", " T/100?&%#+ Ю ", "T%2F+", " null "})
    void exactUniqueRootKeepsRecipientAndDoesNotNormalizeTheId(String owner) {
        Fixture fixture = fixture(owner);

        assertThat(fixture.targets().loadReplyTarget(owner)).contains(new DialogReplyTarget(42L, 77L));
        if (!owner.equals(owner.trim())) {
            assertThat(fixture.targets().loadReplyTarget(owner.trim())).contains(new DialogReplyTarget(88L, 88L));
        }

        verify(fixture.guard(), times(1)).hasUniqueTicket(owner);
        verifyNoInteractions(fixture.metadata());
    }

    @ParameterizedTest
    @ValueSource(strings = {"same-channel", "other-channel"})
    void initiallyAmbiguousTicketStillFailsPrecheck(String variant) {
        Fixture fixture = fixture("T");
        addDuplicate(fixture, "T", variant);

        assertThat(fixture.targets().loadReplyTarget("T")).isEmpty();

        verify(fixture.guard(), times(1)).hasUniqueTicket("T");
        verifyNoInteractions(fixture.metadata());
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "missing", " T "})
    void unresolvedOrPaddedAliasDoesNotResolve(String requested) {
        Fixture fixture = fixture("T");

        assertThat(fixture.targets().loadReplyTarget(requested)).isEmpty();

        verify(fixture.guard(), times(1)).hasUniqueTicket(requested);
        verifyNoInteractions(fixture.metadata());
    }
}
