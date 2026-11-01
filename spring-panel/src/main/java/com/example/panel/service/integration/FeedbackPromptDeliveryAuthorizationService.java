package com.example.panel.service.integration;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Read-only recipient admission; never recaptures context from today's root. */
@Service
public class FeedbackPromptDeliveryAuthorizationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final MultibusinessFeatureFlags flags;

    public FeedbackPromptDeliveryAuthorizationService(JdbcTemplate jdbc, ObjectMapper mapper, MultibusinessFeatureFlags flags) {
        this.jdbc=jdbc;this.mapper=mapper;this.flags=flags;
    }

    public boolean authorize(OutboundFeedbackPromptEvent event, String routingKey) {
        if(event==null || !StringUtils.hasText(event.eventId()) || !StringUtils.hasText(routingKey)
            || event.channelId()==null || event.requestId()==null || event.userId()==null
            || !StringUtils.hasText(event.ticketId()) || !StringUtils.hasText(event.prompt())
            || !"feedback.prompt.dispatch".equals(event.eventType())
            || !("telegram".equals(event.platform()) || "vk".equals(event.platform()) || "max".equals(event.platform()))) return false;
        try {
            return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                  SELECT 1 FROM integration_transport_outbox outbox
                  JOIN pending_feedback_requests request ON request.id=outbox.request_id
                   AND request.user_id=outbox.user_id AND request.channel_id=outbox.channel_id
                   AND request.ticket_id=outbox.ticket_id
                  JOIN tickets owner ON owner.ticket_id=request.ticket_id AND owner.user_id=request.user_id
                   AND owner.channel_id IS NOT DISTINCT FROM request.channel_id
                   AND owner.ticket_public_id IS NOT DISTINCT FROM request.ticket_public_id
                   AND owner.business_id IS NOT DISTINCT FROM request.ticket_business_id
                   AND owner.is_general IS NOT DISTINCT FROM request.ticket_is_general
                   AND owner.feedback_context_epoch=request.ticket_context_epoch
                  JOIN channels channel ON channel.id=request.channel_id AND channel.is_active=TRUE AND channel.platform=?
                 WHERE outbox.event_id=? AND outbox.routing_key=?
                   AND outbox.transport_source='panel.outbound.feedback' AND outbox.event_kind='feedback.prompt.dispatch'
                   AND outbox.status IN ('processing','published')
                   AND outbox.user_id=? AND outbox.channel_id=? AND outbox.request_id=? AND outbox.ticket_id=?
                   AND outbox.payload_json::jsonb-'feedbackTicketContext'=?::jsonb
                   AND jsonb_typeof(outbox.payload_json::jsonb->'feedbackTicketContext')='object'
                   AND outbox.payload_json::jsonb#>>'{feedbackTicketContext,epoch}'=request.ticket_context_epoch::text
                   AND outbox.payload_json::jsonb#>'{feedbackTicketContext,publicId}'=coalesce(to_jsonb(request.ticket_public_id),'null'::jsonb)
                   AND outbox.payload_json::jsonb#>'{feedbackTicketContext,businessId}'=coalesce(to_jsonb(request.ticket_business_id),'null'::jsonb)
                   AND outbox.payload_json::jsonb#>'{feedbackTicketContext,general}'=to_jsonb(request.ticket_is_general)
                   AND (outbox.payload_json::jsonb#>>'{feedbackTicketContext,requestCreatedAt}')::timestamptz=request.created_at
                   AND request.ticket_context_epoch IS NOT NULL AND request.ticket_is_general IS NOT NULL
                   AND (request.expires_at IS NULL OR request.expires_at>clock_timestamp())
                   AND 1=(SELECT count(*) FROM tickets candidate WHERE candidate.ticket_id=request.ticket_id)
                   AND (
                     (?=FALSE AND request.ticket_business_id IS NULL AND request.ticket_is_general=FALSE
                       AND NOT EXISTS(SELECT 1 FROM business_channel_bindings b WHERE b.channel_id=channel.id AND b.is_active=TRUE)
                       AND NOT EXISTS(SELECT 1 FROM general_channel_question_template_bindings g WHERE g.channel_id=channel.id AND g.is_active=TRUE)
                       AND NOT EXISTS(SELECT 1 FROM shared_channel_general_question_template_bindings g WHERE g.channel_id=channel.id AND g.is_active=TRUE))
                     OR (?=TRUE AND (
                       (request.ticket_business_id IS NOT NULL AND request.ticket_is_general=FALSE
                         AND EXISTS(SELECT 1 FROM business_channel_bindings b JOIN businesses business ON business.id=b.business_id
                             WHERE b.channel_id=channel.id AND b.business_id=request.ticket_business_id AND b.is_active=TRUE
                               AND b.resolution_mode IN ('SINGLE_BUSINESS','EXPLICIT_SELECTION') AND business.status='active')
                         AND NOT EXISTS(SELECT 1 FROM general_channel_question_template_bindings g WHERE g.channel_id=channel.id AND g.is_active=TRUE))
                       OR (request.ticket_business_id IS NULL AND request.ticket_is_general=TRUE AND (
                         (?=TRUE AND EXISTS(SELECT 1 FROM general_channel_question_template_bindings g WHERE g.channel_id=channel.id AND g.is_active=TRUE)
                           AND NOT EXISTS(SELECT 1 FROM business_channel_bindings b WHERE b.channel_id=channel.id AND b.is_active=TRUE)
                           AND NOT EXISTS(SELECT 1 FROM shared_channel_general_question_template_bindings g WHERE g.channel_id=channel.id AND g.is_active=TRUE))
                         OR (?=TRUE AND EXISTS(SELECT 1 FROM shared_channel_general_question_template_bindings g WHERE g.channel_id=channel.id AND g.is_active=TRUE)
                           AND NOT EXISTS(SELECT 1 FROM general_channel_question_template_bindings g WHERE g.channel_id=channel.id AND g.is_active=TRUE))
                       ))
                     ))
                   )
                )
                """, Boolean.class, event.platform(),event.eventId(),routingKey,event.userId(),event.channelId(),event.requestId(),event.ticketId(),
                mapper.writeValueAsString(event),flags.isFoundationEnabled(),flags.isFoundationEnabled(),flags.isGeneralChannelResolution(),flags.isSharedChannelResolution()));
        } catch(JsonProcessingException | DataAccessException failure) {
            // Missing schema, malformed persisted metadata or unavailable DB must never authorize a provider send.
            return false;
        }
    }
}
