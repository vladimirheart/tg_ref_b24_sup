package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class DialogScopedResponsibilityPostgresTest {
    private static final String PUBLIC = "a".repeat(32);
    private static final String RAW = " scoped/Ю?% ";
    private record Fixture(JdbcTemplate jdbc, JdbcTemplate observed, MultibusinessFeatureFlags flags,
                           DialogResponsibilityService service, UiEventTicketScope scope) { }

    private Fixture fixture(String raw, boolean existing) {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("scoped_responsibility");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT, ticket_id TEXT, ticket_public_id TEXT, "
            + "business_id BIGINT, is_general BOOLEAN, PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("CREATE TABLE ticket_responsibles(ticket_id TEXT PRIMARY KEY, responsible TEXT, assigned_by TEXT, "
            + "assigned_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, last_read_at TIMESTAMPTZ)");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY, username TEXT, enabled BOOLEAN, is_blocked BOOLEAN)");
        jdbc.execute("CREATE TABLE user_authorities(user_id BIGINT, authority TEXT)");
        jdbc.execute("CREATE TABLE businesses(id BIGINT PRIMARY KEY, status TEXT)");
        jdbc.execute("CREATE TABLE business_memberships(user_id BIGINT, business_id BIGINT, role_code TEXT, is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE business_access_grants(user_id BIGINT, grant_scope TEXT, role_code TEXT, is_active BOOLEAN)");
        jdbc.update("INSERT INTO tickets VALUES (10, ?, ?, 1, FALSE)", raw, PUBLIC);
        jdbc.update("INSERT INTO users VALUES (1, 'operator', TRUE, FALSE)");
        jdbc.update("INSERT INTO businesses VALUES (1, 'active'), (2, 'active')");
        jdbc.update("INSERT INTO business_memberships VALUES (1, 1, 'OPERATOR', TRUE)");
        if (existing) insertResponsible(jdbc, raw, " First Owner ");
        if (!raw.equals(raw.trim())) {
            jdbc.update("INSERT INTO tickets VALUES (20, ?, ?, 2, FALSE)", raw.trim(), "b".repeat(32));
            insertResponsible(jdbc, raw.trim(), "sibling");
        }
        JdbcTemplate observed = spy(jdbc);
        MultibusinessFeatureFlags flags = new MultibusinessFeatureFlags();
        var service = new DialogResponsibilityService(observed, new LegacyTicketIdJdbcGuard(jdbc), flags);
        return new Fixture(jdbc, observed, flags, service, new UiEventTicketScope(10L, raw, PUBLIC, "BUSINESS", 1L));
    }

    private static void insertResponsible(JdbcTemplate jdbc, String raw, String responsible) {
        jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible,assigned_by,last_read_at) "
            + "VALUES (?,?,'Original Lead','2000-01-01T00:00:00Z')", raw, responsible);
    }

    static Stream<Arguments> validAssignments() {
        return Stream.of("T", " T ", "T/100", RAW, "T%2F+", " null ")
            .flatMap(raw -> Stream.of("OPERATOR", "MANAGER", "ADMIN")
                .flatMap(role -> Stream.of("membership", "all")
                    .map(grant -> Arguments.of(raw, role, grant))));
    }

    @ParameterizedTest
    @MethodSource("validAssignments")
    void statementAcceptsOnlyExactOwnerAndAnEffectiveOperateGrant(String raw, String role, String grant) {
        Fixture f = fixture(raw, false);
        f.jdbc.update("DELETE FROM business_memberships");
        if ("membership".equals(grant))
            f.jdbc.update("INSERT INTO business_memberships VALUES (1,1,?,TRUE)", role);
        else f.jdbc.update("INSERT INTO business_access_grants VALUES (1,'ALL_BUSINESSES',?,TRUE)", role);
        var sibling = f.jdbc.queryForList("SELECT * FROM ticket_responsibles WHERE ticket_id <> ?", raw);

        assertThat(f.service.assignScopedResponsibleIfMissing(f.scope, " OpErAtOr ", " Lead ")).isEqualTo("OpErAtOr");

        var row = f.jdbc.queryForMap("SELECT * FROM ticket_responsibles WHERE ticket_id = ?", raw);
        assertThat(row).containsEntry("ticket_id", raw).containsEntry("responsible", "OpErAtOr")
            .containsEntry("assigned_by", "Lead").containsEntry("last_read_at", null);
        assertThat(row.get("assigned_at")).isNotNull();
        assertThat(f.jdbc.queryForList("SELECT * FROM ticket_responsibles WHERE ticket_id <> ?", raw)).isEqualTo(sibling);
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "blocked", "null-enabled", "null-blocked", "duplicate-identity", "foreign",
        "viewer", "unknown-role", "inactive", "revoked", "archived", "missing-business", "all-viewer",
        "inactive-all", "unknown-all-scope", "system-admin-only"})
    void proposedAssigneeCannotObtainBusinessOwnershipWithoutOperate(String state) {
        Fixture f = fixture(RAW, false);
        var before = f.jdbc.queryForList("SELECT * FROM ticket_responsibles");
        // Change the grant/account immediately at the write boundary, not just before an application check.
        var writes = new AtomicInteger();
        doAnswer(call -> {
            writes.incrementAndGet();
            switch (state) {
                case "disabled" -> f.jdbc.update("UPDATE users SET enabled = FALSE");
                case "blocked" -> f.jdbc.update("UPDATE users SET is_blocked = TRUE");
                case "null-enabled" -> f.jdbc.update("UPDATE users SET enabled = NULL");
                case "null-blocked" -> f.jdbc.update("UPDATE users SET is_blocked = NULL");
                case "duplicate-identity" -> f.jdbc.update("INSERT INTO users VALUES (2,'OPERATOR',TRUE,FALSE)");
                case "foreign" -> f.jdbc.update("UPDATE business_memberships SET business_id = 2");
                case "viewer" -> f.jdbc.update("UPDATE business_memberships SET role_code = 'VIEWER'");
                case "unknown-role" -> f.jdbc.update("UPDATE business_memberships SET role_code = 'CUSTOM'");
                case "inactive" -> f.jdbc.update("UPDATE business_memberships SET is_active = FALSE");
                case "revoked" -> f.jdbc.update("DELETE FROM business_memberships");
                case "system-admin-only" -> {
                    f.jdbc.update("DELETE FROM business_memberships");
                    f.jdbc.update("INSERT INTO user_authorities VALUES (1,'ROLE_ADMIN'),(1,'PAGE_DIALOGS')");
                }
                case "archived" -> f.jdbc.update("UPDATE businesses SET status = 'archived' WHERE id=1");
                case "missing-business" -> f.jdbc.update("DELETE FROM businesses WHERE id=1");
                default -> {
                    f.jdbc.update("DELETE FROM business_memberships");
                    f.jdbc.update("INSERT INTO business_access_grants VALUES (1,?,?,?)",
                        "unknown-all-scope".equals(state) ? "ROLE_ADMIN" : "ALL_BUSINESSES",
                        "all-viewer".equals(state) ? "VIEWER" : "OPERATOR", !"inactive-all".equals(state));
                }
            }
            return f.jdbc.update(call.getArgument(0, String.class), (Object[]) call.getRawArguments()[1]);
        }).when(f.observed).update(anyString(), any(Object[].class));

        assertThat(f.service.assignScopedResponsibleIfMissing(f.scope, "operator", "routing")).isNull();

        assertThat(writes).hasValue(1);
        assertThat(f.jdbc.queryForList("SELECT * FROM ticket_responsibles")).isEqualTo(before);
    }

    static Stream<Arguments> lateOwners() {
        return Stream.of("business", "recreated", "tuple", "general", "missing", "collision", "locator")
            .flatMap(mutation -> Stream.of(false, true).map(existing -> Arguments.of(mutation, existing)));
    }

    @ParameterizedTest
    @MethodSource("lateOwners")
    void lateOwnerChangeCannotInsertOrReturnAnAssignmentForTheReplacement(String mutation, boolean existing) {
        Fixture f = fixture(RAW, existing);
        var before = f.jdbc.queryForList("SELECT * FROM ticket_responsibles ORDER BY ticket_id");
        var writes = new AtomicInteger();
        doAnswer(call -> {
            writes.incrementAndGet();
            mutate(f, mutation);
            return f.jdbc.update(call.getArgument(0, String.class), (Object[]) call.getRawArguments()[1]);
        }).when(f.observed).update(anyString(), any(Object[].class));

        assertThat(f.service.assignScopedResponsibleIfMissing(f.scope, "operator", "routing")).isNull();

        assertThat(writes).hasValue(1);
        assertThat(f.jdbc.queryForList("SELECT * FROM ticket_responsibles ORDER BY ticket_id")).isEqualTo(before);
    }

    @ParameterizedTest
    @MethodSource("lateOwners")
    void returnReadRepeatsFullScopeAfterInsertOrExistingOwner(String mutation, boolean existing) {
        Fixture f = fixture(RAW, existing);
        var reads = new AtomicInteger();
        doAnswer(call -> {
            reads.incrementAndGet();
            mutate(f, mutation);
            return f.jdbc.query(call.getArgument(0, String.class), call.getArgument(1, ResultSetExtractor.class),
                (Object[]) call.getRawArguments()[2]);
        }).when(f.observed).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));

        assertThat(f.service.assignScopedResponsibleIfMissing(f.scope, "operator", "routing")).isNull();

        assertThat(reads).hasValue(1);
        assertThat(f.jdbc.queryForObject("SELECT responsible FROM ticket_responsibles WHERE ticket_id = ?",
            String.class, RAW)).isEqualTo(existing ? " First Owner " : "operator");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "blank", "ai_agent", " AI_AGENT ", "operator"})
    void existingAssignmentIsNeverRedirectedOrReadMarked(String username) {
        Fixture f = fixture(RAW, true);
        var before = f.jdbc.queryForList("SELECT * FROM ticket_responsibles ORDER BY ticket_id");
        assertThat(f.service.assignScopedResponsibleIfMissing(f.scope,
            "null".equals(username) ? null : "blank".equals(username) ? " " : username, "routing"))
            .isEqualTo("First Owner");
        assertThat(f.jdbc.queryForList("SELECT * FROM ticket_responsibles ORDER BY ticket_id")).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "blank"})
    void actorFallbackKeepsTrimmedAssignee(String actor) {
        Fixture f = fixture(RAW, false);
        assertThat(f.service.assignScopedResponsibleIfMissing(f.scope, " Operator ", "null".equals(actor) ? null : " "))
            .isEqualTo("Operator");
        assertThat(f.jdbc.queryForObject("SELECT assigned_by FROM ticket_responsibles WHERE ticket_id=?",
            String.class, RAW)).isEqualTo("Operator");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "blank", "ai_agent", " AI_AGENT "})
    void excludedCandidateCannotCreateTheFirstAssignment(String username) {
        Fixture f = fixture(RAW, false);
        var before = f.jdbc.queryForList("SELECT * FROM ticket_responsibles ORDER BY ticket_id");
        assertThat(f.service.assignScopedResponsibleIfMissing(f.scope,
            "null".equals(username) ? null : "blank".equals(username) ? " " : username, "routing")).isNull();
        assertThat(f.jdbc.queryForList("SELECT * FROM ticket_responsibles ORDER BY ticket_id")).isEqualTo(before);
    }

    @Test
    void insertionDoesNotOverwriteAConcurrentFirstAssignee() {
        Fixture f = fixture(RAW, false);
        doAnswer(call -> {
            insertResponsible(f.jdbc, RAW, " Concurrent Owner ");
            return f.jdbc.update(call.getArgument(0, String.class), (Object[]) call.getRawArguments()[1]);
        }).when(f.observed).update(anyString(), any(Object[].class));
        assertThat(f.service.assignScopedResponsibleIfMissing(f.scope, "operator", "routing")).isEqualTo("Concurrent Owner");
        assertThat(f.jdbc.queryForObject("SELECT assigned_by FROM ticket_responsibles WHERE ticket_id=?",
            String.class, RAW)).isEqualTo("Original Lead");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unresolvedRequiresExactLocatorAndCompatibilityMode(boolean strict) {
        Fixture f = fixture(RAW, false);
        f.jdbc.update("UPDATE tickets SET business_id=NULL WHERE ticket_id=?", RAW);
        f.jdbc.update("DELETE FROM business_memberships");
        f.flags.setStrictReadScope(strict);
        var scope = new UiEventTicketScope(10L, RAW, PUBLIC, "UNRESOLVED", null);
        assertThat(f.service.assignScopedResponsibleIfMissing(scope, "legacy-operator", "routing"))
            .isEqualTo(strict ? null : "legacy-operator");
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM ticket_responsibles WHERE ticket_id=?", Long.class, RAW))
            .isEqualTo(strict ? 0L : 1L);
    }

    @Test
    void validAllBusinessesGrantSurvivesRevokedDirectMembership() {
        Fixture f = fixture(RAW, false);
        f.jdbc.update("UPDATE business_memberships SET is_active=FALSE");
        f.jdbc.update("INSERT INTO business_access_grants VALUES (1,'ALL_BUSINESSES','OPERATOR',TRUE)");
        assertThat(f.service.assignScopedResponsibleIfMissing(f.scope, "operator", "routing")).isEqualTo("operator");
    }

    static Stream<UiEventTicketScope> invalidScopes() {
        return Stream.of(null, new UiEventTicketScope(10L, RAW, null, "BUSINESS", 1L),
            new UiEventTicketScope(10L, RAW, PUBLIC, "GENERAL", null),
            new UiEventTicketScope(10L, RAW, PUBLIC, "BUSINESS", null),
            new UiEventTicketScope(10L, RAW, PUBLIC, "UNRESOLVED", 1L),
            new UiEventTicketScope(10L, RAW, "INVALID", "BUSINESS", 1L),
            new UiEventTicketScope(null, RAW, PUBLIC, "BUSINESS", 1L),
            new UiEventTicketScope(10L, " ", PUBLIC, "BUSINESS", 1L));
    }

    @ParameterizedTest
    @MethodSource("invalidScopes")
    void malformedScopeNeverUsesLegacyReadOrWrite(UiEventTicketScope scope) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTicketIdJdbcGuard guard = mock(LegacyTicketIdJdbcGuard.class);
        var service = new DialogResponsibilityService(jdbc, guard, new MultibusinessFeatureFlags());
        assertThat(service.assignScopedResponsibleIfMissing(scope, "operator", "routing")).isNull();
        verifyNoInteractions(jdbc, guard);
    }

    @Test
    void legacyOnlyConstructionCannotSilentlyEnableScopedAssignment() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTicketIdJdbcGuard guard = mock(LegacyTicketIdJdbcGuard.class);
        var service = new DialogResponsibilityService(jdbc, guard);
        assertThat(service.assignScopedResponsibleIfMissing(new UiEventTicketScope(10L, RAW, PUBLIC, "BUSINESS", 1L),
            "operator", "routing")).isNull();
        verifyNoInteractions(jdbc, guard);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ticket_responsibles", "business_memberships", "users", "locator"})
    void missingSchemaFailsClosedWithoutLegacyFallback(String missing) {
        Fixture f = fixture(RAW, false);
        if ("locator".equals(missing)) f.jdbc.execute("ALTER TABLE tickets DROP COLUMN ticket_public_id");
        else f.jdbc.execute("DROP TABLE " + missing);
        assertThatNoException().isThrownBy(() ->
            assertThat(f.service.assignScopedResponsibleIfMissing(f.scope, "operator", "routing")).isNull());
    }

    private void mutate(Fixture f, String mutation) {
        switch (mutation) {
            case "business" -> f.jdbc.update("UPDATE tickets SET business_id=2 WHERE ticket_id=?", RAW);
            case "tuple" -> f.jdbc.update("UPDATE tickets SET user_id=99 WHERE ticket_id=?", RAW);
            case "general" -> f.jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE WHERE ticket_id=?", RAW);
            case "missing", "recreated" -> {
                f.jdbc.update("DELETE FROM tickets WHERE ticket_id=?", RAW);
                if ("recreated".equals(mutation)) f.jdbc.update("INSERT INTO tickets VALUES (10,?,?,1,FALSE)", RAW, "c".repeat(32));
            }
            case "collision" -> f.jdbc.update("INSERT INTO tickets VALUES (99,?,?,2,FALSE)", RAW, "c".repeat(32));
            case "locator" -> f.jdbc.update("UPDATE tickets SET ticket_public_id=NULL WHERE ticket_id=?", RAW);
            default -> throw new IllegalArgumentException(mutation);
        }
    }
}
