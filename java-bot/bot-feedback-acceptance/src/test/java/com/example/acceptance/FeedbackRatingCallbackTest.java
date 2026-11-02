package com.example.acceptance;

import static org.assertj.core.api.Assertions.*;
import static com.example.acceptance.FeedbackChainFixture.*;
import com.example.supportbot.entity.PendingFeedbackRequest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Delivery -> shared bot feedback service -> signed read/write HTTP -> atomic backend rating/UI/expiry. Not live SDK input. */
class FeedbackRatingCallbackTest {
    static FeedbackChainFixture chain;static FeedbackRatingCallbackFixture callback;
    @BeforeAll static void setup()throws Exception{chain=new FeedbackChainFixture();callback=new FeedbackRatingCallbackFixture(chain);}
    @AfterAll static void close(){try{if(callback!=null)callback.close();}finally{if(chain!=null)chain.close();}}
    static Stream<Arguments> owners(){return FeedbackDeliveryChainTest.owners();}
    @Test void callbackRoundTripSmoke()throws Exception{deliveredPromptAcceptsOneRatingAndOneScopedUiEvent("BUSINESS","telegram");}
    private PendingFeedbackRequest delivered(String scope,String platform)throws Exception{
        callback.reset(scope,platform);var event=chain.enqueue();chain.publish();chain.startConsumer();chain.delivered(event.eventId());chain.stopConsumer();
        assertThat(chain.providerAttempts).hasSize(1);
        assertThat(chain.jdbc.queryForObject("SELECT provider_message_id FROM integration_outbound_delivery_receipts WHERE event_id=?",String.class,event.eventId())).isEqualTo("r263.provider.1");
        var pending=callback.bot().findActiveRequest(USER,callback.channel()).orElseThrow();
        assertThat(pending.getUserId()).isEqualTo(USER);assertThat(pending.getChannel().getId()).isEqualTo(CHANNEL);assertThat(pending.getTicketId()).isEqualTo(RAW);
        return pending;
    }
    @ParameterizedTest @MethodSource("owners")
    void deliveredPromptAcceptsOneRatingAndOneScopedUiEvent(String scope,String platform)throws Exception{
        var pending=delivered(scope,platform);var delivery=deliverySnapshot();
        callback.bot().storeFeedback(pending,4);assertSaved(scope,4);var saved=snapshot();
        // A fresh client/transaction observes committed state. A stale duplicate cannot replace the accepted rating.
        assertThat(callback.bot().findActiveRequest(USER,callback.channel())).isEmpty();
        assertThatThrownBy(()->callback.bot().storeFeedback(pending,2)).isInstanceOf(IllegalStateException.class).hasMessageContaining("not confirm");
        assertThat(snapshot()).isEqualTo(saved);assertThat(deliverySnapshot()).isEqualTo(delivery);assertThat(chain.providerAttempts).hasSize(1);
    }
    static Stream<Arguments> drift(){return owners().flatMap(a->Stream.of("expired","root-recreated","business-aba","channel-aba","general-aba","duplicate-raw","user-aba","public-aba").map(change->Arguments.of(a.get()[0],a.get()[1],change)));}
    @ParameterizedTest @MethodSource("drift")
    void callbackAfterReadRejectsExpiredOrReboundContextWithoutWrites(String scope,String platform,String change)throws Exception{
        var pending=delivered(scope,platform);
        switch(change){
            case "expired"->chain.jdbc.update("UPDATE pending_feedback_requests SET expires_at=clock_timestamp()-interval '1 second'");
            case "root-recreated"->{var row=chain.jdbc.queryForMap("SELECT * FROM tickets");chain.jdbc.update("DELETE FROM tickets");chain.jdbc.update("INSERT INTO tickets(user_id,ticket_id,channel_id,ticket_public_id,business_id,is_general) VALUES(?,?,?,?,?,?)",USER,RAW,CHANNEL,row.get("ticket_public_id"),row.get("business_id"),row.get("is_general"));}
            case "business-aba"->{Object business=chain.jdbc.queryForObject("SELECT business_id FROM tickets",Long.class);chain.jdbc.update("UPDATE tickets SET business_id=?,is_general=FALSE",BUSINESS+1);chain.jdbc.update("UPDATE tickets SET business_id=?,is_general=?",business,scope.contains("GENERAL"));}
            case "channel-aba"->{chain.jdbc.update("UPDATE tickets SET channel_id=?",CHANNEL+1);chain.jdbc.update("UPDATE tickets SET channel_id=?",CHANNEL);}
            case "general-aba"->{chain.jdbc.update("UPDATE tickets SET is_general=NOT is_general");chain.jdbc.update("UPDATE tickets SET is_general=NOT is_general");}
            case "duplicate-raw"->chain.jdbc.update("INSERT INTO tickets(user_id,ticket_id,channel_id,ticket_public_id,is_general) VALUES(?,?,?,'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',FALSE)",USER+1,RAW,CHANNEL);
            case "user-aba"->{chain.jdbc.update("UPDATE tickets SET user_id=?",USER+1);chain.jdbc.update("UPDATE tickets SET user_id=?",USER);}
            case "public-aba"->{chain.jdbc.update("UPDATE tickets SET ticket_public_id='bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'");chain.jdbc.update("UPDATE tickets SET ticket_public_id='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'");}
            default->throw new AssertionError(change);
        }
        var before=snapshot();var delivery=deliverySnapshot();
        assertThatThrownBy(()->callback.bot().storeFeedback(pending,5)).isInstanceOf(IllegalStateException.class).hasMessageContaining("not confirm");
        assertThat(snapshot()).isEqualTo(before);assertThat(deliverySnapshot()).isEqualTo(delivery);
        assertThat(callback.bot().findActiveRequest(USER,callback.channel())).isEmpty();assertThat(chain.providerAttempts).hasSize(1);
    }
    static Stream<Arguments> invalidRatings(){return owners().flatMap(a->Stream.of(0,6).map(rating->Arguments.of(a.get()[0],a.get()[1],rating)));}
    @ParameterizedTest @MethodSource("invalidRatings")
    void invalidRatingDoesNotConsumePendingRequest(String scope,String platform,int rating)throws Exception{
        var pending=delivered(scope,platform);var before=snapshot();
        assertThatThrownBy(()->callback.bot().storeFeedback(pending,rating)).isInstanceOf(IllegalStateException.class);
        assertThat(snapshot()).isEqualTo(before);assertThat(callback.bot().findActiveRequest(USER,callback.channel())).isPresent();
    }
    @ParameterizedTest @MethodSource("owners")
    void lostCommitResponseRetriesSameIdempotencyKeyWithoutSecondRatingOrUiEvent(String scope,String platform)throws Exception{
        var pending=delivered(scope,platform);chain.api.setRetryAttempts(2);callback.dropReply.set(true);
        callback.bot().storeFeedback(pending,5);assertSaved(scope,5);
        assertThat(callback.writes.get()).isGreaterThanOrEqualTo(2);assertThat(callback.dropReply).isFalse();assertThat(chain.providerAttempts).hasSize(1);
    }
    static Stream<Arguments> failures(){return owners().flatMap(a->Stream.of("feedbacks","ui_event_outbox","pending_feedback_requests").map(table->Arguments.of(a.get()[0],a.get()[1],table)));}
    @ParameterizedTest @MethodSource("failures")
    void anyCallbackWriteFailureRollsBackRatingUiEventAndExpiry(String scope,String platform,String table)throws Exception{
        var pending=delivered(scope,platform);var before=snapshot();callback.failWrite(table);
        assertThatThrownBy(()->callback.bot().storeFeedback(pending,5)).isInstanceOf(IllegalStateException.class);
        assertThat(snapshot()).isEqualTo(before);assertThat(callback.statuses).contains(500);
        chain.jdbc.execute("DROP TRIGGER r266_fail ON "+table);callback.bot().storeFeedback(pending,3);assertSaved(scope,3);
    }
    @ParameterizedTest @MethodSource("owners")
    void simultaneousRepliesHaveOneCommittedWinner(String scope,String platform)throws Exception{
        var pending=delivered(scope,platform);var start=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try{
            var a=executor.submit(()->submitAfter(start,pending,4));var b=executor.submit(()->submitAfter(start,pending,5));start.countDown();
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(callback.statuses).allMatch(status->status==200);
            int rating=chain.jdbc.queryForObject("SELECT rating FROM feedbacks",Integer.class);assertThat(rating).isIn(4,5);assertSaved(scope,rating);
        }finally{executor.shutdownNow();assertThat(executor.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    boolean submitAfter(CountDownLatch start,PendingFeedbackRequest pending,int rating)throws Exception{
        if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Barrier failed");
        try{callback.bot().storeFeedback(pending,rating);return true;}catch(IllegalStateException rejected){return false;}
    }
    static Stream<Arguments> authentication(){return owners().flatMap(a->Stream.of("bad-hmac","unavailable").map(failure->Arguments.of(a.get()[0],a.get()[1],failure)));}
    @ParameterizedTest @MethodSource("authentication")
    void untrustedOrUnavailableCallbackCannotAcknowledgeOrWrite(String scope,String platform,String failure)throws Exception{
        var pending=delivered(scope,platform);var before=snapshot();
        if(failure.equals("bad-hmac"))chain.api.setSignatureSecret("wrong-test-only-signature");else callback.unavailable.set(true);
        assertThatThrownBy(()->callback.bot().storeFeedback(pending,4)).isInstanceOf(IllegalStateException.class);
        assertThat(snapshot()).isEqualTo(before);assertThat(chain.providerAttempts).hasSize(1);
    }
    static Stream<Arguments> identities(){return owners().flatMap(a->Stream.of("user","channel").map(field->Arguments.of(a.get()[0],a.get()[1],field)));}
    @ParameterizedTest @MethodSource("identities")
    void callbackLookupDoesNotFallBackToAnotherUserOrChannel(String scope,String platform,String field)throws Exception{
        delivered(scope,platform);var before=snapshot();var channel=callback.channel();if(field.equals("channel"))channel.setId(CHANNEL+1);
        assertThat(callback.bot().findActiveRequest(field.equals("user")?USER+1:USER,channel)).isEmpty();assertThat(snapshot()).isEqualTo(before);
    }
    void assertSaved(String scope,int rating){
        var feedback=chain.jdbc.queryForMap("SELECT * FROM feedbacks");assertThat(feedback.get("rating")).isEqualTo(rating);
        assertThat(feedback.get("ticket_id")).isEqualTo(RAW);assertThat(feedback.get("user_id")).isEqualTo(USER);assertThat(feedback.get("channel_id")).isEqualTo(CHANNEL);
        var event=chain.jdbc.queryForMap("SELECT * FROM ui_event_outbox");assertThat(event.get("event_type")).isEqualTo("feedback_created");
        assertThat(event.get("rating")).isEqualTo(rating);assertThat(event.get("ticket_id")).isEqualTo(RAW);assertThat(event.get("ticket_user_id")).isEqualTo(USER);
        assertThat(event.get("channel_id")).isEqualTo(CHANNEL);assertThat(event.get("ticket_public_id")).isEqualTo("a".repeat(32));
        assertThat(event.get("ticket_scope_kind")).isEqualTo(scope.contains("GENERAL")?"GENERAL":scope.equals("BUSINESS")?"BUSINESS":"UNRESOLVED");
        assertThat(event.get("ticket_business_id")).isEqualTo(scope.equals("BUSINESS")?BUSINESS:null);
        assertThat(chain.jdbc.queryForObject("SELECT expires_at<=clock_timestamp() FROM pending_feedback_requests",Boolean.class)).isTrue();
    }
    List<List<Map<String,Object>>> snapshot(){return List.of(chain.jdbc.queryForList("SELECT * FROM feedbacks ORDER BY id"),chain.jdbc.queryForList("SELECT * FROM pending_feedback_requests ORDER BY id"),chain.jdbc.queryForList("SELECT * FROM ui_event_outbox ORDER BY id"));}
    List<List<Map<String,Object>>> deliverySnapshot(){return List.of(chain.jdbc.queryForList("SELECT * FROM integration_transport_outbox"),chain.jdbc.queryForList("SELECT * FROM integration_outbound_event_deliveries"),chain.jdbc.queryForList("SELECT * FROM integration_outbound_delivery_receipts"));}
}
