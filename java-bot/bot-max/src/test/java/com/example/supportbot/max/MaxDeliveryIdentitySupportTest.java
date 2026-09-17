package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MaxDeliveryIdentitySupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deliveryKeyPrefersUpdateId() throws Exception {
        JsonNode update = objectMapper.readTree("""
                {
                  "update_id": "update-42",
                  "message": {
                    "message_id": 100
                  }
                }
                """);

        assertThat(MaxDeliveryIdentitySupport.buildDeliveryKey(update))
                .isEqualTo("update:update-42");
    }

    @Test
    void deliveryKeyFallsBackToMessageIdentity() throws Exception {
        JsonNode update = objectMapper.readTree("""
                {
                  "message": {
                    "sender": { "user_id": 1001 },
                    "recipient": { "chat_id": 2002 },
                    "timestamp": "2026-09-17T10:00:00Z"
                  }
                }
                """);

        assertThat(MaxDeliveryIdentitySupport.buildDeliveryKey(update))
                .isEqualTo("message_created|sender=1001|chat=2002|created_at=2026-09-17T10:00:00Z");
    }

    @Test
    void providerMessageIdUsesExplicitBodyMid() throws Exception {
        JsonNode update = objectMapper.readTree("""
                {
                  "update_id": "update-77",
                  "message": {
                    "body": { "mid": "9001" }
                  }
                }
                """);

        assertThat(MaxDeliveryIdentitySupport.resolveProviderMessageId(update, update.path("message")))
                .isEqualTo(9001L);
    }

    @Test
    void providerMessageIdFallbackIsStableAndPositive() throws Exception {
        JsonNode update = objectMapper.readTree("""
                {
                  "update_id": "stable-update",
                  "message": {
                    "sender": { "user_id": 1001 }
                  }
                }
                """);

        Long first = MaxDeliveryIdentitySupport.resolveProviderMessageId(update, update.path("message"));
        Long second = MaxDeliveryIdentitySupport.resolveProviderMessageId(update, update.path("message"));

        assertThat(first).isPositive().isEqualTo(second);
    }

    @Test
    void replyTargetReadsNestedLinkedMessageMid() throws Exception {
        JsonNode message = objectMapper.readTree("""
                {
                  "link": {
                    "type": "reply",
                    "message": {
                      "mid": "8123"
                    }
                  }
                }
                """);

        assertThat(MaxDeliveryIdentitySupport.resolveReplyToProviderMessageId(message))
                .isEqualTo(8123L);
    }

    @Test
    void nonReplyLinkHasNoReplyTarget() throws Exception {
        JsonNode message = objectMapper.readTree("""
                {
                  "link": {
                    "type": "forward",
                    "message_id": 8123
                  }
                }
                """);

        assertThat(MaxDeliveryIdentitySupport.resolveReplyToProviderMessageId(message)).isNull();
    }
}
