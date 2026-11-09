package com.example.panel.service.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.example.panel.service.integration.SelectedMediaTransactionFixture.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Real signed production SQL path across TG/VK/MAX; no broker/provider/application configuration. */
class SelectedMediaChannelConcurrencyPostgresTest {
    @TempDir static Path files;
    static SelectedMediaTransactionFixture f;
    @BeforeAll static void create()throws Exception{f=new SelectedMediaTransactionFixture(files);}
    @AfterAll static void close(){if(f!=null)f.close();}
    @AfterEach void clearHooks(){f.afterPrincipal.set(()->{});f.duringStorage.set(()->{});}
    static Stream<Arguments> scopes(){return Stream.of("telegram","vk","max").flatMap(p->Stream.of(false,true).map(g->Arguments.of(p,g)));}
    static Stream<Arguments> drifts(){return Stream.of("telegram","vk","max").flatMap(p->Stream.of(false,true).flatMap(g->Stream.of("platform","inactive","principal","channel","business","closed").map(d->Arguments.of(p,g,d))));}
    static Stream<Arguments> channelChanges(){return Stream.of("telegram","vk","max").flatMap(p->Stream.of(false,true).flatMap(g->Stream.of("platform","inactive").map(d->Arguments.of(p,g,d))));}
    static void latch(CountDownLatch latch){try{if(!latch.await(5,TimeUnit.SECONDS))throw new AssertionError("Fixture latch timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
    static void until(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);while(!condition.getAsBoolean()){if(System.nanoTime()>end)throw new AssertionError("Expected real PostgreSQL lock wait");Thread.sleep(20);}}

    @ParameterizedTest @MethodSource("scopes")
    void parallelSameChannelWritesKeepIndependentRootsAndDoNotUpgradeSharedChannelLock(String platform,boolean general)throws Exception {
        f.reset(platform,general);var a=f.event(USER,RAW,33);var b=f.event(OTHER,SECOND,34);
        var admitted=new CountDownLatch(2);f.afterPrincipal.set(()->{admitted.countDown();latch(admitted);});
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->f.deliver(a));var second=pool.submit(()->f.deliver(b));
            first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);
            assertThat(f.count("chat_history")).isEqualTo(2);assertThat(f.count("chat_attachment_metadata")).isEqualTo(2);
            assertThat(f.inbox(a.eventId())).isEqualTo("processed");assertThat(f.inbox(b.eventId())).isEqualTo("processed");
            assertThat(f.jdbc.queryForList("SELECT DISTINCT ticket_owner_user_id FROM chat_history",Long.class)).containsExactlyInAnyOrder(USER,OTHER);
        }finally{pool.shutdownNow();assertThat(pool.awaitTermination(15,TimeUnit.SECONDS)).isTrue();}
    }

    @ParameterizedTest @MethodSource("scopes")
    void sameRootQuotaWaitsOnItsAdvisoryLockWithoutBlockingTheFirstChannelReader(String platform,boolean general)throws Exception {
        f.reset(platform,general);for(int n=0;n<9;n++)f.jdbc.update("INSERT INTO chat_history(user_id,ticket_id,ticket_owner_user_id,attachment,channel_id) VALUES(?,?,?,?,55)",USER,RAW,USER,"old-"+n);
        var firstEvent=f.event(USER,RAW,33);var secondEvent=f.event(USER,RAW,34);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var once=new AtomicBoolean(true);
        f.duringStorage.set(()->{if(once.compareAndSet(true,false)){entered.countDown();latch(release);}});
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->f.deliver(firstEvent));latch(entered);var second=pool.submit(()->f.deliver(secondEvent));
            until(()->f.jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND NOT granted",Integer.class)>0);
            assertThat(second.isDone()).isFalse();release.countDown();first.get(10,TimeUnit.SECONDS);
            assertThatThrownBy(()->second.get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasRootCauseMessage("Selected ticket cannot accept this attachment");
            assertThat(f.count("chat_history")).isEqualTo(10);assertThat(f.count("chat_attachment_metadata")).isEqualTo(1);
            assertThat(f.inbox(firstEvent.eventId())).isEqualTo("processed");assertThat(f.inbox(secondEvent.eventId())).isEqualTo("failed");
        }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(15,TimeUnit.SECONDS)).isTrue();}
    }

    @ParameterizedTest @MethodSource("drifts")
    void restoredWriterRejectsDatabaseDriftAfterEarlierAdmissionBeforeReadingAnyBytes(String platform,boolean general,String change)throws Exception {
        f.reset(platform,general);var proof=f.admit();var event=f.event(USER,RAW,33);
        assertThat(proof.scopeKind()).isEqualTo(general?"GENERAL":"BUSINESS");
        switch(change){
            case "platform"->f.jdbc.update("UPDATE channels SET platform=? WHERE id=55",platform.equals("vk")?"max":"vk");
            case "inactive"->f.jdbc.update("UPDATE channels SET is_active=false WHERE id=55");
            case "principal"->f.jdbc.update("UPDATE bot_child_principals SET enabled=false WHERE channel_id=55");
            case "channel"->f.jdbc.update("UPDATE tickets SET channel_id=56 WHERE ticket_id=?",RAW);
            case "business"->f.jdbc.update("UPDATE tickets SET business_id=8,is_general=false,business_selection_source='USER_SELECTION',general_selection_source=NULL WHERE ticket_id=?",RAW);
            case "closed"->f.jdbc.update("UPDATE tickets SET status='closed' WHERE ticket_id=?",RAW);
        }
        f.rebuild();assertThatThrownBy(()->f.deliver(event)).isInstanceOf(RuntimeException.class);
        assertThat(f.count("chat_history")).isZero();assertThat(f.count("chat_attachment_metadata")).isZero();
        verify(f.storage,never()).openDialogAttachmentByStorageKey(anyString());
        assertThat(f.jdbc.queryForObject("SELECT user_identity FROM ticket_active WHERE ticket_id=?",String.class,RAW)).isEqualTo("original-profile");
    }

    @ParameterizedTest @MethodSource("channelChanges")
    void channelChangeWaitsThroughActualByteReadAndCommitThenRejectsNewEvents(String platform,boolean general,String change)throws Exception {
        f.reset(platform,general);var event=f.event(USER,RAW,33);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var writerPid=new AtomicInteger();
        f.duringStorage.set(()->{entered.countDown();latch(release);});var pool=Executors.newFixedThreadPool(2);
        try {
            var delivery=pool.submit(()->f.deliver(event));latch(entered);
            var mutation=pool.submit(()->new TransactionTemplate(f.manager).executeWithoutResult(tx->{
                writerPid.set(f.jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));
                f.jdbc.execute("SET LOCAL lock_timeout='10s'");
                f.jdbc.update(change.equals("inactive")?"UPDATE channels SET is_active=false WHERE id=55":"UPDATE channels SET platform='other' WHERE id=55");
            }));
            until(()->writerPid.get()>0 && Boolean.TRUE.equals(f.jdbc.queryForObject("SELECT cardinality(pg_blocking_pids(?))>0",Boolean.class,writerPid.get())));
            assertThat(mutation.isDone()).isFalse();release.countDown();delivery.get(10,TimeUnit.SECONDS);mutation.get(10,TimeUnit.SECONDS);
            assertThat(f.count("chat_history")).isEqualTo(1);assertThat(f.count("chat_attachment_metadata")).isEqualTo(1);assertThat(f.inbox(event.eventId())).isEqualTo("processed");
            f.duringStorage.set(()->{});var next=f.event(USER,RAW,34);assertThatThrownBy(()->f.deliver(next)).isInstanceOf(RuntimeException.class);
            assertThat(f.count("chat_history")).isEqualTo(1);assertThat(f.count("chat_attachment_metadata")).isEqualTo(1);
        }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(15,TimeUnit.SECONDS)).isTrue();}
    }

    @ParameterizedTest @MethodSource("scopes")
    void reconstructedServicesReplayCommittedEventWithoutSecondByteReadOrMetadataRow(String platform,boolean general)throws Exception {
        f.reset(platform,general);var event=f.event(USER,RAW,33);f.admit();f.rebuild();f.deliver(event);f.rebuild();f.deliver(event);
        assertThat(f.count("chat_history")).isEqualTo(1);assertThat(f.count("chat_attachment_metadata")).isEqualTo(1);assertThat(f.inbox(event.eventId())).isEqualTo("processed");
        var row=f.jdbc.queryForMap("SELECT ticket_id,channel_id,size,content_hash,storage_key FROM chat_attachment_metadata");
        assertThat(row.get("ticket_id")).isEqualTo(RAW);assertThat(row.get("channel_id")).isEqualTo(CHANNEL);assertThat(row.get("size")).isEqualTo(4L);
        assertThat(row.get("storage_key")).isEqualTo(event.attachmentPath());assertThat(row.get("content_hash")).isEqualTo(event.attachmentPath().substring(event.attachmentPath().length()-68,event.attachmentPath().length()-4));
        verify(f.storage,times(1)).openDialogAttachmentByStorageKey(event.attachmentPath());
    }
}
