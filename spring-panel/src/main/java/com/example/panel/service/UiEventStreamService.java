package com.example.panel.service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class UiEventStreamService {

    private static final long EMITTER_TIMEOUT_MS = 0L;

    private final UiEventFanoutPublisher fanoutPublisher;
    private final LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard;
    private final UiEventTicketAccessService ticketAccess;
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<SseEmitter>> emittersByUser =
        new ConcurrentHashMap<>();

    public UiEventStreamService(UiEventFanoutPublisher fanoutPublisher,
                                LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard,
                                UiEventTicketAccessService ticketAccess) {
        this.fanoutPublisher = fanoutPublisher;
        this.legacyTicketIdJdbcGuard = legacyTicketIdJdbcGuard;
        this.ticketAccess = ticketAccess;
    }

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
        if (!hasUniqueNonGeneralTicket(ticketId)) {
            return;
        }
        publishToAll("dialogs_changed", basePayload(reason, ticketId, null));
    }

    public void publishDialogHistoryChanged(String ticketId, Long channelId, String reason) {
        if (!hasUniqueNonGeneralTicket(ticketId)) {
            return;
        }
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

    void sendHeartbeat() {
        Map<String, Object> payload = Map.of("emittedAt", nowUtc());
        deliverToAll("ping", payload);
    }

    void deliverFanoutEvent(UiEventFanoutEnvelope envelope) {
        if (envelope == null || !StringUtils.hasText(envelope.eventName())) {
            return;
        }

        if (StringUtils.hasText(envelope.targetUser())) {
            deliverToUser(
                normalizeIdentity(envelope.targetUser()),
                envelope.eventName().trim(),
                envelope.payload()
            );
            return;
        }

        deliverToAll(envelope.eventName().trim(), envelope.payload());
    }

    private void publishToAll(String eventName, Map<String, Object> payload) {
        if (!fanoutPublisher.publish(null, eventName, payload)) {
            deliverToAll(eventName, payload);
        }
    }

    private void publishToUser(String userIdentity, String eventName, Map<String, Object> payload) {
        String identity = normalizeIdentity(userIdentity);
        if (!fanoutPublisher.publish(identity, eventName, payload)) {
            deliverToUser(identity, eventName, payload);
        }
    }

    private void deliverToAll(String eventName, Map<String, Object> payload) {
        if (!canDeliverLegacyEvent(eventName, payload)) {
            return;
        }
        emittersByUser.forEach((identity, emitters) -> {
            if (emitters == null || emitters.isEmpty()) {
                return;
            }
            for (SseEmitter emitter : emitters) {
                sendToEmitter(identity, emitter, eventName, payload);
            }
        });
    }

    private void deliverToUser(String identity, String eventName, Map<String, Object> payload) {
        if (!canDeliverLegacyEvent(eventName, payload)) {
            return;
        }
        Set<SseEmitter> emitters = emittersByUser.get(identity);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            sendToEmitter(identity, emitter, eventName, payload);
        }
    }

    private void sendToEmitter(String identity,
                               SseEmitter emitter,
                               String eventName,
                               Map<String, Object> payload) {
        if (isTicketBound(eventName, payload)) {
            Object ticketId = payload != null ? payload.get("ticketId") : null;
            if (!(ticketId instanceof String rawId) || !ticketAccess.canReadTicket(identity, rawId)) {
                return;
            }
        }
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
            payload.put("ticketId", ticketId);
        }
        if (channelId != null) {
            payload.put("channelId", channelId);
        }
        return payload;
    }

    private String normalizeIdentity(String userIdentity) {
        return StringUtils.hasText(userIdentity)
            ? userIdentity.trim().toLowerCase(Locale.ROOT)
            : "anonymous";
    }

    /** Recheck at delivery too: queued Redis envelopes and local fallback can outlive the producer check. */
    private boolean canDeliverLegacyEvent(String eventName, Map<String, Object> payload) {
        if (!isTicketBound(eventName, payload)) {
            return true;
        }
        Object ticketId = payload != null ? payload.get("ticketId") : null;
        return ticketId instanceof String rawId && hasUniqueNonGeneralTicket(rawId);
    }

    private boolean isTicketBound(String eventName, Map<String, Object> payload) {
        return "dialogs_changed".equals(eventName) || "dialog_history_changed".equals(eventName)
            || (payload != null && payload.containsKey("ticketId"));
    }

    private boolean hasUniqueNonGeneralTicket(String ticketId) {
        return StringUtils.hasText(ticketId) && legacyTicketIdJdbcGuard.hasUniqueNonGeneralTicket(ticketId);
    }

    private String nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }
}
