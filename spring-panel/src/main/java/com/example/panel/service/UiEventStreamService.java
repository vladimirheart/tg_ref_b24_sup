package com.example.panel.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
public class UiEventStreamService {

    private static final long EMITTER_TIMEOUT_MS = 0L;

    private final ConcurrentHashMap<String, CopyOnWriteArraySet<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public SseEmitter connect(String userIdentity) {
        String identity = normalizeIdentity(userIdentity);
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emittersByUser.computeIfAbsent(identity, key -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> unregister(identity, emitter));
        emitter.onTimeout(() -> unregister(identity, emitter));
        emitter.onError(error -> unregister(identity, emitter));
        sendToEmitter(identity, emitter, "connected", Map.of(
                "connected", true,
                "emittedAt", nowUtc()
        ));
        return emitter;
    }

    public void publishDialogsChanged(String reason, String ticketId) {
        publishToAll("dialogs_changed", basePayload(reason, ticketId, null));
    }

    public void publishDialogHistoryChanged(String ticketId, Long channelId, String reason) {
        publishToAll("dialog_history_changed", basePayload(reason, ticketId, channelId));
    }

    public void publishNotificationsChanged(String userIdentity, String reason) {
        if (!StringUtils.hasText(userIdentity)) {
            return;
        }
        publishToUser(userIdentity, "notifications_changed", basePayload(reason, null, null));
    }

    public void publishNotificationsChanged(Set<String> userIdentities, String reason) {
        if (userIdentities == null || userIdentities.isEmpty()) {
            return;
        }
        Map<String, Object> payload = basePayload(reason, null, null);
        for (String identity : userIdentities) {
            publishToUser(identity, "notifications_changed", payload);
        }
    }

    public void publishSidebarUnblockChanged(String reason) {
        publishToAll("sidebar_unblock_changed", basePayload(reason, null, null));
    }

    public void publishSidebarBotsChanged(String reason, Long channelId) {
        publishToAll("sidebar_bots_changed", basePayload(reason, null, channelId));
    }

    @Scheduled(fixedDelayString = "${panel.ui-events.heartbeat-ms:25000}")
    void sendHeartbeat() {
        Map<String, Object> payload = Map.of("emittedAt", nowUtc());
        emittersByUser.forEach((identity, emitters) -> {
            if (emitters == null || emitters.isEmpty()) {
                return;
            }
            for (SseEmitter emitter : emitters) {
                sendToEmitter(identity, emitter, "ping", payload);
            }
        });
    }

    private void publishToAll(String eventName, Map<String, Object> payload) {
        emittersByUser.forEach((identity, emitters) -> {
            if (emitters == null || emitters.isEmpty()) {
                return;
            }
            for (SseEmitter emitter : emitters) {
                sendToEmitter(identity, emitter, eventName, payload);
            }
        });
    }

    private void publishToUser(String userIdentity, String eventName, Map<String, Object> payload) {
        String identity = normalizeIdentity(userIdentity);
        Set<SseEmitter> emitters = emittersByUser.get(identity);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            sendToEmitter(identity, emitter, eventName, payload);
        }
    }

    private void sendToEmitter(String identity, SseEmitter emitter, String eventName, Map<String, Object> payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(payload));
        } catch (IOException | IllegalStateException ex) {
            unregister(identity, emitter);
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // emitter is already closed
            }
        }
    }

    private void unregister(String userIdentity, SseEmitter emitter) {
        String identity = normalizeIdentity(userIdentity);
        CopyOnWriteArraySet<SseEmitter> emitters = emittersByUser.get(identity);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUser.remove(identity, emitters);
        }
    }

    private Map<String, Object> basePayload(String reason, String ticketId, Long channelId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("emittedAt", nowUtc());
        if (StringUtils.hasText(reason)) {
            payload.put("reason", reason.trim());
        }
        if (StringUtils.hasText(ticketId)) {
            payload.put("ticketId", ticketId.trim());
        }
        if (channelId != null) {
            payload.put("channelId", channelId);
        }
        return payload;
    }

    private String normalizeIdentity(String userIdentity) {
        return StringUtils.hasText(userIdentity) ? userIdentity.trim().toLowerCase() : "anonymous";
    }

    private String nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }
}
