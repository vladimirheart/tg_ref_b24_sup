package com.example.panel.service;

import org.springframework.security.core.Authentication;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.runtime.UiEventFanoutMode;
import com.example.panel.runtime.UiEventFanoutProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** GENERAL invalidations only: no raw dialogue or owner identifiers reach a browser. */
@Service
public class GeneralTicketRealtimeService implements MessageListener {
    private static final Set<String> EVENTS = Set.of("ticket_created", "client_message_created", "client_message_edited",
        "operator_message_edited", "feedback_created", "ticket_closed_auto", "ticket_closed", "ticket_reopened", "ticket_status_changed",
        "operator_reply_changed", "assignment_changed", "participants_changed");
    private static final String OWNER = """
        ticket.user_id = :ownerId AND ticket.ticket_id = :ticketId AND ticket.ticket_public_id = :publicId
        AND ticket.is_general = TRUE AND ticket.business_id IS NULL
        AND 1 = (SELECT COUNT(*) FROM tickets collision WHERE collision.ticket_id = ticket.ticket_id)
        """;
    private final NamedParameterJdbcTemplate jdbc;
    private final MultibusinessFeatureFlags flags;
    private final UiEventFanoutProperties fanout;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<GeneralTicketActor, CopyOnWriteArraySet<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public GeneralTicketRealtimeService(JdbcTemplate jdbc, MultibusinessFeatureFlags flags, UiEventFanoutProperties fanout,
                                       StringRedisTemplate redis, ObjectMapper mapper) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc); this.flags = flags; this.fanout = fanout;
        this.redis = redis; this.mapper = mapper;
    }
    public SseEmitter connect(Authentication authentication) {
        return connect(GeneralTicketActor.from(authentication));
    }
    private SseEmitter connect(GeneralTicketActor identity) {
        GeneralTicketActor actor = identity;
        if (!canRead(actor, null)) throw new AccessDeniedException("Нет доступа к обращениям без бизнеса");
        var emitter = new SseEmitter(0L);
        emittersByUser.computeIfAbsent(actor, key -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> unregister(actor, emitter));
        emitter.onTimeout(() -> unregister(actor, emitter));
        emitter.onError(error -> unregister(actor, emitter));
        send(actor, emitter, "connected", Map.of("connected", true), null);
        return emitter;
    }
    public String redisChannel() { return fanout.resolvedChannel() + ":general:v1"; }

    public void publish(UiEventTicketScope expected, String eventType) {
        var scope = GeneralTicketEventScope.from(expected);
        String type = eventType == null ? "" : eventType.trim().toLowerCase(Locale.ROOT);
        if (scope == null || !EVENTS.contains(type) || !isCurrent(scope)) return;
        var event = new Event(1, scope, type);
        if (fanout.resolvedMode() == UiEventFanoutMode.REDIS) {
            try { redis.convertAndSend(redisChannel(), mapper.writeValueAsString(event)); }
            catch (Exception ex) { throw new IllegalStateException("GENERAL realtime fanout failed"); }
        } else deliver(event);
    }
    @Override public void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null || message.getBody().length > 65536) return;
        try { deliver(mapper.readValue(new String(message.getBody(), StandardCharsets.UTF_8), Event.class)); }
        catch (Exception ignored) { /* Untrusted envelopes fail closed, without logging payloads. */ }
    }
    void deliver(Event event) {
        if (event == null || event.version() != 1 || !EVENTS.contains(event.eventType() == null ? "" : event.eventType())
                || !isCurrent(event.scope())) return;
        var payload = Map.<String, Object>of("ticketPublicId", event.scope().ticketPublicId(),
            "reason", event.eventType(), "emittedAt", Instant.now().toString());
        emittersByUser.forEach((actor, emitters) -> {
            for (SseEmitter emitter : emitters) send(actor, emitter, "general_tickets_changed", payload, event.scope());
        });
    }
    void sendHeartbeat() {
        emittersByUser.forEach((actor, emitters) -> {
            for (SseEmitter emitter : emitters) send(actor, emitter, "ping", Map.of("emittedAt", Instant.now().toString()), null);
        });
    }
    private void send(GeneralTicketActor actor, SseEmitter emitter, String name, Map<String, Object> payload, GeneralTicketEventScope scope) {
        // One current snapshot per emitter, including additional connections of the same account.
        if (!canRead(actor, scope)) {
            if (scope == null) {
                try { emitter.send(SseEmitter.event().name("general_access_revoked").data(Map.of("refresh", false))); }
                catch (IOException | IllegalStateException ignored) { }
                close(actor, emitter);
            }
            return;
        }
        try { emitter.send(SseEmitter.event().name(name).data(payload)); }
        catch (IOException | IllegalStateException ex) { close(actor, emitter); }
    }
    boolean isCurrent(GeneralTicketEventScope scope) {
        if (!enabled() || scope == null || !scope.isValid()) return false;
        try { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM tickets ticket WHERE " + OWNER + ")",
            args(scope), Boolean.class)); }
        catch (DataAccessException ex) { return false; }
    }
    boolean canRead(Authentication authentication, GeneralTicketEventScope scope) {
        return canRead(GeneralTicketActor.from(authentication),scope);
    }
    private boolean canRead(GeneralTicketActor identity, GeneralTicketEventScope scope) {
        if (!enabled() || identity == null || !identity.valid() || (scope != null && !scope.isValid())) return false;
        var args = scope == null ? new MapSqlParameterSource() : args(scope);
        String owner = scope == null ? "" : " AND EXISTS(SELECT 1 FROM tickets ticket WHERE " + OWNER + ")";
        try { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users account WHERE account.id=:authenticatedActorId AND account.username=:identity AND "
            + GeneralTicketAccessService.READ_ACCOUNT + owner + ")", identity.bind(args), Boolean.class)); }
        catch (DataAccessException ex) { return false; }
    }
    private boolean enabled() { return flags.isFoundationEnabled() && flags.isGeneralTicketQueue(); }
    // Account identity is kept exact by GeneralTicketActor.
    private MapSqlParameterSource args(GeneralTicketEventScope scope) {
        return new MapSqlParameterSource("ownerId", scope.ticketUserId()).addValue("ticketId", scope.ticketId()).addValue("publicId", scope.ticketPublicId());
    }
    private void unregister(GeneralTicketActor actor, SseEmitter emitter) {
        emittersByUser.computeIfPresent(actor, (key, entries) -> { entries.remove(emitter); return entries.isEmpty() ? null : entries; });
    }
    private void close(GeneralTicketActor actor, SseEmitter emitter) {
        unregister(actor, emitter);
        try { emitter.complete(); } catch (RuntimeException ignored) { /* Already disconnected. */ }
    }
    record Event(int version, GeneralTicketEventScope scope, String eventType) {
        @Override public String toString() { return "GeneralTicketRealtimeEvent[version=" + version + "]"; }
    }
}
