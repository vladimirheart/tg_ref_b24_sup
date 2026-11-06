package com.example.supportbot.service;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OutboundTransportDeliveryLedgerService {

    private static final int CONFIRMATION_ATTEMPTS = 3;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;

    public OutboundTransportDeliveryLedgerService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        transactions = new TransactionTemplate(new JdbcTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource())));
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transactions.setTimeout(10);
        ensureSchema();
    }

    public boolean beginDelivery(String eventId,
                                 String eventKind,
                                 String routingKey,
                                 Long channelId,
                                 Long userId,
                                 String ticketId,
                                 Long requestId) {
        if (!StringUtils.hasText(eventId)) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return Boolean.TRUE.equals(transactions.execute(transaction -> {
            int inserted = jdbcTemplate.update("""
                    INSERT INTO integration_outbound_event_deliveries (
                        event_id,
                        event_kind,
                        routing_key,
                        channel_id,
                        user_id,
                        ticket_id,
                        request_id,
                        status,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (event_id) DO NOTHING
                    """,
                eventId.trim(),
                trim(eventKind),
                trim(routingKey),
                channelId,
                userId,
                StringUtils.hasText(ticketId) ? ticketId : null,
                requestId,
                "processing",
                timestamp(now),
                timestamp(now)
            );
            if (inserted == 1) return true;
            DeliveryRecord existing = findRecord(eventId);
            // Old 'failed' rows have no pre-provider provenance. Neither they nor stale claims
            // prove non-delivery; only a failure recorded before our durable handoff is retryable.
            if (existing == null || !"retryable".equals(existing.status())) return false;
            int updated = jdbcTemplate.update("""
                    UPDATE integration_outbound_event_deliveries
                       SET event_kind = ?,
                           routing_key = ?,
                           channel_id = ?,
                           user_id = ?,
                           ticket_id = ?,
                           request_id = ?,
                           status = ?,
                           last_error = NULL,
                           updated_at = ?
                     WHERE event_id = ?
                       AND status = 'retryable'
                       AND updated_at IS NOT DISTINCT FROM ?
                    """,
                trim(eventKind),
                trim(routingKey),
                channelId,
                userId,
                StringUtils.hasText(ticketId) ? ticketId : null,
                requestId,
                "processing",
                timestamp(now),
                eventId.trim(),
                timestamp(existing.updatedAt())
            );
            return updated > 0;
        }));
    }

    /** Commits before provider I/O, even if the caller has a transaction that later rolls back. */
    public void markSending(String eventId) {
        if (!StringUtils.hasText(eventId)) throw new IllegalArgumentException("Missing outbound delivery claim.");
        Integer changed = transactions.execute(transaction -> jdbcTemplate.update("""
                UPDATE integration_outbound_event_deliveries
                   SET status='sending', updated_at=?, last_error=NULL
                 WHERE event_id=? AND status='processing'
                """, timestamp(OffsetDateTime.now(ZoneOffset.UTC)),eventId.trim()));
        if (!Integer.valueOf(1).equals(changed)) throw new IllegalStateException("Outbound delivery claim is not ready for provider handoff.");
    }

    public void markUncertain(String eventId, Exception exception) {
        if (!StringUtils.hasText(eventId)) return;
        transactions.executeWithoutResult(transaction -> jdbcTemplate.update("""
                UPDATE integration_outbound_event_deliveries
                   SET status='uncertain', last_error=?, updated_at=?
                 WHERE event_id=? AND status='sending'
                """, truncateError(exception),timestamp(OffsetDateTime.now(ZoneOffset.UTC)),eventId.trim()));
    }

    /**
     * Confirms the provider success already observed by this dispatcher. Only the ledger transaction
     * is repeated: never provider I/O, admission, claim acquisition or resolution of an unknown result.
     */
    public void markDelivered(String eventId) {
        if (!StringUtils.hasText(eventId)) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (int attempt = 1; ; attempt++) {
            try {
                Boolean confirmed = transactions.execute(transaction -> {
                    int changed = jdbcTemplate.update("""
                            UPDATE integration_outbound_event_deliveries
                               SET status = ?,
                                   delivered_at = ?,
                                   last_error = NULL,
                                   updated_at = ?
                             WHERE event_id = ? AND status = 'sending'
                            """,
                        "delivered",
                        timestamp(now),
                        timestamp(now),
                        eventId.trim()
                    );
                    if (changed == 1) return true;
                    // A previous commit can succeed even when its acknowledgement is lost.
                    // Recognizing it is read-only: preserve the original timestamps and metadata.
                    return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                            SELECT EXISTS (
                                SELECT 1 FROM integration_outbound_event_deliveries
                                 WHERE event_id = ? AND status = 'delivered' AND delivered_at IS NOT NULL
                            )
                            """, Boolean.class, eventId.trim()));
                });
                if (!Boolean.TRUE.equals(confirmed)) throw new IllegalStateException("Outbound provider result has no sending claim.");
                return;
            } catch (DataAccessException | TransactionException persistenceFailure) {
                // Bounded, independent transactions; a programming/ownership error must not loop.
                if (attempt >= CONFIRMATION_ATTEMPTS || Thread.currentThread().isInterrupted()) throw persistenceFailure;
            }
        }
    }

    public void markFailed(String eventId, Exception exception) {
        if (!StringUtils.hasText(eventId)) {
            return;
        }
        transactions.executeWithoutResult(transaction -> jdbcTemplate.update("""
                UPDATE integration_outbound_event_deliveries
                   SET status = ?,
                       last_error = ?,
                       updated_at = ?
                 WHERE event_id = ? AND status = 'processing'
                """,
            "retryable",
            truncateError(exception),
            timestamp(OffsetDateTime.now(ZoneOffset.UTC)),
            eventId.trim()
        ));
    }

    private DeliveryRecord findRecord(String eventId) {
        List<DeliveryRecord> rows = jdbcTemplate.query("""
                SELECT status, updated_at
                  FROM integration_outbound_event_deliveries
                 WHERE event_id = ?
                """,
            (rs, rowNum) -> new DeliveryRecord(
                rs.getString("status"),
                rs.getTimestamp("updated_at") != null
                    ? rs.getTimestamp("updated_at").toInstant().atOffset(ZoneOffset.UTC)
                    : null
            ),
            eventId.trim()
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void ensureSchema() {
        if (com.example.supportbot.config.ChildTransportDatabase.isRestricted(jdbcTemplate)) return;
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS integration_outbound_event_deliveries (
                    event_id TEXT PRIMARY KEY,
                    event_kind TEXT,
                    routing_key TEXT,
                    channel_id BIGINT,
                    user_id BIGINT,
                    ticket_id TEXT,
                    request_id BIGINT,
                    status TEXT NOT NULL,
                    last_error TEXT,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    delivered_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_outbound_event_deliveries_status
                ON integration_outbound_event_deliveries(status, updated_at)
                """);
    }

    private Timestamp timestamp(OffsetDateTime value) {
        return value == null ? null : Timestamp.from(value.toInstant());
    }

    private String trim(String value) {
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

    private record DeliveryRecord(String status, OffsetDateTime updatedAt) {
    }
}
