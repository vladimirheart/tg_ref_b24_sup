package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.repository.NotificationBusinessAccessSql;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.service.integration.LegacyTicketIdConcurrencyGuard;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Operator collaboration only. No transport, customer history, AI or notification body dependencies. */
@Service
public class DialogOperatorCollaborationService {
    private static final String ACCOUNT = """
        actor.id=:actorId AND actor.username=:actorName AND actor.enabled=TRUE
        AND COALESCE(actor.is_blocked,FALSE)=FALSE
        AND 1=(SELECT COUNT(*) FROM users duplicate WHERE lower(duplicate.username)=lower(actor.username))
        """;
    private static final String FROM = " FROM tickets owner JOIN users actor ON "+ACCOUNT
        +" CROSS JOIN (SELECT CAST(:identity AS TEXT) AS user_identity) n ";
    private static final String ROOT = """
        owner.ticket_public_id=:public AND
        1=(SELECT COUNT(*) FROM tickets collision WHERE collision.ticket_id=owner.ticket_id)
        """;
    private static final String READ_BUSINESS=NotificationBusinessAccessSql.CAN_READ_OWNER_BUSINESS;
    private static final String OPERATE_BUSINESS=DialogResponsibilityService.ASSIGNEE_CAN_OPERATE_SQL
        .replace("assignment.responsible","actor.username");
    private static final String READ = """
        AND ((owner.is_general=FALSE AND owner.business_id IS NOT NULL AND (%s))
          OR (owner.is_general=TRUE AND owner.business_id IS NULL AND :generalQueue
              AND EXISTS(SELECT 1 FROM general_ticket_read_grants g WHERE g.user_id=actor.id AND g.is_active=TRUE))
          OR (owner.is_general=FALSE AND owner.business_id IS NULL))
        """.formatted(READ_BUSINESS);
    private static final String WRITE = """
        ((owner.is_general=FALSE AND owner.business_id IS NOT NULL AND (%s))
          OR (owner.is_general=TRUE AND owner.business_id IS NULL
              AND EXISTS(SELECT 1 FROM general_ticket_operate_grants g WHERE g.user_id=actor.id AND g.is_active=TRUE))
          OR (owner.is_general=FALSE AND owner.business_id IS NULL))
        """.formatted(OPERATE_BUSINESS);
    private final NamedParameterJdbcTemplate jdbc;
    private final MultibusinessFeatureFlags flags;
    private final UiEventOutboxAppendService outbox;
    private final LegacyTicketIdConcurrencyGuard guard;
    private final TransactionTemplate transaction;
    public DialogOperatorCollaborationService(JdbcTemplate jdbc,MultibusinessFeatureFlags flags,UiEventOutboxAppendService outbox) {
        this.jdbc=new NamedParameterJdbcTemplate(jdbc);this.flags=flags;this.outbox=outbox;
        guard=new LegacyTicketIdConcurrencyGuard(jdbc);
        transaction=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Optional<Context> find(String publicId,Authentication auth) {
        var args=args(publicId,auth);
        if(args==null)return Optional.empty();
        return context(args,false);
    }
    private Optional<Context> context(MapSqlParameterSource args,boolean lock) {
        var rows=jdbc.query("SELECT owner.user_id,owner.ticket_id,owner.ticket_public_id,owner.business_id,owner.is_general,"
            +"owner.business_work_revision,owner.status,("+WRITE+") AS can_write"+FROM+" WHERE "+ROOT+READ
            +(lock?" FOR UPDATE OF owner FOR SHARE OF actor":""),args,(rs,row)->new Context(
                new UiEventTicketScope(rs.getLong("user_id"),rs.getString("ticket_id"),rs.getString("ticket_public_id"),
                    rs.getBoolean("is_general")?"GENERAL":rs.getObject("business_id")==null?"UNRESOLVED":"BUSINESS",
                    rs.getObject("business_id",Long.class)),rs.getLong("business_work_revision"),rs.getString("status"),rs.getBoolean("can_write")));
        return rows.size()==1?Optional.of(rows.get(0)):Optional.empty();
    }
    public List<Message> messages(String publicId,Authentication auth,long before) {
        var args=args(publicId,auth);
        if(args==null)return List.of();
        var items=jdbc.query("""
            SELECT entry.id,entry.author_user_id,entry.author,entry.body,entry.created_at,
                COALESCE(NULLIF(btrim(author_account.full_name),''),entry.author) AS author_name
            """+FROM+"""
            JOIN dialog_internal_messages entry ON entry.ticket_user_id=owner.user_id
                AND entry.ticket_id=owner.ticket_id AND entry.ticket_public_id=owner.ticket_public_id
            LEFT JOIN users author_account ON author_account.id=entry.author_user_id
            WHERE
            """+ROOT+READ+" AND (:before=0 OR entry.id<:before) ORDER BY entry.id DESC LIMIT 50",
            args.addValue("before",before),(rs,row)->new Message(Long.toString(rs.getLong("id")),
                Long.toString(rs.getLong("author_user_id")),rs.getString("author"),rs.getString("author_name"),rs.getString("body"),
                rs.getObject("created_at",OffsetDateTime.class),List.of()));
        if(items.isEmpty())return items;
        var files=files(publicId,auth,null,items.stream().map(m->Long.parseLong(m.id())).toList());
        return items.stream().map(m->new Message(m.id(),m.authorId(),m.author(),m.authorName(),m.body(),m.createdAt(),
            files.stream().filter(file->Long.toString(file.messageId()).equals(m.id())).map(FileRecord::view).toList())).toList();
    }
    public boolean post(Context expected,Authentication auth,UUID requestId,String body) {
        if(body==null||body.isBlank())return false;
        return save(expected,auth,requestId,body,List.of(),new SaveAttempt()).success();
    }
    public SaveResult save(Context expected,Authentication auth,UUID requestId,String body,List<PreparedAttachment> files,SaveAttempt attempt) {
        if(expected==null||requestId==null||body==null||body.length()>10000||body.indexOf('\0')>=0
            ||files==null||files.size()>10||body.isBlank()&&files.isEmpty())return new SaveResult(false,false);
        var args=args(expected.scope().ticketPublicId(),auth);
        if(args==null)return new SaveResult(false,false);
        var result=transaction.execute(tx->{
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization(){
                    @Override public void afterCompletion(int status){attempt.completion=status;}
                });
            guard.acquire(expected.scope().ticketId());
            var current=context(args,true).orElse(null);
            if(current==null||!current.scope().equals(expected.scope())||!current.canWrite())return new SaveResult(false,false);
            lockGrants(args);
            if(current.scope().businessId()!=null)jdbc.query("SELECT id FROM businesses WHERE id=:source FOR SHARE",
                args.addValue("source",current.scope().businessId()),(rs,row)->rs.getLong(1));
            var existing=jdbc.queryForList("SELECT id,ticket_user_id,ticket_id,ticket_public_id,body FROM dialog_internal_messages WHERE author_user_id=:actorId AND request_id=:request",
                args.addValue("request",requestId));
            if(!existing.isEmpty()){
                var entry=existing.get(0);
                var signatures=jdbc.query("SELECT original_name,mime_type,file_size,sha256 FROM dialog_internal_attachments WHERE message_id=:message ORDER BY position",
                    args.addValue("message",((Number)entry.get("id")).longValue()),(rs,row)->new Signature(rs.getString(1),rs.getString(2),rs.getLong(3),rs.getString(4)));
                boolean same=Objects.equals(entry.get("ticket_user_id"),current.scope().ticketUserId())
                    &&Objects.equals(entry.get("ticket_id"),current.scope().ticketId())
                    &&Objects.equals(entry.get("ticket_public_id"),current.scope().ticketPublicId())
                    &&Objects.equals(entry.get("body"),body)&&signatures.equals(files.stream().map(PreparedAttachment::signature).toList());
                return new SaveResult(same,false);
            }
            var inserted=jdbc.query("""
                INSERT INTO dialog_internal_messages(ticket_user_id,ticket_id,ticket_public_id,author_user_id,author,request_id,body)
                SELECT owner.user_id,owner.ticket_id,owner.ticket_public_id,actor.id,actor.username,:request,:body
                """+FROM+" WHERE "+ROOT+READ+" AND "+WRITE+" RETURNING id",args.addValue("body",body),(rs,row)->rs.getLong(1));
            if(inserted.size()!=1)return new SaveResult(false,false);
            long id=inserted.get(0);int position=0;
            for(var file:files){
                int count=jdbc.update("""
                    INSERT INTO dialog_internal_attachments(id,message_id,position,original_name,mime_type,file_size,sha256,stored_key)
                    VALUES(:id,:message,:position,:name,:mime,:size,:sha,:key)
                    """,new MapSqlParameterSource("id",file.id()).addValue("message",id).addValue("position",position++)
                        .addValue("name",file.name()).addValue("mime",file.mimeType()).addValue("size",file.size())
                        .addValue("sha",file.sha256()).addValue("key",file.key()));
                if(count!=1)throw new IllegalStateException("Attachment metadata was not written");
            }
            return new SaveResult(true,true);
        });
        return result==null?new SaveResult(false,false):result;
    }
    public static final class SaveAttempt {
        private int completion=org.springframework.transaction.support.TransactionSynchronization.STATUS_UNKNOWN;
        public boolean rolledBack(){return completion==org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK;}
    }
    public record SaveResult(boolean success,boolean created){}
    public record Signature(String name,String mimeType,long size,String sha256){}
    public record PreparedAttachment(UUID id,String key,String name,String mimeType,long size,String sha256){
        Signature signature(){return new Signature(name,mimeType,size,sha256);}
    }
    public boolean transfer(Context expected,long businessId,Authentication auth) {
        if(expected==null||!"BUSINESS".equals(expected.scope().scopeKind())||businessId<=0
                ||Objects.equals(businessId,expected.scope().businessId()))return false;
        var args=args(expected.scope().ticketPublicId(),auth);
        if(args==null)return false;
        return Boolean.TRUE.equals(transaction.execute(tx->{
            guard.acquire(expected.scope().ticketId());
            var current=context(args,true).orElse(null);
            if(current==null||!current.equals(expected)||!current.canWrite()
                ||!List.of("pending","open").contains(current.status()))return false;
            lockGrants(args);
            args.addValue("target",businessId);
            // Lock both business records; target eligibility is evaluated using the same OPERATE policy.
            jdbc.query("SELECT id FROM businesses WHERE id IN (:source,:target) ORDER BY id FOR SHARE",
                args.addValue("source",current.scope().businessId()),(rs,row)->rs.getLong(1));
            boolean target=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM businesses owner_business "
                +"CROSS JOIN users actor CROSS JOIN (SELECT :target AS business_id) owner WHERE actor.id=:actorId "
                +"AND actor.username=:actorName AND owner_business.id=:target AND owner_business.status='active' AND ("+OPERATE_BUSINESS+"))",args,Boolean.class));
            if(!target)return false;
            var scope=current.scope();
            args.addValue("ownerId",scope.ticketUserId()).addValue("raw",scope.ticketId()).addValue("revision",current.revision());
            var previousRows=jdbc.getJdbcTemplate().query("SELECT responsible FROM ticket_responsibles WHERE ticket_id=? FOR UPDATE",
                (rs,row)->rs.getString(1),scope.ticketId());
            String previous=previousRows.isEmpty()?null:previousRows.get(0);
            int changed=jdbc.update("UPDATE tickets owner SET business_id=:target FROM users actor "
                +"CROSS JOIN (SELECT CAST(:identity AS TEXT) AS user_identity) n WHERE "+ACCOUNT+" AND "+ROOT+READ+" AND "+WRITE+" AND owner.user_id=:ownerId AND owner.ticket_id=:raw"
                +" AND owner.business_id=:source AND owner.business_work_revision=:revision",args);
            if(changed!=1)throw new IllegalStateException("Business owner changed");
            // The existing root trigger clears old-business category projections and advances work revision.
            jdbc.update("DELETE FROM ticket_responsibles WHERE ticket_id=:raw",args);
            jdbc.update("""
                INSERT INTO dialog_business_transfer_events(ticket_user_id,ticket_id,ticket_public_id,previous_business_id,business_id,
                    actor_user_id,actor,previous_responsible,revision)
                VALUES(:ownerId,:raw,:public,:source,:target,:actorId,:actorName,:previous,:next)
                """,args.addValue("previous",previous,Types.VARCHAR).addValue("next",Math.addExact(current.revision(),1)));
            for(long business:List.of(scope.businessId(),businessId)){
                jdbc.update("""
                    INSERT INTO ui_event_outbox(id,event_type,ticket_id,channel_id,created_at,ticket_user_id,ticket_public_id,ticket_scope_kind,ticket_business_id)
                    SELECT :event,'business_scope_changed',ticket_id,channel_id,CURRENT_TIMESTAMP,user_id,ticket_public_id,'BUSINESS',:eventBusiness
                    FROM tickets WHERE user_id=:ownerId AND ticket_id=:raw AND ticket_public_id=:public
                    """,args.addValue("event",outbox.nextEventId()).addValue("eventBusiness",business));
            }
            return true;
        }));
    }
    private void lockGrants(MapSqlParameterSource args) {
        // Root and bound account are locked first, matching the existing assignment lock order.
        for(String table:List.of("business_memberships","business_access_grants","general_ticket_read_grants","general_ticket_operate_grants"))
            jdbc.query("SELECT user_id FROM "+table+" WHERE user_id=:actorId FOR SHARE",args,(rs,row)->rs.getLong(1));
    }
    private MapSqlParameterSource args(String publicId,Authentication auth) {
        if(!flags.isFoundationEnabled()||!flags.isUiSelector()||publicId==null||!publicId.matches("[a-f0-9]{32}")
            ||auth==null||!auth.isAuthenticated()||auth instanceof AnonymousAuthenticationToken
            ||!(auth.getPrincipal() instanceof PanelUserPrincipal actor)||actor.getUserId()<=0||actor.getUsername().isBlank()
            ||!actor.isEnabled()||!actor.isAccountNonLocked()||!actor.isAccountNonExpired()||!actor.isCredentialsNonExpired())return null;
        return new MapSqlParameterSource("public",publicId).addValue("actorId",actor.getUserId()).addValue("actorName",actor.getUsername())
            .addValue("identity",actor.getUsername().toLowerCase(Locale.ROOT)).addValue("generalQueue",flags.isGeneralTicketQueue());
    }
    public record Context(UiEventTicketScope scope,long revision,String status,boolean canWrite){}
    public record Message(String id,String authorId,String author,String authorName,String body,OffsetDateTime createdAt,List<AttachmentView> attachments){}
    public record AttachmentView(String id,String name,String mimeType,String size){}
    public record FileRecord(UUID id,long messageId,String key,String name,String mimeType,long size,String sha256){
        AttachmentView view(){return new AttachmentView(id.toString(),name,mimeType,Long.toString(size));}
    }
    public Optional<FileRecord> file(String publicId,Authentication auth,UUID id){
        if(id==null)return Optional.empty();
        var found=files(publicId,auth,id,null);return found.size()==1?Optional.of(found.get(0)):Optional.empty();
    }
    private List<FileRecord> files(String publicId,Authentication auth,UUID fileId,List<Long> messageIds){
        var args=args(publicId,auth);if(args==null)return List.of();
        return jdbc.query("""
            SELECT file.*
            """+FROM+"""
            JOIN dialog_internal_messages entry ON entry.ticket_user_id=owner.user_id AND entry.ticket_id=owner.ticket_id
                AND entry.ticket_public_id=owner.ticket_public_id
            JOIN dialog_internal_attachments file ON file.message_id=entry.id WHERE
            """+ROOT+READ+(fileId==null?" AND entry.id IN (:messages)":" AND file.id=:file")+" ORDER BY file.message_id,file.position",
            args.addValue("file",fileId).addValue("messages",messageIds),
            (rs,row)->new FileRecord(rs.getObject("id",UUID.class),rs.getLong("message_id"),rs.getString("stored_key"),
                rs.getString("original_name"),rs.getString("mime_type"),rs.getLong("file_size"),rs.getString("sha256")));
    }

    public List<Unread> unread(List<String> publicIds,Authentication auth) {
        if(publicIds==null||publicIds.isEmpty()||publicIds.size()>100
            ||publicIds.stream().anyMatch(id->id==null||!id.matches("[a-f0-9]{32}")))return List.of();
        var args=args(publicIds.get(0),auth);
        if(args==null)return List.of();
        return jdbc.query("""
            SELECT owner.ticket_public_id,
              (SELECT count(*) FROM dialog_internal_messages entry
                WHERE entry.ticket_user_id=owner.user_id AND entry.ticket_id=owner.ticket_id
                  AND entry.ticket_public_id=owner.ticket_public_id AND entry.author_user_id<>actor.id
                  AND entry.id>COALESCE((SELECT marker.last_read_id FROM dialog_internal_read_markers marker
                    WHERE marker.user_id=actor.id AND marker.ticket_user_id=owner.user_id
                      AND marker.ticket_id=owner.ticket_id AND marker.ticket_public_id=owner.ticket_public_id),0)) AS unread_count,
              (SELECT COALESCE(max(entry.id),0) FROM dialog_internal_messages entry
                WHERE entry.ticket_user_id=owner.user_id AND entry.ticket_id=owner.ticket_id
                  AND entry.ticket_public_id=owner.ticket_public_id) AS latest_id
            """+FROM+" WHERE "+ROOT.replace("owner.ticket_public_id=:public","owner.ticket_public_id IN (:publicIds)")+READ,
            args.addValue("publicIds",publicIds.stream().distinct().toList()),
            (rs,row)->new Unread(rs.getString("ticket_public_id"),Long.toString(rs.getLong("unread_count")),Long.toString(rs.getLong("latest_id"))));
    }
    public boolean markRead(Context expected,Authentication auth,long messageId) {
        if(expected==null||messageId<=0)return false;
        var args=args(expected.scope().ticketPublicId(),auth);
        if(args==null)return false;
        return Boolean.TRUE.equals(transaction.execute(tx->{
            guard.acquire(expected.scope().ticketId());
            var current=context(args,true).orElse(null);
            if(current==null||!current.scope().equals(expected.scope()))return false;
            lockGrants(args);
            if(current.scope().businessId()!=null)jdbc.query("SELECT id FROM businesses WHERE id=:source FOR SHARE",
                args.addValue("source",current.scope().businessId()),(rs,row)->rs.getLong(1));
            return jdbc.update("""
                INSERT INTO dialog_internal_read_markers AS marker(user_id,ticket_user_id,ticket_id,ticket_public_id,last_read_id)
                SELECT actor.id,owner.user_id,owner.ticket_id,owner.ticket_public_id,seen.id
                """+FROM+"""
                JOIN dialog_internal_messages seen ON seen.ticket_user_id=owner.user_id AND seen.ticket_id=owner.ticket_id
                  AND seen.ticket_public_id=owner.ticket_public_id AND seen.id=:seen
                WHERE
                """+ROOT+READ+"""
                ON CONFLICT(user_id,ticket_public_id) DO UPDATE
                  SET last_read_id=GREATEST(marker.last_read_id,EXCLUDED.last_read_id),updated_at=CURRENT_TIMESTAMP
                  WHERE marker.ticket_user_id=EXCLUDED.ticket_user_id AND marker.ticket_id=EXCLUDED.ticket_id
                """,args.addValue("seen",messageId))==1;
        }));
    }
    public record Unread(String ticketPublicId,String unreadCount,String latestId){}
}

