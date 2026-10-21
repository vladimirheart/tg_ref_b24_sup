package com.example.panel.service.integration;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.storage.AttachmentObjectStorageService;
import com.example.panel.storage.QuestionnaireMediaStorageProof;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admission is proved from the frozen root, never from current bindings/templates. */
@Service
public class SelectedTicketFollowupMediaService {
    private final JdbcTemplate jdbc;
    private final MultibusinessFeatureFlags flags;
    private final AttachmentObjectStorageService storage;
    public SelectedTicketFollowupMediaService(JdbcTemplate jdbc, MultibusinessFeatureFlags flags, AttachmentObjectStorageService storage) {
        this.jdbc=jdbc;this.flags=flags;this.storage=storage;
    }
    public record Admission(int version,long channelId,long userId,String ticketId,Long businessId,String scopeKind,
                            String questionTemplateId,String templateSha256,String catalogSha256,int remainingFiles) {}

    @Transactional(readOnly=true)
    public Admission admission(long channelId,long userId,String ticketId) { return require(channelId,userId,ticketId,false); }

    private Admission require(long channelId,long userId,String ticketId,boolean lock) {
        if(channelId<=0 || userId<=0 || ticketId==null || ticketId.isBlank() || ticketId.length()>512
                || !flags.isFoundationEnabled() || !flags.isTicketWriteRequiresBusiness() || !flags.isSharedChannelResolution()) throw denied();
        var rows=jdbc.query("""
            SELECT ticket.business_id,ticket.is_general,ticket.general_selection_source,ticket.business_selection_source,
                ticket.question_template_id,ticket.question_template_sha256,ticket.questionnaire_catalog_sha256,
                (SELECT count(*) FROM chat_history history WHERE history.ticket_id=ticket.ticket_id
                    AND history.ticket_owner_user_id=ticket.user_id AND history.attachment IS NOT NULL AND history.attachment<>'') AS media_count
            FROM tickets ticket JOIN channels channel ON channel.id=ticket.channel_id
            JOIN ticket_active active ON active.ticket_id=ticket.ticket_id
            WHERE ticket.channel_id=? AND ticket.user_id=? AND ticket.ticket_id=?
                AND ticket.questionnaire_version=4 AND ticket.status IN ('open','pending','in_progress')
                AND channel.is_active=TRUE AND channel.platform='telegram'
                AND NOT EXISTS(SELECT 1 FROM tickets other WHERE other.ticket_id=ticket.ticket_id AND other.user_id<>ticket.user_id)
            """+(lock?" FOR UPDATE OF ticket,channel,active":""),(rs,row)->{
                Long business=(Long)rs.getObject("business_id");boolean general=rs.getBoolean("is_general");
                if(general ? business!=null || !"USER_SELECTION".equals(rs.getString("general_selection_source")) || !flags.isGeneralChannelResolution()
                    : business==null || business<=0 || !"USER_SELECTION".equals(rs.getString("business_selection_source"))) throw denied();
                String template=rs.getString("question_template_id"),sha=rs.getString("question_template_sha256"),catalog=rs.getString("questionnaire_catalog_sha256");
                long count=rs.getLong("media_count");
                if(template==null || template.isBlank() || sha==null || !sha.matches("[a-f0-9]{64}")
                        || catalog==null || !catalog.matches("[a-f0-9]{64}") || count>=10) throw denied();
                return new Admission(1,channelId,userId,ticketId,business,general?"GENERAL":"BUSINESS",template,sha,catalog,(int)(10-count));
            },channelId,userId,ticketId);
        if(rows.size()!=1) throw denied();
        return rows.get(0);
    }

    /** Caller holds the legacy-ID guard; root/channel/active row locks serialize close and new media. */
    public SelectedQuestionnaireMediaValidator.Verified verify(InboundClientMessageEvent event) {
        if(!"telegram".equals(event.platform()) || event.channelId()==null || event.userId()==null
                || event.providerMessageId()==null || !event.providerMessageId().matches("[1-9][0-9]{0,18}")
                || event.occurredAt()==null || !Set.of("document","photo","video","voice","audio","animation","sticker","video_note").contains(Objects.toString(event.messageType(),""))
                || (event.attachmentName()!=null && (event.attachmentName().length()>255
                    || event.attachmentName().chars().anyMatch(c->c<32 || c==127 || c=='/' || c=='\\')))) throw denied();
        try {if(Long.parseLong(event.providerMessageId())<=0) throw denied();} catch(NumberFormatException ex) {throw denied();}
        var root=require(event.channelId(),event.userId(),event.ticketId(),true);
        String prefix="questionnaires/telegram/"+root.channelId()+"/"+(root.businessId()==null?"general":"business-"+root.businessId())
            +"/"+root.userId()+"/"+root.catalogSha256()+"/"+event.providerMessageId()+"/";
        String key=event.attachmentPath();
        if(key==null || !key.startsWith(prefix) || !key.substring(prefix.length()).matches("[a-f0-9]{64}\\.[a-z0-9]{1,10}")) throw denied();
        if(!jdbc.queryForList("SELECT id FROM chat_history WHERE channel_id=? AND user_id=? AND tg_message_id=? LIMIT 1",Long.class,
                event.channelId(),event.userId(),Long.parseLong(event.providerMessageId())).isEmpty()) throw denied();
        String sha=key.substring(prefix.length(),prefix.length()+64);
        try(var binary=storage.openDialogAttachmentByStorageKey(key)) {
            var bytes=QuestionnaireMediaStorageProof.read(binary,sha,binary.size());
            return new SelectedQuestionnaireMediaValidator.Verified(bytes.length,sha);
        } catch(Exception ex) {throw new IllegalStateException("Selected follow-up media is unavailable or changed",ex);}
    }
    private static IllegalStateException denied() {return new IllegalStateException("Selected ticket cannot accept this attachment");}
}
