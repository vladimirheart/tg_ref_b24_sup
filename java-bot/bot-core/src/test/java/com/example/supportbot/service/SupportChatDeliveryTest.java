package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import com.example.supportbot.config.IntegrationPanelApiProperties;
import com.example.supportbot.entity.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Actual signed panel boundary; no provider, working database or external network. */
class SupportChatDeliveryTest {
    HttpServer server;
    Channel channel;
    String platform, recipient, apiToken;
    AtomicReference<String> providerToken = new AtomicReference<>("r294-fake-provider-token");
    AtomicReference<Throwable> failure = new AtomicReference<>();
    AtomicInteger requests = new AtomicInteger();
    AtomicBoolean allowed = new AtomicBoolean(true);
    List<String> deliveries = new ArrayList<>();
    SupportChatDeliveryService service;

    static Stream<Arguments> decisions() {
        return Stream.of("telegram", "vk", "max").flatMap(p -> Stream.of("allow", "deny", "sender-deny",
            "old-backend", "missing-recipient", "wrong-recipient", "backend-rebind", "local-rebind",
            "rotate-token", "disable-channel").map(change -> Arguments.of(p, change)));
    }

    void start(String platform, String change) throws Exception {
        this.platform = platform;
        recipient = platform.equals("vk") ? "2000000011" : "-1001234567890";
        apiToken = "iguana-child-v1:" + platform + ":22:" + "a".repeat(64);
        channel = new Channel(); channel.setId(22L); channel.setPlatform(platform); channel.setActive(true);
        channel.setSupportChatId(recipient);
        var mapper = new ObjectMapper();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/api/bot/outbound/", exchange -> {
            requests.incrementAndGet(); int status = 200; String body;
            try {
                byte[] bytes = exchange.getRequestBody().readAllBytes();
                String route = exchange.getRequestURI().getRawPath();
                boolean chat = route.endsWith("authorize-support-chat");
                assertThat(exchange.getRequestHeaders().getFirst("X-Iguana-Bot-Api-Token")).isEqualTo(apiToken);
                assertThat(exchange.getRequestHeaders().getFirst("X-Iguana-Idempotency-Key")).isNull();
                String timestamp = exchange.getRequestHeaders().getFirst("X-Iguana-Request-Timestamp");
                var mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(apiToken.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                assertThat(exchange.getRequestHeaders().getFirst("X-Iguana-Request-Signature"))
                    .isEqualTo(HexFormat.of().formatHex(mac.doFinal(("POST\n" + route + "\n" + timestamp + "\n" + digest).getBytes(StandardCharsets.UTF_8))));
                var request = mapper.readTree(bytes);
                var sender = chat ? request.path("sender") : request;
                assertThat(sender.path("channelId").longValue()).isEqualTo(22L);
                assertThat(sender.path("platform").asText()).isEqualTo(platform);
                assertThat(sender.path("tokenSha256").asText()).isEqualTo(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest("r294-fake-provider-token".getBytes(StandardCharsets.UTF_8))));
                if (chat) assertThat(request.path("supportChatId").asText()).isEqualTo(recipient);
                boolean decision = allowed.get() && !(chat && Set.of("deny", "backend-rebind").contains(change))
                    && !(!chat && change.equals("sender-deny"));
                body = "{\"channelId\":22,\"platform\":\"" + platform + "\",\"allowed\":" + decision
                    + (chat && !change.equals("missing-recipient") ? ",\"supportChatId\":\"" + (change.equals("wrong-recipient") ? "123" : recipient) + "\"" : "") + "}";
                if (chat) switch (change) {
                    case "old-backend" -> status = 404;
                    case "local-rebind" -> channel.setSupportChatId("123");
                    case "rotate-token" -> providerToken.set("rotated");
                    case "disable-channel" -> channel.setActive(false);
                }
            } catch (Throwable error) { failure.set(error); status = 500; body = "{}"; }
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        }); server.start();
        var properties = new IntegrationPanelApiProperties(); properties.setToken(apiToken);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        var authorization = new PanelOutboundSenderAuthorizationClient(properties, mapper, new PanelApiRequestHeadersFactory(properties));
        service = new SupportChatDeliveryService(new BotRuntimeSenderIdentity(22, platform), authorization);
    }

    @AfterEach void stop() { if (server != null) server.stop(0); assertThat(failure.get()).isNull(); }

    @ParameterizedTest @MethodSource("decisions")
    void exactSignedDecisionsPrecedeEachProviderSend(String platform, String change) throws Exception {
        start(platform, change); var target = service.prepare(channel, platform, providerToken::get); assertThat(target).isNotNull();
        if (Set.of("old-backend", "missing-recipient", "wrong-recipient").contains(change))
            assertThatThrownBy(() -> target.send(deliveries::add)).isInstanceOf(IllegalStateException.class);
        else assertThat(target.send(deliveries::add)).isEqualTo(change.equals("allow"));
        assertThat(deliveries).containsExactly(change.equals("allow") ? new String[]{recipient} : new String[0]);
        assertThat(requests.get()).isEqualTo(change.equals("sender-deny") ? 1 : 2);
    }

    @ParameterizedTest @ValueSource(strings = {"telegram", "vk", "max"})
    void previousAllowDoesNotSurviveRevocationBetweenMaterializationAndSend(String platform) throws Exception {
        start(platform, "allow"); var target = service.prepare(channel, platform, providerToken::get);
        assertThat(target.authorize()).isTrue(); allowed.set(false);
        assertThat(target.send(deliveries::add)).isFalse(); assertThat(deliveries).isEmpty();
        assertThat(requests.get()).isEqualTo(3);
    }

    @ParameterizedTest @ValueSource(strings = {"recipient", "channel", "platform", "inactive", "token"})
    void pinnedTargetCannotFollowLocalChanges(String change) throws Exception {
        start("telegram", "allow"); var target = service.prepare(channel, platform, providerToken::get);
        switch (change) {
            case "recipient" -> channel.setSupportChatId("123");
            case "channel" -> channel.setId(23L);
            case "platform" -> channel.setPlatform("vk");
            case "inactive" -> channel.setActive(false);
            case "token" -> providerToken.set("rotated");
        }
        assertThat(target.send(deliveries::add)).isFalse(); assertThat(deliveries).isEmpty(); assertThat(requests.get()).isZero();
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"0", " 12", "12 ", "+12", "0012", "-0", "not-a-chat", "9223372036854775808"})
    void missingOrNoncanonicalRecipientNeverFallsBackToChannelId(String recipient) throws Exception {
        start("telegram", "allow"); channel.setSupportChatId(recipient);
        assertThat(service.prepare(channel, platform, providerToken::get)).isNull(); assertThat(requests.get()).isZero();
    }
}
