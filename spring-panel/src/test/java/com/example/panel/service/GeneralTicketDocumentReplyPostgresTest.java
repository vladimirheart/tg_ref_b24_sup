package com.example.panel.service;

import static com.example.panel.support.GeneralTicketActorTestSupport.auth;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.storage.AttachmentObjectStorageService;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class GeneralTicketDocumentReplyPostgresTest {
    private static JdbcTemplate jdbc;
    private static final AtomicLong IDS=new AtomicLong(9850000);
    private GeneralTicketDocumentReplyService documents;
    private GeneralTicketAccessService access;
    private DialogReplyTransportService transport;
    private AttachmentObjectStorageService storage;
    private MultibusinessFeatureFlags flags;
    private GeneralTicketEventScope scope;
    private String actor,key,raw,destination;
    private long actorId,owner,channel;
    private final byte[] bytes={0,1,2,3,-1,10,13};
    private final Map<String,byte[]> objects=new ConcurrentHashMap<>();
    @BeforeAll static void migrate(){
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("general_documents");
        Flyway.configure().dataSource(jdbc.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
    }
    @BeforeEach void setup()throws Exception{
        owner=IDS.incrementAndGet();actor="document-"+owner;raw="opaque /document "+owner;key=key();scope=new GeneralTicketEventScope(owner,raw,key());
        actorId=jdbc.queryForObject("INSERT INTO users(username,password,enabled) VALUES(?,'unused',TRUE) RETURNING id",Long.class,actor);
        channel=jdbc.queryForObject("INSERT INTO channels(token,channel_name,platform,is_active) VALUES(?,'test','telegram',TRUE) RETURNING id",Long.class,"fake-document-"+owner);
        jdbc.update("INSERT INTO messages(group_msg_id,user_id,ticket_id,channel_id) VALUES(?,?,?,?)",owner,owner,raw,channel);
        root(scope.ticketPublicId());
        flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setGeneralTicketQueue(true);
        access=new GeneralTicketAccessService(jdbc);access.grantRead(actorId);access.grantOperate(actorId);
        transport=mock(DialogReplyTransportService.class);storage=mock(AttachmentObjectStorageService.class);
        when(storage.providerLabel()).thenReturn("s3");
        when(storage.storeDialogAttachment(anyString(),anyString(),anyString(),any())).thenAnswer(call->{
            String object=call.getArgument(0)+"/"+call.getArgument(1);byte[] payload=((InputStream)call.getArgument(3)).readAllBytes();objects.put(object,payload);
            return new AttachmentObjectStorageService.StoredBinary(object,"application/octet-stream",payload.length,new ByteArrayInputStream(payload));
        });
        when(transport.sendTelegramDocumentGuarded(any(),anyLong(),any(),anyString(),any())).thenAnswer(call->{
            assertThat(((BooleanSupplier)call.getArgument(4)).getAsBoolean()).isTrue();MultipartFile file=call.getArgument(2);
            assertThat(file.getBytes()).isEqualTo(bytes);assertThat(file.getContentType()).isEqualTo("application/octet-stream");
            return DialogReplyTransportService.DialogReplyTransportResult.success(77L,1L);
        });
        documents=new GeneralTicketDocumentReplyService(jdbc,flags,transport,storage,new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc)));
        destination=documents.destinationFingerprint(scope,auth(jdbc,actor),0).orElseThrow();clearInvocations(storage);
    }
    @AfterEach void restoreUniqueToken(){jdbc.update("UPDATE channels SET token=? WHERE id=?","fake-document-"+owner,channel);}
    private void root(String publicId){jdbc.update("INSERT INTO tickets(user_id,ticket_id,group_msg_id,channel_id,ticket_public_id,is_general,general_selection_source,status) VALUES(?,?,?,?,?,TRUE,'GENERAL_CHANNEL','pending')",owner,raw,owner,channel,publicId);}
    private static String key(){return UUID.randomUUID().toString().replace("-","");}
    private MultipartFile file(){return new MockMultipartFile("file","Отчёт 😀.pdf","text/html",bytes);}
    private boolean prepare(){return documents.prepare(scope,auth(jdbc,actor),0,key," Подпись ",file(),destination);}
    private boolean submit(){return documents.submit(scope,auth(jdbc,actor),0,key," Подпись ",file(),destination);}
    private String state(){return jdbc.queryForObject("SELECT state FROM general_ticket_document_replies WHERE request_key=?",String.class,key);}
    private long historyId(){return jdbc.queryForObject("SELECT chat_history_id FROM general_ticket_document_replies WHERE request_key=?",Long.class,key);}
    private int history(){return jdbc.queryForObject("SELECT COUNT(*) FROM chat_history WHERE ticket_public_id=?",Integer.class,scope.ticketPublicId());}
    @Test void duplicateFileIntentSendsOnceAndPreservesExactBytesMetadataAndCanonicalGeneration()throws Exception{
        assertThat(submit()).isTrue();assertThat(submit()).isTrue();assertThat(state()).isEqualTo("SENT");assertThat(history()).isEqualTo(1);
        verify(transport,times(1)).sendTelegramDocumentGuarded(any(),eq(owner),any(),eq("Подпись"),any());
        verify(storage,times(1)).storeDialogAttachment(eq(scope.ticketPublicId()),eq("general-"+key+".bin"),eq("application/octet-stream"),any());
        String object=scope.ticketPublicId()+"/general-"+key+".bin";assertThat(objects.get(object)).isEqualTo(bytes);
        assertThat(jdbc.queryForMap("SELECT sender,message,message_type,attachment,ticket_owner_user_id,ticket_public_id FROM chat_history WHERE id=?",historyId()))
            .containsEntry("sender","operator").containsEntry("message","Подпись").containsEntry("message_type","general_operator_document")
            .containsEntry("attachment",object).containsEntry("ticket_owner_user_id",owner).containsEntry("ticket_public_id",scope.ticketPublicId());
        assertThat(jdbc.queryForMap("SELECT size,content_hash,storage_key,storage_provider,mime_type FROM chat_attachment_metadata WHERE chat_history_id=?",historyId()))
            .containsEntry("size",(long)bytes.length).containsEntry("content_hash",GeneralTicketDocumentReplyService.sha256(bytes)).containsEntry("storage_key",object)
            .containsEntry("storage_provider","s3").containsEntry("mime_type","application/octet-stream");
        var reference=new GeneralTicketReadService(jdbc,access).attachment(actorId,scope.ticketPublicId(),historyId()).orElseThrow();
        assertThat(reference.storageKey()).isEqualTo(object);assertThat(reference.sha256()).isEqualTo(GeneralTicketDocumentReplyService.sha256(bytes));assertThat(reference.size()).isEqualTo((long)bytes.length);
        assertThat(jdbc.queryForList("SELECT state FROM general_ticket_document_events WHERE request_key=? ORDER BY id",String.class,key)).containsExactly("QUEUED","SENDING","SENT");
        assertThat(jdbc.queryForMap("SELECT ticket_scope_kind,ticket_business_id,message_text,attachment FROM ui_event_outbox WHERE ticket_public_id=?",scope.ticketPublicId()))
            .containsEntry("ticket_scope_kind","GENERAL").containsEntry("ticket_business_id",null).containsEntry("message_text",null).containsEntry("attachment",null);
        assertThat(documents.recent(scope,auth(jdbc,actor))).singleElement().extracting(GeneralTicketDocumentReplyService.Reply::name).isEqualTo("Отчёт 😀.pdf");
    }
    @ParameterizedTest @ValueSource(strings={"read","operate","disabled","blocked","duplicate","missing-account","unresolved","business","generation","owner","collision","root","root-channel","channel","blank-token","vk","max","closed","revision","foundation","flag","manual","configuration","rotation","raw-id"})
    void prepareProvesCurrentScopeGrantsDestinationAndProviderOrigin(String mutation){
        mutate(mutation);assertThat(submit()).isFalse();verifyNoInteractions(transport,storage);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_document_replies WHERE request_key=?",Integer.class,key)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"read","operate","disabled","blocked","duplicate","missing-account","unresolved","business","generation","owner","collision","root","root-channel","channel","blank-token","vk","max","closed","revision","foundation","flag","manual","configuration","rotation","raw-id"})
    void queuedIntentNeverRecapturesAnotherOwnerChannelOrPayload(String mutation){
        assertThat(prepare()).isTrue();mutate(mutation);documents.dispatch(key);assertThat(state()).isEqualTo("CANCELLED");assertThat(history()).isZero();verifyNoInteractions(transport,storage);
    }
    private void mutate(String mutation){switch(mutation){
        case "read"->access.revokeRead(actorId);case "operate"->access.revokeOperate(actorId);
        case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",actorId);
        case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",actorId);
        case "duplicate"->jdbc.update("INSERT INTO users(username,password,enabled) VALUES(?,'unused',TRUE)",actor.toUpperCase());
        case "missing-account"->jdbc.update("DELETE FROM users WHERE id=?",actorId);
        case "unresolved"->jdbc.update("UPDATE tickets SET is_general=FALSE,general_selection_source=NULL WHERE ticket_public_id=?",scope.ticketPublicId());
        case "business"->jdbc.update("UPDATE tickets SET is_general=FALSE,general_selection_source=NULL,business_id=(SELECT id FROM businesses LIMIT 1) WHERE ticket_public_id=?",scope.ticketPublicId());
        case "generation"->{jdbc.update("DELETE FROM tickets WHERE ticket_public_id=?",scope.ticketPublicId());root(key());}
        case "owner"->jdbc.update("UPDATE tickets SET user_id=user_id+100000 WHERE ticket_public_id=?",scope.ticketPublicId());
        case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id) VALUES(?,?)",owner+100000,raw);
        case "root"->jdbc.update("DELETE FROM messages WHERE group_msg_id=?",owner);
        case "root-channel"->jdbc.update("UPDATE messages SET channel_id=NULL WHERE group_msg_id=?",owner);
        case "channel"->jdbc.update("UPDATE channels SET is_active=FALSE WHERE id=?",channel);
        case "blank-token"->jdbc.update("UPDATE channels SET token='' WHERE id=?",channel);
        case "vk","max"->jdbc.update("UPDATE channels SET platform=? WHERE id=?",mutation,channel);
        case "closed"->jdbc.update("UPDATE tickets SET status='closed' WHERE ticket_public_id=?",scope.ticketPublicId());
        case "revision"->jdbc.update("UPDATE tickets SET status='open' WHERE ticket_public_id=?",scope.ticketPublicId());
        case "foundation"->flags.getFoundation().setEnabled(false);case "flag"->flags.setGeneralTicketQueue(false);
        case "manual"->jdbc.update("UPDATE tickets SET general_selection_source='MANUAL' WHERE ticket_public_id=?",scope.ticketPublicId());
        case "configuration"->jdbc.update("UPDATE channels SET delivery_settings='{}' WHERE id=?",channel);
        case "rotation"->jdbc.update("UPDATE channels SET token=? WHERE id=?","rotated-"+owner,channel);
        case "raw-id"->jdbc.update("UPDATE tickets SET ticket_id=? WHERE ticket_public_id=?","changed "+raw,scope.ticketPublicId());
    }}
    @Test void keyCannotChangeActorFilenameCaptionOrBytesAndAuditCannotBeRewritten(){
        assertThat(prepare()).isTrue();
        assertThat(documents.prepare(scope,auth(jdbc,actor),0,key,"Other",file(),destination)).isFalse();
        assertThat(documents.prepare(scope,auth(jdbc,actor),0,key,"Подпись",new MockMultipartFile("file","other.pdf","application/pdf",bytes),destination)).isFalse();
        assertThat(documents.prepare(scope,auth(jdbc,actor),0,key,"Подпись",new MockMultipartFile("file","Отчёт 😀.pdf","application/pdf",new byte[]{9}),destination)).isFalse();
        String other=actor+"-other";long id=jdbc.queryForObject("INSERT INTO users(username,password,enabled) VALUES(?,'unused',TRUE) RETURNING id",Long.class,other);
        access.grantRead(id);access.grantOperate(id);assertThat(documents.prepare(scope,auth(jdbc,other),0,key,"Подпись",file(),destination)).isFalse();
        assertThatThrownBy(()->jdbc.update("UPDATE general_ticket_document_replies SET original_name='tampered',state='SENDING' WHERE request_key=?",key)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM general_ticket_document_replies WHERE request_key=?",key)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM general_ticket_document_events WHERE request_key=?",key)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(transport,storage);
    }
    @ParameterizedTest @ValueSource(strings={"timeout","storage","storage-bytes","metadata","history","audit","terminal","outbox"})
    void ambiguousProviderOrPostSendPersistenceFailureNeverRepeatsUpload(String stage)throws Exception{
        String trigger="doc_fail_"+owner;String table=null;
        if(stage.equals("timeout"))doReturn(null).when(transport).sendTelegramDocumentGuarded(any(),anyLong(),any(),anyString(),any());
        else if(stage.equals("storage"))doThrow(new IOException("synthetic storage failure")).when(storage).storeDialogAttachment(anyString(),anyString(),anyString(),any());
        else if(stage.equals("storage-bytes"))doAnswer(call->new AttachmentObjectStorageService.StoredBinary(scope.ticketPublicId()+"/general-"+key+".bin","application/octet-stream",bytes.length,new ByteArrayInputStream(new byte[bytes.length])))
            .when(storage).storeDialogAttachment(anyString(),anyString(),anyString(),any());
        else{
            table=switch(stage){case "history"->"chat_history";case "metadata"->"chat_attachment_metadata";case "audit"->"general_ticket_document_events";case "terminal"->"general_ticket_document_replies";default->"ui_event_outbox";};
            String condition=switch(stage){case "history","outbox"->"NEW.ticket_public_id='"+scope.ticketPublicId()+"'";case "metadata"->"NEW.ticket_id='"+raw+"'";default->"NEW.request_key='"+key+"' AND NEW.state='SENT'";};
            jdbc.execute("CREATE FUNCTION "+trigger+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF "+condition+" THEN RAISE EXCEPTION 'synthetic failure'; END IF; RETURN NEW; END $$");
            jdbc.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT OR UPDATE ON "+table+" FOR EACH ROW EXECUTE FUNCTION "+trigger+"()");
        }
        try{assertThat(submit()).isTrue();assertThat(state()).isIn("UNKNOWN","SENDING");assertThat(history()).isZero();
            assertThat(submit()).isTrue();documents.dispatch(key);verify(transport,times(1)).sendTelegramDocumentGuarded(any(),anyLong(),any(),anyString(),any());
            assertThat(new GeneralTicketReadService(jdbc,access).attachment(actorId,scope.ticketPublicId(),1)).isEmpty();
        }finally{if(table!=null){jdbc.execute("DROP TRIGGER "+trigger+" ON "+table);jdbc.execute("DROP FUNCTION "+trigger+"()");}}
    }
    @ParameterizedTest @ValueSource(strings={"read","scope","owner","generation","type","hash","size","archived","key","duplicate","deleted"})
    void downloadReferenceRequiresCurrentReadCanonicalFileGenerationAndMetadata(String mutation){
        assertThat(submit()).isTrue();long h=historyId();
        switch(mutation){
            case "read"->access.revokeRead(actorId);
            case "scope"->jdbc.update("UPDATE tickets SET is_general=FALSE,general_selection_source=NULL WHERE ticket_public_id=?",scope.ticketPublicId());
            case "owner"->jdbc.update("UPDATE chat_history SET ticket_owner_user_id=NULL WHERE id=?",h);
            case "generation"->jdbc.update("UPDATE chat_history SET ticket_public_id=? WHERE id=?",key(),h);
            case "type"->jdbc.update("UPDATE chat_history SET message_type='operator_message' WHERE id=?",h);
            case "hash"->jdbc.update("UPDATE chat_attachment_metadata SET content_hash='bad' WHERE chat_history_id=?",h);
            case "size"->jdbc.update("UPDATE chat_attachment_metadata SET size=1 WHERE chat_history_id=?",h);
            case "archived"->jdbc.update("UPDATE chat_attachment_metadata SET archived_at=CURRENT_TIMESTAMP WHERE chat_history_id=?",h);
            case "key"->jdbc.update("UPDATE chat_attachment_metadata SET storage_key='foreign/file' WHERE chat_history_id=?",h);
            case "duplicate"->{long other=jdbc.queryForObject("INSERT INTO chat_history(ticket_id,ticket_owner_user_id) VALUES(?,?) RETURNING id",Long.class,raw,owner);
                jdbc.update("INSERT INTO chat_attachment_metadata(chat_history_id,storage_key) VALUES(?,?)",other,scope.ticketPublicId()+"/general-"+key+".bin");}
            case "deleted"->jdbc.update("UPDATE chat_history SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",h);
        }
        assertThat(new GeneralTicketReadService(jdbc,access).attachment(actorId,scope.ticketPublicId(),h)).isEmpty();
    }
    @Test void concurrentDuplicatePostsSendAndStoreExactlyOnce()throws Exception{
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var a=pool.submit(()->{start.await();return submit();});var b=pool.submit(()->{start.await();return submit();});start.countDown();
            assertThat(a.get(25,TimeUnit.SECONDS)).isTrue();assertThat(b.get(25,TimeUnit.SECONDS)).isTrue();assertThat(state()).isEqualTo("SENT");
            verify(transport,times(1)).sendTelegramDocumentGuarded(any(),anyLong(),any(),anyString(),any());verify(storage,times(1)).storeDialogAttachment(anyString(),anyString(),anyString(),any());
        }finally{pool.shutdownNow();}
    }
    @Test void documentCannotFallBackToLegacyReadEvenWhenRawIdEqualsPublicId(){
        String publicId=scope.ticketPublicId();jdbc.update("DELETE FROM tickets WHERE ticket_public_id=?",publicId);
        raw=publicId;jdbc.update("UPDATE messages SET ticket_id=? WHERE group_msg_id=?",raw,owner);
        scope=new GeneralTicketEventScope(owner,raw,publicId);root(publicId);
        destination=documents.destinationFingerprint(scope,auth(jdbc,actor),0).orElseThrow();assertThat(submit()).isTrue();
        jdbc.update("UPDATE chat_history SET message_type='operator_message' WHERE id=?",historyId());
        assertThat(new GeneralTicketReadService(jdbc,access).attachment(actorId,publicId,historyId())).isEmpty();
    }
    @Test void grantRevocationWaitsForProviderBoundaryAndPreventsTheNextIntent()throws Exception{
        var pool=Executors.newSingleThreadExecutor();var started=new CountDownLatch(1);final Future<?>[] revoked=new Future<?>[1];
        doAnswer(call->{revoked[0]=pool.submit(()->{started.countDown();access.revokeOperate(actorId);});
            assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->revoked[0].get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            assertThat(((BooleanSupplier)call.getArgument(4)).getAsBoolean()).isTrue();return DialogReplyTransportService.DialogReplyTransportResult.success(77L,1L);
        }).when(transport).sendTelegramDocumentGuarded(any(),anyLong(),any(),anyString(),any());
        try{assertThat(submit()).isTrue();revoked[0].get(10,TimeUnit.SECONDS);key=key();assertThat(submit()).isFalse();}
        finally{pool.shutdownNow();}
    }
    @Test void inputValidationBoundsActualStreamsAndRejectsPathsControlsAndFalseLength()throws Exception{
        for(String name:new String[]{"../file","C:\\file","file\r\nheader"," ","\ud800","x".repeat(201)})
            assertThat(GeneralTicketDocumentReplyService.validInput(key,"",new MockMultipartFile("file",name,"text/html",bytes))).isFalse();
        assertThat(GeneralTicketDocumentReplyService.validInput(key,"x".repeat(1025),file())).isFalse();
        MultipartFile liar=mock(MultipartFile.class);when(liar.getSize()).thenReturn(2L);when(liar.getOriginalFilename()).thenReturn("file.bin");
        when(liar.getInputStream()).thenReturn(new ByteArrayInputStream(bytes));
        assertThat(documents.prepare(scope,auth(jdbc,actor),0,key,"",liar,destination)).isFalse();
        when(liar.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[GeneralTicketDocumentReplyService.MAX_BYTES+2]));
        assertThat(documents.prepare(scope,auth(jdbc,actor),0,key,"",liar,destination)).isFalse();
        when(liar.getSize()).thenReturn((long)GeneralTicketDocumentReplyService.MAX_BYTES+1);
        assertThat(GeneralTicketDocumentReplyService.validInput(key,"",liar)).isFalse();verifyNoInteractions(transport,storage);
    }
    @Test void actualNetworkBoundaryRechecksGrantAfterCreatingHttpClient()throws Exception{
        var network=mock(IntegrationNetworkService.class);var client=mock(java.net.http.HttpClient.class);
        when(network.createChannelHttpClient(any(),any())).thenAnswer(call->{access.revokeOperate(actorId);return client;});
        var guarded=new DialogReplyTransportService(mock(com.example.panel.repository.ChannelRepository.class),network,new com.fasterxml.jackson.databind.ObjectMapper());
        documents=new GeneralTicketDocumentReplyService(jdbc,flags,guarded,storage,new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc)));
        assertThat(submit()).isTrue();assertThat(state()).isEqualTo("CANCELLED");
        verify(client,never()).send(any(),any(java.net.http.HttpResponse.BodyHandler.class));verifyNoInteractions(storage);
    }
    @Test void missingRegistryFailsClosedIncludingTheLegacyDownloadPath(){
        jdbc.execute("ALTER TABLE general_ticket_document_replies RENAME TO hidden_document_intents");
        try{assertThat(submit()).isFalse();assertThat(documents.recent(scope,auth(jdbc,actor))).isEmpty();
            assertThat(new GeneralTicketReadService(jdbc,access).attachment(actorId,scope.ticketPublicId(),1)).isEmpty();verifyNoInteractions(transport,storage);
        }finally{jdbc.execute("ALTER TABLE hidden_document_intents RENAME TO general_ticket_document_replies");}
    }
    @Test void realLocalStoragePreservesBinaryUnderPublicGenerationWithVerifiedMetadata(@org.junit.jupiter.api.io.TempDir Path temp)throws Exception{
        var properties=new com.example.panel.storage.ObjectStorageProperties();
        properties.setMode("local_fs");properties.setRequiredForPostgresql(false);
        var local=new AttachmentObjectStorageService(properties,temp.resolve("attachments").toString(),
            temp.resolve("knowledge").toString(),temp.resolve("photos").toString(),temp.resolve("avatars").toString());
        documents=new GeneralTicketDocumentReplyService(jdbc,flags,transport,local,new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc)));
        assertThat(submit()).isTrue();assertThat(state()).isEqualTo("SENT");
        String object=scope.ticketPublicId()+"/general-"+key+".bin";
        assertThat(Files.readAllBytes(temp.resolve("attachments").resolve(object))).isEqualTo(bytes);
        try(var binary=local.openDialogAttachmentByStorageKey(object)){
            assertThat(binary.inputStream().readAllBytes()).isEqualTo(bytes);assertThat(binary.size()).isEqualTo(bytes.length);
        }
        assertThat(jdbc.queryForObject("SELECT storage_provider FROM chat_attachment_metadata WHERE chat_history_id=?",String.class,historyId())).isEqualTo("local_fs");
        assertThat(new GeneralTicketReadService(jdbc,access).attachment(actorId,scope.ticketPublicId(),historyId()).orElseThrow().sha256())
            .isEqualTo(GeneralTicketDocumentReplyService.sha256(bytes));
    }
    @Test void v71UpgradePreservesExistingHistoryAndDoesNotImportFilesOrGrants(){
        var upgrade=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("documents_upgrade");
        Flyway.configure().dataSource(upgrade.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").target("71").load().migrate();
        upgrade.update("INSERT INTO tickets(user_id,ticket_id,is_general,general_selection_source) VALUES(701,'old',TRUE,'GENERAL_CHANNEL')");
        upgrade.update("INSERT INTO chat_history(user_id,ticket_id,ticket_owner_user_id,message,attachment) VALUES(701,'old',701,'old','legacy/file')");
        var roots=upgrade.queryForList("SELECT * FROM tickets");var history=upgrade.queryForList("SELECT * FROM chat_history");
        // Compare every pre-upgrade column; verify every added column separately below.
        String oldColumns=roots.get(0).keySet().stream().map(column->"\""+column.replace("\"","\"\"")+"\"")
            .collect(java.util.stream.Collectors.joining(","));
        Flyway.configure().dataSource(upgrade.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        assertThat(upgrade.queryForList("SELECT "+oldColumns+" FROM tickets")).isEqualTo(roots);assertThat(upgrade.queryForList("SELECT * FROM chat_history")).isEqualTo(history);
        var expectedColumns=new java.util.HashSet<>(roots.get(0).keySet());
        expectedColumns.addAll(java.util.List.of("business_selection_source","questionnaire_catalog_sha256","questionnaire_version","responsibility_revision","business_work_revision","business_categories"));
        assertThat(upgrade.queryForList("SELECT * FROM tickets")).allSatisfy(row->
            assertThat(row.keySet()).containsExactlyInAnyOrderElementsOf(expectedColumns));
        assertThat(upgrade.queryForList("SELECT business_selection_source,questionnaire_catalog_sha256,questionnaire_version,"
            +"responsibility_revision,business_work_revision,business_categories::text AS categories FROM tickets"))
            .allSatisfy(row->assertThat(row).containsEntry("business_selection_source",null).containsEntry("questionnaire_catalog_sha256",null)
                .containsEntry("questionnaire_version",null).containsEntry("responsibility_revision",0L)
                .containsEntry("business_work_revision",0L).containsEntry("categories","[]"));
        assertThat(upgrade.queryForObject("SELECT COUNT(*) FROM general_ticket_document_replies",Integer.class)).isZero();
        assertThat(upgrade.queryForObject("SELECT COUNT(*) FROM general_ticket_operate_grants",Integer.class)).isZero();
    }
}
