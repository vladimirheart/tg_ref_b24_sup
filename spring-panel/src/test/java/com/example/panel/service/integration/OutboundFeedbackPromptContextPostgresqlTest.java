package com.example.panel.service.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.panel.config.IntegrationRabbitProperties;
import com.example.panel.service.LegacyTicketIdJdbcGuard;
import com.example.panel.service.RuntimeCoordinationService;
import com.example.panel.support.FeedbackPromptOutboxTestSupport;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** Real V84/JDBC admission and persisted outbox; Rabbit publish/confirm is a double. */
class OutboundFeedbackPromptContextPostgresqlTest {
    private static final long USER=9_007_199_254_740_993L;
    private static final long CHANNEL=9_007_199_254_740_995L;
    private static final String PUBLIC="0123456789abcdef0123456789abcdef";
    private static JdbcTemplate jdbc;
    private RabbitTemplate rabbit;
    private LegacyTicketIdJdbcGuard guard;
    private OutboundFeedbackPromptPublishOutboxService service;
    private final ObjectMapper mapper=new ObjectMapper();

    @BeforeAll static void database() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("feedback_outbox_r246");
        FeedbackPromptOutboxTestSupport.createSchema(jdbc);
    }
    @BeforeEach void reset() {
        for(String table:new String[]{"integration_transport_outbox","pending_feedback_requests","tickets"})jdbc.update("DELETE FROM "+table);
        rabbit=mock(RabbitTemplate.class);
        doAnswer(call->{call.<CorrelationData>getArgument(4).getFuture().complete(new CorrelationData.Confirm(true,null));return null;})
            .when(rabbit).convertAndSend(anyString(),anyString(),any(),any(),any(CorrelationData.class));
        var settings=new IntegrationRabbitProperties();settings.setOutboundExchange("isolated.outbound");
        guard=spy(new LegacyTicketIdJdbcGuard(jdbc));
        service=new OutboundFeedbackPromptPublishOutboxService(jdbc,mapper,rabbit,settings,mock(RuntimeCoordinationService.class),guard);
    }
    static Stream<Arguments> owners() {
        return Stream.of("BUSINESS","GENERAL","UNRESOLVED").flatMap(scope->Stream.of("T-R246"," padded/Ю%?' ").map(raw->Arguments.of(scope,raw)));
    }
    static Stream<Arguments> enqueueDenials() {
        return owners().flatMap(a->Stream.of("historical-request","missing-request","foreign-user","foreign-channel","scope-aba","channel-aba","expired","wrong-request-id")
            .map(change->Arguments.of(a.get()[0],a.get()[1],change)));
    }
    static Stream<Arguments> relayDenials() {
        return owners().flatMap(a->Stream.of("scope-aba","channel-aba","recreated-root","expired","missing-request","recreated-request","historical-request","business-changed")
            .map(change->Arguments.of(a.get()[0],a.get()[1],change)));
    }

    @ParameterizedTest @MethodSource("owners")
    void freezesPrivateContextWithoutChangingBrokerDto(String scope,String raw) throws Exception {
        seed(scope,raw);var event=event(raw,USER,CHANNEL,100L);
        service.enqueue(event,"isolated.route");
        JsonNode payload=mapper.readTree(payload(event));
        var context=payload.get("feedbackTicketContext");
        assertThat(context).isNotNull();
        assertThat(context.path("epoch").asText()).isEqualTo(jdbc.queryForObject("SELECT ticket_context_epoch::text FROM pending_feedback_requests",String.class));
        assertThat(context.path("publicId").asText()).isEqualTo(PUBLIC);
        assertThat(context.path("general").asBoolean()).isEqualTo(scope.equals("GENERAL"));
        if(scope.equals("BUSINESS"))assertThat(context.path("businessId").longValue()).isEqualTo(USER+6);
        else assertThat(context.path("businessId").isNull()).isTrue();
        assertThat(context.path("requestCreatedAt").asText()).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT ticket_id FROM integration_transport_outbox",String.class)).isEqualTo(raw);
        service.dispatchBatch();
        var sent=ArgumentCaptor.forClass(OutboundFeedbackPromptEvent.class);
        verify(rabbit).convertAndSend(eq("isolated.outbound"),eq("isolated.route"),sent.capture(),any(),any(CorrelationData.class));
        assertThat(sent.getValue()).isEqualTo(event);
        assertThat(mapper.valueToTree(sent.getValue()).has("feedbackTicketContext")).isFalse();
        assertThat(status(event)).isEqualTo("published");
    }
    @ParameterizedTest @MethodSource("enqueueDenials")
    void rejectsNonCurrentPendingBeforeEnqueue(String scope,String raw,String change) {
        seed(scope,raw);
        if(!change.startsWith("foreign-")&&!change.equals("wrong-request-id"))change(change,raw);
        var event=event(raw,change.equals("foreign-user")?USER+1:USER,change.equals("foreign-channel")?CHANNEL+1:CHANNEL,change.equals("wrong-request-id")?101L:100L);
        assertThatThrownBy(()->service.enqueue(event,"isolated.route")).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM integration_transport_outbox",Integer.class)).isZero();
        verifyNoInteractions(rabbit);
    }
    @ParameterizedTest @MethodSource("relayDenials")
    void doesNotPublishAfterQueuedContextChanges(String scope,String raw,String change) {
        seed(scope,raw);var event=event(raw,USER,CHANNEL,100L);
        service.enqueue(event,"isolated.route");change(change,raw);
        service.dispatchBatch();
        assertDenied(event);
    }
    @ParameterizedTest @MethodSource("owners")
    void doesNotRecaptureHistoricalQueuedPayload(String scope,String raw) {
        seed(scope,raw);var event=event(raw,USER,CHANNEL,100L);service.enqueue(event,"isolated.route");
        jdbc.update("UPDATE integration_transport_outbox SET payload_json=(payload_json::jsonb-'feedbackTicketContext')::text");
        service.dispatchBatch();assertDenied(event);
    }
    @ParameterizedTest @MethodSource("owners")
    void rechecksGenerationAfterLegacyGuardReturns(String scope,String raw) {
        seed(scope,raw);var event=event(raw,USER,CHANNEL,100L);service.enqueue(event,"isolated.route");
        doAnswer(call->{boolean result=(boolean)call.callRealMethod();change("scope-aba",raw);return result;}).when(guard).hasUniqueTicket(raw);
        service.dispatchBatch();assertDenied(event);
    }
    @ParameterizedTest @MethodSource("owners")
    void renewalAndSentMarkerKeepSameCapturedContext(String scope,String raw) {
        seed(scope,raw);var event=event(raw,USER,CHANNEL,100L);service.enqueue(event,"isolated.route");
        jdbc.update("UPDATE pending_feedback_requests SET sent_at=clock_timestamp(),expires_at=NULL");
        service.dispatchBatch();assertThat(status(event)).isEqualTo("published");
        verify(rabbit).convertAndSend(anyString(),anyString(),eq(event),any(),any(CorrelationData.class));
    }
    @ParameterizedTest @MethodSource("owners")
    void duplicateRootStillFailsClosed(String scope,String raw) {
        seed(scope,raw);var event=event(raw,USER,CHANNEL,100L);service.enqueue(event,"isolated.route");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,channel_id,ticket_public_id,business_id,is_general) SELECT user_id+1,ticket_id,channel_id,ticket_public_id,business_id,is_general FROM tickets");
        service.dispatchBatch();assertDenied(event);
    }
    private void assertDenied(OutboundFeedbackPromptEvent event) {
        assertThat(status(event)).isEqualTo("failed");
        assertThat(jdbc.queryForObject("SELECT published_at FROM integration_transport_outbox",java.sql.Timestamp.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM integration_transport_outbox",Integer.class)).isEqualTo(1);
        verifyNoInteractions(rabbit);
    }
    private void seed(String scope,String raw) {
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,channel_id,ticket_public_id,business_id,is_general) VALUES(?,?,?,?,?,?)",USER,raw,CHANNEL,PUBLIC,scope.equals("BUSINESS")?USER+6:null,scope.equals("GENERAL"));
        FeedbackPromptOutboxTestSupport.request(jdbc,100L,USER,CHANNEL,raw);
    }
    private void change(String kind,String raw) {
        switch(kind) {
            case "scope-aba" -> {
                jdbc.update("UPDATE tickets SET ticket_public_id=NULL WHERE ticket_id=?",raw);
                jdbc.update("UPDATE tickets SET ticket_public_id=? WHERE ticket_id=?",PUBLIC,raw);
            }
            case "channel-aba" -> {
                jdbc.update("UPDATE tickets SET channel_id=channel_id+1 WHERE ticket_id=?",raw);
                jdbc.update("UPDATE tickets SET channel_id=channel_id-1 WHERE ticket_id=?",raw);
            }
            case "recreated-root" -> {
                var old=jdbc.queryForMap("SELECT * FROM tickets WHERE ticket_id=?",raw);
                jdbc.update("DELETE FROM tickets WHERE ticket_id=?",raw);
                jdbc.update("INSERT INTO tickets(user_id,ticket_id,channel_id,ticket_public_id,business_id,is_general) VALUES(?,?,?,?,?,?)",old.get("user_id"),raw,old.get("channel_id"),old.get("ticket_public_id"),old.get("business_id"),old.get("is_general"));
            }
            case "expired" -> jdbc.update("UPDATE pending_feedback_requests SET expires_at=clock_timestamp()-interval '1 second'");
            case "missing-request" -> jdbc.update("DELETE FROM pending_feedback_requests");
            case "recreated-request" -> {
                jdbc.update("DELETE FROM pending_feedback_requests");
                // Distinct creation instant deterministically, even on coarse timer hosts.
                jdbc.update("INSERT INTO pending_feedback_requests(id,user_id,channel_id,ticket_id,created_at,expires_at) VALUES(100,?,?,?,clock_timestamp()+interval '1 second',clock_timestamp()+interval '1 hour')",USER,CHANNEL,raw);
            }
            case "historical-request" -> {
                // Reproduce pre-V84 unbound rows; bypass is restricted to this isolated fixture.
                jdbc.execute("ALTER TABLE pending_feedback_requests DISABLE TRIGGER trg_pending_feedback_context");
                try {jdbc.update("UPDATE pending_feedback_requests SET ticket_context_epoch=NULL,ticket_public_id=NULL,ticket_business_id=NULL,ticket_is_general=NULL");}
                finally {jdbc.execute("ALTER TABLE pending_feedback_requests ENABLE TRIGGER trg_pending_feedback_context");}
            }
            case "business-changed" -> jdbc.update("UPDATE tickets SET business_id=?,is_general=false WHERE ticket_id=?",USER+9,raw);
            default -> throw new AssertionError(kind);
        }
    }
    private OutboundFeedbackPromptEvent event(String raw,Long user,Long channel,Long request) {
        var id=UUID.randomUUID().toString();return new OutboundFeedbackPromptEvent(id,"feedback.prompt.dispatch",id,"telegram",channel,request,user,raw,"Rate "+raw);
    }
    private String payload(OutboundFeedbackPromptEvent event) {return jdbc.queryForObject("SELECT payload_json FROM integration_transport_outbox WHERE event_id=?",String.class,event.eventId());}
    private String status(OutboundFeedbackPromptEvent event) {return jdbc.queryForObject("SELECT status FROM integration_transport_outbox WHERE event_id=?",String.class,event.eventId());}
}
