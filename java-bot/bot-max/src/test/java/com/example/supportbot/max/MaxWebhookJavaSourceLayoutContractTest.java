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

    @Test
    void questionInputPolicyUsesDedicatedPureOwner() throws IOException {
        String controller = read("src/main/java/com/example/supportbot/max/MaxWebhookController.java");
        String support = read("src/main/java/com/example/supportbot/max/MaxQuestionInputSupport.java");
        String session = read("src/main/java/com/example/supportbot/max/MaxConversationSession.java");

        assertThat(controller)
                .contains("MaxQuestionInputSupport.BACK_BUTTON.equalsIgnoreCase")
                .contains("MaxQuestionInputSupport.SKIP_BUTTON.equalsIgnoreCase")
                .contains("MaxQuestionInputSupport.isChoiceQuestion(current)")
                .contains("MaxQuestionInputSupport.isOptionalFreeQuestion(current)")
                .contains("MaxQuestionInputSupport.resolveChoiceAnswer(resolvedAnswer, options)")
                .contains("MaxQuestionInputSupport.buildQuestionPromptText(current, options, session.canGoBack())")
                .contains("MaxQuestionInputSupport.isSelectQuestion(current)")
                .doesNotContain("return isPresetQuestion(item) ? answer : null;")
                .doesNotContain("private static final String SKIP_BUTTON")
                .doesNotContain("private static final String BACK_BUTTON")
                .doesNotContain("private boolean isPresetQuestion(")
                .doesNotContain("private boolean isSelectQuestion(")
                .doesNotContain("private boolean isChoiceQuestion(")
                .doesNotContain("private boolean isOptionalFreeQuestion(")
                .doesNotContain("private String buildQuestionPromptText(")
                .doesNotContain("private String resolveChoiceAnswer(");

        assertThat(session)
                .contains("MaxQuestionInputSupport.isChoiceQuestion(item)")
                .contains("MaxQuestionInputSupport.isPresetQuestion(item)");

        assertThat(support)
                .contains("final class MaxQuestionInputSupport")
                .contains("static boolean isChoiceQuestion(QuestionFlowItemDto current)")
                .contains("static boolean isOptionalFreeQuestion(QuestionFlowItemDto current)")
                .contains("static String buildQuestionPromptText(QuestionFlowItemDto current, List<String> options, boolean includeBack)")
                .contains("static String resolveChoiceAnswer(String rawAnswer, List<String> options)")
                .doesNotContain("@RestController")
                .doesNotContain("TicketService")
                .doesNotContain("AttachmentService")
                .doesNotContain("MaxApiClient")
                .doesNotContain("MessagingService")
                .doesNotContain("BotWebhookDeliveryGuardService")
                .doesNotContain("ResponseEntity")
                .doesNotContain("ObjectMapper");
    }

    @Test
    void questionOptionsUseDedicatedPureOwner() throws IOException {
        String controller = read("src/main/java/com/example/supportbot/max/MaxWebhookController.java");
        String support = read("src/main/java/com/example/supportbot/max/MaxQuestionOptionSupport.java");

        assertThat(controller)
                .contains("MaxQuestionOptionSupport.resolveSelectOptions(current)")
                .contains("MaxQuestionOptionSupport.resolveLocationOptions(field, answers, tree)")
                .contains("MaxQuestionOptionSupport.resolvePresetDefinitionOptions(group, field, presetDefinitions())")
                .contains("MaxQuestionOptionSupport.applyExcludedOptions(options, current.getExcludedOptions())")
                .contains("private Map<String, Object> locationTree()")
                .contains("private Map<String, Object> presetDefinitions()")
                .doesNotContain("private List<String> resolvePresetDefinitionOptions(")
                .doesNotContain("private List<String> resolveLocationOptions(")
                .doesNotContain("private List<String> sortedKeys(")
                .doesNotContain("private Map<String, Object> asMap(")
                .doesNotContain("private List<String> asList(")
                .doesNotContain("import java.util.Objects;");

        assertThat(support)
                .contains("final class MaxQuestionOptionSupport")
                .contains("static List<String> resolveSelectOptions(QuestionFlowItemDto current)")
                .contains("static List<String> resolvePresetDefinitionOptions(String group,")
                .contains("static List<String> resolveLocationOptions(String field,")
                .contains("static List<String> applyExcludedOptions(List<String> options, List<String> excluded)")
                .doesNotContain("@RestController")
                .doesNotContain("RuntimeConfigService")
                .doesNotContain("BotSettingsService")
                .doesNotContain("TicketService")
                .doesNotContain("MessagingService")
                .doesNotContain("ResponseEntity")
                .doesNotContain("ObjectMapper");
    }

    @Test
    void conversationSessionUsesDedicatedStateOwner() throws IOException {
        String controller = read("src/main/java/com/example/supportbot/max/MaxWebhookController.java");
        String session = read("src/main/java/com/example/supportbot/max/MaxConversationSession.java");

        assertThat(controller)
                .contains("MaxConversationSession session = loadSession(userId)")
                .contains("private MaxConversationSession startSession(")
                .contains("private MaxConversationSession loadSession(Long userId)")
                .contains("MaxConversationSession.State.class")
                .contains("new MaxConversationSession(stored.payload())")
                .contains("private void saveSession(MaxConversationSession session)")
                .contains("private void deleteSession(MaxConversationSession session)")
                .contains("session.snapshot()")
                .contains("sessionStoreService.saveIfUnchanged(")
                .contains("sessionStoreService.deleteIfUnchanged(")
                .doesNotContain("private record HistoryEvent(")
                .doesNotContain("private record ConversationSessionState(")
                .doesNotContain("private final class ConversationSession")
                .doesNotContain("import com.example.supportbot.settings.dto.QuestionOptionDto;")
                .doesNotContain("import com.example.supportbot.settings.dto.QuestionRouteDto;");

        assertThat(session)
                .contains("final class MaxConversationSession")
                .contains("record HistoryEvent(Long userId, String text, String messageType)")
                .contains("record State(Long userId,")
                .contains("void recordAnswer(String text)")
                .contains("boolean consumeReuseDecision(String decision)")
                .contains("boolean shouldExpireDueToMissingFirstResponse(OffsetDateTime now, int timeoutMinutes)")
                .contains("State snapshot()")
                .doesNotContain("@RestController")
                .doesNotContain("BotSessionStoreService")
                .doesNotContain("SessionStateConflictException")
                .doesNotContain("MessagingService")
                .doesNotContain("RuntimeConfigService")
                .doesNotContain("ResponseEntity")
                .doesNotContain("ObjectMapper");
    }

    @Test
    void incidentFlowUsesDedicatedPureOwner() throws IOException {
        String controller = read("src/main/java/com/example/supportbot/max/MaxWebhookController.java");
        String support = read("src/main/java/com/example/supportbot/max/MaxIncidentFlowSupport.java");

        assertThat(controller)
                .contains("MaxIncidentFlowSupport.normalize(botSettingsService.questionFlow(settings))")
                .contains("botSettingsService.questionFlow(settings)")
                .doesNotContain("CORE_LOCATION_FIELDS")
                .doesNotContain("private List<QuestionFlowItemDto> buildIncidentFlow(")
                .doesNotContain("private String defaultPrompt(")
                .doesNotContain("import com.example.supportbot.settings.dto.PresetReference;")
                .doesNotContain("import java.util.ArrayList;")
                .doesNotContain("import java.util.Comparator;");

        assertThat(support)
                .contains("final class MaxIncidentFlowSupport")
                .contains("static List<QuestionFlowItemDto> normalize(List<QuestionFlowItemDto> configuredFlow)")
                .contains("new PresetReference(\"locations\", field)")
                .contains("new QuestionFlowItemDto(\"problem\", \"text\", \"Опишите проблему\"")
                .doesNotContain("@RestController")
                .doesNotContain("BotSettingsService")
                .doesNotContain("RuntimeConfigService")
                .doesNotContain("TicketService")
                .doesNotContain("MessagingService")
                .doesNotContain("BotSessionStoreService")
                .doesNotContain("ResponseEntity")
                .doesNotContain("ObjectMapper");
    }

    @Test
    void unblockMessagePolicyUsesDedicatedPureOwner() throws IOException {
        String controller = read("src/main/java/com/example/supportbot/max/MaxWebhookController.java");
        String support = read("src/main/java/com/example/supportbot/max/MaxUnblockMessageSupport.java");

        assertThat(controller)
                .contains("MaxUnblockMessageSupport.buildOperatorRequestMessage(request)")
                .contains("MaxUnblockMessageSupport.buildClientResponse(decision.request(), decision.created(), decision.retryAfter())")
                .contains("blacklistService.requestUnblock(userId, \"\", channelId, cooldown)")
                .contains("messagingService.sendToSupportChat(channel")
                .contains("messagingService.sendToUser(")
                .doesNotContain("private String buildUnblockResponse(")
                .doesNotContain("private String formatRetryAfter(")
                .doesNotContain("private String formatTimestamp(")
                .doesNotContain("import java.time.format.DateTimeFormatter;");

        assertThat(support)
                .contains("final class MaxUnblockMessageSupport")
                .contains("static String buildOperatorRequestMessage(ClientUnblockRequest request)")
                .contains("static String buildClientResponse(ClientUnblockRequest request, boolean created, Duration retryAfter)")
                .contains("DateTimeFormatter.ofPattern(\"dd.MM.yyyy HH:mm\")")
                .doesNotContain("@RestController")
                .doesNotContain("BlacklistService")
                .doesNotContain("MessagingService")
                .doesNotContain("BotSettingsService")
                .doesNotContain("RuntimeConfigService")
                .doesNotContain("TicketService")
                .doesNotContain("BotSessionStoreService")
                .doesNotContain("ResponseEntity")
                .doesNotContain("ObjectMapper")
                .doesNotContain("StringUtils");
    }

    @Test
    void attachmentMetadataPolicyUsesDedicatedPureOwner() throws IOException {
        String controller = read("src/main/java/com/example/supportbot/max/MaxWebhookController.java");
        String support = read("src/main/java/com/example/supportbot/max/MaxAttachmentMetadataSupport.java");

        assertThat(controller)
                .contains("MaxAttachmentMetadataSupport.firstNonBlank(attachment.name(), downloaded.filename())")
                .contains("MaxAttachmentMetadataSupport.resolveAttachmentExtension(originalName, downloaded.contentType(), attachment.type())")
                .contains("String channelPublicId = MaxAttachmentMetadataSupport.firstNonBlank(")
                .contains("maxApiClient.downloadAttachment(attachment.url())")
                .contains("attachmentService.store(channelPublicId, extension, downloaded.body())")
                .contains("private StoredIncomingAttachment storeIncomingAttachment(")
                .doesNotContain("private String firstNonBlank(")
                .doesNotContain("private String resolveAttachmentExtension(")
                .doesNotContain("private String trimOrNull(");

        assertThat(support)
                .contains("final class MaxAttachmentMetadataSupport")
                .contains("static String firstNonBlank(String... values)")
                .contains("static String resolveAttachmentExtension(String filename, String contentType, String attachmentType)")
                .doesNotContain("@RestController")
                .doesNotContain("AttachmentService")
                .doesNotContain("MaxApiClient")
                .doesNotContain("MessagingService")
                .doesNotContain("TicketService")
                .doesNotContain("BotSessionStoreService")
                .doesNotContain("ResponseEntity")
                .doesNotContain("ObjectMapper")
                .doesNotContain("StringUtils")
                .doesNotContain("LoggerFactory");
    }

    @Test
    void activeTicketOperatorMessageUsesDedicatedPureOwner() throws IOException {
        String controller = read("src/main/java/com/example/supportbot/max/MaxWebhookController.java");
        String support = read("src/main/java/com/example/supportbot/max/MaxActiveMessageSupport.java");

        assertThat(controller)
                .contains("MaxActiveMessageSupport.buildOperatorMessage(")
                .contains("clientProfile.displayLabel()")
                .contains("messagingService.sendToSupportChat(")
                .contains("private void notifyOperatorsAboutActiveMessage(")
                .doesNotContain("builder.append(\"Новый ответ клиента \")")
                .doesNotContain("builder.append(\"ID заявки: #\")");

        assertThat(support)
                .contains("final class MaxActiveMessageSupport")
                .contains("static String buildOperatorMessage(")
                .contains("builder.append(\"Новый ответ клиента \")")
                .contains("builder.append(\"ID заявки: #\")")
                .contains("builder.append(\"\\nВложение: \")")
                .contains("builder.append(\"\\nВложений: \")")
                .doesNotContain("@RestController")
                .doesNotContain("MessagingService")
                .doesNotContain("TicketService")
                .doesNotContain("AttachmentService")
                .doesNotContain("MaxApiClient")
                .doesNotContain("BotSessionStoreService")
                .doesNotContain("ResponseEntity")
                .doesNotContain("ObjectMapper")
                .doesNotContain("StringUtils")
                .doesNotContain("LoggerFactory");
    }

    @Test
    void maxCommandsUseDedicatedPureOwner() throws IOException {
        String controller = read("src/main/java/com/example/supportbot/max/MaxWebhookController.java");
        String support = read("src/main/java/com/example/supportbot/max/MaxCommandSupport.java");

        assertThat(controller)
                .contains("MaxCommandSupport.isUnblockCommand(text)")
                .contains("MaxCommandSupport.isStartCommand(text)")
                .contains("MaxCommandSupport.isCancelCommand(text)")
                .doesNotContain("\"/unblock\".equalsIgnoreCase(text)")
                .doesNotContain("\"/start\".equalsIgnoreCase(text)")
                .doesNotContain("private boolean isCancelCommand(")
                .doesNotContain("\"отмена\".equals(normalized)");

        assertThat(support)
                .contains("final class MaxCommandSupport")
                .contains("static boolean isUnblockCommand(String text)")
                .contains("static boolean isStartCommand(String text)")
                .contains("static boolean isCancelCommand(String text)")
                .contains("text.trim().toLowerCase(Locale.ROOT)")
                .contains("\"отмена\".equals(normalized)")
                .doesNotContain("@RestController")
                .doesNotContain("MessagingService")
                .doesNotContain("TicketService")
                .doesNotContain("BlacklistService")
                .doesNotContain("BotSessionStoreService")
                .doesNotContain("ResponseEntity")
                .doesNotContain("ObjectMapper")
                .doesNotContain("StringUtils")
                .doesNotContain("LoggerFactory");
    }
}
