package com.example.panel.security;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** AMQP user_id is validated by Rabbit against the authenticated publisher, not a JSON/header claim. */
@Service
public class ChildBotInboundPrincipalService {
    private final JdbcTemplate jdbc;
    private final InternalBotApiProperties credentials;
    public ChildBotInboundPrincipalService(JdbcTemplate jdbc, InternalBotApiProperties credentials) { this.jdbc=jdbc;this.credentials=credentials; }
    public void authorize(String publisher, Long channelId, String platform, Long userId, String ticketId, boolean allowNew,
            byte[] wire, String signature) {
        if (channelId==null || channelId<=0 || platform==null || !java.util.Set.of("telegram","vk","max").contains(platform)
                || !("iguana_child_"+channelId).equals(publisher)
                || userId==null || userId<=0 || ticketId==null || ticketId.isBlank() || ticketId.length()>512
                || !TransactionSynchronizationManager.isActualTransactionActive()) throw denied();
        var child=new ChildBotCredentials.Principal(channelId,platform);
        if(!ChildBotCredentials.verifyEvent(child,credentials.getToken(),credentials.getSignatureSecret(),publisher,wire,signature))throw denied();
        if(jdbc.queryForList("""
            SELECT c.id FROM channels c JOIN bot_child_principals p ON p.channel_id=c.id
             WHERE c.id=? AND c.platform=? AND c.is_active=true AND p.enabled=true
               AND p.role_name=? AND p.platform=c.platform FOR SHARE OF c,p
            """,channelId,platform,publisher).size()!=1)throw denied();
        new com.example.panel.service.integration.LegacyTicketIdConcurrencyGuard(jdbc).acquire(ticketId);
        var roots=jdbc.queryForList("SELECT channel_id,user_id FROM tickets WHERE ticket_id=? FOR UPDATE",ticketId);
        if(roots.isEmpty() && allowNew)return;
        if(roots.size()!=1 || !(roots.get(0).get("channel_id") instanceof Number channel)
                || channel.longValue()!=channelId || !(roots.get(0).get("user_id") instanceof Number user)
                || user.longValue()!=userId)throw denied();
    }
    private static AmqpRejectAndDontRequeueException denied() {
        return new AmqpRejectAndDontRequeueException("Inbound publisher is outside its channel principal");
    }
}
