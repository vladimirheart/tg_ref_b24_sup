package com.example.acceptance;
import static org.assertj.core.api.Assertions.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class FeedbackReceiptRecoveryTest {
    static FeedbackChainFixture chain;
    @BeforeAll static void setup()throws Exception{chain=new FeedbackChainFixture();}
    @AfterAll static void stop(){if(chain!=null)chain.close();}
    static Stream<Arguments> recoveries(){return FeedbackDeliveryChainTest.owners().flatMap(owner->Stream.of("sending","uncertain").map(state->Arguments.of(owner.get()[0],owner.get()[1],state)));}
    static Stream<Arguments> unknown(){return FeedbackDeliveryChainTest.owners().flatMap(owner->Stream.of("provider-false","lost-reply").map(change->Arguments.of(owner.get()[0],owner.get()[1],change)));}
    @ParameterizedTest @MethodSource("recoveries")
    void durableReceiptRecoversAfterConsumerAndServiceRestartWithoutProviderOrAdmission(String scope,String platform,String state)throws Exception{
        chain.reset(scope,platform);chain.receiptMode.set(true);chain.failResultPersistence.set(true);
        chain.failUncertainPersistence.set(state.equals("sending"));var event=chain.enqueue();chain.publish();
        chain.startConsumer();chain.awaitDeadLetter();chain.stopConsumer();
        assertThat(chain.state("integration_outbound_event_deliveries",event.eventId())).isEqualTo(state);
        var receipt=chain.jdbc.queryForMap("SELECT * FROM integration_outbound_delivery_receipts WHERE event_id=?",event.eventId());
        assertThat(receipt.get("provider_message_id")).isEqualTo("r263.provider.1");assertThat(receipt.get("accepted_at")).isNotNull();
        // Revoke current admission. Historical acceptance is not a new delivery permission.
        chain.jdbc.update("UPDATE channels SET is_active=FALSE");chain.credentials.set(java.util.List.of());
        chain.failResultPersistence.set(false);chain.replay(event);chain.startConsumer();chain.drained();chain.stopConsumer();
        assertThat(chain.state("integration_outbound_event_deliveries",event.eventId())).isEqualTo("delivered");
        assertThat(chain.providerAttempts).hasSize(1);assertThat(chain.recipientCalls).hasValue(1);assertThat(chain.senderCalls).hasValue(1);
        assertThat(chain.jdbc.queryForMap("SELECT * FROM integration_outbound_delivery_receipts WHERE event_id=?",event.eventId())).isEqualTo(receipt);
        assertThat(chain.bridgeFailures).isEmpty();assertThat(chain.consumerFailures).hasSize(1);
    }
    @ParameterizedTest @MethodSource("unknown")
    void providerFailureWithoutPersistedAcceptanceStaysBlockedAfterRestart(String scope,String platform,String change)throws Exception{
        chain.reset(scope,platform);chain.receiptMode.set(true);
        if(change.equals("provider-false"))chain.providerStatus.set(503);else chain.dropProviderReply.set(true);
        var event=chain.enqueue();chain.publish();chain.startConsumer();chain.awaitDeadLetter();chain.stopConsumer();
        assertThat(chain.state("integration_outbound_event_deliveries",event.eventId())).isEqualTo("uncertain");
        assertThat(chain.jdbc.queryForObject("SELECT provider_message_id FROM integration_outbound_delivery_receipts WHERE event_id=?",String.class,event.eventId())).isNull();
        chain.providerStatus.set(200);chain.dropProviderReply.set(false);chain.replay(event);chain.startConsumer();chain.drained();chain.stopConsumer();
        assertThat(chain.state("integration_outbound_event_deliveries",event.eventId())).isEqualTo("uncertain");
        assertThat(chain.providerAttempts).hasSize(1);assertThat(chain.recipientCalls).hasValue(1);assertThat(chain.senderCalls).hasValue(1);
    }
}
