package com.example.panel.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;

/** Checks the selected MVC route, not a client-supplied channel header. Default deny. */
@Service
public class ChildBotRequestScopeService {
    public static final String PRINCIPAL_ATTRIBUTE = ChildBotRequestScopeService.class.getName();
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ChildBotRequestScopeService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc; this.mapper = mapper;
    }

    public void authorize(HttpServletRequest request, ChildBotCredentials.Principal child, byte[] bytes) {
        if (request == null || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Child authorization requires an owning transaction");
        }
        // Locks stay held through the controller/service transaction. No positive decision cache.
        var channels = jdbc.queryForList("""
            SELECT c.id FROM channels c JOIN bot_child_principals p ON p.channel_id=c.id
             WHERE c.id=? AND c.platform=? AND c.is_active=true AND p.enabled=true
               AND p.platform=c.platform AND p.role_name=? FOR SHARE OF c,p
            """, child.channelId(), child.platform(), child.databaseRole());
        if (channels.size() != 1) throw forbidden();
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        Object rawVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        Map<?, ?> variables = rawVariables instanceof Map<?, ?> value ? value : Map.of();
        String pattern = route instanceof String value ? value : "";
        String method = request.getMethod();
        JsonNode body;
        try { body = bytes.length == 0 ? mapper.createObjectNode() : mapper.readTree(bytes); }
        catch (Exception ex) { throw forbidden(); }
        if (body == null || !body.isObject()) throw forbidden();

        if (pattern.startsWith("/internal/api/bot/channels/{channelId}/")
                || pattern.equals("/internal/api/bot/channels/{channelId}")) {
            requireChannel(child, variables.get("channelId"));
            // A child cannot reconfigure its operator audience or use the retired unowned edit.
            if (pattern.endsWith("/support-chat") || pattern.equals("/internal/api/bot/channels/{channelId}/messages/{telegramMessageId}/client-edit")) throw forbidden();
            if (!"GET".equals(method) && !pattern.equals("/internal/api/bot/channels/{channelId}/users/{userId}/messages/{telegramMessageId}/client-edit")) throw forbidden();
        } else if (pattern.equals("/internal/api/bot/channels/resolve") && "POST".equals(method)) {
            requireChannel(child, body.get("channelId"));
            requirePlatform(child, body.get("platform"));
        } else if (pattern.startsWith("/internal/api/bot/tickets/{ticketId}")) {
            requireTicket(child, variables.get("ticketId"));
            if (!Set.of("/internal/api/bot/tickets/{ticketId}", "/internal/api/bot/tickets/{ticketId}/request-number",
                    "/internal/api/bot/tickets/{ticketId}/activity", "/internal/api/bot/tickets/{ticketId}/reopen",
                    "/internal/api/bot/tickets/{ticketId}/operator-relay",
                    "/internal/api/bot/tickets/{ticketId}/operator-messages/{telegramMessageId}").contains(pattern)) throw forbidden();
        } else if (pattern.equals("/internal/api/bot/feedback/pending/{requestId}/submit") && "POST".equals(method)) {
            long id = positive(variables.get("requestId"));
            var rows = jdbc.queryForList("SELECT ticket_id FROM pending_feedback_requests WHERE id=? AND channel_id=? FOR UPDATE", id, child.channelId());
            if (rows.size() != 1) throw forbidden();
            requireTicket(child, rows.get(0).get("ticket_id"));
        } else if (pattern.equals("/internal/api/bot/tickets/active") && "GET".equals(method)) {
            requireChannel(child, uniqueParameter(request, "channelId"));
        } else if (Set.of("/internal/api/bot/users/{userId}/last-ticket-context",
                "/internal/api/bot/users/{userId}/tickets/recent", "/internal/api/bot/users/{userId}/feedback/pending",
                "/internal/api/bot/blacklist/status", "/internal/api/bot/unblock-requests/pending-summary").contains(pattern) && "GET".equals(method)) {
            // These handlers apply the authenticated channel to the query, before LIMIT.
        } else if (pattern.equals("/internal/api/bot/blacklist/unblock-requests") && "POST".equals(method)) {
            requireChannel(child, body.get("channelId"));
        } else if (pattern.equals("/internal/api/bot/outbound/authorize-sender") && "POST".equals(method)) {
            requireChannel(child, body.get("channelId")); requirePlatform(child, body.get("platform"));
        } else if (pattern.equals("/internal/api/bot/outbound/authorize-support-chat") && "POST".equals(method)) {
            requireChannel(child,body.path("sender").get("channelId")); requirePlatform(child,body.path("sender").get("platform"));
        } else if (pattern.equals("/internal/api/bot/feedback-prompts/authorize-delivery") && "POST".equals(method)) {
            requireChannel(child, body.path("event").get("channelId")); requirePlatform(child, body.path("event").get("platform"));
            requireTicket(child, body.path("event").path("ticketId").asText(null));
        } else { throw forbidden(); }

        if (body.has("channelId")) requireChannel(child, body.get("channelId"));
        if (body.has("platform")) requirePlatform(child, body.get("platform"));
        if (request.getParameterMap().containsKey("channelId")) requireChannel(child, uniqueParameter(request, "channelId"));
        if (pattern.contains("/questionnaire-media")) {
            requireTicket(child, uniqueParameter(request, "ticketId"));
            if (pattern.endsWith("/max-batch") && !"max".equals(child.platform())
                    || pattern.endsWith("/vk-batch") && !"vk".equals(child.platform())) throw forbidden();
        }
        request.setAttribute(PRINCIPAL_ATTRIBUTE, child);
    }

    private void requireTicket(ChildBotCredentials.Principal child, Object rawId) {
        if (!(rawId instanceof String id) || id.isBlank() || id.length() > 512) throw forbidden();
        var rows = jdbc.queryForList("SELECT channel_id FROM tickets WHERE ticket_id=? FOR UPDATE", id);
        if (rows.size() != 1 || !(rows.get(0).get("channel_id") instanceof Number channel)
                || channel.longValue() != child.channelId()) throw forbidden();
    }
    private static void requireChannel(ChildBotCredentials.Principal child, Object raw) {
        if (positive(raw) != child.channelId()) throw forbidden();
    }
    private static long positive(Object raw) {
        String text;
        if (raw instanceof JsonNode node) {
            if (!node.isIntegralNumber() || !node.canConvertToLong()) throw forbidden();
            text = node.asText();
        } else text = raw instanceof String string ? string : "";
        if (!text.matches("[1-9][0-9]{0,18}")) throw forbidden();
        try { return Long.parseLong(text); } catch (NumberFormatException ex) { throw forbidden(); }
    }
    private static void requirePlatform(ChildBotCredentials.Principal child, JsonNode raw) {
        if (raw == null || !raw.isTextual() || !child.platform().equals(raw.textValue())) throw forbidden();
    }
    private static String uniqueParameter(HttpServletRequest request, String key) {
        String[] values = request.getParameterValues(key);
        if (values == null || values.length != 1) throw forbidden();
        return values[0];
    }
    public static Long channelId(HttpServletRequest request) {
        return request != null && request.getAttribute(PRINCIPAL_ATTRIBUTE) instanceof ChildBotCredentials.Principal child
            ? child.channelId() : null;
    }
    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Child request is outside its channel");
    }
}
