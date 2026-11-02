package com.example.supportbot.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

/** Private durable provider acceptance. No SDK, HTTP, current catalog lookup or business authorization. */
@Service
public class OutboundDeliveryReceiptService {
    private static final String SAME_CONTEXT = """
             d.event_kind IS NOT DISTINCT FROM a.event_kind
         AND d.routing_key IS NOT DISTINCT FROM a.routing_key
         AND d.channel_id IS NOT DISTINCT FROM a.channel_id
         AND d.user_id IS NOT DISTINCT FROM a.user_id
         AND d.ticket_id IS NOT DISTINCT FROM a.ticket_id
         AND d.request_id IS NOT DISTINCT FROM a.request_id
        """;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public OutboundDeliveryReceiptService(JdbcTemplate jdbc) {
        this.jdbc=jdbc;
        transactions=new TransactionTemplate(new JdbcTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transactions.setTimeout(10);
        // Fail before any provider call if the separately controlled backend migration is missing.
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('integration_outbound_delivery_receipts') IS NOT NULL",Boolean.class))) {
            throw new IllegalStateException("Outbound receipt schema V85 must be installed before starting bot children.");
        }
    }
    public String prepare(String eventId,OutboundSenderIdentity sender,String prompt) {
        requireSender(sender);
        if(eventId==null || eventId.isBlank() || prompt==null || prompt.isBlank())throw new IllegalArgumentException("Missing receipt attempt context.");
        String attempt=UUID.randomUUID().toString(),digest=digest(prompt);
        Timestamp now=Timestamp.from(Instant.now());
        return persist(() -> transactions.execute(tx -> {
            jdbc.update("""
                INSERT INTO integration_outbound_delivery_receipts
                    (event_id,attempt_id,event_kind,routing_key,channel_id,user_id,ticket_id,request_id,
                     platform,sender_fingerprint,prompt_sha256,prepared_at)
                SELECT d.event_id,?::uuid,d.event_kind,d.routing_key,d.channel_id,d.user_id,d.ticket_id,d.request_id,?,?,?,?
                  FROM integration_outbound_event_deliveries d
                 WHERE d.event_id=? AND d.status='sending' AND d.channel_id=? AND d.user_id IS NOT NULL
                ON CONFLICT(event_id) DO NOTHING
                """,attempt,sender.platform(),sender.tokenSha256(),digest,now,eventId.trim(),sender.channelId());
            Boolean matches=jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM integration_outbound_delivery_receipts a
                    JOIN integration_outbound_event_deliveries d ON d.event_id=a.event_id
                 WHERE a.event_id=? AND a.attempt_id=?::uuid AND a.channel_id=? AND a.platform=?
                   AND a.sender_fingerprint=? AND a.prompt_sha256=? AND d.status='sending' AND
                """+SAME_CONTEXT+")",Boolean.class,eventId.trim(),attempt,sender.channelId(),sender.platform(),sender.tokenSha256(),digest);
            if(!Boolean.TRUE.equals(matches))throw new IllegalStateException("Receipt attempt has no exact sending claim.");
            return attempt;
        }));
    }
    public void record(String eventId,String attempt,OutboundSenderIdentity sender,Long userId,ProviderSendResult result) {
        requireSender(sender);
        if(eventId==null || eventId.isBlank() || attempt==null || userId==null || result==null || !result.accepted() || result.messageId()==null) {
            throw new IllegalArgumentException("Incomplete provider acceptance receipt.");
        }
        UUID.fromString(attempt);
        Timestamp now=Timestamp.from(Instant.now());
        persist(() -> transactions.execute(tx -> {
            jdbc.update("""
                UPDATE integration_outbound_delivery_receipts a SET provider_message_id=?,accepted_at=?
                  FROM integration_outbound_event_deliveries d
                 WHERE a.event_id=d.event_id AND a.event_id=? AND a.attempt_id=?::uuid
                   AND a.channel_id=? AND a.platform=? AND a.sender_fingerprint=? AND a.user_id=?
                   AND a.provider_message_id IS NULL AND d.status IN ('sending','uncertain') AND
                """+SAME_CONTEXT,result.messageId(),now,eventId.trim(),attempt,sender.channelId(),sender.platform(),sender.tokenSha256(),userId);
            Boolean recorded=jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM integration_outbound_delivery_receipts a
                    JOIN integration_outbound_event_deliveries d ON a.event_id=d.event_id
                 WHERE a.event_id=? AND a.attempt_id=?::uuid AND a.channel_id=? AND a.platform=?
                   AND a.sender_fingerprint=? AND a.user_id=? AND a.provider_message_id=? AND a.accepted_at IS NOT NULL
                   AND d.status IN ('sending','uncertain','delivered') AND
                """+SAME_CONTEXT+")",Boolean.class,eventId.trim(),attempt,sender.channelId(),sender.platform(),sender.tokenSha256(),userId,result.messageId());
            if(!Boolean.TRUE.equals(recorded))throw new IllegalStateException("Provider receipt does not match its immutable attempt.");
            return true;
        }));
    }
    /** Resolves only an existing committed positive receipt, never a timeout, missing row or old failed state. */
    public boolean reconcile(String eventId) {
        if(eventId==null || eventId.isBlank())return false;
        return Boolean.TRUE.equals(persist(() -> transactions.execute(tx -> jdbc.update("""
                UPDATE integration_outbound_event_deliveries d
                   SET status='delivered',delivered_at=a.accepted_at,last_error=NULL,updated_at=?
                  FROM integration_outbound_delivery_receipts a
                 WHERE d.event_id=a.event_id AND d.event_id=? AND d.status IN ('sending','uncertain')
                   AND a.provider_message_id IS NOT NULL AND a.accepted_at IS NOT NULL AND
                """+SAME_CONTEXT,Timestamp.from(Instant.now()),eventId.trim())==1)));
    }
    private static void requireSender(OutboundSenderIdentity sender) {
        if(sender==null || sender.channelId()==null || sender.channelId()<=0 || sender.platform()==null
            || !java.util.Set.of("telegram","vk","max").contains(sender.platform())
            || sender.tokenSha256()==null || !sender.tokenSha256().matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid receipt sender identity.");
    }
    private static String digest(String text) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static <T> T persist(Supplier<T> action) {
        for(int attempt=1;;attempt++){
            try{return action.get();}
            catch(DataAccessException|TransactionException failure){if(attempt>=3 || Thread.currentThread().isInterrupted())throw failure;}
        }
    }
}
