package com.example.panel.service.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.panel.config.IntegrationRabbitProperties;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.LegacyTicketIdJdbcGuard;
import com.example.panel.service.RuntimeCoordinationService;
import com.example.panel.support.FeedbackPromptOutboxTestSupport;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** Canonical V84/persisted outbox; real revalidation, no broker/provider or working datasource. */
class FeedbackPromptRecipientAuthorizationPostgresqlTest {
    private static final long USER=9_007_199_254_740_993L,CHANNEL=USER+2,BUSINESS=USER+6;
    private static JdbcTemplate jdbc;
    private final ObjectMapper mapper=new ObjectMapper();
    private MultibusinessFeatureFlags flags;
    private FeedbackPromptDeliveryAuthorizationService authorization;
    private OutboundFeedbackPromptPublishOutboxService outbox;
    @BeforeAll static void database()throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("feedback_recipient_r249");
        FeedbackPromptOutboxTestSupport.createSchema(jdbc);
        jdbc.execute("CREATE TABLE channels(id BIGINT PRIMARY KEY,is_active BOOLEAN,platform TEXT)");
        jdbc.execute("CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT)");
        jdbc.execute("CREATE TABLE business_channel_bindings(channel_id BIGINT,business_id BIGINT,is_active BOOLEAN,resolution_mode TEXT)");
        jdbc.execute("CREATE TABLE general_channel_question_template_bindings(channel_id BIGINT PRIMARY KEY,is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE shared_channel_general_question_template_bindings(channel_id BIGINT PRIMARY KEY,is_active BOOLEAN)");
    }
    @BeforeEach void reset(){
        for(String table:new String[]{"integration_transport_outbox","pending_feedback_requests","tickets","channels","businesses","business_channel_bindings","general_channel_question_template_bindings","shared_channel_general_question_template_bindings"})jdbc.update("DELETE FROM "+table);
        flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setGeneralChannelResolution(true);flags.setSharedChannelResolution(true);
        authorization=new FeedbackPromptDeliveryAuthorizationService(jdbc,mapper,flags);
        var rabbit=new IntegrationRabbitProperties();rabbit.setOutboundExchange("isolated.outbound");
        outbox=new OutboundFeedbackPromptPublishOutboxService(jdbc,mapper,mock(RabbitTemplate.class),rabbit,mock(RuntimeCoordinationService.class),new LegacyTicketIdJdbcGuard(jdbc));
    }
    static Stream<Arguments> owners(){return Stream.of("BUSINESS","GENERAL","SHARED_GENERAL","LEGACY").flatMap(scope->Stream.of("telegram","vk","max").flatMap(platform->Stream.of("T-R249"," padded/Ю%?' ").map(raw->Arguments.of(scope,platform,raw))));}
    static Stream<Arguments> denials(){return owners().flatMap(owner->Stream.of("root-aba","expired","missing-pending","duplicate-root","missing-snapshot","changed-prompt","wrong-route","inactive-channel","changed-platform","scope-revoked").map(change->Arguments.of(owner.get()[0],owner.get()[1],owner.get()[2],change)));}
    @ParameterizedTest @MethodSource("owners")
    void authorizesExactPublishedEventWithoutAnyWrite(String scope,String platform,String raw)throws Exception{
        var event=seed(scope,platform,raw);String before=fingerprint();
        assertThat(authorization.authorize(event,route(platform))).isTrue();
        assertThat(fingerprint()).isEqualTo(before);
    }
    @ParameterizedTest @MethodSource("owners")
    void processingEventCanBeReadBeforeRelayCommitsPublishedMarker(String scope,String platform,String raw){
        var event=seed(scope,platform,raw);jdbc.update("UPDATE integration_transport_outbox SET status='processing'");
        assertThat(authorization.authorize(event,route(platform))).isTrue();
    }
    @ParameterizedTest @MethodSource("owners")
    void flagRevocationNeverFallsBackToAnotherScope(String scope,String platform,String raw){
        var event=seed(scope,platform,raw);flags.getFoundation().setEnabled(scope.equals("LEGACY"));
        assertThat(authorization.authorize(event,route(platform))).isFalse();
    }
    @ParameterizedTest @MethodSource("denials")
    void queuedEventCannotAuthorizeAfterContextOrRecipientChanges(String scope,String platform,String raw,String change){
        var event=seed(scope,platform,raw);String routing=route(platform);
        switch(change){
            case "root-aba" -> {jdbc.update("UPDATE tickets SET ticket_public_id=NULL");jdbc.update("UPDATE tickets SET ticket_public_id=?","a".repeat(32));}
            case "expired" -> jdbc.update("UPDATE pending_feedback_requests SET expires_at=clock_timestamp()-interval '1 second'");
            case "missing-pending" -> jdbc.update("DELETE FROM pending_feedback_requests");
            case "duplicate-root" -> jdbc.update("INSERT INTO tickets(user_id,ticket_id,channel_id,ticket_public_id,business_id,is_general) SELECT user_id+1,ticket_id,channel_id,ticket_public_id,business_id,is_general FROM tickets");
            case "missing-snapshot" -> jdbc.update("UPDATE integration_transport_outbox SET payload_json=(payload_json::jsonb-'feedbackTicketContext')::text");
            case "changed-prompt" -> event=new OutboundFeedbackPromptEvent(event.eventId(),event.eventType(),event.correlationId(),event.platform(),event.channelId(),event.requestId(),event.userId(),event.ticketId(),"forged prompt");
            case "wrong-route" -> routing="foreign.route";
            case "inactive-channel" -> jdbc.update("UPDATE channels SET is_active=FALSE");
            case "changed-platform" -> jdbc.update("UPDATE channels SET platform='foreign'");
            case "scope-revoked" -> {
                if(scope.equals("BUSINESS"))jdbc.update("UPDATE business_channel_bindings SET is_active=FALSE");
                else if(scope.equals("GENERAL"))jdbc.update("UPDATE general_channel_question_template_bindings SET is_active=FALSE");
                else if(scope.equals("SHARED_GENERAL"))jdbc.update("UPDATE shared_channel_general_question_template_bindings SET is_active=FALSE");
                else jdbc.update("INSERT INTO business_channel_bindings VALUES(?,?,TRUE,'EXPLICIT_SELECTION')",CHANNEL,BUSINESS);
            }
            default -> throw new AssertionError(change);
        }
        assertThat(authorization.authorize(event,routing)).isFalse();
    }
    private OutboundFeedbackPromptEvent seed(String scope,String platform,String raw){
        boolean general=scope.contains("GENERAL");boolean business=scope.equals("BUSINESS");
        flags.getFoundation().setEnabled(!scope.equals("LEGACY"));
        jdbc.update("INSERT INTO channels VALUES(?,TRUE,?)",CHANNEL,platform);
        jdbc.update("INSERT INTO businesses VALUES(?,'active')",BUSINESS);
        if(business)jdbc.update("INSERT INTO business_channel_bindings VALUES(?,?,TRUE,'SINGLE_BUSINESS')",CHANNEL,BUSINESS);
        if(scope.equals("GENERAL"))jdbc.update("INSERT INTO general_channel_question_template_bindings VALUES(?,TRUE)",CHANNEL);
        if(scope.equals("SHARED_GENERAL"))jdbc.update("INSERT INTO shared_channel_general_question_template_bindings VALUES(?,TRUE)",CHANNEL);
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,channel_id,ticket_public_id,business_id,is_general) VALUES(?,?,?,?,?,?)",USER,raw,CHANNEL,"a".repeat(32),business?BUSINESS:null,general);
        FeedbackPromptOutboxTestSupport.request(jdbc,100L,USER,CHANNEL,raw);
        String id=UUID.randomUUID().toString();var event=new OutboundFeedbackPromptEvent(id,"feedback.prompt.dispatch",id,platform,CHANNEL,100L,USER,raw,"Rate "+raw);
        outbox.enqueue(event,route(platform));jdbc.update("UPDATE integration_transport_outbox SET status='published'");return event;
    }
    private String route(String platform){return "integration.outbound.feedback.prompt."+platform+".channel."+CHANNEL;}
    private String fingerprint(){return jdbc.queryForObject("SELECT md5((SELECT jsonb_agg(to_jsonb(o))::text FROM integration_transport_outbox o)||(SELECT jsonb_agg(to_jsonb(p))::text FROM pending_feedback_requests p)||(SELECT jsonb_agg(to_jsonb(t))::text FROM tickets t))",String.class);}
}
