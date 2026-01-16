package com.example.supportbot.vk;

import com.example.supportbot.config.VkBotProperties;
import com.google.gson.Gson;
import com.vk.api.sdk.objects.messages.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/callbacks/vk")
public class VkCallbackController {

    private static final Logger log = LoggerFactory.getLogger(VkCallbackController.class);

    private final VkSupportBot vkSupportBot;
    private final VkBotProperties properties;
    private final Gson gson = new Gson();

    public VkCallbackController(VkSupportBot vkSupportBot, VkBotProperties properties) {
        this.vkSupportBot = vkSupportBot;
        this.properties = properties;
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
            Object object = payload.get("object");
            if (object instanceof Map<?, ?> objectMap) {
                Object messageNode = objectMap.get("message");
                if (messageNode != null) {
                    Message message = gson.fromJson(gson.toJson(messageNode), Message.class);
                    vkSupportBot.handleIncomingMessage(message);
                }
            }
            return ResponseEntity.ok("ok");
        }

        return ResponseEntity.ok("ignored");
    }
}
