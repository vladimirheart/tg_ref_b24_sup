package com.example.panel.service;

import com.example.panel.model.publicform.PublicFormConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PublicFormApiContractServiceTest {

    private final PublicFormApiContractService service = new PublicFormApiContractService();

    @Test
    void resolveRequesterContextPrefersForwardedHeaderThenRealIpThenRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        request.addHeader("X-Real-IP", "198.51.100.1");
        request.addHeader("X-Public-Form-Fingerprint", "fp-1");
        request.setRemoteAddr("127.0.0.1");

        PublicFormApiContractService.RequesterContext context = service.resolveRequesterContext(request);

        assertThat(context.requesterIp()).isEqualTo("203.0.113.10");
        assertThat(context.fingerprint()).isEqualTo("fp-1");
    }

    @Test
    void resolveRequesterContextFallsBackToRealIpAndRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "198.51.100.2");
        request.setRemoteAddr("127.0.0.1");

        assertThat(service.resolveRequesterContext(request).requesterIp()).isEqualTo("198.51.100.2");

        MockHttpServletRequest remoteOnly = new MockHttpServletRequest();
        remoteOnly.setRemoteAddr("127.0.0.2");

        assertThat(service.resolveRequesterContext(remoteOnly).requesterIp()).isEqualTo("127.0.0.2");
    }

    @Test
    void resolveErrorCodeMapsKnownValidationAndConflictBranches() {
        assertThat(service.resolveErrorCode("Слишком много запросов. Попробуйте позже")).isEqualTo("RATE_LIMITED");
        assertThat(service.resolveErrorCode("captcha failed")).isEqualTo("CAPTCHA_FAILED");
        assertThat(service.resolveErrorCode("required field missing")).isEqualTo("VALIDATION_REQUIRED");
        assertThat(service.resolveErrorCode("invalid email")).isEqualTo("VALIDATION_EMAIL");
        assertThat(service.resolveErrorCode("invalid phone")).isEqualTo("VALIDATION_PHONE");
        assertThat(service.resolveErrorCode("message exceeds max length")).isEqualTo("VALIDATION_MAX_LENGTH");
        assertThat(service.resolveErrorCode("minimum 3 symbols")).isEqualTo("VALIDATION_MIN_LENGTH");
        assertThat(service.resolveErrorCode("requestId idempotency mismatch")).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(service.resolveErrorCode("validation failed")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void resolveDisabledStatusFallsBackToNotFoundForInvalidCode() {
        PublicFormConfig config = new PublicFormConfig(
                1L,
                "web-disabled",
                "Web Form",
                1,
                false,
                false,
                999,
                null,
                null,
                List.of()
        );

        assertThat(service.resolveDisabledStatus(config)).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void maskTokenSupportsBlankShortAndLongTokens() {
        assertThat(service.maskToken(null)).isEqualTo("[empty]");
        assertThat(service.maskToken("   ")).isEqualTo("[empty]");
        assertThat(service.maskToken("token1")).startsWith("tok:");
        assertThat(service.maskToken("1234567890abcdef")).isEqualTo("1234…cdef");
    }
}
