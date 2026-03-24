package com.example.supportbot.max;

import com.example.supportbot.config.MaxBotProperties;
import com.example.supportbot.entity.Channel;
import com.example.supportbot.entity.TicketActive;
import com.example.supportbot.service.ChannelService;
import com.example.supportbot.service.ChatHistoryService;
import com.example.supportbot.service.MessagingService;
import com.example.supportbot.service.TicketService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/max")
public class MaxWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MaxWebhookController.class);

    private final MaxBotProperties properties;
    private final ChannelService channelService;
    private final TicketService ticketService;
    private final ChatHistoryService chatHistoryService;
    private final MessagingService messagingService;

    public MaxWebhookController(MaxBotProperties properties,
                                ChannelService channelService,
                                TicketService ticketService,
                                ChatHistoryService chatHistoryService,
                                MessagingService messagingService) {
        this.properties = properties;
        this.channelService = channelService;
        this.ticketService = ticketService;
        this.chatHistoryService = chatHistoryService;
        this.messagingService = messagingService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> handleUpdate(
        @RequestBody JsonNode update,
        @RequestHeader(value = "X-Max-Bot-Api-Secret", required = false) String secret
    ) {
        if (!properties.isEnabled()) {
            return ResponseEntity.ok(Map.of("ok", true, "ignored", "max-bot-disabled"));
        }
        if (!secretValid(secret)) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "error", "invalid-secret"));
        }
        String updateType = text(update, "update_type");
        if (!"message_created".equals(updateType)) {
            return ResponseEntity.ok(Map.of("ok", true, "ignored", updateType));
        }

        JsonNode message = update.path("message");
        Long userId = asLong(message.path("sender").path("user_id"));
        Long chatId = asLong(message.path("recipient").path("chat_id"));
        String text = text(message.path("body"), "text");
        String username = text(message.path("sender"), "username");
        if (userId == null || text.isBlank()) {
            return ResponseEntity.ok(Map.of("ok", true, "ignored", "missing-user-or-text"));
        }

        Channel channel = channelService.ensurePublicIdForToken(properties.getToken(), "MAX", "max");
        if ("/start".equalsIgnoreCase(text.trim())) {
            messagingService.sendToUser(channel, userId, "Здравствуйте! Я бот поддержки. Опишите проблему в одном сообщении.");
            return ResponseEntity.ok(Map.of("ok", true));
        }

        Optional<TicketActive> active = ticketService.findActiveTicketForUser(userId, username);
        if (active.isPresent()) {
            String ticketId = active.get().getTicketId();
            chatHistoryService.storeUserMessage(userId, null, text, channel, ticketId, "text", null, null, null);
            messagingService.sendToUser(channel, userId, "Сообщение добавлено в заявку #" + ticketId + ".");
            return ResponseEntity.ok(Map.of("ok", true, "ticket_id", ticketId));
        }

        Map<String, String> answers = new LinkedHashMap<>();
        answers.put("problem", text);
        answers.put("business", "MAX");
        answers.put("location_type", chatId != null ? "chat:" + chatId : "dialog");

        TicketService.TicketCreationResult created = ticketService.createTicket(userId, username, answers, channel);
        chatHistoryService.storeUserMessage(userId, null, text, channel, created.ticketId(), "text", null, null, null);
        messagingService.sendToUser(channel, userId, "Заявка создана. Номер: " + created.ticketId());
        messagingService.sendToSupportChat(channel,
            "Новая заявка из MAX\nID: " + created.ticketId() + "\nПользователь: " + userId + "\nТекст: " + text);
        return ResponseEntity.ok(Map.of("ok", true, "ticket_id", created.ticketId()));
    }

    private boolean secretValid(String provided) {
        String expected = properties.getWebhookSecret();
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return expected.equals(provided);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node != null ? node.path(field) : null;
        return value == null || value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private Long asLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.longValue();
        }
        String raw = node.asText("").trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
