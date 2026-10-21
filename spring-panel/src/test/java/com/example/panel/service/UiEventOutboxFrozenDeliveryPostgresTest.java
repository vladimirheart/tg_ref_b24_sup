package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.NotificationRepository;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class UiEventOutboxFrozenDeliveryPostgresTest {
    private static final String RAW = " frozen/Ю?% ";
    private static final String PUBLIC = "a".repeat(32);
    private JdbcTemplate jdbc;
    private JdbcTemplate observed;
    private MultibusinessFeatureFlags flags;
    private UiEventTicketAccessService access;
    private UiEventTicketScope scope;
    private UiEventFanoutPublisher publisher;
    private UiEventStreamService stream;
    private SseEmitter reader;
    private NotificationRepository repository;
    private NotificationService notices;
    private ChannelRepository channels;
    private ChannelAssignmentRoutingService routing;
    private DialogResponsibilityService responsibility;
    private DialogNotificationService supportChat;
    private SupportChatDeliveryAccessService supportAccess;
    private IntegrationNetworkService supportNetwork;
    private HttpClient supportClient;
    private DialogAiAssistantService ai;
    private AlertQueueService queue;
    private DialogRealtimeEventService realtime;
    private UiEventOutboxAppendService writer;
    private UiEventOutboxWatcher watcher;
    private RuntimeWorkerCheckpointService checkpoint;
    private Channel channel;
    private Runnable beforeInsert = () -> {};
    private Runnable afterInsert = () -> {};
    private Runnable beforeChild = () -> {};
    private Runnable beforeAssignment = () -> {};
    private Runnable afterAssignment = () -> {};
    private Runnable supportSetupMutation = () -> {};
    private int childRowsRead;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("ui_outbox_frozen_delivery");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY, username TEXT, enabled BOOLEAN, is_blocked BOOLEAN, last_portal_activity_at TEXT)");
        jdbc.execute("CREATE TABLE businesses(id BIGINT PRIMARY KEY, status TEXT)");
        jdbc.execute("CREATE TABLE business_memberships(user_id BIGINT, business_id BIGINT, role_code TEXT, is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE business_access_grants(user_id BIGINT, grant_scope TEXT, role_code TEXT, is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT, ticket_id TEXT, ticket_public_id TEXT, is_general BOOLEAN, "
            + "business_id BIGINT, channel_id BIGINT DEFAULT 9, PRIMARY KEY(user_id, ticket_id))");
        jdbc.execute("CREATE TABLE channels(id BIGINT PRIMARY KEY, token TEXT, support_chat_id TEXT, platform TEXT, "
            + "platform_config TEXT, delivery_settings TEXT, credential_id BIGINT, is_active BOOLEAN)");
        jdbc.update("INSERT INTO channels VALUES (9,'test-token','42','telegram',"
            + "'{\"base_url\":\"https://support.invalid\"}',NULL,NULL,TRUE)");
        jdbc.execute("CREATE TABLE notifications(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, user_identity TEXT NOT NULL, "
            + "text TEXT NOT NULL, url TEXT, is_read BOOLEAN, created_at TIMESTAMPTZ, ticket_user_id BIGINT, ticket_id TEXT, "
            + "ticket_public_id TEXT, ticket_scope_kind TEXT, ticket_business_id BIGINT)");
        jdbc.execute("CREATE TABLE ticket_responsibles(ticket_id TEXT PRIMARY KEY, responsible TEXT, assigned_by TEXT)");
        jdbc.execute("CREATE TABLE ticket_active(ticket_id TEXT, user_identity TEXT)");
        jdbc.execute("CREATE TABLE ticket_participants(ticket_id TEXT, username TEXT)");
        jdbc.execute("CREATE TABLE ui_event_outbox(id BIGINT PRIMARY KEY, event_type TEXT, ticket_id TEXT, channel_id BIGINT, "
            + "message_text TEXT, message_type TEXT, attachment TEXT, rating INTEGER, created_at TIMESTAMPTZ, "
            + "ticket_user_id BIGINT, ticket_public_id TEXT, ticket_scope_kind TEXT, ticket_business_id BIGINT)");
        jdbc.update("INSERT INTO users VALUES (1, 'all-reader', TRUE, FALSE), (2, 'foreign-reader', TRUE, FALSE)");
        jdbc.update("INSERT INTO businesses VALUES (1, 'active'), (2, 'active')");
        jdbc.update("INSERT INTO business_access_grants VALUES (1, 'ALL_BUSINESSES', 'VIEWER', TRUE)");
        jdbc.update("INSERT INTO business_memberships VALUES (2, 2, 'VIEWER', TRUE)");
        jdbc.update("INSERT INTO tickets VALUES (10, ?, ?, FALSE, 1), (20, ?, ?, FALSE, 2)",
            RAW, PUBLIC, RAW.trim(), "b".repeat(32));
        jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible) VALUES (?, 'all-reader')", RAW);
        flags = new MultibusinessFeatureFlags();
        LegacyTicketIdJdbcGuard guard = new LegacyTicketIdJdbcGuard(jdbc);
        access = spy(new UiEventTicketAccessService(jdbc, flags));
        scope = access.captureScope(RAW).orElseThrow();
        publisher = mock(UiEventFanoutPublisher.class);
        stream = new UiEventStreamService(publisher, guard, access);
        reader = mock(SseEmitter.class);
        Map<String, CopyOnWriteArraySet<SseEmitter>> emitters =
            (Map<String, CopyOnWriteArraySet<SseEmitter>>) ReflectionTestUtils.getField(stream, "emittersByUser");
        emitters.put("all-reader", new CopyOnWriteArraySet<>(List.of(reader)));
        repository = mock(NotificationRepository.class);
        observed = spy(jdbc);
        doAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("INSERT INTO ticket_responsibles")) {
                Runnable mutation = beforeAssignment;
                beforeAssignment = () -> {};
                mutation.run();
            }
            if (sql.contains("INSERT INTO notifications")) {
                Runnable mutation = beforeInsert;
                beforeInsert = () -> {};
                mutation.run();
            }
            int changed = jdbc.update(sql, (Object[]) call.getRawArguments()[1]);
            if (sql.contains("INSERT INTO ticket_responsibles")) {
                Runnable mutation = afterAssignment;
                afterAssignment = () -> {};
                mutation.run();
            }
            if (sql.contains("INSERT INTO notifications") && changed > 0) {
                Runnable mutation = afterInsert;
                afterInsert = () -> {};
                mutation.run();
            }
            return changed;
        }).when(observed).update(anyString(), any(Object[].class));
        doAnswer(call -> {
            Runnable mutation = beforeChild;
            beforeChild = () -> {};
            mutation.run();
            RowCallbackHandler callback = call.getArgument(1);
            jdbc.query(call.getArgument(0, String.class), (RowCallbackHandler) rs -> {
                childRowsRead++;
                callback.processRow(rs);
            },
                (Object[]) call.getRawArguments()[2]);
            return null;
        }).when(observed).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
        notices = new NotificationService(repository, observed, jdbc, stream, guard);
        ReflectionTestUtils.setField(notices, "multibusinessFeatureFlags", flags);
        channels = mock(ChannelRepository.class);
        channel = new Channel();
        channel.setId(9L);
        channel.setChannelName("scope channel");
        when(channels.findById(9L)).thenReturn(Optional.of(channel));
        routing = mock(ChannelAssignmentRoutingService.class);
        when(routing.resolveRecipients(any(), any(), anyString())).thenReturn(ChannelAssignmentRoutingService.ResolvedAssignmentRouting.disabled());
        when(routing.resolveScoped(any(),any(),any())).thenReturn(ChannelAssignmentRoutingService.ResolvedAssignmentRouting.disabled());
        queue = new AlertQueueService(jdbc, notices, new ObjectMapper(), routing, guard);
        ai = mock(DialogAiAssistantService.class);
        responsibility = spy(new DialogResponsibilityService(observed, guard, flags));
        supportAccess = spy(new SupportChatDeliveryAccessService(jdbc, flags));
        supportNetwork = mock(IntegrationNetworkService.class);
        supportClient = mock(HttpClient.class);
        HttpResponse<String> supportResponse = mock(HttpResponse.class);
        when(supportResponse.statusCode()).thenReturn(200);
        when(supportResponse.body()).thenReturn("{\"ok\":true,\"result\":{\"message_id\":42}}");
        doReturn(supportResponse).when(supportClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        when(supportNetwork.createChannelHttpClient(any(), any())).thenAnswer(call -> {
            supportSetupMutation.run();
            return supportClient;
        });
        supportChat = spy(new DialogNotificationService(jdbc, channels, supportNetwork,
            mock(SharedConfigService.class), new BotSettingsPayloadNormalizer(), new ObjectMapper(), guard, supportAccess));
        realtime = new DialogRealtimeEventService(notices, ai, queue, routing, channels, responsibility, supportChat, stream, guard, access);
        writer = new UiEventOutboxAppendService(jdbc, guard);
        checkpoint = new RuntimeWorkerCheckpointService(jdbc);
        RuntimeCoordinationService coordination = mock(RuntimeCoordinationService.class);
        doAnswer(call -> { call.getArgument(2, Runnable.class).run(); return null; })
            .when(coordination).runWithLease(anyString(), any(Duration.class), any(Runnable.class));
        watcher = new UiEventOutboxWatcher(jdbc, realtime, coordination, checkpoint, guard, access);
        watcher.initialize();
        clearInvocations(access, observed);
    }

    static Stream<Arguments> lateInserts() {
        return Stream.of("ticket_created", "client_message_created", "feedback_created", "ticket_closed_auto")
            .flatMap(event -> Stream.of("business", "recreated", "tuple", "general", "missing", "collision")
                .map(mutation -> Arguments.of(event, mutation)));
    }

    @ParameterizedTest
    @MethodSource("lateInserts")
    void ownerChangeImmediatelyBeforeBellInsertCannotRetagOldOutboxPayload(String event, String mutation) {
        beforeInsert = () -> mutate(mutation);
        dispatch(event);
        assertNoDelivery();
        verifyNoInteractions(ai, supportChat);
        verify(access, never()).captureScope(anyString());
        assertCursorAdvanced();
        if (List.of("business", "recreated", "tuple").contains(mutation))
            assertThat(access.canReadTicket("all-reader", access.captureScope(RAW).orElseThrow())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ticket_created", "client_message_created", "ticket_closed_auto", "feedback_created",
        "client_message_edited", "operator_message_edited", "ticket_closed", "ticket_reopened"})
    void allEightCallbacksPreserveFrozenOwnerThroughBellAndSse(String event) throws Exception {
        dispatch(event);
        boolean bell = List.of("ticket_created", "client_message_created", "ticket_closed_auto", "feedback_created").contains(event);
        assertThat(count()).isEqualTo(bell ? 1 : 0);
        if (bell) {
            var row = jdbc.queryForMap("SELECT * FROM notifications");
            assertThat(row.get("ticket_user_id")).isEqualTo(scope.ticketUserId());
            assertThat(row.get("ticket_id")).isEqualTo(RAW);
            assertThat(row.get("ticket_public_id")).isEqualTo(PUBLIC);
            assertThat(row.get("ticket_scope_kind")).isEqualTo("BUSINESS");
            assertThat(row.get("ticket_business_id")).isEqualTo(1L);
            assertThat(row.get("user_identity")).isEqualTo("all-reader");
        }
        verify(publisher, atLeastOnce()).publish(isNull(), eq("dialogs_changed"), anyMap(), eq(scope));
        verify(reader, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
        verify(access, never()).captureScope(anyString());
        if ("client_message_created".equals(event)) {
            verify(ai).processScopedIncomingClientMessage(scope, "old payload", "photo", "private/key");
            verify(ai, never()).processIncomingClientMessage(anyString(), any(), any(), any());
        }
        else verifyNoInteractions(ai);
        verifyNoInteractions(repository);
        assertCursorAdvanced();
    }

    static Stream<Arguments> midCallbackCases() {
        return Stream.of("ticket_created", "client_message_created")
            .flatMap(event -> Stream.of("business", "recreated").map(mutation -> Arguments.of(event, mutation)));
    }

    @ParameterizedTest
    @MethodSource("midCallbackCases")
    void channelLookupCannotMoveTheCallbackToANewOwner(String event, String mutation) {
        when(channels.findById(9L)).thenAnswer(call -> { mutate(mutation); return Optional.of(channel); });
        dispatch(event);
        assertNoDelivery();
        verifyNoInteractions(ai, supportChat, responsibility, routing);
        assertCursorAdvanced();
    }

    @ParameterizedTest
    @MethodSource("midCallbackCases")
    void routingAndGlobalRecipientFallbackKeepTheOriginalScope(String event, String mutation) {
        when(routing.resolveScoped(any(),any(),any())).thenAnswer(call -> {
            mutate(mutation);
            return new ChannelAssignmentRoutingService.ResolvedAssignmentRouting(true,
                List.of("all-reader"),"all-reader","fixed","fixed");
        });
        when(routing.resolveRecipients(any(), any(), anyString())).thenAnswer(call -> {
            mutate(mutation);
            return new ChannelAssignmentRoutingService.ResolvedAssignmentRouting(true,
                List.of("all-reader"), "all-reader", "fixed", "fixed");
        });
        dispatch(event);
        assertNoDelivery();
        verifyNoInteractions(ai, supportChat, responsibility);
        verify(access, never()).captureScope(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dialogs_changed", "dialog_history_changed"})
    void localSseFallbackCannotRecaptureOwnerAfterPublication(String event) {
        when(publisher.publish(any(), eq(event), any(), any())).thenAnswer(call -> {
            assertThat(call.getArgument(3, UiEventTicketScope.class)).isEqualTo(scope);
            mutate("business");
            return false;
        });
        if ("dialogs_changed".equals(event)) stream.publishScopedDialogsChanged("updated", scope);
        else stream.publishScopedDialogHistoryChanged(scope, 9L, "updated");
        verifyNoInteractions(reader);
        verify(access, never()).captureScope(anyString());
    }

    @Test
    void recipientReadAndEmptyRecipientFallbackRequireTheFrozenOwner() {
        beforeChild = () -> mutate("business");
        notices.notifyScopedDialogParticipants(scope, "private preview", "/dialogs", null);
        assertNoDelivery();
        assertThat(notices.findScopedDialogRecipients(scope)).isEmpty();
        assertThat(childRowsRead).isZero();
    }

    @Test
    void recipientReadStillAppliesImmediatelyBeforeTheConditionalInsert() {
        beforeInsert = () -> jdbc.update("DELETE FROM business_access_grants WHERE user_id = 1");
        assertThat(notices.notifyScopedDialogUsers(scope, Set.of("all-reader"), "private preview", "/dialogs")).isEmpty();
        assertNoDelivery();
    }

    @Test
    void ownerChangeBetweenRecipientsCannotDeliverTheSecondNotificationInAnotherBusiness() {
        jdbc.update("INSERT INTO users VALUES (3, 'next-reader', TRUE, FALSE)");
        jdbc.update("INSERT INTO business_access_grants VALUES (3, 'ALL_BUSINESSES', 'VIEWER', TRUE)");
        afterInsert = () -> mutate("business");
        Set<String> recipients = new LinkedHashSet<>(Arrays.asList("all-reader", "next-reader"));
        assertThat(notices.notifyScopedDialogUsers(scope, recipients, "private preview", "/dialogs")).containsExactly("all-reader");
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT ticket_business_id FROM notifications", Long.class)).isEqualTo(1L);
    }

    @Test
    void scopedUnresolvedWithLocatorKeepsCompatibilityWithoutGuessingBusiness() {
        jdbc.update("UPDATE tickets SET business_id = NULL WHERE ticket_id = ?", RAW);
        UiEventTicketScope unresolved = access.captureScope(RAW).orElseThrow();
        assertThat(notices.notifyScopedDialogUsers(unresolved, Set.of("all-reader"), "unresolved", "/dialogs"))
            .containsExactly("all-reader");
        var row = jdbc.queryForMap("SELECT * FROM notifications");
        assertThat(row.get("ticket_scope_kind")).isEqualTo("UNRESOLVED");
        assertThat(row.get("ticket_business_id")).isNull();
        assertThat(row.get("ticket_public_id")).isEqualTo(PUBLIC);
        assertThat(notices.findScopedDialogRecipients(unresolved)).containsExactly("all-reader");
        clearInvocations(access);
        stream.publishScopedDialogsChanged("updated", unresolved);
        verify(publisher).publish(isNull(), eq("dialogs_changed"), anyMap(), eq(unresolved));
        verify(access, never()).captureScope(anyString());
    }

    @Test
    void strictReadDeniesScopedUnresolvedWithoutFallingBackToLegacyWriter() {
        jdbc.update("UPDATE tickets SET business_id = NULL WHERE ticket_id = ?", RAW);
        UiEventTicketScope unresolved = access.captureScope(RAW).orElseThrow();
        flags.setStrictReadScope(true);
        assertThat(notices.notifyScopedDialogUsers(unresolved, Set.of("all-reader"), "unresolved", "/dialogs")).isEmpty();
        stream.publishScopedDialogsChanged("updated", unresolved);
        assertNoDelivery();
    }

    static Stream<UiEventTicketScope> invalidScopes() {
        return Stream.of(null, new UiEventTicketScope(10L, RAW, null, "BUSINESS", 1L),
            new UiEventTicketScope(10L, RAW, PUBLIC, "GENERAL", null),
            new UiEventTicketScope(10L, RAW, PUBLIC, "BUSINESS", null),
            new UiEventTicketScope(10L, " ", PUBLIC, "BUSINESS", 1L));
    }

    @ParameterizedTest
    @MethodSource("invalidScopes")
    void invalidExpectedScopeNeverEntersTheLegacyPath(UiEventTicketScope invalid) {
        realtime.handleScopedTicketCreated(invalid, 9L, "preview");
        realtime.handleScopedIncomingClientMessage(invalid, 9L, "message", "text", null, true);
        realtime.handleScopedClientMessageEdited(invalid, 9L);
        realtime.handleScopedOperatorMessageEdited(invalid, 9L);
        realtime.handleScopedFeedbackCreated(invalid, 5);
        realtime.handleScopedTicketAutoClosed(invalid, 9L, "text");
        realtime.handleScopedTicketClosed(invalid, 9L);
        realtime.handleScopedTicketReopened(invalid, 9L);
        assertThat(notices.notifyScopedDialogUsers(invalid, Set.of("all-reader"), "text", "/dialogs")).isEmpty();
        notices.notifyScopedDialogParticipants(invalid, "text", "/dialogs", null);
        assertThat(notices.findScopedDialogRecipients(invalid)).isEmpty();
        assertThat(queue.notifyScopedQueueForNewPublicAppealRecipients(channel, invalid, "text")).isEmpty();
        assertThat(queue.notifyScopedIncomingClientMessage(channel, invalid, "text")).isFalse();
        stream.publishScopedDialogsChanged("updated", invalid);
        stream.publishScopedDialogHistoryChanged(invalid, 9L, "updated");
        verifyNoInteractions(observed, channels, routing, ai, responsibility, supportChat, publisher, reader, repository);
    }

    private void dispatch(String event) {
        writer.append(event, RAW, 9L, "old payload", "photo", "private/key", 5);
        watcher.watch();
    }

    private void enableAutoAssignment() {
        jdbc.update("DELETE FROM ticket_responsibles WHERE ticket_id=?", RAW);
        jdbc.update("UPDATE business_access_grants SET role_code='OPERATOR' WHERE user_id=1");
        when(routing.resolveRecipients(any(), any(), anyString())).thenReturn(
            new ChannelAssignmentRoutingService.ResolvedAssignmentRouting(true,
                List.of("all-reader"), "all-reader", "single_operator", "round_robin"));
        when(routing.resolveScoped(any(),any(),any())).thenReturn(
            new ChannelAssignmentRoutingService.ResolvedAssignmentRouting(true,
                List.of("all-reader"),"all-reader","single_operator","round_robin"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"telegram", "vk", "max"})
    void outboxAutoClosePreservesScopeAndUsesCanonicalDestinationNotTheMutableJpaChannel(String platform) throws Exception {
        jdbc.update("UPDATE channels SET platform=? WHERE id=9", platform);
        channel.setToken("stale-jpa-token");
        channel.setSupportChatId("999");
        channel.setPlatform("unknown");
        dispatch("ticket_closed_auto");
        verify(supportChat).notifyScopedSupportChat(scope, 9L, notices.formatAutoCloseText(RAW));
        verify(supportChat, never()).notifySupportChat(any(), anyString());
        verify(supportAccess).captureTarget(scope, 9L);
        verify(supportClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(access, never()).captureScope(anyString());
        assertThat(count()).isEqualTo(1);
        assertCursorAdvanced();
    }

    static Stream<Arguments> supportHandoffCases() {
        return Stream.of("telegram", "vk", "max").flatMap(platform ->
            Stream.of("business", "destination").map(mutation -> Arguments.of(platform, mutation)));
    }

    @ParameterizedTest
    @MethodSource("supportHandoffCases")
    void lateSupportChangeCannotRetargetOldAutoCloseOrRepeatTheValidBell(String platform, String mutation) {
        jdbc.update("UPDATE channels SET platform=? WHERE id=9", platform);
        supportSetupMutation = () -> {
            if ("business".equals(mutation)) mutate("business");
            else jdbc.update("UPDATE channels SET support_chat_id='999' WHERE id=9");
        };
        dispatch("ticket_closed_auto");
        verifyNoInteractions(supportClient);
        verify(supportChat, never()).notifySupportChat(any(), anyString());
        verify(access, never()).captureScope(anyString());
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT ticket_business_id FROM notifications", Long.class)).isEqualTo(1L);
        assertCursorAdvanced();
    }

    @ParameterizedTest
    @ValueSource(strings = {"business", "recreated", "tuple", "general", "missing", "collision"})
    void supportTargetSqlRejectsOwnerChangeInsideTheCallback(String mutation) {
        doAnswer(call -> {
            mutate(mutation);
            return call.callRealMethod();
        }).when(supportAccess).captureTarget(scope, 9L);
        dispatch("ticket_closed_auto");
        verifyNoInteractions(supportNetwork, supportClient);
        verify(supportChat, never()).notifySupportChat(any(), anyString());
        assertThat(count()).isEqualTo(1);
        assertCursorAdvanced();
    }

    @Test
    void autoCloseCannotUseAnOutboxChannelDifferentFromTheCanonicalOwnerChannel() {
        jdbc.update("UPDATE tickets SET channel_id=8 WHERE ticket_id=?", RAW);
        dispatch("ticket_closed_auto");
        verify(supportChat).notifyScopedSupportChat(scope, 9L, notices.formatAutoCloseText(RAW));
        verifyNoInteractions(supportNetwork, supportClient);
        assertThat(count()).isEqualTo(1);
        assertCursorAdvanced();
    }

    @Test
    void outboxAutoAssignmentCarriesTheOriginalScopeToTheRealConditionalInsert() {
        enableAutoAssignment();
        dispatch("ticket_created");
        verify(responsibility).assignScopedResponsibleIfMissing(scope, "all-reader", "channel_assignment_routing");
        verify(responsibility, never()).assignResponsibleIfMissing(anyString(), anyString(), anyString());
        assertThat(jdbc.queryForMap("SELECT responsible,assigned_by FROM ticket_responsibles WHERE ticket_id=?", RAW))
            .containsEntry("responsible", "all-reader").containsEntry("assigned_by", "channel_assignment_routing");
        assertThat(count()).isEqualTo(1);
        verify(publisher).publish(isNull(), eq("dialogs_changed"), anyMap(), eq(scope));
        verify(access, never()).captureScope(anyString());
        assertCursorAdvanced();
    }

    @ParameterizedTest
    @ValueSource(strings = {"business", "recreated", "tuple", "general", "missing", "collision"})
    void ownerChangeAtAssignmentSqlCannotAssignReplacementOrContinueOldDelivery(String mutation) {
        enableAutoAssignment();
        beforeAssignment = () -> mutate(mutation);
        dispatch("ticket_created");
        verify(responsibility).assignScopedResponsibleIfMissing(scope, "all-reader", "channel_assignment_routing");
        verify(responsibility, never()).assignResponsibleIfMissing(anyString(), anyString(), anyString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket_responsibles WHERE ticket_id=?", Long.class, RAW)).isZero();
        assertNoDelivery();
        verify(access, never()).captureScope(anyString());
        assertCursorAdvanced();
    }

    @Test
    void changeAfterSuccessfulAssignmentDoesNotRetagTheFollowingBellOrSse() {
        enableAutoAssignment();
        afterAssignment = () -> mutate("business");
        dispatch("ticket_created");
        // The first statement was valid; subsequent effects must independently reject the old owner.
        assertThat(jdbc.queryForObject("SELECT responsible FROM ticket_responsibles WHERE ticket_id=?", String.class, RAW))
            .isEqualTo("all-reader");
        assertNoDelivery();
        verify(access, never()).captureScope(anyString());
        assertCursorAdvanced();
    }

    @Test
    void readOnlyAssigneeDenialDoesNotDisableTheIndependentlyAuthorizedBell() {
        enableAutoAssignment();
        jdbc.update("UPDATE business_access_grants SET role_code='VIEWER' WHERE user_id=1");
        dispatch("ticket_created");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket_responsibles WHERE ticket_id=?", Long.class, RAW)).isZero();
        assertThat(count()).isEqualTo(1);
        verify(responsibility, never()).assignResponsibleIfMissing(anyString(), anyString(), anyString());
        assertCursorAdvanced();
    }

    private void mutate(String mutation) {
        switch (mutation) {
            case "business" -> jdbc.update("UPDATE tickets SET business_id = 2 WHERE ticket_id = ?", RAW);
            case "general" -> jdbc.update("UPDATE tickets SET business_id = NULL, is_general = TRUE WHERE ticket_id = ?", RAW);
            case "missing", "recreated" -> {
                jdbc.update("DELETE FROM tickets WHERE ticket_id = ?", RAW);
                if ("recreated".equals(mutation)) jdbc.update("INSERT INTO tickets VALUES (10, ?, ?, FALSE, 2)", RAW, "c".repeat(32));
            }
            case "tuple" -> jdbc.update("UPDATE tickets SET user_id = 99 WHERE ticket_id = ?", RAW);
            case "collision" -> jdbc.update("INSERT INTO tickets VALUES (99, ?, ?, FALSE, 2)", RAW, "c".repeat(32));
            default -> throw new IllegalArgumentException(mutation);
        }
    }

    private long count() { return jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Long.class); }

    private void assertNoDelivery() {
        assertThat(count()).isZero();
        verifyNoInteractions(publisher, reader, repository);
    }

    private void assertCursorAdvanced() {
        assertThat(checkpoint.readLongCursor("ui-event-outbox-watch"))
            .isEqualTo(Optional.of(jdbc.queryForObject("SELECT MAX(id) FROM ui_event_outbox", Long.class)));
    }
}
