package com.example.supportbot.vk;

import com.example.supportbot.config.VkBotProperties;
import com.example.supportbot.service.BotIngressCoordinationService;
import com.example.supportbot.service.BotWebhookDeliveryGuardService;
import com.google.gson.Gson;
import com.vk.api.sdk.objects.messages.Message;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/callbacks/vk")
public class VkCallbackController {

    private static final Logger log = LoggerFactory.getLogger(VkCallbackController.class);

    private final VkSupportBot vkSupportBot;
    private final VkBotProperties properties;
    private final BotIngressCoordinationService ingressCoordinationService;
    private final BotWebhookDeliveryGuardService webhookDeliveryGuardService;
    private final Gson gson = new Gson();

    public VkCallbackController(VkSupportBot vkSupportBot,
                                VkBotProperties properties,
                                BotIngressCoordinationService ingressCoordinationService,
                                BotWebhookDeliveryGuardService webhookDeliveryGuardService) {
        this.vkSupportBot = vkSupportBot;
        this.properties = properties;
        this.ingressCoordinationService = ingressCoordinationService;
        this.webhookDeliveryGuardService = webhookDeliveryGuardService;
    }

    @PostMapping("/{groupId}")
    public ResponseEntity<String> handle(@PathVariable Integer groupId, @RequestBody Map<String, Object> payload) {
        if (!properties.isWebhookEnabled() || !groupId.equals(properties.getGroupId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("webhook disabled");
        }

        String type = payload.getOrDefault("type", "").toString();
        if ("confirmation".equals(type)) {
            return ResponseEntity.ok(properties.getConfirmationToken());
        }

        if (properties.getSecret() != null && !properties.getSecret().isBlank()) {
            String secret = payload.getOrDefault("secret", "").toString();
            if (!properties.getSecret().equals(secret)) {
                log.warn("Rejecting VK callback: secret mismatch for group {}", groupId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("forbidden");
            }
        }

        if ("message_new".equals(type)) {
            if (!ingressCoordinationService.tryAcquireOrRenew("vk", properties.getChannelId())) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("not-owner");
            }
            BotWebhookDeliveryGuardService.DeliveryClaim claim = webhookDeliveryGuardService.tryClaim(
                "vk",
                properties.getChannelId(),
                buildDeliveryKey(type, payload)
            );
            if (claim.alreadyProcessed()) {
                return ResponseEntity.ok("ok");
            }
            if (claim.inFlight()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("delivery-inflight");
            }
            Object object = payload.get("object");
            try {
                if (object instanceof Map<?, ?> objectMap) {
                    Object messageNode = objectMap.get("message");
                    if (messageNode != null) {
                        Message message = gson.fromJson(gson.toJson(messageNode), Message.class);
                        vkSupportBot.handleIncomingMessage(message);
                    }
                }
                webhookDeliveryGuardService.markProcessed(claim);
                return ResponseEntity.ok("ok");
            } catch (RuntimeException ex) {
                webhookDeliveryGuardService.release(claim);
                throw ex;
            }
        }

        return ResponseEntity.ok("ignored");
    }

    private String buildDeliveryKey(String type, Map<String, Object> payload) {
        String eventId = value(payload.get("event_id"));
        if (!eventId.isBlank()) {
            return type + "|event:" + eventId;
        }
        Map<String, Object> object = nestedMap(payload.get("object"));
        Map<String, Object> message = nestedMap(object.get("message"));
        Map<String, Object> keyParts = new LinkedHashMap<>();
        keyParts.put("type", type);
        keyParts.put("message_id", value(message.get("id")));
        keyParts.put("conversation_message_id", value(message.get("conversation_message_id")));
        keyParts.put("peer_id", value(message.get("peer_id")));
        keyParts.put("from_id", value(message.get("from_id")));
        keyParts.put("date", value(message.get("date")));
        return keyParts.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            return (Map<String, Object>) rawMap;
        }
        return Map.of();
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
