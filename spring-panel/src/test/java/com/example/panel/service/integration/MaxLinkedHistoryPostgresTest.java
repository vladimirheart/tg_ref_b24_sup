package com.example.panel.service.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.panel.entity.*;
import com.example.panel.service.*;
import com.example.panel.storage.AttachmentService;
import com.example.panel.support.*;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Real V87, actual Hibernate entity mapping and actual scoped history SQL in a nonce-owned schema. */
class MaxLinkedHistoryPostgresTest {
    static JdbcTemplate jdbc;
    static SessionFactory sessions;
    static String schema;
    @BeforeAll static void database()throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplate("r292_linked_history");schema=jdbc.queryForObject("SELECT current_schema()",String.class);
        jdbc.execute("CREATE TABLE channels(id bigint PRIMARY KEY,platform text,is_active boolean);INSERT INTO channels VALUES(55,'max',true),(56,'vk',true),(57,'max',false)");
        jdbc.execute("CREATE TABLE chat_history(id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,user_id bigint,sender text,message text,timestamp timestamptz,ticket_id text,ticket_owner_user_id bigint,message_type text,attachment text,channel_id bigint REFERENCES channels(id),tg_message_id bigint,reply_to_tg_id bigint,original_message text,forwarded_from text,file_name text,edited_at timestamptz,deleted_at timestamptz)");
        jdbc.execute(new ClassPathResource("db/migration/postgresql/V87__max_message_source_link.sql").getContentAsString(StandardCharsets.UTF_8));
        var configuration=new org.hibernate.cfg.Configuration().addAnnotatedClass(ChatHistory.class).addAnnotatedClass(Channel.class);
        configuration.getProperties().put("hibernate.connection.datasource",jdbc.getDataSource());
        configuration.setProperty("hibernate.hbm2ddl.auto","none");configuration.setProperty("hibernate.dialect","org.hibernate.dialect.PostgreSQLDialect");
        configuration.setPhysicalNamingStrategy(new org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy());
        sessions=configuration.buildSessionFactory();
    }
    @AfterAll static void stop(){if(sessions!=null)sessions.close();if(jdbc!=null && schema.matches("r292_linked_history_[a-f0-9]{12}"))jdbc.execute("DROP SCHEMA "+schema+" CASCADE");}
    @BeforeEach void clean(){jdbc.execute("TRUNCATE chat_history");}
    @ParameterizedTest @ValueSource(strings={"forward","reply"})
    void actualJpaRoundTripAndHistoryReadPreserveSourceAndOuterOwner(String type) {
        var link=new MessageSourceLink(type,"mid.raw:<opaque>+identity","9007199254740993","Original <author>");
        long id;
        try(var session=sessions.openSession()) {
            var tx=session.beginTransaction();var row=new ChatHistory();row.setUserId(1001L);row.setTicketOwnerUserId(1001L);row.setTicketId(" raw-root ");
            row.setChannel(session.getReference(Channel.class,55L));row.setSender("client");row.setMessage("Caption");row.setMessageType("document");row.setTimestamp(OffsetDateTime.now());
            row.setTgMessageId(Long.MAX_VALUE);row.setSourceLinkJson(MessageSourceLinkJson.write(link));session.persist(row);tx.commit();id=row.getId();
        }
        try(var session=sessions.openSession()) {
            var restored=session.find(ChatHistory.class,id);assertThat(restored.getUserId()).isEqualTo(1001L);assertThat(restored.getTgMessageId()).isEqualTo(Long.MAX_VALUE);
            assertThat(restored.getReplyToTgId()).isNull();assertThat(MessageSourceLinkJson.read(restored.getSourceLinkJson())).isEqualTo(link);
        }
        var guard=mock(LegacyTicketIdJdbcGuard.class);when(guard.hasUniqueNonGeneralTicket(" raw-root ")).thenReturn(true);
        var reader=new DialogConversationReadService(jdbc,mock(AttachmentService.class),new PanelTimestampSqlSupport(),guard);
        assertThat(reader.loadHistory(" raw-root ",55L)).singleElement().satisfies(message->assertThat(message.sourceLink()).isEqualTo(link));
        assertThat(reader.loadHistory(" raw-root ",56L)).isEmpty();assertThat(reader.loadHistory("foreign",55L)).isEmpty();
        jdbc.update("UPDATE chat_history SET deleted_at=now() WHERE id=?",id);
        assertThat(reader.loadHistory(" raw-root ",55L)).singleElement().satisfies(message->assertThat(message.sourceLink()).isNull());
    }
    @ParameterizedTest @ValueSource(strings={"{}","[]","{\"type\":\"reply\"}","{\"type\":\"other\",\"providerMessageId\":\"mid.a\"}","{\"type\":\"reply\",\"providerMessageId\":3}","{bad}"})
    void malformedContextIsRejectedByTheActualMigration(String json) {
        assertThatThrownBy(()->jdbc.update("INSERT INTO chat_history(source_link_json) VALUES(?)",json)).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_history",Integer.class)).isZero();
    }
    @Test void oldNullHistoryStillPersists(){jdbc.execute("INSERT INTO chat_history(message) VALUES('legacy')");assertThat(jdbc.queryForObject("SELECT source_link_json FROM chat_history",String.class)).isNull();}
    @ParameterizedTest @ValueSource(longs={55,56,57,58})
    void capabilityRequiresTheCurrentActiveMaxChannel(long channel) {
        var service=new SelectedTicketFollowupMediaService(jdbc,new com.example.panel.config.MultibusinessFeatureFlags(),mock(com.example.panel.storage.AttachmentObjectStorageService.class));
        if(channel==55)assertThatCode(()->service.requireMaxLinkedMedia(channel)).doesNotThrowAnyException();
        else assertThatThrownBy(()->service.requireMaxLinkedMedia(channel)).isInstanceOf(IllegalStateException.class);
    }
}
