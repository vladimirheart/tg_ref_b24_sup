package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.entity.Channel;
import com.example.panel.storage.AttachmentObjectStorageService;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/** One-attempt Telegram document delivery from an immutable, bounded upload intent. */
@Service
public class GeneralTicketDocumentReplyService {
    public static final int MAX_BYTES=10*1024*1024;
    private static final String MIME="application/octet-stream";
    private static final String DIGEST="""
        encode(sha256(convert_to(jsonb_build_array(destination.id,destination.token,destination.platform,
            destination.platform_config,destination.delivery_settings,destination.credential_id)::text,'UTF8')),'hex')
        """;
    private static final String OWNER="""
        ticket.user_id=:ownerId AND ticket.ticket_id=:ticketId AND ticket.ticket_public_id=:publicId
        AND ticket.is_general=TRUE AND ticket.business_id IS NULL
        AND 1=(SELECT COUNT(*) FROM tickets collision WHERE collision.ticket_id=ticket.ticket_id)
        """;
    private final NamedParameterJdbcTemplate sql;
    private final MultibusinessFeatureFlags flags;
    private final DialogReplyTransportService transport;
    private final AttachmentObjectStorageService storage;
    private final UiEventOutboxAppendService ids;
    private final TransactionTemplate transaction;
    public GeneralTicketDocumentReplyService(JdbcTemplate jdbc,MultibusinessFeatureFlags flags,DialogReplyTransportService transport,
            AttachmentObjectStorageService storage,UiEventOutboxAppendService ids) {
        this.sql=new NamedParameterJdbcTemplate(jdbc);this.flags=flags;this.transport=transport;this.storage=storage;this.ids=ids;
        this.transaction=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public static boolean validInput(String key,String caption,MultipartFile file) {
        if(key==null||!key.matches("[a-f0-9]{32}")||caption==null||caption.length()>1024||file==null||file.getSize()<1||file.getSize()>MAX_BYTES) return false;
        String name=file.getOriginalFilename();
        return name!=null&&!name.isBlank()&&name.equals(name.strip())&&name.length()<=200&&!name.equals(".")&&!name.equals("..")
            &&!name.contains("/")&&!name.contains("\\")&&name.codePoints().noneMatch(Character::isISOControl)
            &&GeneralTicketReplyService.validInput(key,name)
            &&(caption.isBlank()||GeneralTicketReplyService.validInput(key,caption));
    }
    public Optional<String> destinationFingerprint(GeneralTicketEventScope scope,String identity,long revision) {
        try {return transaction.execute(tx->capture(scope,identity,revision).map(Destination::digest));}
        catch(RuntimeException ex){return Optional.empty();}
    }
    public boolean submit(GeneralTicketEventScope scope,String identity,long revision,String key,String caption,MultipartFile file,String destination) {
        if(!prepare(scope,identity,revision,key,caption,file,destination)) return false;
        dispatch(key);return true;
    }
    boolean prepare(GeneralTicketEventScope scope,String identity,long revision,String key,String caption,MultipartFile file,String destination) {
        if(!allowed(scope,identity)||revision<0||!validInput(key,caption,file)||destination==null||!destination.matches("[a-f0-9]{64}"))return false;
        final byte[] payload;
        try(var in=file.getInputStream()) {
            payload=in.readNBytes(MAX_BYTES+1);
            if(payload.length!=file.getSize()||payload.length==0||payload.length>MAX_BYTES)return false;
        }catch(IOException|RuntimeException ex){return false;}
        String normalized=caption.isBlank()?"":caption.trim();
        String hash=sha256(payload);
        try{return Boolean.TRUE.equals(transaction.execute(tx->{
            var target=capture(scope,identity,revision);
            if(target.isEmpty()||!destination.equals(target.get().digest()))return false;
            var t=target.get();var args=params(scope,identity).addValue("revision",revision).addValue("key",key)
                .addValue("caption",normalized).addValue("name",file.getOriginalFilename()).addValue("payload",payload,java.sql.Types.BINARY)
                .addValue("sha",hash).addValue("storageKey",scope.ticketPublicId()+"/general-"+key+".bin")
                .addValue("actorId",t.actorId()).addValue("channelId",t.channel().getId()).addValue("digest",t.digest());
            int changed=sql.update("""
                INSERT INTO general_ticket_document_replies(request_key,ticket_user_id,ticket_id,ticket_public_id,owner_revision,
                    actor_user_id,actor,channel_id,destination_sha256,original_name,caption,payload,content_sha256,storage_key)
                VALUES(:key,:ownerId,:ticketId,:publicId,:revision,:actorId,:identity,:channelId,:digest,:name,:caption,:payload,:sha,:storageKey)
                ON CONFLICT(request_key) DO NOTHING
                """,args);
            if(changed==1){audit(key,"QUEUED",t.actorId());return true;}
            return Boolean.TRUE.equals(sql.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM general_ticket_document_replies WHERE request_key=:key AND ticket_user_id=:ownerId
                    AND ticket_id=:ticketId AND ticket_public_id=:publicId AND owner_revision=:revision AND actor_user_id=:actorId
                    AND channel_id=:channelId AND destination_sha256=:digest AND original_name=:name AND caption=:caption
                    AND content_sha256=:sha AND octet_length(payload)=:bytes)
                """,args.addValue("bytes",payload.length),Boolean.class));
        }));}catch(RuntimeException ex){return false;}
    }
    void dispatch(String key) {
        Optional<Attempt> claimed;
        try{claimed=transaction.execute(tx->{
            var rows=sql.query("UPDATE general_ticket_document_replies SET state='SENDING',updated_at=CURRENT_TIMESTAMP WHERE request_key=:key AND state='QUEUED' RETURNING *",
                new MapSqlParameterSource("key",key),(rs,row)->new Attempt(new GeneralTicketEventScope(rs.getLong("ticket_user_id"),rs.getString("ticket_id"),rs.getString("ticket_public_id")),
                    rs.getString("actor"),rs.getLong("actor_user_id"),rs.getLong("owner_revision"),rs.getLong("channel_id"),rs.getString("destination_sha256"),
                    rs.getString("original_name"),rs.getString("caption"),rs.getBytes("payload"),rs.getString("content_sha256"),rs.getString("storage_key")));
            if(rows.isEmpty())return Optional.<Attempt>empty();audit(key,"SENDING",rows.get(0).actorId());return Optional.of(rows.get(0));
        });}catch(RuntimeException ex){return;}
        if(claimed==null||claimed.isEmpty())return;var a=claimed.get();
        try{transaction.executeWithoutResult(tx->{
            var target=capture(a.scope(),a.actor(),a.revision());
            if(target.isEmpty()||!a.matches(target.get())||!a.sha().equals(sha256(a.payload()))){finish(key,a,"CANCELLED",null,null);return;}
            var result=transport.sendTelegramDocumentGuarded(target.get().channel(),a.scope().ticketUserId(),new FrozenFile(a.name(),a.payload()),a.caption(),
                ()->capture(a.scope(),a.actor(),a.revision()).filter(a::matches).isPresent());
            if(result==null||!result.success()||result.telegramMessageId()==null||result.telegramMessageId()<=0){
                finish(key,a,result!=null&&"scope_denied".equals(result.classification())?"CANCELLED":"UNKNOWN",null,null);return;
            }
            // Object storage is external to PostgreSQL. A later rollback may leave an unreferenced object; never resend.
            try(var binary=storage.storeDialogAttachment(a.scope().ticketPublicId(),"general-"+key+".bin",MIME,new ByteArrayInputStream(a.payload()))){
                if(binary==null||!a.storageKey().equals(binary.logicalKey())||binary.size()!=a.payload().length||binary.inputStream()==null
                        ||!a.sha().equals(sha256(binary.inputStream().readNBytes(MAX_BYTES+1))))throw new IOException("Document storage integrity mismatch");
            }catch(IOException ex){throw new IllegalStateException("Document storage unavailable",ex);}
            var args=params(a.scope(),a.actor()).addValue("caption",a.caption()).addValue("storageKey",a.storageKey())
                .addValue("channelId",a.channelId()).addValue("providerId",result.telegramMessageId());
            Long historyId=sql.queryForObject("""
                INSERT INTO chat_history(user_id,sender,message,timestamp,ticket_id,message_type,attachment,channel_id,tg_message_id,ticket_owner_user_id,ticket_public_id)
                VALUES(:ownerId,'operator',:caption,CURRENT_TIMESTAMP,:ticketId,'general_operator_document',:storageKey,:channelId,:providerId,:ownerId,:publicId) RETURNING id
                """,args,Long.class);
            if(sql.update("""
                INSERT INTO chat_attachment_metadata(chat_history_id,ticket_id,channel_id,storage_key,storage_provider,storage_class,
                    original_name,mime_type,size,content_hash,legacy_attachment_ref,normalization_status,availability_status)
                VALUES(:historyId,:ticketId,:channelId,:storageKey,:provider,'dialog_attachment',:name,'application/octet-stream',:size,:sha,:storageKey,'normalized','available')
                """,args.addValue("historyId",historyId).addValue("provider",storage.providerLabel()).addValue("name",a.name())
                    .addValue("size",a.payload().length).addValue("sha",a.sha()))!=1)throw new IllegalStateException("Document metadata unavailable");
            finish(key,a,"SENT",historyId,result.telegramMessageId());
        });}catch(RuntimeException ex){
            try{transaction.executeWithoutResult(tx->finish(key,a,"UNKNOWN",null,null));}
            catch(RuntimeException ignored){/* Durable SENDING also requires manual reconciliation; never auto-retry. */}
        }
    }
    public List<Reply> recent(GeneralTicketEventScope scope,String identity) {
        if(!allowed(scope,identity))return List.of();
        try{return sql.query("""
            SELECT document.original_name,document.caption,document.state,document.created_at,document.chat_history_id
            FROM general_ticket_document_replies document JOIN tickets ticket ON ticket.user_id=document.ticket_user_id
                AND ticket.ticket_id=document.ticket_id AND ticket.ticket_public_id=document.ticket_public_id
            JOIN users account ON lower(account.username)=lower(:identity) WHERE
            """+OWNER+" AND "+GeneralTicketAccessService.READ_ACCOUNT+"""
            ORDER BY document.created_at DESC,document.request_key LIMIT 20
            """,params(scope,identity),(rs,row)->new Reply(rs.getString("original_name"),rs.getString("caption"),rs.getString("state"),
                rs.getObject("created_at",OffsetDateTime.class),rs.getObject("chat_history_id",Long.class)));
        }catch(RuntimeException ex){return List.of();}
    }
    private Optional<Destination> capture(GeneralTicketEventScope scope,String identity,long revision){
        if(!allowed(scope,identity)||revision<0)return Optional.empty();
        var rows=sql.query("SELECT account.id AS actor_id,destination.*,"+DIGEST+" AS digest "+"""
            FROM tickets ticket JOIN messages root ON root.group_msg_id=ticket.group_msg_id AND root.user_id=ticket.user_id
                AND root.ticket_id=ticket.ticket_id AND root.channel_id=ticket.channel_id
            JOIN channels destination ON destination.id=ticket.channel_id
            JOIN users account ON lower(account.username)=lower(:identity)
            JOIN general_ticket_read_grants read_access ON read_access.user_id=account.id AND read_access.is_active=TRUE
            JOIN general_ticket_operate_grants operation ON operation.user_id=account.id AND operation.is_active=TRUE
            WHERE
            """+OWNER+" AND "+GeneralTicketAccessService.CURRENT_ACCOUNT+"""
            AND ticket.general_work_revision=:revision AND ticket.status IN ('pending','open')
            AND ticket.general_selection_source IN ('USER_SELECTION','GENERAL_CHANNEL')
            AND length(ticket.ticket_id)<=255
            AND destination.is_active=TRUE AND destination.platform='telegram' AND btrim(coalesce(destination.token,''))<>''
            FOR SHARE OF ticket,root,destination,account,read_access,operation
            """,params(scope,identity).addValue("revision",revision),(rs,row)->{
                var c=new Channel();c.setId(rs.getLong("id"));c.setToken(rs.getString("token"));c.setPlatform(rs.getString("platform"));
                c.setPlatformConfig(rs.getString("platform_config"));c.setDeliverySettings(rs.getString("delivery_settings"));
                c.setCredentialId(rs.getObject("credential_id",Long.class));c.setActive(true);
                return new Destination(rs.getLong("actor_id"),c,rs.getString("digest"));
            });return rows.size()==1?Optional.of(rows.get(0)):Optional.empty();
    }
    private void finish(String key,Attempt a,String state,Long historyId,Long providerId){
        if(sql.update("UPDATE general_ticket_document_replies SET state=:state,chat_history_id=:historyId,provider_message_id=:providerId,updated_at=CURRENT_TIMESTAMP WHERE request_key=:key AND state='SENDING'",
            new MapSqlParameterSource("key",key).addValue("state",state).addValue("historyId",historyId,java.sql.Types.BIGINT).addValue("providerId",providerId,java.sql.Types.BIGINT))!=1)
            throw new IllegalStateException("Document claim unavailable");
        audit(key,state,a.actorId());
        if(sql.update("""
            INSERT INTO ui_event_outbox(id,event_type,ticket_id,channel_id,created_at,ticket_user_id,ticket_public_id,ticket_scope_kind,ticket_business_id)
            VALUES(:eventId,'operator_reply_changed',:ticketId,:channelId,CURRENT_TIMESTAMP,:ownerId,:publicId,'GENERAL',NULL)
            """,params(a.scope(),a.actor()).addValue("eventId",ids.nextEventId()).addValue("channelId",a.channelId()))!=1)throw new IllegalStateException("Document event unavailable");
    }
    private void audit(String key,String state,long actor){
        if(sql.update("INSERT INTO general_ticket_document_events(request_key,state,actor_user_id) VALUES(:key,:state,:actorId)",
            new MapSqlParameterSource("key",key).addValue("state",state).addValue("actorId",actor))!=1)throw new IllegalStateException("Document audit unavailable");
    }
    private boolean allowed(GeneralTicketEventScope scope,String identity){return flags.isFoundationEnabled()&&flags.isGeneralTicketQueue()&&scope!=null&&scope.isValid()&&identity!=null&&!identity.isBlank();}
    private MapSqlParameterSource params(GeneralTicketEventScope scope,String identity){return new MapSqlParameterSource("ownerId",scope.ticketUserId()).addValue("ticketId",scope.ticketId()).addValue("publicId",scope.ticketPublicId()).addValue("identity",identity);}
    public static String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
    public record Reply(String name,String caption,String state,OffsetDateTime createdAt,Long historyId){}
    private record Destination(long actorId,Channel channel,String digest){@Override public String toString(){return "GeneralDocumentDestination";}}
    private record Attempt(GeneralTicketEventScope scope,String actor,long actorId,long revision,long channelId,String digest,String name,String caption,byte[] payload,String sha,String storageKey){
        boolean matches(Destination d){return actorId==d.actorId()&&channelId==d.channel().getId()&&digest.equals(d.digest());}
        @Override public String toString(){return "GeneralDocumentAttempt";}
    }
    private record FrozenFile(String filename,byte[] payload) implements MultipartFile{
        public String getName(){return "document";}public String getOriginalFilename(){return filename;}public String getContentType(){return MIME;}
        public boolean isEmpty(){return payload.length==0;}public long getSize(){return payload.length;}public byte[] getBytes(){return payload.clone();}
        public InputStream getInputStream(){return new ByteArrayInputStream(payload);}public void transferTo(File file)throws IOException{Files.write(file.toPath(),payload);}
        @Override public String toString(){return "FrozenGeneralDocument";}
    }
}
