package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.supportbot.config.IntegrationPanelApiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PanelChannelClientScopeTest {
    private static final long CHANNEL = 9_007_199_254_740_993L;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private HttpServer server;
    private int status = 200;
    private long responseChannel = CHANNEL;
    private String responsePlatform;

    private PanelChannelClient client(long channelId, String platform) throws Exception {
        responsePlatform = platform;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/api/bot/channels/resolve", exchange -> {
            requests.incrementAndGet();
            try {
                byte[] body = exchange.getRequestBody().readAllBytes();
                var json = mapper.readTree(body);
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                assertThat(json.path("channelId").longValue()).isEqualTo(CHANNEL);
                assertThat(json.path("platform").textValue()).isEqualTo(platform);
                assertThat(json.path("token").textValue()).isEqualTo("provider-test-token");
                assertThat(exchange.getRequestHeaders().getFirst("X-Iguana-Bot-Api-Token")).isEqualTo("child-test-secret");
                String timestamp = exchange.getRequestHeaders().getFirst("X-Iguana-Request-Timestamp");
                String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec("child-test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                String canonical = "POST\n/internal/api/bot/channels/resolve\n" + timestamp + "\n" + digest;
                assertThat(exchange.getRequestHeaders().getFirst("X-Iguana-Request-Signature"))
                    .isEqualTo(HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
            } catch (Throwable error) {
                failure.set(error);
            }
            byte[] response = ("{\"id\":" + responseChannel + ",\"platform\":\"" + responsePlatform
                + "\",\"active\":true,\"publicId\":\"own-channel\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        var properties = new IntegrationPanelApiProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setToken("child-test-secret");
        return new PanelChannelClient(properties, mapper, new PanelApiRequestHeadersFactory(properties),
            new BotRuntimeSenderIdentity(channelId, platform));
    }

    @AfterEach void stop() {
        if (server != null) server.stop(0);
        assertThat(failure.get()).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"telegram", "vk", "max"})
    void tokenOnlyAndExplicitLookupsSendSignedLauncherChannel(String platform) throws Exception {
        var client = client(CHANNEL, platform);
        assertThat(client.resolveConfiguredChannel(null, "provider-test-token", "Test", platform))
            .hasValueSatisfying(channel -> assertThat(channel.getId()).isEqualTo(CHANNEL));
        assertThat(client.resolveConfiguredChannel(CHANNEL, "provider-test-token", "Test", platform)).isPresent();
        assertThat(requests.get()).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(strings = {"telegram", "vk", "max"})
    void foreignChannelOrPlatformNeverContactsPanel(String platform) throws Exception {
        var client = client(CHANNEL, platform);
        assertThat(client.resolveConfiguredChannel(CHANNEL + 1, "provider-test-token", "Test", platform)).isEmpty();
        assertThat(client.resolveConfiguredChannel(0L, "provider-test-token", "Test", platform)).isEmpty();
        assertThat(client.resolveConfiguredChannel(null, "provider-test-token", "Test", "foreign")).isEmpty();
        assertThat(client.resolveConfiguredChannel(null, "", "Test", platform)).isEmpty();
        assertThat(requests.get()).isZero();
    }

    @ParameterizedTest @CsvSource({"0,telegram", "-1,vk", "22,foreign", "22,''"})
    void missingOrInvalidRuntimeIdentityNeverContactsPanel(long channel, String platform) throws Exception {
        var client = client(channel, platform);
        assertThat(client.resolveConfiguredChannel(null, "provider-test-token", "Test", platform)).isEmpty();
        assertThat(requests.get()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"telegram", "vk", "max"})
    void wrongResponseOrDeniedLookupNeverFallsBackToToken(String platform) throws Exception {
        var client = client(CHANNEL, platform);
        responseChannel = CHANNEL + 1;
        assertThat(client.resolveConfiguredChannel(null, "provider-test-token", "Test", platform)).isEmpty();
        responseChannel = CHANNEL;
        responsePlatform = "foreign";
        assertThat(client.resolveConfiguredChannel(null, "provider-test-token", "Test", platform)).isEmpty();
        responsePlatform = platform;
        status = 403;
        assertThat(client.resolveConfiguredChannel(null, "provider-test-token", "Test", platform)).isEmpty();
        assertThat(requests.get()).isEqualTo(3);
    }
}
