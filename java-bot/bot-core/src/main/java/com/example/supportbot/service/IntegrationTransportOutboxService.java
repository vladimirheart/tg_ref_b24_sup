package com.example.supportbot.service;

import com.example.supportbot.config.IntegrationRabbitProperties;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
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
public class IntegrationTransportOutboxService {

    private static final Logger log = LoggerFactory.getLogger(IntegrationTransportOutboxService.class);
    private static final Duration STALE_PROCESSING_TIMEOUT = Duration.ofMinutes(5);
    private static final int BATCH_SIZE = 50;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectMapper transportObjectMapper;
    private final RabbitTemplate rabbitTemplate;

    public IntegrationTransportOutboxService(JdbcTemplate jdbcTemplate,
                                             ObjectMapper objectMapper,
                                             RabbitTemplate rabbitTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.transportObjectMapper = objectMapper.copy().registerModule(offsetDateTimeModule());
        this.rabbitTemplate = rabbitTemplate;
        ensureSchema();
    }

    public void enqueueInboundClientMessage(InboundClientMessageEvent event,
                                            String routingKey,
                                            IntegrationRabbitProperties properties) {
        enqueue(
            event.eventId(),
            "bot.inbound.client-message",
            event.eventKind(),
            properties.getInboundExchange(),
            routingKey,
            event,
            event.channelId(),
            event.userId(),
            event.ticketId(),
            null
        );
    }

    public void enqueueConversationTicketCreated(ConversationTicketCreatedEvent event,
                                                 IntegrationRabbitProperties properties) {
        enqueue(
            event.eventId(),
            "bot.inbound.ticket-created",
            event.eventKind(),
            properties.getInboundExchange(),
            properties.getRoutingTicketCreated(),
            event,
            event.channelId(),
            event.userId(),
            event.ticketId(),
            null
        );
    }

    @Scheduled(fixedDelayString = "${app.integration.outbox.dispatch-interval-ms:1500}")
    public void dispatchScheduled() {
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
                markPublished(entry.eventId());
            } catch (Exception ex) {
                markFailed(entry, ex);
            }
        }
    }

    private void enqueue(String eventId,
                         String transportSource,
                         String eventKind,
                         String exchangeName,
                         String routingKey,
                         Object payload,
                         Long channelId,
                         Long userId,
                         String ticketId,
                         Long requestId) {
        if (!StringUtils.hasText(eventId) || !StringUtils.hasText(exchangeName) || !StringUtils.hasText(routingKey)) {
            throw new IllegalArgumentException("Integration transport outbox requires event id, exchange, and routing key.");
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
            eventId.trim(),
            normalize(transportSource),
            normalize(eventKind),
            exchangeName.trim(),
            routingKey.trim(),
            toJson(payload, eventId),
            channelId,
            userId,
            normalize(ticketId),
            requestId,
            "queued",
            0,
            timestamp(now),
            timestamp(now),
            timestamp(now)
        );
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
                       ticket_id,
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
                rs.getString("ticket_id"),
                rs.getInt("attempt_count")
            ),
            eventId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void publish(OutboxEntry entry) throws Exception {
        Object payload = switch (normalize(entry.eventKind())) {
            case "client_message.active_ticket" ->
                transportObjectMapper.readValue(entry.payloadJson(), InboundClientMessageEvent.class);
            case "ticket.created.initial_contact" ->
                transportObjectMapper.readValue(entry.payloadJson(), ConversationTicketCreatedEvent.class);
            default -> throw new IllegalStateException("Unsupported integration transport outbox event kind: " + entry.eventKind());
        };
        CorrelationData correlationData = new CorrelationData(entry.eventId());
        rabbitTemplate.convertAndSend(
            entry.exchangeName(),
            entry.routingKey(),
            payload,
            message -> {
                message.getMessageProperties().setMessageId(entry.eventId());
                message.getMessageProperties().setCorrelationId(entry.eventId());
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            },
            correlationData
        );
        awaitBrokerConfirm(correlationData);
        log.info("Published integration transport outbox event {} ({}) for ticket {}",
            entry.eventId(), entry.eventKind(), entry.ticketId());
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
        log.warn("Failed to publish integration transport outbox event {}: {}", entry.eventId(), exception.getMessage());
    }

    private void ensureSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS integration_transport_outbox (
                    event_id TEXT PRIMARY KEY,
                    transport_source TEXT NOT NULL,
                    event_kind TEXT NOT NULL,
                    exchange_name TEXT NOT NULL,
                    routing_key TEXT NOT NULL,
                    payload_json TEXT NOT NULL,
                    channel_id BIGINT,
                    user_id BIGINT,
                    ticket_id TEXT,
                    request_id BIGINT,
                    status TEXT NOT NULL,
                    attempt_count INTEGER NOT NULL DEFAULT 0,
                    last_error TEXT,
                    available_at TIMESTAMP,
                    processing_started_at TIMESTAMP,
                    published_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_integration_transport_outbox_status
                    ON integration_transport_outbox(status, available_at, updated_at)
                """);
    }

    private String toJson(Object payload,
                          String eventId) {
        try {
            return transportObjectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize integration transport outbox event " + eventId, ex);
        }
    }

    private SimpleModule offsetDateTimeModule() {
        SimpleModule module = new SimpleModule("integration-transport-offset-datetime");
        module.addSerializer(OffsetDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(OffsetDateTime value,
                                  JsonGenerator gen,
                                  SerializerProvider serializers) throws java.io.IOException {
                if (value == null) {
                    gen.writeNull();
                    return;
                }
                gen.writeString(value.toString());
            }
        });
        module.addDeserializer(OffsetDateTime.class, new JsonDeserializer<>() {
            @Override
            public OffsetDateTime deserialize(JsonParser p,
                                              DeserializationContext ctxt) throws java.io.IOException {
                String value = p.getValueAsString();
                if (!StringUtils.hasText(value)) {
                    return null;
                }
                return OffsetDateTime.parse(value.trim());
            }
        });
        return module;
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
                               String ticketId,
                               int attemptCount) {
    }
}
