package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.NotificationRepository;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class ScopedIncomingAiPostgresTest {
    private static final String RAW = " ai/Ю?% ";
    private static final String PUBLIC = "a".repeat(32);
    private JdbcTemplate jdbc;
    private JdbcTemplate observed;
    private MultibusinessFeatureFlags flags;
    private UiEventTicketAccessService access;
    private UiEventTicketScope scope;
    private UiEventStreamService stream;
    private NotificationRepository repository;
    private NotificationService notices;
    private DialogAiAssistantConfigService config;
    private DialogAiAssistantEscalationService escalation;
    private DialogAiAssistantMessageFlowService flow;
    private DialogAiAssistantService facade;
    private DialogRealtimeEventService realtime;
    private LegacyTicketIdJdbcGuard guard;
    private final List<Object> rawDependencies = new ArrayList<>();
    private Runnable beforeInsert = () -> {};
    private Runnable afterInsert = () -> {};

    @BeforeEach
    void setUp() {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("scoped_incoming_ai");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT, ticket_id TEXT, ticket_public_id TEXT, is_general BOOLEAN, business_id BIGINT, PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY, username TEXT, enabled BOOLEAN, is_blocked BOOLEAN)");
        jdbc.execute("CREATE TABLE businesses(id BIGINT PRIMARY KEY, status TEXT)");
        jdbc.execute("CREATE TABLE business_memberships(user_id BIGINT, business_id BIGINT, role_code TEXT, is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE business_access_grants(user_id BIGINT, grant_scope TEXT, role_code TEXT, is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE notifications(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, user_identity TEXT, text TEXT, url TEXT, is_read BOOLEAN, created_at TIMESTAMPTZ, ticket_user_id BIGINT, ticket_id TEXT, ticket_public_id TEXT, ticket_scope_kind TEXT, ticket_business_id BIGINT)");
        jdbc.update("INSERT INTO users VALUES (1,'reader',TRUE,FALSE), (2,'foreign',TRUE,FALSE), (3,'admin',TRUE,FALSE), (4,'all-reader',TRUE,FALSE)");
        jdbc.update("INSERT INTO businesses VALUES (1,'active'), (2,'active')");
        jdbc.update("INSERT INTO business_memberships VALUES (1,1,'VIEWER',TRUE), (2,2,'OPERATOR',TRUE)");
        jdbc.update("INSERT INTO business_access_grants VALUES (4,'ALL_BUSINESSES','VIEWER',TRUE)");
        jdbc.update("INSERT INTO tickets VALUES (10,?,?,FALSE,1), (20,?,?,FALSE,2)", RAW, PUBLIC, RAW.trim(), "b".repeat(32));
        flags = new MultibusinessFeatureFlags();
        guard = new LegacyTicketIdJdbcGuard(jdbc);
        access = spy(new UiEventTicketAccessService(jdbc, flags));
        scope = access.captureScope(RAW).orElseThrow();
        stream = mock(UiEventStreamService.class);
        repository = mock(NotificationRepository.class);
        observed = spy(jdbc);
        doAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("INSERT INTO notifications")) {
                Runnable hook = beforeInsert;
                beforeInsert = () -> {};
                hook.run();
            }
            int changed = jdbc.update(sql, (Object[]) call.getRawArguments()[1]);
            if (sql.contains("INSERT INTO notifications") && changed > 0) {
                Runnable hook = afterInsert;
                afterInsert = () -> {};
                hook.run();
            }
            return changed;
        }).when(observed).update(anyString(), any(Object[].class));
        notices = spy(new NotificationService(repository, observed, jdbc, stream, guard));
        ReflectionTestUtils.setField(notices, "multibusinessFeatureFlags", flags);
        config = mock(DialogAiAssistantConfigService.class);
        when(config.isAgentEnabled()).thenReturn(true);
        escalation = new DialogAiAssistantEscalationService(notices);
        AiRetrievalService retrieval = raw(AiRetrievalService.class);
        AiInputNormalizerService normalizer = raw(AiInputNormalizerService.class);
        DialogAiAssistantStateService state = raw(DialogAiAssistantStateService.class);
        DialogAiAssistantSuggestionService suggestions = raw(DialogAiAssistantSuggestionService.class);
        DialogAiSolutionMemoryService memory = raw(DialogAiSolutionMemoryService.class);
        flow = spy(new DialogAiAssistantMessageFlowService(raw(AiPolicyService.class), retrieval,
            raw(AiDecisionService.class), normalizer, raw(AiControlledLlmService.class), state, config,
            raw(DialogAiAssistantPolicyService.class), suggestions, raw(DialogAiAssistantEventService.class),
            escalation, raw(DialogAiAssistantMessageOutcomeService.class)));
        facade = spy(new DialogAiAssistantService(raw(AiMonitoringService.class), normalizer, retrieval,
            raw(DialogAiAssistantReviewService.class), memory, state, config,
            raw(DialogAiAssistantOperatorFeedbackService.class), suggestions, flow, guard));
        ChannelRepository channels = mock(ChannelRepository.class);
        when(channels.findById(9L)).thenReturn(Optional.empty());
        realtime = new DialogRealtimeEventService(notices, facade, mock(AlertQueueService.class),
            mock(ChannelAssignmentRoutingService.class), channels, mock(DialogResponsibilityService.class),
            mock(DialogNotificationService.class), stream, guard, access);
        clearInvocations(access, observed);
    }

    @ParameterizedTest
    @ValueSource(strings = {RAW, "T/slash", "T?#[]&=+", "Обращение-ёж", "T%2F%25", "null"})
    void scopedEscalationPreservesExactOwnerAndFiltersBusinessRecipients(String id) {
        jdbc.update("UPDATE tickets SET ticket_id = ? WHERE user_id = 10", id);
        scope = access.captureScope(id).orElseThrow();
        clearInvocations(access);
        facade.processScopedIncomingClientMessage(scope, "  Вопрос   клиента  ", "photo", "private/media-key");

        assertThat(recipients()).isEqualTo(Set.of("reader", "all-reader"));
        jdbc.queryForList("SELECT * FROM notifications").forEach(row -> {
            assertThat(row.get("ticket_user_id")).isEqualTo(10L);
            assertThat(row.get("ticket_id")).isEqualTo(id);
            assertThat(row.get("ticket_public_id")).isEqualTo(PUBLIC);
            assertThat(row.get("ticket_business_id")).isEqualTo(1L);
            assertThat(row.get("text").toString()).contains(id, "Вопрос клиента").doesNotContain("private/media-key");
            assertThat(row.get("url")).isEqualTo(notices.buildDialogUrl(id));
        });
        assertNoRawEffects();
        verify(access, never()).captureScope(anyString());
    }

    static Stream<Arguments> modesAndEntries() {
        return Stream.of("auto_reply", "assist_only", "escalate_only").flatMap(mode ->
            Stream.of("facade", "flow", "callback").map(entry -> Arguments.of(mode, entry)));
    }

    @ParameterizedTest
    @MethodSource("modesAndEntries")
    void scopedIngressNeverOpensRawAiStagesRegardlessOfConfiguredMode(String mode, String entry) {
        when(config.resolveAgentMode()).thenReturn(mode);
        invoke(entry);
        assertThat(recipients()).isEqualTo(Set.of("reader", "all-reader"));
        verify(config, never()).resolveAgentMode();
        assertNoRawEffects();
    }

    static Stream<Arguments> lateOwners() {
        return Stream.of("business", "recreated", "tuple", "general", "missing", "collision", "unresolved")
            .flatMap(mutation -> Stream.of("config", "insert").map(stage -> Arguments.of(mutation, stage)));
    }

    @ParameterizedTest
    @MethodSource("lateOwners")
    void originalOwnerSurvivesUntilInsertAndCannotRetagAnOldQuestion(String mutation, String stage) {
        if (stage.equals("config")) {
            when(config.isAgentEnabled()).thenAnswer(call -> { mutateOwner(mutation); return true; });
        } else {
            beforeInsert = () -> mutateOwner(mutation);
        }
        facade.processScopedIncomingClientMessage(scope, "private old question", "text", null);
        assertThat(recipients()).isEmpty();
        verifyNoInteractions(stream);
        assertNoRawEffects();
        verify(access, never()).captureScope(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"revoke", "delete", "disabled", "blocked", "collision", "archived"})
    void finalSqlRechecksRecipientReadInsteadOfTrustingTheCandidatePool(String mutation) {
        beforeInsert = () -> {
            switch (mutation) {
                case "revoke" -> jdbc.update("UPDATE business_memberships SET is_active = FALSE WHERE user_id = 1");
                case "delete" -> jdbc.update("DELETE FROM business_memberships WHERE user_id = 1");
                case "disabled" -> jdbc.update("UPDATE users SET enabled = FALSE WHERE id = 1");
                case "blocked" -> jdbc.update("UPDATE users SET is_blocked = TRUE WHERE id = 1");
                case "collision" -> jdbc.update("INSERT INTO users VALUES (5,'READER',FALSE,FALSE)");
                case "archived" -> jdbc.update("UPDATE businesses SET status = 'archived' WHERE id = 1");
                default -> throw new IllegalArgumentException(mutation);
            }
        };
        invoke("facade");
        assertThat(recipients()).isEqualTo(mutation.equals("archived") ? Set.of() : Set.of("all-reader"));
        assertNoRawEffects();
    }

    @ParameterizedTest
    @ValueSource(strings = {"facade", "flow", "callback"})
    void globallyDisabledAiDoesNotEscalateOrTouchLegacyState(String entry) {
        when(config.isAgentEnabled()).thenReturn(false);
        invoke(entry);
        assertThat(recipients()).isEmpty();
        verifyNoInteractions(notices);
        assertNoRawEffects();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unresolvedUsesTheExistingNotificationCompatibilityPolicy(boolean strict) {
        jdbc.update("UPDATE tickets SET business_id = NULL WHERE user_id = 10");
        scope = access.captureScope(RAW).orElseThrow();
        flags.setStrictReadScope(strict);
        invoke("facade");
        assertThat(recipients()).hasSize(strict ? 0 : 4);
        assertNoRawEffects();
    }

    @Test
    void nullGeneralAndMalformedScopesStopAtEveryPublicScopedEntry() {
        var invalid = Arrays.asList(null,
            new UiEventTicketScope(10L, RAW, PUBLIC, "GENERAL", null),
            new UiEventTicketScope(10L, RAW, null, "BUSINESS", 1L),
            new UiEventTicketScope(10L, " ", PUBLIC, "BUSINESS", 1L),
            new UiEventTicketScope(null, RAW, PUBLIC, "BUSINESS", 1L),
            new UiEventTicketScope(10L, RAW, PUBLIC, "UNRESOLVED", 1L));
        for (UiEventTicketScope candidate : invalid) {
            facade.processScopedIncomingClientMessage(candidate, "private", "text", null);
            flow.processScopedIncomingClientMessage(candidate, "private", "text", null);
            escalation.notifyScopedOperatorsEscalation(candidate, "private", "reason");
        }
        verifyNoInteractions(config, notices, stream);
        assertNoRawEffects();
    }

    @Test
    void validEscalationCommittedBeforeOwnerChangeIsNotRetriedOrRetagged() {
        afterInsert = () -> mutateOwner("business");
        invoke("facade");
        var rows = jdbc.queryForList("SELECT ticket_business_id,ticket_public_id FROM notifications");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("ticket_business_id", 1L).containsEntry("ticket_public_id", PUBLIC);
        assertNoRawEffects();
    }

    @ParameterizedTest
    @ValueSource(strings = {"normal", "owner_change", "missing_access_schema"})
    void realFrozenOutboxPreservesScopeThroughAiAndAdvancesItsCursor(String condition) {
        jdbc.execute("CREATE TABLE ui_event_outbox(id BIGINT PRIMARY KEY,event_type TEXT,ticket_id TEXT,channel_id BIGINT,message_text TEXT,message_type TEXT,attachment TEXT,rating INTEGER,created_at TIMESTAMPTZ,ticket_user_id BIGINT,ticket_public_id TEXT,ticket_scope_kind TEXT,ticket_business_id BIGINT)");
        RuntimeWorkerCheckpointService checkpoint = new RuntimeWorkerCheckpointService(jdbc);
        RuntimeCoordinationService coordination = mock(RuntimeCoordinationService.class);
        doAnswer(call -> { call.getArgument(2, Runnable.class).run(); return null; })
            .when(coordination).runWithLease(anyString(), any(Duration.class), any(Runnable.class));
        UiEventOutboxWatcher watcher = new UiEventOutboxWatcher(jdbc, realtime, coordination, checkpoint, guard, access);
        watcher.initialize();
        new UiEventOutboxAppendService(jdbc, guard).append("client_message_created", RAW, 9L,
            "old client payload", "photo", "private/key", null);
        if (!condition.equals("normal")) {
            when(config.isAgentEnabled()).thenAnswer(call -> {
                if (condition.equals("owner_change")) mutateOwner("business");
                else jdbc.execute("DROP TABLE business_access_grants");
                return true;
            });
        }

        watcher.watch();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class)).isEqualTo(condition.equals("normal") ? 4 : 2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE text LIKE 'AI-агент%'", Integer.class))
            .isEqualTo(condition.equals("normal") ? 2 : 0);
        assertThat(checkpoint.readLongCursor("ui-event-outbox-watch"))
            .isEqualTo(Optional.of(jdbc.queryForObject("SELECT max(id) FROM ui_event_outbox", Long.class)));
        verify(facade).processScopedIncomingClientMessage(scope, "old client payload", "photo", "private/key");
        verify(access, never()).captureScope(anyString());
        assertNoRawEffects();
    }

    private void invoke(String entry) {
        switch (entry) {
            case "facade" -> facade.processScopedIncomingClientMessage(scope, "private question", "photo", "private/key");
            case "flow" -> flow.processScopedIncomingClientMessage(scope, "private question", "photo", "private/key");
            case "callback" -> realtime.handleScopedIncomingClientMessage(scope, 9L, "private question", "photo", "private/key", false);
            default -> throw new IllegalArgumentException(entry);
        }
    }

    private void mutateOwner(String mutation) {
        switch (mutation) {
            case "business" -> jdbc.update("UPDATE tickets SET business_id = 2 WHERE user_id = 10");
            case "recreated" -> {
                jdbc.update("DELETE FROM tickets WHERE user_id = 10");
                jdbc.update("INSERT INTO tickets VALUES (10,?,?,FALSE,1)", RAW, "c".repeat(32));
            }
            case "tuple" -> jdbc.update("UPDATE tickets SET user_id = 30 WHERE user_id = 10");
            case "general" -> jdbc.update("UPDATE tickets SET is_general = TRUE,business_id = NULL WHERE user_id = 10");
            case "missing" -> jdbc.update("DELETE FROM tickets WHERE user_id = 10");
            case "collision" -> jdbc.update("INSERT INTO tickets VALUES (30,?,?,FALSE,2)", RAW, "c".repeat(32));
            case "unresolved" -> jdbc.update("UPDATE tickets SET business_id = NULL WHERE user_id = 10");
            default -> throw new IllegalArgumentException(mutation);
        }
    }

    private Set<String> recipients() {
        return Set.copyOf(jdbc.queryForList("SELECT user_identity FROM notifications", String.class));
    }

    private <T> T raw(Class<T> type) {
        T dependency = mock(type);
        rawDependencies.add(dependency);
        return dependency;
    }

    private void assertNoRawEffects() {
        verifyNoInteractions(rawDependencies.toArray());
        verifyNoInteractions(repository);
        verify(facade, never()).processIncomingClientMessage(anyString(), any(), any(), any());
        verify(flow, never()).processIncomingClientMessage(anyString(), any(), any(), any());
        verify(notices, never()).notifyDialogUsers(anyString(), any(), anyString(), anyString());
    }
}
