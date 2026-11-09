package com.example.panel.service.integration;

import static org.mockito.Mockito.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.entity.*;
import com.example.panel.repository.*;
import com.example.panel.security.*;
import com.example.panel.service.ChatAttachmentMetadataService;
import com.example.panel.storage.*;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

/** Production signed principal/listener/ingestion/byte proof/metadata and transactions; JDBC repository adapters. */
final class SelectedMediaTransactionFixture implements AutoCloseable {
    static final long CHANNEL=55,USER=1001,OTHER=1002;
    static final String RAW=" raw/root:Ю%?' ",SECOND=" second/root ",MASTER="r299-own-test-only-master-01234567890123456789";
    static final OffsetDateTime NOW=OffsetDateTime.parse("2026-11-09T07:00:00Z");
    final JdbcTemplate jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplate("r299_selected_media");
    final String schema=jdbc.queryForObject("SELECT current_schema()",String.class);
    final DataSourceTransactionManager manager=new DataSourceTransactionManager(jdbc.getDataSource());
    final ObjectMapper mapper=org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
    final AtomicReference<Runnable> afterPrincipal=new AtomicReference<>(()->{}),duringStorage=new AtomicReference<>(()->{});
    final AttachmentObjectStorageService storage;
    final Path files;
    String platform;boolean general;
    SelectedTicketFollowupMediaService media;
    InboundClientMessageListener listener;

    SelectedMediaTransactionFixture(Path files)throws Exception {
        this.files=files;
        storage=spy(new AttachmentObjectStorageService(new ObjectStorageProperties(),files.toString(),files.resolve("kb").toString(),files.resolve("photos").toString(),files.resolve("avatars").toString()));
        doAnswer(call->{var binary=(AttachmentObjectStorageService.StoredBinary)call.callRealMethod();
            try{duringStorage.get().run();return binary;}catch(Throwable failure){binary.close();throw failure;}
        }).when(storage).openDialogAttachmentByStorageKey(anyString());
        jdbc.execute("""
            CREATE TABLE channels(id bigint PRIMARY KEY,platform text,is_active boolean);
            CREATE TABLE bot_child_principals(role_name text PRIMARY KEY,channel_id bigint,platform text,enabled boolean);
            CREATE TABLE tickets(user_id bigint,ticket_id text,channel_id bigint,status text,business_id bigint,is_general boolean,general_selection_source text,business_selection_source text,question_template_id text,question_template_sha256 text,questionnaire_catalog_sha256 text,questionnaire_version integer,PRIMARY KEY(user_id,ticket_id));
            CREATE TABLE ticket_active(ticket_id text PRIMARY KEY,user_identity text,last_seen timestamptz);
            CREATE TABLE chat_history(id bigserial PRIMARY KEY,user_id bigint,sender text,message text,timestamp timestamptz,ticket_id text,ticket_owner_user_id bigint,message_type text,attachment text,channel_id bigint,tg_message_id bigint,reply_to_tg_id bigint,forwarded_from text,file_name text,source_link_json text,FOREIGN KEY(ticket_owner_user_id,ticket_id) REFERENCES tickets(user_id,ticket_id));
            CREATE TABLE chat_attachment_metadata(chat_history_id bigint PRIMARY KEY REFERENCES chat_history(id),ticket_id text,channel_id bigint,storage_key text,storage_provider text,storage_class text,original_name text,mime_type text,size bigint,content_hash text,legacy_attachment_ref text,normalization_status text,availability_status text,created_at timestamptz,updated_at timestamptz,archived_at timestamptz,deleted_at timestamptz);
            CREATE TABLE integration_inbound_event_inbox(event_id text PRIMARY KEY,event_kind text,platform text,channel_id bigint,ticket_id text,transport_source text,routing_key text,payload_json text,status text,received_at timestamptz,processed_at timestamptz,last_error text,attempt_count integer,processing_started_at timestamptz,updated_at timestamptz);
            """);
        rebuild();
    }

    @SuppressWarnings("unchecked") private <T>T transactional(T target){var p=new ProxyFactory(target);p.setProxyTargetClass(true);p.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return(T)p.getProxy();}

    void rebuild() {
        var flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setTicketWriteRequiresBusiness(true);flags.setSharedChannelResolution(true);flags.setGeneralChannelResolution(true);
        media=transactional(new SelectedTicketFollowupMediaService(jdbc,flags,storage));
        var channels=mock(ChannelRepository.class);when(channels.findById(anyLong())).thenAnswer(c->jdbc.query("SELECT id,platform,is_active FROM channels WHERE id=?",(r,n)->{var v=new Channel();v.setId(r.getLong(1));v.setPlatform(r.getString(2));v.setActive(r.getBoolean(3));return v;},c.getArgument(0,Long.class)).stream().findFirst());
        var tickets=mock(TicketRepository.class);when(tickets.findById(any(TicketId.class))).thenAnswer(c->{TicketId id=c.getArgument(0);return jdbc.query("SELECT channel_id,questionnaire_version,questionnaire_catalog_sha256 FROM tickets WHERE user_id=? AND ticket_id=?",(r,n)->{var t=new Ticket();t.setId(id);var ch=new Channel();ch.setId(r.getLong(1));t.setChannel(ch);t.setQuestionnaireVersion(r.getInt(2));t.setQuestionnaireCatalogSha256(r.getString(3));return t;},id.getUserId(),id.getTicketId()).stream().findFirst();});
        var history=mock(ChatHistoryRepository.class);when(history.save(any(ChatHistory.class))).thenAnswer(c->{ChatHistory h=c.getArgument(0);h.setId(jdbc.queryForObject("INSERT INTO chat_history(user_id,sender,message,timestamp,ticket_id,ticket_owner_user_id,message_type,attachment,channel_id,tg_message_id,reply_to_tg_id,forwarded_from,file_name,source_link_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id",Long.class,h.getUserId(),h.getSender(),h.getMessage(),h.getTimestamp(),h.getTicketId(),h.getTicketOwnerUserId(),h.getMessageType(),h.getAttachment(),h.getChannel().getId(),h.getTgMessageId(),h.getReplyToTgId(),h.getForwardedFrom(),h.getFileName(),h.getSourceLinkJson()));return h;});
        var active=mock(TicketActiveRepository.class);when(active.findById(anyString())).thenAnswer(c->jdbc.query("SELECT ticket_id FROM ticket_active WHERE ticket_id=?",(r,n)->{var a=new TicketActive();a.setTicketId(r.getString(1));return a;},c.getArgument(0,String.class)).stream().findFirst());
        when(active.save(any(TicketActive.class))).thenAnswer(c->{TicketActive a=c.getArgument(0);jdbc.update("UPDATE ticket_active SET user_identity=?,last_seen=? WHERE ticket_id=?",a.getUserIdentity(),a.getLastSeen(),a.getTicketId());return a;});
        var inbox=transactional(new IntegrationInboundEventInboxService(jdbc,mapper));
        var ingestion= new InboundClientMessageIngestionService(inbox,new LegacyTicketIdConcurrencyGuard(jdbc),channels,tickets,history,mock(MessageRepository.class),active,new ChatAttachmentMetadataService(jdbc,storage));ingestion.setSelectedMedia(media);
        var properties=new InternalBotApiProperties();properties.setToken(MASTER);
        var principal=new ProxyFactory(new ChildBotInboundPrincipalService(jdbc,properties));principal.setProxyTargetClass(true);
        principal.addAdvice((MethodInterceptor)call->{Object result=call.proceed();if(call.getMethod().getName().equals("authorize"))afterPrincipal.get().run();return result;});
        listener=transactional(new InboundClientMessageListener(transactional(ingestion),(ChildBotInboundPrincipalService)principal.getProxy()));
    }

    void reset(String platform,boolean general) {
        this.platform=platform;this.general=general;afterPrincipal.set(()->{});duringStorage.set(()->{});clearInvocations(storage);
        jdbc.execute("TRUNCATE chat_attachment_metadata,chat_history,ticket_active,tickets,channels,bot_child_principals,integration_inbound_event_inbox RESTART IDENTITY CASCADE");
        for(long id:List.of(CHANNEL,CHANNEL+1))jdbc.update("INSERT INTO channels VALUES(?,?,true)",id,platform);
        jdbc.update("INSERT INTO bot_child_principals VALUES('iguana_child_55',55,?,true)",platform);
        for(var entry:Map.of(USER,RAW,OTHER,SECOND).entrySet()) {
            jdbc.update("INSERT INTO tickets VALUES(?,?,55,'open',?,?,?,?,'frozen','"+"a".repeat(64)+"','"+"c".repeat(64)+"',4)",entry.getKey(),entry.getValue(),general?null:7L,general,general?"USER_SELECTION":null,general?null:"USER_SELECTION");
            jdbc.update("INSERT INTO ticket_active VALUES(?,'original-profile',?)",entry.getValue(),NOW);
        }
    }

    SelectedTicketFollowupMediaService.Admission admit(){return switch(platform){case "telegram"->media.admission(CHANNEL,USER,RAW);case "vk"->media.vkBatchAdmission(CHANNEL,USER,RAW);default->media.maxBatchAdmission(CHANNEL,USER,RAW);};}

    InboundClientMessageEvent event(long user,String ticket,long provider)throws Exception {
        byte[] bytes={1,2,3,4};String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        String key="questionnaires/"+platform+"/55/"+(general?"general":"business-7")+"/"+user+"/"+"c".repeat(64)+"/"+provider+"/"+(platform.equals("telegram")?"":"1/")+hash+".bin";
        Path file=files.resolve(key);Files.createDirectories(file.getParent());Files.write(file,bytes);
        boolean batch=!platform.equals("telegram");String kind=batch?InboundClientMessageEvent.MEDIA_BATCH_KIND:"client_message.active_ticket";
        return new InboundClientMessageEvent(InboundClientMessageEvent.mediaBatchId(CHANNEL,user,ticket,Long.toString(provider)),kind,platform,CHANNEL,ticket,user,"client","client","Client","Caption",batch?"selected_media_batch.v1":"document",key,"proof.bin",Long.toString(provider),null,null,NOW,batch?List.of(new InboundClientMessageEvent.MediaItem("document",key,"proof.bin")):null);
    }

    void deliver(InboundClientMessageEvent event) {
        try {
            byte[] bytes=mapper.writeValueAsBytes(event);String publisher="iguana_child_55";
            var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(ChildBotCredentials.apiToken(MASTER,null,new ChildBotCredentials.Principal(CHANNEL,event.platform())).getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            String signature=HexFormat.of().formatHex(mac.doFinal(("iguana-child-event-v1\n"+publisher+"\n"+hash).getBytes(StandardCharsets.UTF_8)));
            var properties=new org.springframework.amqp.core.MessageProperties();properties.setHeader("X-Iguana-Child-Event-Signature",signature);
            new TransactionTemplate(manager).executeWithoutResult(tx->{jdbc.execute("SET LOCAL lock_timeout='6s';SET LOCAL statement_timeout='12s'");listener.onInboundClientMessage(event,"integration."+event.platform(),publisher,new org.springframework.amqp.core.Message(bytes,properties));});
        }catch(java.security.GeneralSecurityException|java.io.IOException failure){throw new IllegalStateException(failure);}
    }

    int count(String table){if(!Set.of("chat_history","chat_attachment_metadata","integration_inbound_event_inbox").contains(table))throw new IllegalArgumentException();return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    String inbox(String id){return jdbc.query("SELECT status FROM integration_inbound_event_inbox WHERE event_id=?",(r,n)->r.getString(1),id).stream().findFirst().orElse(null);}
    @Override public void close(){if(!schema.matches("r299_selected_media_[a-f0-9]{12}"))throw new IllegalStateException("Foreign schema cleanup");jdbc.execute("DROP SCHEMA "+schema+" CASCADE");}
}
