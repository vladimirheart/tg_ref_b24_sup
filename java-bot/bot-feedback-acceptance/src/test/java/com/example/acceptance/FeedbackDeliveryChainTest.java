package com.example.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.acceptance.FeedbackChainFixture.*;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Delivery leg only: no live provider SDK, customer message, callback or working-runtime mutation. */
class FeedbackDeliveryChainTest {
    private static FeedbackChainFixture chain;
    @BeforeAll static void setup() throws Exception { chain = new FeedbackChainFixture(); }
    @AfterAll static void cleanup() { if (chain != null) chain.close(); }
    @AfterEach void stop() { chain.stopConsumer(); }
    @Test void isolatedWiringSmoke() throws Exception {
        chain.reset("BUSINESS", "telegram"); var event = chain.enqueue(); chain.publish();
        assertThat(new com.example.panel.service.integration.FeedbackPromptDeliveryAuthorizationService(chain.jdbc, chain.mapper, chain.flags)
            .authorize(event, chain.route())).as("direct persisted recipient decision").isTrue();
        boolean allowed = chain.recipientClient.authorize(chain.botEvent(event), chain.route());
        assertThat(chain.recipientInputs).containsExactly(event);
        assertThat(allowed).as("signed HTTP recipient decision").isTrue();
        chain.startConsumer(); chain.delivered(event.eventId()); assertThat(chain.providerAttempts).hasSize(1);
    }
    static Stream<Arguments> owners() {
        return Stream.of("BUSINESS", "GENERAL", "SHARED_GENERAL", "LEGACY")
            .flatMap(scope -> Stream.of("telegram", "vk", "max").map(platform -> Arguments.of(scope, platform)));
    }
    static Stream<Arguments> revocations() {
        return owners().flatMap(owner -> Stream.of("root-aba", "expired", "scope", "channel", "catalog-revoke", "catalog-rotate", "bad-hmac", "api-unavailable")
            .map(change -> Arguments.of(owner.get()[0], owner.get()[1], change)));
    }
    static Stream<Arguments> poisonedMessages() {
        return owners().flatMap(owner -> Stream.of("foreign-child", "changed-prompt", "changed-user")
            .map(change -> Arguments.of(owner.get()[0], owner.get()[1], change)));
    }
    @ParameterizedTest @MethodSource("owners")
    void publishesConsumesAuthorizesAndDeliversThenSkipsReplayAfterConsumerRestart(String scope, String platform) throws Exception {
        chain.reset(scope, platform); var event = chain.enqueue();
        chain.publish(); assertThat(chain.state("integration_transport_outbox", event.eventId())).isEqualTo("published");
        assertThat(new com.example.panel.service.integration.FeedbackPromptDeliveryAuthorizationService(chain.jdbc, chain.mapper, chain.flags)
            .authorize(event, chain.route())).as("persisted recipient before HTTP/broker consumption").isTrue();
        chain.startConsumer(); chain.delivered(event.eventId());
        assertThat(chain.wirePayloads).hasSize(1);
        assertThat(chain.wirePayloads.get(0).has("feedbackTicketContext")).isFalse();
        assertThat(chain.providerAttempts).hasSize(1);
        var sent = chain.providerAttempts.get(0);
        assertThat(sent.path("platform").asText()).isEqualTo(platform);
        assertThat(sent.path("userId").longValue()).isEqualTo(USER);
        assertThat(sent.path("text").asText()).isEqualTo("Rate " + RAW);
        assertThat(chain.recipientCalls).hasValue(1); assertThat(chain.senderCalls).hasValue(1);
        chain.stopConsumer(); chain.replay(event); chain.startConsumer(); chain.drained(); chain.stopConsumer();
        chain.assertQueueEmpty();
        assertThat(chain.providerAttempts).hasSize(1);
        assertThat(chain.recipientCalls).hasValue(1); assertThat(chain.senderCalls).hasValue(1);
        assertThat(chain.consumerFailures).isEmpty();
        assertThat(chain.state("integration_outbound_event_deliveries", event.eventId())).isEqualTo("delivered");
    }
    @ParameterizedTest @MethodSource("revocations")
    void queuedPromptNeverReusesEarlierAllowAfterRevocation(String scope, String platform, String change) throws Exception {
        chain.reset(scope, platform); var event = chain.enqueue(); chain.publish();
        assertThat(chain.recipientClient.authorize(chain.botEvent(event), chain.route())).isTrue();
        assertThat(chain.senderClient.authorize(chain.senderIdentity())).isTrue();
        switch (change) {
            case "root-aba" -> { chain.jdbc.update("UPDATE tickets SET ticket_public_id=NULL"); chain.jdbc.update("UPDATE tickets SET ticket_public_id=?", "a".repeat(32)); }
            case "expired" -> chain.jdbc.update("UPDATE pending_feedback_requests SET expires_at=clock_timestamp()-interval '1 second'");
            case "scope" -> {
                if (scope.equals("BUSINESS")) chain.jdbc.update("UPDATE business_channel_bindings SET is_active=FALSE");
                else if (scope.equals("GENERAL")) chain.jdbc.update("UPDATE general_channel_question_template_bindings SET is_active=FALSE");
                else if (scope.equals("SHARED_GENERAL")) chain.jdbc.update("UPDATE shared_channel_general_question_template_bindings SET is_active=FALSE");
                else chain.jdbc.update("INSERT INTO business_channel_bindings VALUES(?,?,TRUE,'EXPLICIT_SELECTION')", CHANNEL, BUSINESS);
            }
            case "channel" -> chain.jdbc.update("UPDATE channels SET is_active=FALSE");
            case "catalog-revoke" -> chain.credentials.set(List.of());
            case "catalog-rotate" -> chain.credentials.set(List.of(new com.example.panel.model.channel.BotCredential(7L,"rotated",platform,"rotated-test-token",true)));
            case "bad-hmac" -> chain.api.setSignatureSecret("wrong-test-secret");
            case "api-unavailable" -> chain.apiUnavailable.set(true);
            default -> throw new AssertionError(change);
        }
        chain.startConsumer(); chain.rejected();
        assertThat(chain.state("integration_outbound_event_deliveries", event.eventId())).isEqualTo("failed");
        assertThat(chain.state("integration_transport_outbox", event.eventId())).isEqualTo("published");
        assertThat(chain.senderCalls).hasValue(change.startsWith("catalog-") ? 2 : 1);
    }
    @ParameterizedTest @MethodSource("poisonedMessages")
    void mutatedWireCannotChangeRecipientOrChild(String scope, String platform, String change) throws Exception {
        chain.reset(scope, platform); var event = chain.enqueue(); chain.publish();
        var forged = new com.example.supportbot.service.OutboundFeedbackPromptEvent(event.eventId(),event.eventType(),event.correlationId(),event.platform(),
            change.equals("foreign-child") ? CHANNEL + 1 : CHANNEL,event.requestId(),change.equals("changed-user") ? USER + 1 : USER,
            event.ticketId(),change.equals("changed-prompt") ? "foreign prompt" : event.prompt());
        chain.replaceQueuedMessage(forged); chain.startConsumer(); chain.rejected();
        assertThat(chain.senderCalls).hasValue(0);
        if (change.equals("foreign-child")) {
            assertThat(chain.recipientCalls).hasValue(0);
            assertThat(chain.jdbc.queryForObject("SELECT count(*) FROM integration_outbound_event_deliveries",Long.class)).isZero();
        } else {
            assertThat(chain.recipientCalls).hasValue(1);
            assertThat(chain.state("integration_outbound_event_deliveries", event.eventId())).isEqualTo("failed");
        }
    }
    @ParameterizedTest @MethodSource("owners")
    void explicitRetryAfterKnownProviderRejectionReauthorizesAndDoesNotLoseRawTicketId(String scope, String platform) throws Exception {
        chain.reset(scope, platform); var event = chain.enqueue(); chain.publish(); chain.providerStatus.set(503);
        chain.startConsumer();
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(chain.state("integration_outbound_event_deliveries", event.eventId())).isEqualTo("failed"));
        chain.stopConsumer(); assertThat(chain.providerAttempts).hasSize(1);
        chain.consumerFailures.clear(); chain.providerStatus.set(200); chain.replay(event); chain.startConsumer(); chain.delivered(event.eventId());
        assertThat(chain.providerAttempts).hasSize(2);
        assertThat(chain.recipientCalls).hasValue(2); assertThat(chain.senderCalls).hasValue(2);
        assertThat(chain.jdbc.queryForObject("SELECT ticket_id FROM integration_outbound_event_deliveries WHERE event_id=?",String.class,event.eventId())).isEqualTo(RAW);
    }
}
