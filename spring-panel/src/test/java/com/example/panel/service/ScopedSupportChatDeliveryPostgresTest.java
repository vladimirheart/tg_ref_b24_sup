package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class ScopedSupportChatDeliveryPostgresTest {
    private static final String RAW = " support/Ю?% ";
    private static final String PUBLIC = "a".repeat(32);
    private static final String PREVIEW = "private preview Ю +";
    private static final String CONFIG = "{\"base_url\":\"https://scoped.invalid\"}";

    private static class Fixture {
        final JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("scoped_support_delivery");
        final MultibusinessFeatureFlags flags = new MultibusinessFeatureFlags();
        final SupportChatDeliveryAccessService access = spy(new SupportChatDeliveryAccessService(jdbc, flags));
        final ChannelRepository channels = mock(ChannelRepository.class);
        final IntegrationNetworkService network = mock(IntegrationNetworkService.class);
        final SharedConfigService settings = mock(SharedConfigService.class);
        final LegacyTicketIdJdbcGuard guard = mock(LegacyTicketIdJdbcGuard.class);
        final HttpClient client = mock(HttpClient.class);
        final ObjectMapper mapper = spy(new ObjectMapper());
        final DialogNotificationService service;
        final UiEventTicketScope scope;
        Runnable setupMutation = () -> {};

        @SuppressWarnings("unchecked")
        Fixture(String platform, String raw) throws Exception {
            jdbc.execute("CREATE TABLE businesses(id BIGINT PRIMARY KEY, status TEXT)");
            jdbc.execute("CREATE TABLE channels(id BIGINT PRIMARY KEY, token TEXT, support_chat_id TEXT, platform TEXT, "
                + "platform_config TEXT, delivery_settings TEXT, credential_id BIGINT, is_active BOOLEAN)");
            jdbc.execute("CREATE TABLE tickets(user_id BIGINT, ticket_id TEXT, ticket_public_id TEXT, business_id BIGINT, "
                + "is_general BOOLEAN, channel_id BIGINT, PRIMARY KEY(user_id,ticket_id))");
            jdbc.update("INSERT INTO businesses VALUES (1,'active'),(2,'active')");
            jdbc.update("INSERT INTO channels VALUES (9,'token-9','42',?,?,NULL,NULL,TRUE),"
                + "(8,'other-token','88','telegram',NULL,NULL,NULL,TRUE)", platform, CONFIG);
            jdbc.update("INSERT INTO tickets VALUES (10,?,?,1,FALSE,9)", raw, PUBLIC);
            if (!raw.equals(raw.trim()))
                jdbc.update("INSERT INTO tickets VALUES (20,?,?,2,FALSE,8)", raw.trim(), "b".repeat(32));
            scope = new UiEventTicketScope(10L, raw, PUBLIC, "BUSINESS", 1L);
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn("{\"ok\":true,\"result\":{\"message_id\":42}}");
            doReturn(response).when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
            when(network.createChannelHttpClient(any(Channel.class), any(Duration.class))).thenAnswer(call -> {
                setupMutation.run();
                // Mutating an adapter's detached copy must not mutate the frozen request destination.
                Channel copy = call.getArgument(0);
                copy.setToken("mutated-adapter-token");
                copy.setPlatformConfig("{\"base_url\":\"https://mutated.invalid\"}");
                return client;
            });
            service = spy(new DialogNotificationService(jdbc, channels, network, settings,
                new BotSettingsPayloadNormalizer(), mapper, guard, access));
        }

        boolean send() { return service.notifyScopedSupportChat(scope, 9L, PREVIEW); }

        void assertNoLegacyFallback() {
            verify(service, never()).notifySupportChat(any(), anyString());
            verifyNoInteractions(channels, settings, guard);
        }
    }

    static Stream<Arguments> validCases() {
        return Stream.of("telegram", "vk", "max").flatMap(platform ->
            Stream.of("T", " T ", "T/100", RAW, "T%2F+", " null ").map(raw -> Arguments.of(platform, raw)));
    }

    @ParameterizedTest
    @MethodSource("validCases")
    void immutableDestinationPreservesPayloadForAllProvidersAndExactIds(String platform, String raw) throws Exception {
        Fixture f = new Fixture(platform, raw);
        assertThat(f.send()).isTrue();
        verify(f.access).captureTarget(f.scope, 9L);
        var target = f.access.captureTarget(f.scope, 9L).orElseThrow();
        verify(f.access).isCurrentTarget(f.scope, target);
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(f.client).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = captor.getValue();
        assertThat(request.method()).isEqualTo("POST");
        String body = body(request);
        switch (platform) {
            case "telegram" -> {
                assertThat(request.uri().toString()).isEqualTo("https://scoped.invalid/bottoken-9/sendMessage");
                assertThat(f.mapper.readTree(body).path("chat_id").longValue()).isEqualTo(42);
                assertThat(f.mapper.readTree(body).path("text").asText()).isEqualTo(PREVIEW);
            }
            case "vk" -> {
                assertThat(request.uri().toString()).isEqualTo("https://api.vk.com/method/messages.send");
                assertThat(URLDecoder.decode(body, StandardCharsets.UTF_8))
                    .contains("peer_id=42", "message=" + PREVIEW, "access_token=token-9");
            }
            case "max" -> {
                assertThat(request.uri().toString()).isEqualTo("https://platform-api.max.ru/messages?user_id=42");
                assertThat(request.headers().firstValue("Authorization")).contains("token-9");
                assertThat(f.mapper.readTree(body).path("text").asText()).isEqualTo(PREVIEW);
            }
            default -> throw new IllegalArgumentException(platform);
        }
        assertThat(body).doesNotContain("ticketUserId", "ticketPublicId", "businessId", "mutated-adapter-token");
        f.assertNoLegacyFallback();
    }

    static Stream<Arguments> lateCases() {
        return Stream.of("telegram", "vk", "max").flatMap(platform ->
            Stream.of("business", "recreated", "tuple", "general", "missing", "collision", "locator", "ticket-channel",
                "archived-business", "inactive-channel", "missing-channel", "token", "chat", "platform",
                "platform-config", "delivery-settings", "credential")
                .map(mutation -> Arguments.of(platform, mutation)));
    }

    @ParameterizedTest
    @MethodSource("lateCases")
    void networkSetupCannotMoveOldPayloadToChangedOwnerOrDestination(String platform, String mutation) throws Exception {
        Fixture f = new Fixture(platform, RAW);
        f.setupMutation = () -> mutate(f, mutation);
        assertThat(f.send()).isFalse();
        verifyNoInteractions(f.client);
        verify(f.access).isCurrentTarget(eq(f.scope), any());
        f.assertNoLegacyFallback();
    }

    @ParameterizedTest
    @ValueSource(strings = {"business", "recreated", "tuple", "general", "missing", "collision", "locator", "ticket-channel",
        "archived-business", "inactive-channel", "missing-channel"})
    void targetCaptureRejectsStaleOwnerAndNeverLoadsAReplacement(String mutation) throws Exception {
        Fixture f = new Fixture("telegram", RAW);
        mutate(f, mutation);
        assertThat(f.send()).isFalse();
        verifyNoInteractions(f.network, f.client);
        f.assertNoLegacyFallback();
    }

    @ParameterizedTest
    @ValueSource(strings = {"telegram", "max"})
    void finalOwnerProofRunsAfterJsonSerialization(String platform) throws Exception {
        Fixture f = new Fixture(platform, RAW);
        doAnswer(call -> {
            Object result = call.callRealMethod();
            mutate(f, "business");
            return result;
        }).when(f.mapper).writeValueAsString(any());
        assertThat(f.send()).isFalse();
        verifyNoInteractions(f.client);
        f.assertNoLegacyFallback();
    }

    @Test
    void finalOwnerProofRunsAfterLegacyTelegramMirrorResolution() throws Exception {
        Fixture f = new Fixture("telegram", RAW);
        f.jdbc.update("UPDATE channels SET platform_config=NULL WHERE id=9");
        when(f.network.resolveTelegramLegacyBotApiBaseUrl(any())).thenAnswer(call -> {
            mutate(f, "business");
            return "https://mirror.invalid";
        });
        assertThat(f.send()).isFalse();
        verifyNoInteractions(f.client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"telegram", "vk", "max"})
    void unresolvedIsNeverGeneralAndStrictFlagRevocationIsAppliedAtHandoff(String platform) throws Exception {
        Fixture f = new Fixture(platform, RAW);
        f.jdbc.update("UPDATE tickets SET business_id=NULL WHERE ticket_id=?", RAW);
        UiEventTicketScope unresolved = new UiEventTicketScope(10L, RAW, PUBLIC, "UNRESOLVED", null);
        assertThat(f.service.notifyScopedSupportChat(unresolved, 9L, PREVIEW)).isTrue();
        f.setupMutation = () -> f.flags.setStrictReadScope(true);
        assertThat(f.service.notifyScopedSupportChat(unresolved, 9L, PREVIEW)).isFalse();
        // Exactly one send: strict mode cannot be bypassed by an already captured destination.
        verify(f.client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        f.assertNoLegacyFallback();
    }

    @Test
    void generalTransitionWithUnchangedNullableBusinessIsDenied() throws Exception {
        Fixture f = new Fixture("telegram", RAW);
        f.jdbc.update("UPDATE tickets SET business_id=NULL WHERE ticket_id=?", RAW);
        UiEventTicketScope unresolved = new UiEventTicketScope(10L, RAW, PUBLIC, "UNRESOLVED", null);
        f.setupMutation = () -> f.jdbc.update("UPDATE tickets SET is_general=TRUE WHERE ticket_id=?", RAW);
        assertThat(f.service.notifyScopedSupportChat(unresolved, 9L, PREVIEW)).isFalse();
        verifyNoInteractions(f.client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "blank", " VK ", "MAX"})
    void supportedLegacyPlatformSpellingsAndNullableFieldsRemainProvable(String platform) throws Exception {
        Fixture f = new Fixture("null".equals(platform) ? null : "blank".equals(platform) ? " " : platform, RAW);
        f.jdbc.update("UPDATE channels SET platform_config=NULL,delivery_settings=NULL,credential_id=NULL WHERE id=9");
        assertThat(f.send()).isTrue();
        verify(f.client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown-platform", "empty-token", "empty-chat", "invalid-chat", "vk-negative-chat", "vk-too-large"})
    void invalidDestinationCannotReachAnyHttpTransport(String invalid) throws Exception {
        Fixture f = new Fixture("telegram", RAW);
        switch (invalid) {
            case "unknown-platform" -> f.jdbc.update("UPDATE channels SET platform='unknown' WHERE id=9");
            case "empty-token" -> f.jdbc.update("UPDATE channels SET token=' ' WHERE id=9");
            case "empty-chat" -> f.jdbc.update("UPDATE channels SET support_chat_id=' ' WHERE id=9");
            case "invalid-chat" -> f.jdbc.update("UPDATE channels SET support_chat_id='not-a-chat' WHERE id=9");
            case "vk-negative-chat" -> f.jdbc.update("UPDATE channels SET platform='vk',support_chat_id='-42' WHERE id=9");
            case "vk-too-large" -> f.jdbc.update("UPDATE channels SET platform='vk',support_chat_id='2147483648' WHERE id=9");
            default -> throw new IllegalArgumentException(invalid);
        }
        assertThat(f.send()).isFalse();
        verifyNoInteractions(f.network, f.client);
        f.assertNoLegacyFallback();
    }

    static Stream<UiEventTicketScope> invalidScopes() {
        return Stream.of(null, new UiEventTicketScope(10L, RAW, null, "BUSINESS", 1L),
            new UiEventTicketScope(10L, RAW, PUBLIC, "GENERAL", null),
            new UiEventTicketScope(10L, RAW, PUBLIC, "BUSINESS", null),
            new UiEventTicketScope(10L, RAW, PUBLIC, "UNRESOLVED", 1L),
            new UiEventTicketScope(null, RAW, PUBLIC, "BUSINESS", 1L),
            new UiEventTicketScope(10L, " ", PUBLIC, "BUSINESS", 1L));
    }

    @ParameterizedTest
    @MethodSource("invalidScopes")
    void malformedScopeNeverFallsBackToRawChannelOrIdentity(UiEventTicketScope invalid) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SupportChatDeliveryAccessService access = new SupportChatDeliveryAccessService(jdbc, new MultibusinessFeatureFlags());
        IntegrationNetworkService network = mock(IntegrationNetworkService.class);
        ChannelRepository channels = mock(ChannelRepository.class);
        DialogNotificationService service = new DialogNotificationService(jdbc, channels, network,
            mock(SharedConfigService.class), new BotSettingsPayloadNormalizer(), new ObjectMapper(),
            mock(LegacyTicketIdJdbcGuard.class), access);
        assertThat(service.notifyScopedSupportChat(invalid, 9L, PREVIEW)).isFalse();
        assertThat(access.captureTarget(invalid, 9L)).isEmpty();
        assertThat(access.isCurrentTarget(invalid, new SupportChatDeliveryAccessService.Target(
            9L, "token-9", "42", "telegram", null, null, null))).isFalse();
        verifyNoInteractions(jdbc, network, channels);
    }

    @Test
    void legacyOnlyConstructionAndMissingChannelOrTextFailClosed() throws Exception {
        Fixture f = new Fixture("telegram", RAW);
        DialogNotificationService legacy = new DialogNotificationService(f.jdbc, f.channels, f.network,
            f.settings, new BotSettingsPayloadNormalizer(), f.mapper, f.guard);
        assertThat(legacy.notifyScopedSupportChat(f.scope, 9L, PREVIEW)).isFalse();
        assertThat(f.service.notifyScopedSupportChat(f.scope, null, PREVIEW)).isFalse();
        assertThat(f.service.notifyScopedSupportChat(f.scope, 9L, " ")).isFalse();
        assertThat(f.access.captureTarget(f.scope, null)).isEmpty();
        assertThat(f.access.isCurrentTarget(f.scope, null)).isFalse();
        verify(f.access, never()).captureTarget(any(), anyLong());
        verifyNoInteractions(f.network, f.client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tickets", "channels", "businesses", "locator"})
    void missingSchemaDeniesCaptureAndFinalHandoff(String missing) throws Exception {
        Fixture f = new Fixture("telegram", RAW);
        var target = f.access.captureTarget(f.scope, 9L).orElseThrow();
        if ("locator".equals(missing)) f.jdbc.execute("ALTER TABLE tickets DROP COLUMN ticket_public_id");
        else f.jdbc.execute("DROP TABLE " + missing);
        assertThatNoException().isThrownBy(() -> {
            assertThat(f.send()).isFalse();
            assertThat(f.access.isCurrentTarget(f.scope, target)).isFalse();
        });
        verifyNoInteractions(f.network, f.client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"telegram", "vk", "max"})
    void providerIoFailureDoesNotTriggerRawOrSecondDelivery(String platform) throws Exception {
        Fixture f = new Fixture(platform, RAW);
        doAnswer(call -> { throw new IOException("mock transport failure"); })
            .when(f.client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        assertThat(f.send()).isFalse();
        verify(f.client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        f.assertNoLegacyFallback();
    }

    @Test
    void targetDebugStringDoesNotExposeCredentialsOrPlatformConfiguration() throws Exception {
        Fixture f = new Fixture("telegram", RAW);
        assertThat(f.access.captureTarget(f.scope, 9L).orElseThrow().toString())
            .isEqualTo("SupportChatTarget[channelId=9]");
    }

    @Test
    void invalidTelegramUriFailsClosedWithoutLeakingTheTokenThroughAnException() throws Exception {
        Fixture f = new Fixture("telegram", RAW);
        f.jdbc.update("UPDATE channels SET platform_config='{\"base_url\":\"not a valid URI\"}' WHERE id=9");
        assertThatNoException().isThrownBy(() -> assertThat(f.send()).isFalse());
        verifyNoInteractions(f.client);
        f.assertNoLegacyFallback();
    }

    @ParameterizedTest
    @ValueSource(strings = {"telegram", "vk", "max"})
    void networkConfigurationFailureCannotEscapeOrUseTheRawSender(String platform) throws Exception {
        Fixture f = new Fixture(platform, RAW);
        f.setupMutation = () -> { throw new IllegalStateException("mock secret token-9"); };
        assertThatNoException().isThrownBy(() -> assertThat(f.send()).isFalse());
        verifyNoInteractions(f.client);
        f.assertNoLegacyFallback();
    }

    private static void mutate(Fixture f, String mutation) {
        switch (mutation) {
            case "business" -> f.jdbc.update("UPDATE tickets SET business_id=2 WHERE ticket_id=?", RAW);
            case "tuple" -> f.jdbc.update("UPDATE tickets SET user_id=99 WHERE ticket_id=?", RAW);
            case "general" -> f.jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE WHERE ticket_id=?", RAW);
            case "missing", "recreated" -> {
                f.jdbc.update("DELETE FROM tickets WHERE ticket_id=?", RAW);
                if ("recreated".equals(mutation)) f.jdbc.update("INSERT INTO tickets VALUES (10,?,?,1,FALSE,9)", RAW, "c".repeat(32));
            }
            case "collision" -> f.jdbc.update("INSERT INTO tickets VALUES (99,?,?,2,FALSE,9)", RAW, "c".repeat(32));
            case "locator" -> f.jdbc.update("UPDATE tickets SET ticket_public_id=NULL WHERE ticket_id=?", RAW);
            case "ticket-channel" -> f.jdbc.update("UPDATE tickets SET channel_id=8 WHERE ticket_id=?", RAW);
            case "archived-business" -> f.jdbc.update("UPDATE businesses SET status='archived' WHERE id=1");
            case "inactive-channel" -> f.jdbc.update("UPDATE channels SET is_active=FALSE WHERE id=9");
            case "missing-channel" -> f.jdbc.update("DELETE FROM channels WHERE id=9");
            case "token" -> f.jdbc.update("UPDATE channels SET token='replacement-token' WHERE id=9");
            case "chat" -> f.jdbc.update("UPDATE channels SET support_chat_id='99' WHERE id=9");
            case "platform" -> f.jdbc.update("UPDATE channels SET platform=NULL WHERE id=9");
            case "platform-config" -> f.jdbc.update("UPDATE channels SET platform_config=NULL WHERE id=9");
            case "delivery-settings" -> f.jdbc.update("UPDATE channels SET delivery_settings='{}' WHERE id=9");
            case "credential" -> f.jdbc.update("UPDATE channels SET credential_id=77 WHERE id=9");
            default -> throw new IllegalArgumentException(mutation);
        }
    }

    private static String body(HttpRequest request) throws Exception {
        CompletableFuture<String> result = new CompletableFuture<>();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) {
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            public void onError(Throwable error) { result.completeExceptionally(error); }
            public void onComplete() { result.complete(bytes.toString(StandardCharsets.UTF_8)); }
        });
        return result.get(5, TimeUnit.SECONDS);
    }
}
