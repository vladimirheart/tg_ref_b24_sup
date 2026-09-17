package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MaxInboundPayloadSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void resolvesForwardedTextAndOriginalAuthorWithoutReplacingOuterClient() throws Exception {
        JsonNode message = objectMapper.readTree("""
                {
                  "sender": { "user_id": 1001, "name": "Client" },
                  "body": null,
                  "link": {
                    "type": "forward",
                    "sender": { "user_id": 1001, "name": "Client" },
                    "author": { "user_id": 3003, "name": "Alex", "username": "alex" },
                    "message": { "body": { "text": "Forwarded text" } }
                  }
                }
                """);

        MaxInboundPayloadSupport.ClientProfile client = MaxInboundPayloadSupport.resolveClientProfile(message, 1001L);
        MaxInboundPayloadSupport.InboundPayload payload = MaxInboundPayloadSupport.resolveInboundPayload(message, client);

        assertThat(client.username()).isEqualTo("max_1001");
        assertThat(client.clientName()).isEqualTo("Client");
        assertThat(payload.text()).isEqualTo("Forwarded text");
        assertThat(payload.forwardedFrom()).isEqualTo("Alex");
        assertThat(payload.attachments()).isEmpty();
    }

    @Test
    void fallsBackToForwardedAttachmentsWhenOuterPayloadHasNone() throws Exception {
        JsonNode message = objectMapper.readTree("""
                {
                  "sender": { "user_id": 1001 },
                  "body": null,
                  "link": {
                    "type": "forward",
                    "message": {
                      "body": {
                        "attachments": [
                          { "type": "image/jpeg", "url": "https://cdn.example/image.jpg", "name": "image.jpg" }
                        ]
                      }
                    }
                  }
                }
                """);

        MaxInboundPayloadSupport.ClientProfile client = MaxInboundPayloadSupport.resolveClientProfile(message, 1001L);
        MaxInboundPayloadSupport.InboundPayload payload = MaxInboundPayloadSupport.resolveInboundPayload(message, client);

        assertThat(payload.attachments()).hasSize(1);
        MaxInboundPayloadSupport.IncomingAttachment attachment = payload.attachments().get(0);
        assertThat(attachment.urlOrName()).isEqualTo("https://cdn.example/image.jpg");
        assertThat(MaxInboundPayloadSupport.normalizeAttachmentType(attachment.type())).isEqualTo("photo");
    }

    @Test
    void suppliesStableClientFallbacks() throws Exception {
        JsonNode message = objectMapper.readTree("{\"sender\":{}}");

        MaxInboundPayloadSupport.ClientProfile client = MaxInboundPayloadSupport.resolveClientProfile(message, 42L);

        assertThat(client.username()).isEqualTo("max_42");
        assertThat(client.clientName()).isEqualTo("MAX user 42");
        assertThat(client.identity()).isEqualTo("max_42");
    }
}
