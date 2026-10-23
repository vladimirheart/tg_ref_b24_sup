package com.example.panel.service;

import com.example.panel.repository.NotificationBusinessAccessSql;
import com.example.panel.repository.ScopedAiSql;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** Actor-authorized public-locator resolver and explicit, text-only MEMORY curation. Never reads legacy AI. */
@Service
public class ScopedDialogAiWorkbenchService {
    private static final String ACTOR=" CROSS JOIN (SELECT CAST(:identity AS TEXT) AS user_identity) n ";
    private static final String READ=" AND ("+NotificationBusinessAccessSql.CAN_READ_OWNER_BUSINESS+")";
    private static final String CONFIGURE="""
        AND (EXISTS(SELECT 1 FROM business_memberships m WHERE m.user_id=actor.id
              AND m.business_id=owner.business_id AND m.is_active=TRUE AND m.role_code='ADMIN')
          OR EXISTS(SELECT 1 FROM business_access_grants g WHERE g.user_id=actor.id
              AND g.grant_scope='ALL_BUSINESSES' AND g.is_active=TRUE AND g.role_code='ADMIN'))
        """;
    private static final String TEXT_HISTORY="""
        history.ticket_owner_user_id=owner.user_id AND history.ticket_id=owner.ticket_id
        AND history.deleted_at IS NULL AND history.sender IN ('client','operator')
        AND COALESCE(history.message_type,'text') IN ('text','operator_message')
        AND COALESCE(history.attachment,'')='' AND btrim(COALESCE(history.message,''))<>''
        AND length(history.message)<=16000
        """;
    private final NamedParameterJdbcTemplate jdbc;
    public ScopedDialogAiWorkbenchService(JdbcTemplate jdbc) { this.jdbc=new NamedParameterJdbcTemplate(jdbc); }

    public Optional<UiEventTicketScope> resolve(String publicId,String identity) {
        if (publicId==null || !publicId.matches("[0-9a-f]{32}") || identity==null || identity.isBlank()) return Optional.empty();
        try {
            var rows=jdbc.query("SELECT owner.* FROM tickets owner"+ACTOR
                +" WHERE owner.ticket_public_id=:publicId AND owner.business_id IS NOT NULL AND owner.is_general=FALSE"
                +" AND 1=(SELECT COUNT(*) FROM tickets c WHERE c.ticket_id=owner.ticket_id)"+READ,
                new MapSqlParameterSource("publicId",publicId).addValue("identity",identity.toLowerCase(Locale.ROOT)),
                (rs,row)->new UiEventTicketScope(rs.getLong("user_id"),rs.getString("ticket_id"),rs.getString("ticket_public_id"),"BUSINESS",rs.getLong("business_id")));
            return rows.size()==1 ? Optional.of(rows.get(0)) : Optional.empty();
        } catch (DataAccessException ex) { return Optional.empty(); }
    }
    public List<Message> messages(UiEventTicketScope scope,String identity) {
        if (!valid(scope,identity)) return List.of();
        String sql="SELECT history.id,history.sender,LEFT(history.message,1000) AS preview,encode(sha256(convert_to(history.message,'UTF8')),'hex') AS fingerprint"
            +" FROM tickets owner"+ACTOR+" JOIN chat_history history ON "+TEXT_HISTORY
            +" WHERE "+ScopedAiSql.CURRENT_OWNER+READ+" ORDER BY history.id DESC LIMIT 50";
        try { return jdbc.query(sql,args(scope,identity),(rs,row)->new Message(rs.getLong("id"),rs.getString("sender"),
            rs.getString("preview"),rs.getString("fingerprint"))); }
        catch (DataAccessException ex) { return List.of(); }
    }
    public boolean createMemory(UiEventTicketScope scope,String identity,String key,long question,long answer,
                                String questionHash,String answerHash,String title,String body,boolean reviewed) {
        if (!valid(scope,identity) || !reviewed || question<1 || answer<=question || !hash(questionHash) || !hash(answerHash)
                || key==null || !key.matches("[a-f0-9-]{36}") || title==null || title.isBlank() || title.length()>300
                || body==null || body.isBlank() || body.length()>16000 || title.indexOf(0)>=0 || body.indexOf(0)>=0) return false;
        String sql="""
            WITH changed AS (
                INSERT INTO ai_scoped_sources (business_id,source_kind,source_key,title,body,status,revision,
                    origin_ticket_user_id,origin_ticket_id,origin_ticket_public_id,origin_ticket_scope_kind,origin_ticket_business_id,
                    updated_by_user_id,updated_by)
                SELECT owner.business_id,'MEMORY',:key,:title,:body,'DRAFT',1,owner.user_id,owner.ticket_id,
                    owner.ticket_public_id,'BUSINESS',owner.business_id,actor.id,n.user_identity
                FROM tickets owner
            """+ACTOR+" JOIN users actor ON lower(actor.username)=n.user_identity WHERE "+ScopedAiSql.CURRENT_OWNER+READ+CONFIGURE+"""
                AND EXISTS (SELECT 1 FROM chat_history history WHERE
            """+TEXT_HISTORY+"""
                    AND history.id=:question AND history.sender='client' AND encode(sha256(convert_to(history.message,'UTF8')),'hex')=:questionHash)
                AND EXISTS (SELECT 1 FROM chat_history history WHERE
            """+TEXT_HISTORY+"""
                    AND history.id=:answer AND history.sender='operator' AND encode(sha256(convert_to(history.message,'UTF8')),'hex')=:answerHash)
                ON CONFLICT (business_id,source_kind,source_key) DO NOTHING RETURNING *
            ), audit AS (
                INSERT INTO ai_scoped_source_events(source_id,business_id,source_kind,source_key,revision,status,action,actor_user_id,actor)
                SELECT id,business_id,source_kind,source_key,revision,status,'create',updated_by_user_id,updated_by FROM changed
                RETURNING source_id
            )
            INSERT INTO ai_scoped_memory_evidence(source_id,question_history_id,answer_history_id,question_fingerprint,answer_fingerprint)
            SELECT changed.id,:question,:answer,:questionHash,:answerHash FROM changed JOIN audit ON audit.source_id=changed.id
            """;
        var parameters=args(scope,identity).addValue("key",key,Types.VARCHAR).addValue("title",title.trim(),Types.VARCHAR)
            .addValue("body",body.trim(),Types.VARCHAR).addValue("question",question,Types.BIGINT).addValue("answer",answer,Types.BIGINT)
            .addValue("questionHash",questionHash,Types.VARCHAR).addValue("answerHash",answerHash,Types.VARCHAR);
        try { return jdbc.update(sql,parameters)==1; } catch (DataAccessException ex) { return false; }
    }
    private boolean valid(UiEventTicketScope s,String a) {
        return s!=null && s.isValid() && "BUSINESS".equals(s.scopeKind()) && a!=null && !a.isBlank();
    }
    private boolean hash(String value) { return value!=null && value.matches("[a-f0-9]{64}"); }
    private MapSqlParameterSource args(UiEventTicketScope s,String actor) {
        return new MapSqlParameterSource().addValue("ticketUserId",s.ticketUserId(),Types.BIGINT)
            .addValue("ticketId",s.ticketId(),Types.VARCHAR).addValue("publicId",s.ticketPublicId(),Types.VARCHAR)
            .addValue("scopeKind",s.scopeKind(),Types.VARCHAR).addValue("businessId",s.businessId(),Types.BIGINT)
            .addValue("strict",false,Types.BOOLEAN).addValue("identity",actor.toLowerCase(Locale.ROOT),Types.VARCHAR);
    }
    public record Message(long id,String sender,String preview,String fingerprint) {
        @Override public String toString() { return "CurationMessage[id="+id+"]"; }
    }
}
