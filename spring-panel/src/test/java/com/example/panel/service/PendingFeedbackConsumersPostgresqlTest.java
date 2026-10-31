package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.example.panel.config.PanelIntegrationTransportMode;
import com.example.panel.entity.*;
import com.example.panel.repository.*;
import com.example.panel.service.integration.OutboundFeedbackPromptPublisher;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;

/** Real pending repository/native ownership SQL and Hibernate transactions; provider sends are doubles. */
class PendingFeedbackConsumersPostgresqlTest {
    private static final long USER = 9_007_199_254_740_993L;
    private static final long CHANNEL = 9_007_199_254_740_995L;
    private static JdbcTemplate jdbc;
    private static EntityManagerFactory emf;
    private static PendingFeedbackRequestRepository realRequests;
    private static FeedbackRepository realFeedback;
    private PendingFeedbackRequestRepository requests;
    private FeedbackRepository feedback;
    private TicketRepository tickets;
    private BotRuntimeTicketReadService reads;
    private BotRuntimeTicketWriteService writes;
    private FeedbackPromptDispatchSchedulerService dispatch;
    private PanelBotSettingsService settings;
    private OutboundFeedbackPromptPublisher publisher;

    @BeforeAll static void database() throws Exception {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("feedback_consumers_r244");
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(jdbc.getDataSource());
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setManagedTypes(PersistenceManagedTypes.of(Channel.class.getName(), Feedback.class.getName(), PendingFeedbackRequest.class.getName()));
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.physical_naming_strategy",
            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet();
        emf = factory.getObject();
        var repositories = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf));
        realRequests = repositories.getRepository(PendingFeedbackRequestRepository.class);
        realFeedback = repositories.getRepository(FeedbackRepository.class);
        jdbc.execute("CREATE TABLE tickets(ticket_id TEXT,user_id BIGINT,channel_id BIGINT,status TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN)");
        jdbc.execute("CREATE TABLE ui_event_outbox(id BIGINT PRIMARY KEY,event_type TEXT,ticket_id TEXT,channel_id BIGINT,message_text TEXT,message_type TEXT,attachment TEXT,rating INTEGER,ticket_user_id BIGINT,ticket_public_id TEXT,ticket_business_id BIGINT,ticket_scope_kind TEXT,created_at TIMESTAMPTZ)");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V84__pending_feedback_context.sql")));
        jdbc.update("INSERT INTO channels(id,token,channel_name) VALUES(?,'isolated','isolated')", CHANNEL);
    }
    @AfterAll static void close() { if (emf != null) emf.close(); }
    @BeforeEach void reset() {
        for (String table : List.of("pending_feedback_requests","feedbacks","ui_event_outbox","tickets")) jdbc.update("DELETE FROM "+table);
        requests = mock(PendingFeedbackRequestRepository.class, delegatesTo(realRequests));
        feedback = mock(FeedbackRepository.class, delegatesTo(realFeedback));
        tickets = mock(TicketRepository.class);
        when(tickets.findUniqueByLegacyTicketId(anyString())).thenAnswer(call -> {
            String raw=call.getArgument(0);
            var rows=jdbc.queryForList("SELECT * FROM tickets WHERE ticket_id=?",raw);
            if(rows.size()!=1)return Optional.empty();
            var row=rows.get(0);var ticket=new Ticket();var id=new TicketId();
            id.setUserId((Long)row.get("user_id"));id.setTicketId(raw);ticket.setId(id);
            if(row.get("channel_id")!=null){var channel=new Channel();channel.setId((Long)row.get("channel_id"));ticket.setChannel(channel);}
            return Optional.of(ticket);
        });
        reads = new BotRuntimeTicketReadService(mock(MessageRepository.class), tickets, mock(TicketActiveRepository.class), feedback, requests);
        var guard = new LegacyTicketIdJdbcGuard(jdbc);
        writes = new BotRuntimeTicketWriteService(jdbc,mock(DialogReplyTargetService.class),mock(DialogResponsibilityService.class),
            mock(DialogParticipantService.class),new UiEventOutboxAppendService(jdbc,guard),mock(ProviderDeliveryLedgerService.class),requests,feedback,guard);
        publisher=mock(OutboundFeedbackPromptPublisher.class);settings=mock(PanelBotSettingsService.class);
        when(settings.resolveRatingPromptTemplate(any())).thenReturn(new PanelBotSettingsService.RatingPromptTemplate("rate {ticket_id} {scale}",5));
        dispatch = new FeedbackPromptDispatchSchedulerService(requests,reads,settings,publisher,
            new PanelIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode","rabbitmq")),
            mock(RuntimeCoordinationService.class),tickets);
    }
    static Stream<Arguments> owners() {
        return Stream.of("BUSINESS","GENERAL","UNRESOLVED").flatMap(scope -> Stream.of("T-R244"," padded/Ю%?' ")
            .map(raw -> Arguments.of(scope,raw)));
    }
    static Stream<Arguments> currentCases() {
        return owners().flatMap(a -> Stream.of("read","prompt","write").map(surface -> Arguments.of(a.get()[0],a.get()[1],surface)));
    }
    static Stream<Arguments> staleCases() {
        return currentCases().flatMap(a -> Stream.of("unbound","scope-aba","channel-aba","recreated","public-assigned")
            .map(change -> Arguments.of(a.get()[0],a.get()[1],a.get()[2],change)));
    }
    @ParameterizedTest(name="{0}/{1}/{2}") @MethodSource("currentCases")
    void currentCapturedRequestRetainsEachExistingPath(String scope,String raw,String surface) {
        seed(scope,raw,false);
        switch(surface){
            case "read" -> {var result=tx().execute(s -> reads.findActiveFeedbackRequest(USER,CHANNEL));assertThat(result).get()
                .satisfies(row -> {assertThat(row.id()).isEqualTo(1L);assertThat(row.ticketId()).isEqualTo(raw);assertThat(row.userId()).isEqualTo(USER);assertThat(row.channelId()).isEqualTo(CHANNEL);});}
            case "prompt" -> {tx().executeWithoutResult(s -> dispatch.dispatchPendingFeedbackRequestsInternal());verify(publisher).publish(eq(1L),any(Channel.class),eq(USER),eq(raw),eq("rate "+raw+" 5"));assertThat(jdbc.queryForObject("SELECT sent_at IS NOT NULL FROM pending_feedback_requests",Boolean.class)).isTrue();}
            case "write" -> {var result=tx().execute(s -> writes.storeFeedback(1L,5));assertThat(result).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(true,true));assertThat(jdbc.queryForObject("SELECT rating FROM feedbacks",Integer.class)).isEqualTo(5);assertThat(jdbc.queryForObject("SELECT rating FROM ui_event_outbox",Integer.class)).isEqualTo(5);}
            default -> throw new AssertionError(surface);
        }
    }
    @ParameterizedTest(name="{0}/{1}/{2}/{3}") @MethodSource("staleCases")
    void everyConsumerRejectsHistoricalOrPreviousGeneration(String scope,String raw,String surface,String change) {
        seed(scope,raw,change.equals("public-assigned"));
        invalidate(scope,raw,change);
        var before=snapshot();
        denied(surface);
        assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest(name="{0}/{1}/{2}") @MethodSource("currentCases")
    void lateChangeAfterLookupCannotBeRecaptured(String scope,String raw,String surface) {
        seed(scope,raw,false);
        switch(surface){
            case "read" -> doAnswer(call -> {invalidate(scope,raw,"scope-aba");return false;}).when(feedback).existsByTicketId(raw);
            case "prompt" -> doAnswer(call -> {invalidate(scope,raw,"scope-aba");return new PanelBotSettingsService.RatingPromptTemplate("rate {ticket_id} {scale}",5);}).when(settings).resolveRatingPromptTemplate(any());
            case "write" -> doAnswer(call -> {invalidate(scope,raw,"scope-aba");return Optional.empty();}).when(feedback).findFirstByTicketIdOrderByTimestampDesc(raw);
            default -> throw new AssertionError(surface);
        }
        var before=snapshot();
        denied(surface);
        assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest(name="{0}/{1}") @MethodSource("owners")
    void staleManagedUnsentCandidateDoesNotSendAfterAnotherSender(String scope,String raw) {
        seed(scope,raw,false);
        tx().executeWithoutResult(s -> {
            var candidate=realRequests.findById(1L).orElseThrow();
            var sent=OffsetDateTime.now().minusMinutes(1);
            jdbc.update("UPDATE pending_feedback_requests SET sent_at=? WHERE id=1",sent);
            doReturn(List.of(candidate)).when(requests).findTop50BySentAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(any());
            var before=snapshot();
            dispatch.dispatchPendingFeedbackRequestsInternal();
            verifyNoInteractions(publisher);
            assertThat(snapshot()).isEqualTo(before);
        });
    }
    private void denied(String surface) {
        switch(surface){
            case "read" -> {var result=tx().execute(s -> reads.findActiveFeedbackRequest(USER,CHANNEL));assertThat(result).isEmpty();}
            case "prompt" -> {tx().executeWithoutResult(s -> dispatch.dispatchPendingFeedbackRequestsInternal());verifyNoInteractions(publisher);}
            case "write" -> {var result=tx().execute(s -> writes.storeFeedback(1L,5));assertThat(result).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(false,false));}
            default -> throw new AssertionError(surface);
        }
    }
    private void seed(String scope,String raw,boolean nullPublic) {
        jdbc.update("INSERT INTO tickets(ticket_id,user_id,channel_id,status,ticket_public_id,business_id,is_general) VALUES(?,?,?,'closed',?,?,?)",
            raw,USER,CHANNEL,nullPublic?null:"a".repeat(32),scope.equals("BUSINESS")?31L:null,scope.equals("GENERAL"));
        jdbc.update("INSERT INTO pending_feedback_requests(id,user_id,channel_id,ticket_id,source,created_at,expires_at) VALUES(1,?,?,?,'new',?,?)",
            USER,CHANNEL,raw,OffsetDateTime.now().minusMinutes(1),OffsetDateTime.now().plusDays(1));
    }
    private void invalidate(String scope,String raw,String change) {
        switch(change){
            case "unbound" -> {
                // Model rows that predate V84, without relaxing the production immutable UPDATE rule.
                jdbc.execute("ALTER TABLE pending_feedback_requests DISABLE TRIGGER trg_pending_feedback_context");
                try {jdbc.update("UPDATE pending_feedback_requests SET ticket_context_epoch=NULL,ticket_public_id=NULL,ticket_business_id=NULL,ticket_is_general=NULL WHERE id=1");}
                finally {jdbc.execute("ALTER TABLE pending_feedback_requests ENABLE TRIGGER trg_pending_feedback_context");}
            }
            case "scope-aba" -> {jdbc.update("UPDATE tickets SET is_general=FALSE,business_id=? WHERE ticket_id=?",scope.equals("BUSINESS")?32L:31L,raw);jdbc.update("UPDATE tickets SET is_general=?,business_id=? WHERE ticket_id=?",scope.equals("GENERAL"),scope.equals("BUSINESS")?31L:null,raw);}
            case "channel-aba" -> {jdbc.update("UPDATE tickets SET channel_id=NULL WHERE ticket_id=?",raw);jdbc.update("UPDATE tickets SET channel_id=? WHERE ticket_id=?",CHANNEL,raw);}
            case "recreated" -> {jdbc.update("DELETE FROM tickets WHERE ticket_id=?",raw);jdbc.update("INSERT INTO tickets(ticket_id,user_id,channel_id,status,ticket_public_id,business_id,is_general) VALUES(?,?,?,'closed',?,?,?)",raw,USER,CHANNEL,"a".repeat(32),scope.equals("BUSINESS")?31L:null,scope.equals("GENERAL"));}
            case "public-assigned" -> jdbc.update("UPDATE tickets SET ticket_public_id=? WHERE ticket_id=?","a".repeat(32),raw);
            default -> throw new AssertionError(change);
        }
    }
    private static List<List<Map<String,Object>>> snapshot() {
        return List.of(jdbc.queryForList("SELECT * FROM pending_feedback_requests ORDER BY id"),jdbc.queryForList("SELECT * FROM feedbacks ORDER BY id"),jdbc.queryForList("SELECT * FROM ui_event_outbox ORDER BY id"));
    }
    private static TransactionTemplate tx() {return new TransactionTemplate(new JpaTransactionManager(emf));}
}
