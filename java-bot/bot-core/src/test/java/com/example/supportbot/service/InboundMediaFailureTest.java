package com.example.supportbot.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.io.IOException;

class InboundMediaFailureTest {
    @Test void causeChainUsesKnownReceiveReasonWithoutRawTokensOrUrls() {
        for (var item : java.util.Map.of("file is too big token=private", "INBOUND_FILE_TOO_LARGE",
                "HTTP 403 https://private.example", "INBOUND_PROVIDER_ACCESS", "deadline exceeded", "INBOUND_MEDIA_TIMEOUT",
                "HTTP 404 token=private", "INBOUND_FILE_UNAVAILABLE", "disk failure secret=private", "INBOUND_MEDIA_UNAVAILABLE").entrySet()) {
            var result = InboundMediaFailure.describe(new IllegalStateException("wrapper", new IOException(item.getKey())));
            assertThat(result.code()).isEqualTo(item.getValue());
            assertThat(result.message()).doesNotContain("private", "https://", "secret=");
        }
    }
    @Test void knownQuestionnaireLimitRemainsSpecificButProviderLimitIsNotInvented() {
        assertThat(InboundMediaFailure.describe(new AttachmentService.MediaSizeException()).message()).contains("1 байта", "10 МиБ");
        assertThat(InboundMediaFailure.describe(new IOException("413")).message()).doesNotContain("10 МиБ", "50 МБ");
    }
    @Test void webhookFailureKeepsRetryableStatusAndSafeErrorEnvelope() {
        var response = new InboundMediaException("max", new IOException("file too large token=private")).envelope();
        assertThat(response).containsEntry("success", false).containsEntry("ok", false).containsEntry("errorSource", "max");
        assertThat(response.get("error").toString()).doesNotContain("private");
    }
}
