package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.model.publicform.PublicFormSessionDto;
import com.example.panel.model.publicform.PublicFormSubmission;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicFormAntiAbuseServiceTest {

    @Test
    void prepareSubmissionFingerprintIsStableAcrossAnswerOrder() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        PublicFormAntiAbuseService service = new PublicFormAntiAbuseService(
                new PublicFormRuntimeConfigService(sharedConfigService),
                new ObjectMapper()
        );
        PublicFormSubmission submission = new PublicFormSubmission(
                "Need help",
                "Anna",
                "+79990000000",
                "anna",
                null,
                Map.of(),
                "req-1"
        );

        PublicFormAntiAbuseService.SubmissionFingerprint first = service.prepareSubmissionFingerprint(
                submission,
                new LinkedHashMap<>(Map.of("b", "2", "a", "1"))
        );
        PublicFormAntiAbuseService.SubmissionFingerprint second = service.prepareSubmissionFingerprint(
                submission,
                new LinkedHashMap<>(Map.of("a", "1", "b", "2"))
        );

        assertThat(first.requestId()).isEqualTo("req-1");
        assertThat(first.payloadHash()).isEqualTo(second.payloadHash());
    }

    @Test
    void findIdempotentSessionRejectsSameRequestIdWithDifferentPayload() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of("public_form_idempotency_ttl_seconds", 300)
        ));
        PublicFormAntiAbuseService service = new PublicFormAntiAbuseService(
                new PublicFormRuntimeConfigService(sharedConfigService),
                new ObjectMapper()
        );
        Channel channel = new Channel();
        channel.setId(42L);
        PublicFormAntiAbuseService.SubmissionFingerprint original = service.prepareSubmissionFingerprint(
                new PublicFormSubmission("Need help", "Anna", null, null, null, Map.of(), "req-1"),
                Map.of("topic", "billing")
        );
        PublicFormSessionDto session = new PublicFormSessionDto(
                "token-1",
                "T-1",
                42L,
                "web-42",
                "Anna",
                null,
                "anna",
                OffsetDateTime.parse("2026-05-18T09:00:00+03:00")
        );
        service.cacheIdempotentSession(channel, "ip-1", original, session);

        PublicFormAntiAbuseService.SubmissionFingerprint changed = service.prepareSubmissionFingerprint(
                new PublicFormSubmission("Need help", "Anna", null, null, null, Map.of(), "req-1"),
                Map.of("topic", "support")
        );

        assertThatThrownBy(() -> service.findIdempotentSession(channel, "ip-1", changed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestId");
    }

    @Test
    void enforceRateLimitHonorsChannelOverridesFromQuestionsConfig() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of(
                        "public_form_rate_limit_enabled", true,
                        "public_form_rate_limit_window_seconds", 60,
                        "public_form_rate_limit_max_requests", 5
                )
        ));
        PublicFormAntiAbuseService service = new PublicFormAntiAbuseService(
                new PublicFormRuntimeConfigService(sharedConfigService),
                new ObjectMapper()
        );
        Channel channel = new Channel();
        channel.setId(77L);
        channel.setQuestionsCfg("""
                {"schemaVersion":1,"enabled":true,"rateLimitEnabled":true,"rateLimitWindowSeconds":60,"rateLimitMaxRequests":2}
                """);

        service.enforceRateLimit(channel, "same-ip");
        service.enforceRateLimit(channel, "same-ip");

        assertThatThrownBy(() -> service.enforceRateLimit(channel, "same-ip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Слишком много запросов");
    }

    @Test
    void buildRequesterKeyFallsBackToIpWhenFingerprintDisabled() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of("public_form_rate_limit_use_fingerprint", false)
        ));
        PublicFormAntiAbuseService service = new PublicFormAntiAbuseService(
                new PublicFormRuntimeConfigService(sharedConfigService),
                new ObjectMapper()
        );

        assertThat(service.buildRequesterKey("203.0.113.10", "fp-1")).isEqualTo("203.0.113.10");
    }
}
