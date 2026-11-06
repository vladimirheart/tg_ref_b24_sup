package com.example.acceptance;

import static org.assertj.core.api.Assertions.*;
import static com.example.acceptance.MaxMediaChainFixture.*;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Explicit -Dtest=MaxFollowupMediaChainIT only; unavailable Docker is an ERROR, never a skip. */
class MaxFollowupMediaChainIT {
    @TempDir static Path files;
    static MaxMediaChainFixture f;
    @BeforeAll static void start()throws Exception{f=new MaxMediaChainFixture(files,System.getProperty("max.media.acceptance.nonce"));}
    @AfterAll static void stop(){try{if(f!=null)f.close();}finally{f=null;}}
    @AfterEach void stopConsumer(){f.stopConsumer();}
    static Stream<Arguments> positiveCases(){return Stream.of(false,true).flatMap(g->Stream.of(1,2,10).flatMap(count->Stream.of(33L,Long.MAX_VALUE).map(provider->Arguments.of(g,count,provider))));}
    static Stream<Arguments> rollbackCases(){return Stream.of(false,true).flatMap(g->Stream.of("chat_history","chat_attachment_metadata","ticket_active").map(table->Arguments.of(g,table)));}
    static Stream<Arguments> byteCases(){return Stream.of(false,true).flatMap(g->Stream.of("missing","hash").map(kind->Arguments.of(g,kind)));}
    static Stream<Arguments> driftCases(){return Stream.of(false,true).flatMap(g->Stream.of("closed","owner","duplicate").map(kind->Arguments.of(g,kind)));}
    static Stream<Arguments> initialCases(){return Stream.of(false,true).flatMap(g->Stream.of(false,true).map(scoped->Arguments.of(g,scoped)));}
    @ParameterizedTest @MethodSource("positiveCases")
    void signedAdmissionOutboxBrokerListenerAndOwnedMetadata(boolean general,int count,long provider)throws Exception{
        f.reset(general);var proof=f.client.selectedMaxMediaAdmission(CHANNEL,USER,RAW);assertThat(proof.version()).isEqualTo(3);assertThat(proof.remainingFiles()).isEqualTo(10);assertThat(proof.businessId()).isEqualTo(general?null:BUSINESS);assertThat(proof.ticketId()).isEqualTo(RAW);
        var event=f.enqueue(general,provider,count);String saved=f.jdbc.queryForObject("SELECT payload_json FROM integration_transport_outbox WHERE event_id=?",String.class,event.eventId());
        assertThat(f.outbox.findRecordedMaxMediaTicket(CHANNEL,USER,provider)).contains(RAW);f.startConsumer();f.outbox.dispatchScheduled();await(()->"processed".equals(f.state(event.eventId())));f.stopConsumer();
        assertThat(f.count("chat_history")).isEqualTo(count);assertThat(f.count("chat_attachment_metadata")).isEqualTo(count);assertThat(f.wire).hasSize(1);
        for(var row:f.jdbc.queryForList("SELECT ticket_id,channel_id,size,content_hash,storage_key FROM chat_attachment_metadata")){assertThat(row.get("ticket_id")).isEqualTo(RAW);assertThat(row.get("channel_id")).isEqualTo(CHANNEL);assertThat(row.get("size")).isEqualTo(4L);assertThat((String)row.get("content_hash")).matches("[a-f0-9]{64}");assertThat((String)row.get("storage_key")).contains("/"+USER+"/","/"+provider+"/");}
        assertThat(f.jdbc.queryForList("SELECT DISTINCT ticket_owner_user_id,tg_message_id FROM chat_history")).containsExactly(Map.of("ticket_owner_user_id",USER,"tg_message_id",provider));
        assertThat(f.jdbc.queryForObject("SELECT status FROM integration_transport_outbox WHERE event_id=?",String.class,event.eventId())).isEqualTo("published");
        assertThat(f.outbox.findRecordedMaxMediaTicket(CHANNEL,USER,provider)).contains(RAW);assertThat(f.jdbc.queryForObject("SELECT payload_json FROM integration_transport_outbox WHERE event_id=?",String.class,event.eventId())).isEqualTo(saved);
        assertThat(f.rabbit.receive(f.dlq,100)).isNull();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void brokerRequeuesOriginalBytesAfterCommittedWriteBeforeAck(boolean general)throws Exception{
        f.reset(general);var event=f.enqueue(general,33,2);f.requeueAfterCommit.set(true);f.startConsumer();f.outbox.dispatchScheduled();await(()->f.wire.size()>=2&&"processed".equals(f.state(event.eventId())));f.stopConsumer();
        assertThat(f.wire).hasSize(2);assertThat(f.wire.get(1)).containsExactly(f.wire.get(0));assertThat(f.redelivered).containsExactly(false,true);assertThat(f.count("chat_history")).isEqualTo(2);assertThat(f.count("chat_attachment_metadata")).isEqualTo(2);
        assertThat(f.jdbc.queryForObject("SELECT attempt_count FROM integration_inbound_event_inbox WHERE event_id=?",Integer.class,event.eventId())).isEqualTo(1);assertThat(f.rabbit.receive(f.dlq,100)).isNull();
    }
    @ParameterizedTest @MethodSource("rollbackCases")
    void failedWriteRollsBackEveryItemAndFailedEnvelopeCanBeRetried(boolean general,String table)throws Exception{
        f.reset(general);var event=f.enqueue(general,33,2);f.failWrite(table);assertThatThrownBy(()->f.deliver(event)).isInstanceOf(RuntimeException.class);
        assertThat(f.count("chat_history")).isZero();assertThat(f.count("chat_attachment_metadata")).isZero();assertThat(f.state(event.eventId())).isEqualTo("failed");assertThat(f.jdbc.queryForObject("SELECT user_identity FROM ticket_active WHERE ticket_id=?",String.class,RAW)).isEqualTo("original-profile");
        f.jdbc.execute("DROP TRIGGER r287_fail ON "+table);f.jdbc.execute("DROP FUNCTION r287_fail()");f.deliver(event);assertThat(f.state(event.eventId())).isEqualTo("processed");assertThat(f.count("chat_history")).isEqualTo(2);assertThat(f.count("chat_attachment_metadata")).isEqualTo(2);
    }
    @ParameterizedTest @MethodSource("byteCases")
    void badSecondFileRejectsWholeBrokerEnvelope(boolean general,String kind)throws Exception{
        f.reset(general);var event=f.enqueue(general,33,2);Path second=f.files.resolve(event.mediaBatch().get(1).attachmentPath());if(kind.equals("missing"))Files.delete(second);else Files.write(second,new byte[]{9,9,9,9});
        f.startConsumer();f.outbox.dispatchScheduled();await(()->"failed".equals(f.state(event.eventId())));f.stopConsumer();assertThat(f.count("chat_history")).isZero();assertThat(f.count("chat_attachment_metadata")).isZero();assertThat(f.rabbit.receive(f.dlq,5000)).isNotNull();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void committedOutboxSurvivesUnknownCallerResultWithoutRebuildingBatch(boolean general)throws Exception{
        f.reset(general);var event=f.enqueue(general,33,2);String original=f.jdbc.queryForObject("SELECT payload_json FROM integration_transport_outbox",String.class);
        // A new service instance represents reconstruction after losing the caller's successful commit response.
        var restored=new com.example.supportbot.service.IntegrationTransportOutboxService(f.jdbc,f.mapper,f.rabbit);
        f.jdbc.update("UPDATE tickets SET status='closed' WHERE ticket_id=?",RAW);
        assertThat(restored.findRecordedMaxMediaTicket(CHANNEL,USER,33)).contains(RAW);assertThat(f.jdbc.queryForObject("SELECT payload_json FROM integration_transport_outbox",String.class)).isEqualTo(original);assertThat(f.count("integration_transport_outbox")).isEqualTo(1);assertThat(f.count("chat_history")).isZero();assertThat(f.wire).isEmpty();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void advisoryLockSerializesCompetingQuotaDecisions(boolean general)throws Exception{
        f.reset(general);for(int n=0;n<9;n++)f.jdbc.update("INSERT INTO chat_history(user_id,ticket_id,ticket_owner_user_id,attachment,channel_id) VALUES(?,?,?,?,?)",USER,RAW,USER,"old-owned-"+n,CHANNEL);
        var first=f.enqueue(general,33,1);var second=f.enqueue(general,34,1);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var once=new AtomicBoolean(true);var executor=Executors.newFixedThreadPool(2);
        f.beforeHistory.set(()->{if(once.compareAndSet(true,false)){entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Release missing");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}});
        try{var a=executor.submit(()->f.deliver(first));assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();var b=executor.submit(()->f.deliver(second));
            await(()->f.jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND NOT granted",Integer.class)>0);assertThat(b.isDone()).isFalse();release.countDown();a.get(10,TimeUnit.SECONDS);assertThatThrownBy(()->b.get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            assertThat(f.count("chat_history")).isEqualTo(10);assertThat(f.count("chat_attachment_metadata")).isEqualTo(1);assertThat(f.state(first.eventId())).isEqualTo("processed");assertThat(f.state(second.eventId())).isEqualTo("failed");
        }finally{release.countDown();executor.shutdownNow();}
    }
    @ParameterizedTest @MethodSource("driftCases")
    void canonicalRootDriftAfterAdmissionCannotWriteMedia(boolean general,String kind)throws Exception{
        f.reset(general);f.client.selectedMaxMediaAdmission(CHANNEL,USER,RAW);var event=f.enqueue(general,33,1);
        switch(kind){case "closed"->f.jdbc.update("UPDATE tickets SET status='closed'");case "owner"->f.jdbc.update("UPDATE tickets SET business_id=?,is_general=FALSE,business_selection_source='USER_SELECTION'",BUSINESS+1);case "duplicate"->f.jdbc.update("INSERT INTO tickets(user_id,ticket_id,channel_id,status) VALUES(?,?,?,'open')",USER+1,RAW,CHANNEL);}
        assertThatThrownBy(()->f.deliver(event)).isInstanceOf(IllegalStateException.class);assertThat(f.count("chat_history")).isZero();assertThat(f.count("chat_attachment_metadata")).isZero();assertThat(f.state(event.eventId())).isEqualTo("failed");
    }
    @ParameterizedTest @MethodSource("initialCases")
    void postgresJsonbRecognizesScopedInitialMediaAndIgnoresLegacyKeys(boolean general,boolean scoped)throws Exception{
        f.reset(general);var items=f.files(general,33,1);String key=scoped?items.get(0).attachmentPath():"max/legacy/file.bin";
        var history=List.of(new com.example.supportbot.service.ConversationHistoryEntry(USER,"Caption","document",key,"proof.bin","33",OffsetDateTime.parse("2026-11-05T08:00:00Z")));
        var event=new com.example.supportbot.service.ConversationTicketCreatedEvent("r287-initial","ticket.created.initial_contact","max",CHANNEL,RAW,USER,"i","u","C",null,null,null,null,"Caption",OffsetDateTime.parse("2026-11-05T08:00:00Z"),List.of(),history,general?null:BUSINESS,general?"GENERAL":"BUSINESS","USER_SELECTION","q-frozen","a".repeat(64),"c".repeat(64));
        assertThat(f.outbox.enqueueConversationTicketCreated(event,f.routes)).isTrue();assertThat(f.outbox.findRecordedMaxMediaTicket(CHANNEL,USER,33)).isEqualTo(scoped?Optional.of(RAW):Optional.empty());assertThat(f.count("chat_history")).isZero();
    }
}
