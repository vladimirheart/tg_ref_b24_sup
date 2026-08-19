package com.example.panel.service.integration;

import com.example.panel.config.IntegrationRabbitProperties;
import com.example.panel.service.RuntimeCoordinationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OutboundFeedbackPromptPublishOutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboundFeedbackPromptPublishOutboxService.class);
    private static final Duration DISPATCH_LEASE_TTL = Duration.ofSeconds(45);
    private static final Duration STALE_PROCESSING_TIMEOUT = Duration.ofMinutes(5);
    private static final int BATCH_SIZE = 50;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final IntegrationRabbitProperties rabbitProperties;
    private final RuntimeCoordinationService runtimeCoordinationService;

    public OutboundFeedbackPromptPublishOutboxService(JdbcTemplate jdbcTemplate,
                                                      ObjectMapper objectMapper,
                                                      RabbitTemplate rabbitTemplate,
                                                      IntegrationRabbitProperties rabbitProperties,
                                                      RuntimeCoordinationService runtimeCoordinationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitProperties = rabbitProperties;
        this.runtimeCoordinationService = runtimeCoordinationService;
    }

    public void enqueue(OutboundFeedbackPromptEvent event,
                        String routingKey) {
        if (event == null || !StringUtils.hasText(event.eventId()) || !StringUtils.hasText(routingKey)) {
            throw new IllegalArgumentException("Outbound feedback prompt outbox requires event id and routing key.");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO integration_transport_outbox (
                    event_id,
                    transport_source,
                    event_kind,
                    exchange_name,
                    routing_key,
                    payload_json,
                    channel_id,
                    user_id,
                    ticket_id,
                    request_id,
                    status,
                    attempt_count,
                    available_at,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            event.eventId(),
            "panel.outbound.feedback",
            normalize(event.eventType()),
            rabbitProperties.getOutboundExchange(),
            routingKey.trim(),
            toJson(event),
            event.channelId(),
            event.userId(),
            normalize(event.ticketId()),
            event.requestId(),
            "queued",
            0,
            timestamp(now),
            timestamp(now),
            timestamp(now)
        );
    }

    @Scheduled(fixedDelayString = "${panel.integration.outbox.dispatch-interval-ms:1500}")
    public void dispatchScheduled() {
        runtimeCoordinationService.runWithLease(
            "integration-transport-outbox-dispatch",
            DISPATCH_LEASE_TTL,
            this::dispatchBatch
        );
    }

    void dispatchBatch() {
        recoverStaleProcessing();
        List<String> eventIds = jdbcTemplate.query("""
                SELECT event_id
                  FROM integration_transport_outbox
                 WHERE status IN ('queued', 'failed')
                   AND (available_at IS NULL OR available_at <= CURRENT_TIMESTAMP)
                 ORDER BY created_at ASC, event_id ASC
                 LIMIT ?
                """,
            (rs, rowNum) -> rs.getString("event_id"),
            BATCH_SIZE
        );
        for (String eventId : eventIds) {
            if (!claim(eventId)) {
                continue;
            }
            OutboxEntry entry = load(eventId);
            if (entry == null) {
                continue;
            }
            try {
                publish(entry);
                markPublished(eventId);
            } catch (Exception ex) {
                markFailed(entry, ex);
            }
        }
    }

    private void recoverStaleProcessing() {
        jdbcTemplate.update("""
                UPDATE integration_transport_outbox
                   SET status = 'queued',
                       processing_started_at = NULL,
                       available_at = CURRENT_TIMESTAMP,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE status = 'processing'
                   AND processing_started_at IS NOT NULL
                   AND processing_started_at < ?
                """,
            timestamp(OffsetDateTime.now(ZoneOffset.UTC).minus(STALE_PROCESSING_TIMEOUT))
        );
    }

    private boolean claim(String eventId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return jdbcTemplate.update("""
                UPDATE integration_transport_outbox
                   SET status = 'processing',
                       attempt_count = attempt_count + 1,
                       processing_started_at = ?,
                       updated_at = ?
                 WHERE event_id = ?
                   AND status IN ('queued', 'failed')
                   AND (available_at IS NULL OR available_at <= ?)
                """,
            timestamp(now),
            timestamp(now),
            eventId,
            timestamp(now)
        ) > 0;
    }

    private OutboxEntry load(String eventId) {
        List<OutboxEntry> rows = jdbcTemplate.query("""
                SELECT event_id,
                       event_kind,
                       exchange_name,
                       routing_key,
                       payload_json,
                       channel_id,
                       user_id,
                       ticket_id,
                       request_id,
                       attempt_count
                  FROM integration_transport_outbox
                 WHERE event_id = ?
                """,
            (rs, rowNum) -> new OutboxEntry(
                rs.getString("event_id"),
                rs.getString("event_kind"),
                rs.getString("exchange_name"),
                rs.getString("routing_key"),
                rs.getString("payload_json"),
                nullableLong(rs, "channel_id"),
                nullableLong(rs, "user_id"),
                rs.getString("ticket_id"),
                nullableLong(rs, "request_id"),
                rs.getInt("attempt_count")
            ),
            eventId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void publish(OutboxEntry entry) throws Exception {
        OutboundFeedbackPromptEvent event = objectMapper.readValue(entry.payloadJson(), OutboundFeedbackPromptEvent.class);
        CorrelationData correlationData = new CorrelationData(entry.eventId());
        rabbitTemplate.convertAndSend(
            entry.exchangeName(),
            entry.routingKey(),
            event,
            message -> {
                message.getMessageProperties().setMessageId(entry.eventId());
                message.getMessageProperties().setCorrelationId(entry.eventId());
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            },
            correlationData
        );
        awaitBrokerConfirm(correlationData);
        log.info("Published feedback prompt outbox event {} for request {}", entry.eventId(), entry.requestId());
    }

    private void awaitBrokerConfirm(CorrelationData correlationData) throws Exception {
        CorrelationData.Confirm confirm;
        try {
            confirm = correlationData.getFuture().get(15, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        }
        if (correlationData.getReturned() != null) {
            throw new IllegalStateException("RabbitMQ returned publish for event " + correlationData.getId());
        }
        if (confirm == null || !confirm.isAck()) {
            String reason = confirm != null ? confirm.getReason() : "missing confirm";
            throw new IllegalStateException("RabbitMQ publish was not acknowledged for event "
                + correlationData.getId() + ": " + reason);
        }
    }

    private void markPublished(String eventId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("""
                UPDATE integration_transport_outbox
                   SET status = 'published',
                       last_error = NULL,
                       processing_started_at = NULL,
                       published_at = ?,
                       updated_at = ?
                 WHERE event_id = ?
                """,
            timestamp(now),
            timestamp(now),
            eventId
        );
    }

    private void markFailed(OutboxEntry entry,
                            Exception exception) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime retryAt = now.plusSeconds(Math.min(300L, Math.max(5L, entry.attemptCount() * 5L)));
        jdbcTemplate.update("""
                UPDATE integration_transport_outbox
                   SET status = 'failed',
                       last_error = ?,
                       processing_started_at = NULL,
                       available_at = ?,
                       updated_at = ?
                 WHERE event_id = ?
                """,
            truncateError(exception),
            timestamp(retryAt),
            timestamp(now),
            entry.eventId()
        );
        log.warn("Failed to publish feedback prompt outbox event {}: {}", entry.eventId(), exception.getMessage());
    }

    private String toJson(OutboundFeedbackPromptEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize feedback prompt outbox event " + event.eventId(), ex);
        }
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Timestamp timestamp(OffsetDateTime value) {
        return value == null ? null : Timestamp.from(value.toInstant());
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String truncateError(Exception exception) {
        String message = exception == null ? "" : String.valueOf(exception.getMessage());
        if (message.length() <= 2000) {
            return message;
        }
        return message.substring(0, 2000);
    }

    private record OutboxEntry(String eventId,
                               String eventKind,
                               String exchangeName,
                               String routingKey,
                               String payloadJson,
                               Long channelId,
                               Long userId,
                               String ticketId,
                               Long requestId,
                               int attemptCount) {
    }
}
