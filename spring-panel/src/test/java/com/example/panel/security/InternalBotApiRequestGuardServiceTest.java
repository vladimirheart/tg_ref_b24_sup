package com.example.panel.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.panel.config.RuntimeCoordinationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.util.ContentCachingRequestWrapper;

class InternalBotApiRequestGuardServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void authorizeAcceptsValidSignedRequest() throws Exception {
        InternalBotApiRequestGuardService service = createService(true);
        String body = """
            {"userIdentity":"operator-1"}
            """;
        String timestamp = OffsetDateTime.now(ZoneOffset.UTC).toString();
        ContentCachingRequestWrapper request = wrapRequest(
            "POST",
            "/internal/api/bot/tickets/T-1/reopen",
            body,
            Map.of(
                InternalBotApiRequestGuardService.AUTH_HEADER, "panel-token",
                InternalBotApiRequestGuardService.TIMESTAMP_HEADER, timestamp,
                InternalBotApiRequestGuardService.SIGNATURE_HEADER, sign(
                    "panel-signature",
                    "POST",
                    URI.create("/internal/api/bot/tickets/T-1/reopen"),
                    timestamp,
                    body
                )
            )
        );

        assertThatCode(() -> service.authorize(request, "panel-token")).doesNotThrowAnyException();
    }

    @Test
    void prepareWriteReplaysCachedResponseForRepeatedIdempotencyKey() throws Exception {
        InternalBotApiRequestGuardService service = createService(false);
        String idempotencyKey = "ticket-reopen:T-2";
        String body = """
            {"userIdentity":"operator-2"}
            """;

        ContentCachingRequestWrapper firstRequest = wrapRequest(
            "POST",
            "/internal/api/bot/tickets/T-2/reopen",
            body,
            Map.of(
                InternalBotApiRequestGuardService.AUTH_HEADER, "panel-token",
                InternalBotApiRequestGuardService.IDEMPOTENCY_HEADER, idempotencyKey
            )
        );

        InternalBotApiRequestGuardService.WriteExecution firstExecution = service.prepareWrite(firstRequest, "panel-token");
        assertThat(firstExecution.replayResponse()).isNull();
        String firstBody = service.successResponse(firstExecution, new Payload(true, true)).getBody();

        ContentCachingRequestWrapper secondRequest = wrapRequest(
            "POST",
            "/internal/api/bot/tickets/T-2/reopen",
            body,
            Map.of(
                InternalBotApiRequestGuardService.AUTH_HEADER, "panel-token",
                InternalBotApiRequestGuardService.IDEMPOTENCY_HEADER, idempotencyKey
            )
        );

        InternalBotApiRequestGuardService.WriteExecution secondExecution = service.prepareWrite(secondRequest, "panel-token");
        assertThat(secondExecution.replayResponse()).isNotNull();
        assertThat(secondExecution.replayResponse().getStatusCode().value()).isEqualTo(200);
        assertThat(secondExecution.replayResponse().getBody()).isEqualTo(firstBody);
    }

    private InternalBotApiRequestGuardService createService(boolean requireSignature) {
        InternalBotApiProperties properties = new InternalBotApiProperties();
        properties.setToken("panel-token");
        properties.setSignatureSecret("panel-signature");
        properties.setRequireRequestSignature(requireSignature);

        RuntimeCoordinationProperties coordinationProperties = new RuntimeCoordinationProperties();
        coordinationProperties.setMode("direct");
        coordinationProperties.setLeaseNamespace("iguana-test");

        @SuppressWarnings("unchecked")
        ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(null);
        return new InternalBotApiRequestGuardService(properties, coordinationProperties, objectMapper, provider);
    }

    private ContentCachingRequestWrapper wrapRequest(String method,
                                                    String path,
                                                    String body,
                                                    Map<String, String> headers) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        headers.forEach(request::addHeader);
        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request);
        wrapper.getInputStream().readAllBytes();
        return wrapper;
    }

    private String sign(String secret, String method, URI uri, String timestamp, String body) throws GeneralSecurityException {
        String canonicalPath = uri.getRawPath();
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        String canonical = method + "\n" + canonicalPath + "\n" + timestamp + "\n" + digest;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private record Payload(boolean updated, boolean exists) {
    }
}
