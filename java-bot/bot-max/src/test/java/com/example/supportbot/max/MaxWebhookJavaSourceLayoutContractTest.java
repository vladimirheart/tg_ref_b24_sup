package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MaxWebhookJavaSourceLayoutContractTest {

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }

    @Test
    void inboundPayloadDecodingUsesDedicatedPureOwner() throws IOException {
        String controller = read("src/main/java/com/example/supportbot/max/MaxWebhookController.java");
        String support = read("src/main/java/com/example/supportbot/max/MaxInboundPayloadSupport.java");

        assertThat(controller)
                .contains("MaxInboundPayloadSupport.resolveClientProfile(message, userId)")
                .contains("MaxInboundPayloadSupport.resolveInboundPayload(message, clientProfile)")
                .contains("MaxInboundPayloadSupport.normalizeAttachmentType(attachments.get(0).type())")
                .contains("MaxDeliveryIdentitySupport.resolveProviderMessageId(update, message)")
                .contains("MaxDeliveryIdentitySupport.resolveReplyToProviderMessageId(message)")
                .contains("storeIncomingAttachment(channel, attachments.get(0))")
                .doesNotContain("private MaxInboundPayload resolveInboundPayload(")
                .doesNotContain("private MaxClientProfile resolveClientProfile(")
                .doesNotContain("private List<MaxIncomingAttachment> extractIncomingAttachments(")
                .doesNotContain("private String extractMessageText(")
                .doesNotContain("private String normalizeAttachmentType(")
                .doesNotContain("private record MaxInboundPayload(")
                .doesNotContain("private record MaxIncomingAttachment(")
                .doesNotContain("private record MaxClientProfile(");

        assertThat(support)
                .contains("final class MaxInboundPayloadSupport")
                .contains("static ClientProfile resolveClientProfile(JsonNode message, Long userId)")
                .contains("static InboundPayload resolveInboundPayload(JsonNode message, ClientProfile clientProfile)")
                .contains("static String normalizeAttachmentType(String rawType)")
                .doesNotContain("@RestController")
                .doesNotContain("TicketService")
                .doesNotContain("AttachmentService")
                .doesNotContain("MaxApiClient")
                .doesNotContain("MessagingService")
                .doesNotContain("BotWebhookDeliveryGuardService")
                .doesNotContain("ResponseEntity");
    }

    @Test
    void deliveryIdentityUsesDedicatedPureOwner() throws IOException {
        String controller = read("src/main/java/com/example/supportbot/max/MaxWebhookController.java");
        String support = read("src/main/java/com/example/supportbot/max/MaxDeliveryIdentitySupport.java");

        assertThat(controller)
                .contains("MaxDeliveryIdentitySupport.buildDeliveryKey(update)")
                .contains("MaxDeliveryIdentitySupport.resolveProviderMessageId(update, message)")
                .contains("MaxDeliveryIdentitySupport.resolveReplyToProviderMessageId(message)")
                .doesNotContain("private String buildDeliveryKey(")
                .doesNotContain("private Long resolveProviderMessageId(")
                .doesNotContain("private Long resolveReplyToProviderMessageId(")
                .doesNotContain("import java.util.UUID;")
                .doesNotContain("import java.nio.charset.StandardCharsets;");

        assertThat(support)
                .contains("final class MaxDeliveryIdentitySupport")
                .contains("static String buildDeliveryKey(JsonNode update)")
                .contains("static Long resolveProviderMessageId(JsonNode update, JsonNode message)")
                .contains("static Long resolveReplyToProviderMessageId(JsonNode message)")
                .doesNotContain("@RestController")
                .doesNotContain("TicketService")
                .doesNotContain("AttachmentService")
                .doesNotContain("MaxApiClient")
                .doesNotContain("MessagingService")
                .doesNotContain("BotWebhookDeliveryGuardService")
                .doesNotContain("ResponseEntity")
                .doesNotContain("StringUtils");
    }
}
