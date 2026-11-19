package com.example.panel.service;

import static com.example.panel.service.BusinessTicketAssignmentService.OWNER;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.entity.Channel;
import java.sql.Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Closure history commits with the owner; provider I/O runs only after that transaction returns. */
@Service
public class BusinessTicketClosureNotificationService {
    private static final Logger log=LoggerFactory.getLogger(BusinessTicketClosureNotificationService.class);
    static final String MESSAGE="Диалог закрыт. Спасибо за обращение!";
    private final NamedParameterJdbcTemplate jdbc;
    private final MultibusinessFeatureFlags flags;
    private final DialogReplyTransportService transport;
    private final ProviderDeliveryLedgerService ledger;

    public BusinessTicketClosureNotificationService(JdbcTemplate jdbc,MultibusinessFeatureFlags flags,
            DialogReplyTransportService transport,ProviderDeliveryLedgerService ledger) {
        this.jdbc=new NamedParameterJdbcTemplate(jdbc);this.flags=flags;this.transport=transport;this.ledger=ledger;
    }

    Closure recordClosure(UiEventTicketScope scope,Long channelId,long revision) {
        if(!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Closure history requires the owner transaction");
        var closure=new Closure(scope,channelId,revision);
        int inserted=jdbc.update("""
            INSERT INTO chat_history(user_id,sender,message,timestamp,ticket_id,message_type,channel_id,
                                     ticket_owner_user_id,ticket_public_id)
            SELECT owner.user_id,'system',:message,owner.resolved_at,owner.ticket_id,'system_notification',
                   owner.channel_id,owner.user_id,owner.ticket_public_id FROM tickets owner
            WHERE
            """+OWNER+" AND owner.status='resolved' AND owner.business_work_revision=:revision"
                +" AND owner.channel_id IS NOT DISTINCT FROM CAST(:channel AS BIGINT)",args(closure).addValue("message",MESSAGE));
        if(inserted!=1)throw new IllegalStateException("Closure owner changed before history append");
        return closure;
    }

    void notifyClosed(Closure closure) {
        // One attempt for the winning close CAS. Repeated closes never reach this path.
        // Do not retry an unconfirmed provider request or replay historical closure events.
        try {
            if(!enabled(closure))return;
            var channel=transport.loadChannel(closure.channelId()).orElse(null);
            if(channel==null || "webform".equals(channel.getPlatform()) || !isCurrent(closure,channel))return;
            var result=transport.sendTextGuarded(channel,closure.scope().ticketUserId(),MESSAGE,null,
                ()->isCurrent(closure,channel));
            if(result==null || !result.success())log.warn("Business closure notification unconfirmed: revision={}, classification={}",
                closure.revision(),result==null?"unknown":result.classification());
            ledger.recordAttempt(channel,closure.scope().ticketId(),closure.scope().ticketUserId(),"system","text",null,result);
        } catch(RuntimeException ex) {
            // A failed provider or delivery audit must not report an already committed close as failed.
            log.warn("Business closure notification failed: revision={}, exceptionType={}",closure.revision(),ex.getClass().getSimpleName());
        }
    }

    private boolean enabled(Closure closure) {
        return flags.isFoundationEnabled() && flags.isUiSelector() && closure!=null && closure.scope()!=null
            && closure.scope().isValid() && "BUSINESS".equals(closure.scope().scopeKind()) && closure.channelId()!=null;
    }

    private boolean isCurrent(Closure closure,Channel channel) {
        if(!enabled(closure) || channel==null || !closure.channelId().equals(channel.getId()))return false;
        try {
            return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM tickets owner
                    JOIN businesses business ON business.id=owner.business_id AND business.status='active'
                    JOIN channels destination ON destination.id=owner.channel_id AND destination.is_active=TRUE
                    WHERE
                """+OWNER+"""
                    AND owner.channel_id=:channel AND owner.status='resolved' AND owner.business_work_revision=:revision
                    AND destination.token IS NOT DISTINCT FROM :token AND destination.platform=:platform
                    AND destination.platform_config IS NOT DISTINCT FROM :platformConfig
                    AND destination.delivery_settings IS NOT DISTINCT FROM :deliverySettings
                    AND destination.credential_id IS NOT DISTINCT FROM CAST(:credential AS BIGINT))
                """,args(closure).addValue("token",channel.getToken()).addValue("platform",channel.getPlatform())
                    .addValue("platformConfig",channel.getPlatformConfig()).addValue("deliverySettings",channel.getDeliverySettings())
                    .addValue("credential",channel.getCredentialId(),Types.BIGINT),Boolean.class));
        } catch(RuntimeException ex) { return false; }
    }

    private MapSqlParameterSource args(Closure closure) {
        var scope=closure.scope();
        return new MapSqlParameterSource("owner",scope.ticketUserId()).addValue("raw",scope.ticketId())
            .addValue("public",scope.ticketPublicId()).addValue("business",scope.businessId())
            .addValue("channel",closure.channelId(),Types.BIGINT).addValue("revision",closure.revision());
    }
    record Closure(UiEventTicketScope scope,Long channelId,long revision) { }
}
