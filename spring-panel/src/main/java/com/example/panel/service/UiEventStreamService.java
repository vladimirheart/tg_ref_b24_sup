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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class UiEventStreamService {

    private static final long EMITTER_TIMEOUT_MS = 0L;

    private final UiEventFanoutPublisher fanoutPublisher;
    private final LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard;
    private final UiEventTicketAccessService ticketAccess;
    private final ConcurrentHashMap<UiEventRecipient, CopyOnWriteArraySet<SseEmitter>> emittersByUser =
        new ConcurrentHashMap<>();

    public UiEventStreamService(UiEventFanoutPublisher fanoutPublisher,
                                LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard,
                                UiEventTicketAccessService ticketAccess) {
        this.fanoutPublisher = fanoutPublisher;
        this.legacyTicketIdJdbcGuard = legacyTicketIdJdbcGuard;
        this.ticketAccess = ticketAccess;
    }

    public SseEmitter connect(Authentication authentication) {
        UiEventRecipient recipient = UiEventRecipient.from(authentication);
        if (recipient == null) throw new AccessDeniedException("Authenticated user is required");
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emittersByUser.computeIfAbsent(recipient, key -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> unregister(recipient, emitter));
        emitter.onTimeout(() -> unregister(recipient, emitter));
        emitter.onError(error -> unregister(recipient, emitter));
        sendToEmitter(recipient, emitter, "connected", Map.of(
            "connected", true,
            "emittedAt", nowUtc()
        ), null);
        return emitter;
    }

    public void publishDialogsChanged(String reason, String ticketId) {
        if (!hasUniqueNonGeneralTicket(ticketId)) {
            return;
        }
        publishTicketEvent("dialogs_changed", basePayload(reason, ticketId, null), ticketId);
    }

    public void publishDialogHistoryChanged(String ticketId, Long channelId, String reason) {
        if (!hasUniqueNonGeneralTicket(ticketId)) {
            return;
        }
        publishTicketEvent("dialog_history_changed", basePayload(reason, ticketId, channelId), ticketId);
    }

    public void publishNotificationsChanged(String userIdentity, String reason) {
        if (!StringUtils.hasText(userIdentity)) {
            return;
        }
        publishToUser(userIdentity, "notifications_changed", basePayload(reason, null, null));
    }

    public void publishScopedDialogsChanged(String reason, UiEventTicketScope scope) {
        if (scope != null && scope.isValid() && ticketAccess.isCurrentScope(scope)) {
            publishCapturedTicketEvent("dialogs_changed", basePayload(reason, scope.ticketId(), null), scope);
        }
    }

    public void publishScopedDialogHistoryChanged(UiEventTicketScope scope, Long channelId, String reason) {
        if (scope != null && scope.isValid() && ticketAccess.isCurrentScope(scope)) {
            publishCapturedTicketEvent("dialog_history_changed", basePayload(reason, scope.ticketId(), channelId), scope);
        }
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
        deliverToAll("ping", payload, null);
    }

    void deliverFanoutEvent(UiEventFanoutEnvelope envelope) {
        if (envelope == null || !StringUtils.hasText(envelope.eventName())) {
            return;
        }

        if (StringUtils.hasText(envelope.targetUser())) {
            deliverToUser(
                normalizeIdentity(envelope.targetUser()),
                envelope.eventName().trim(),
                envelope.payload(),
                envelope.ticketScope()
            );
            return;
        }

        deliverToAll(envelope.eventName().trim(), envelope.payload(), envelope.ticketScope());
    }

    private void publishTicketEvent(String eventName, Map<String, Object> payload, String ticketId) {
        ticketAccess.captureScope(ticketId).ifPresent(scope -> publishCapturedTicketEvent(eventName, payload, scope));
    }

    private void publishCapturedTicketEvent(String eventName, Map<String, Object> payload, UiEventTicketScope scope) {
        if (!fanoutPublisher.publish(null, eventName, payload, scope)) {
            deliverToAll(eventName, payload, scope);
        }
    }

    private void publishToAll(String eventName, Map<String, Object> payload) {
        if (!fanoutPublisher.publish(null, eventName, payload)) {
            deliverToAll(eventName, payload, null);
        }
    }

    private void publishToUser(String userIdentity, String eventName, Map<String, Object> payload) {
        String identity = normalizeIdentity(userIdentity);
        if (!fanoutPublisher.publish(identity, eventName, payload)) {
            deliverToUser(identity, eventName, payload, null);
        }
    }

    private void deliverToAll(String eventName, Map<String, Object> payload, UiEventTicketScope scope) {
        if (!canDeliverLegacyEvent(eventName, payload, scope)) {
            return;
        }
        emittersByUser.forEach((recipient, emitters) -> {
            if (emitters == null || emitters.isEmpty()) {
                return;
            }
            for (SseEmitter emitter : emitters) {
                sendToEmitter(recipient, emitter, eventName, payload, scope);
            }
        });
    }

    private void deliverToUser(String identity, String eventName, Map<String, Object> payload, UiEventTicketScope scope) {
        if (!canDeliverLegacyEvent(eventName, payload, scope)) {
            return;
        }
        emittersByUser.forEach((recipient, emitters) -> {
            if (recipient.routingKey().equals(identity)) {
                for (SseEmitter emitter : emitters) {
                    sendToEmitter(recipient, emitter, eventName, payload, scope);
                }
            }
        });
    }

    private void sendToEmitter(UiEventRecipient recipient,
                               SseEmitter emitter,
                               String eventName,
                               Map<String, Object> payload,
                               UiEventTicketScope scope) {
        if (isTicketBound(eventName, payload, scope)) {
            Object ticketId = payload != null ? payload.get("ticketId") : null;
            if (!(ticketId instanceof String rawId) || scope == null || !rawId.equals(scope.ticketId())
                    || !ticketAccess.canReadTicketForRecipient(recipient, scope)) {
                return;
            }
        }
        try {
            emitter.send(SseEmitter.event()
                .name(eventName)
                .data(payload));
        } catch (IOException | IllegalStateException ex) {
            unregister(recipient, emitter);
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // emitter is already closed
            }
        }
    }

    private void unregister(UiEventRecipient recipient, SseEmitter emitter) {
        CopyOnWriteArraySet<SseEmitter> emitters = emittersByUser.get(recipient);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUser.remove(recipient, emitters);
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
            ? userIdentity.toLowerCase(Locale.ROOT)
            : "anonymous";
    }

    /** Recheck at delivery too: queued Redis envelopes and local fallback can outlive the producer check. */
    private boolean canDeliverLegacyEvent(String eventName, Map<String, Object> payload, UiEventTicketScope scope) {
        if (!isTicketBound(eventName, payload, scope)) {
            return true;
        }
        Object ticketId = payload != null ? payload.get("ticketId") : null;
        return scope != null && scope.isValid() && ticketId instanceof String rawId
            && rawId.equals(scope.ticketId()) && hasUniqueNonGeneralTicket(rawId);
    }

    private boolean isTicketBound(String eventName, Map<String, Object> payload, UiEventTicketScope scope) {
        return scope != null || "dialogs_changed".equals(eventName) || "dialog_history_changed".equals(eventName)
            || (payload != null && payload.containsKey("ticketId"));
    }

    private boolean hasUniqueNonGeneralTicket(String ticketId) {
        return StringUtils.hasText(ticketId) && legacyTicketIdJdbcGuard.hasUniqueNonGeneralTicket(ticketId);
    }

    private String nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }
}
