package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import com.example.panel.repository.MessageRepository;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Executes the repository's actual native query, including LIMIT, on PostgreSQL's real column names. */
class ChildScopedHistoryPostgresTest {
    private JdbcTemplate jdbc;
    private String schema;
    private static final long USER=9007199254740993L,CHANNEL=Long.MAX_VALUE;
    @BeforeEach void setup() {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplate("r291_child_history");schema=jdbc.queryForObject("SELECT current_schema()",String.class);
        jdbc.execute("CREATE TABLE tickets(user_id bigint,ticket_id text,channel_id bigint,is_general boolean,PRIMARY KEY(user_id,ticket_id));CREATE TABLE messages(group_msg_id bigint PRIMARY KEY,user_id bigint,ticket_id text,channel_id bigint,created_at timestamp,problem text)");
    }
    @AfterEach void cleanup(){if(jdbc!=null && schema.matches("r291_child_history_[a-f0-9]{12}"))jdbc.execute("DROP SCHEMA "+schema+" CASCADE");}
    private List<Map<String,Object>> read(int limit) throws Exception {
        String sql=MessageRepository.class.getMethod("findScopedRecent",Long.class,Long.class,int.class).getAnnotation(Query.class).value();
        return new NamedParameterJdbcTemplate(jdbc).queryForList(sql,Map.of("userId",USER,"channelId",CHANNEL,"limit",limit));
    }
    private void root(String ticket,long user,long channel,Boolean general) {jdbc.update("INSERT INTO tickets VALUES(?,?,?,?)",user,ticket,channel,general);}
    private void message(long id,String ticket,long user,long channel,String time) {
        jdbc.update("INSERT INTO messages VALUES(?,?,?,?,?::timestamp,'private payload')",id,user,ticket,channel,time);
    }
    @ParameterizedTest @ValueSource(strings={"business","general","unresolved"})
    void channelConstraintPrecedesLimitAndPreservesRawIdentity(String kind) throws Exception {
        String raw=" padded/Ю%?' ";root(raw,USER,CHANNEL,kind.equals("unresolved")?null:kind.equals("general"));
        message(1,raw,USER,CHANNEL,"2026-11-01 00:00:00");
        for(int i=0;i<20;i++){String foreign="foreign-"+i;root(foreign,USER,22L,false);message(100+i,foreign,USER,22L,"2026-11-02 00:00:00");}
        assertThat(read(1)).singleElement().satisfies(row->assertThat(row.get("ticket_id")).isEqualTo(raw));
        assertThat(read(10)).hasSize(1);
    }
    @ParameterizedTest @ValueSource(strings={"foreign-message-channel","foreign-message-author","duplicate-root","foreign-root-channel"})
    void inconsistentChildAndRootNeverLeakIntoHistory(String change) throws Exception {
        root("root",USER,change.equals("foreign-root-channel")?22L:CHANNEL,false);
        if(change.equals("duplicate-root"))root("root",USER+1,22L,false);
        message(1,"root",change.equals("foreign-message-author")?USER+1:USER,change.equals("foreign-message-channel")?22L:CHANNEL,"2026-11-01 00:00:00");
        assertThat(read(10)).isEmpty();
    }
    @Test void timestampTiesUseActualGroupMessagePrimaryKey() throws Exception {
        root("old",USER,CHANNEL,false);root("new",USER,CHANNEL,false);
        message(1,"old",USER,CHANNEL,"2026-11-01 00:00:00");message(2,"new",USER,CHANNEL,"2026-11-01 00:00:00");
        assertThat(read(1)).singleElement().satisfies(row->assertThat(row.get("ticket_id")).isEqualTo("new"));
    }
}
