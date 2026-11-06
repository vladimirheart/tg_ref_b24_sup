package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static com.example.panel.support.GeneralReadActorFixture.*;
import com.example.panel.service.integration.*;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class MaxLinkedGeneralReadPostgresTest {
    static JdbcTemplate jdbc;static String namespace;
    @BeforeAll static void schema(){jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplate("r292_general_link");namespace=jdbc.queryForObject("SELECT current_schema()",String.class);com.example.panel.support.GeneralReadActorFixture.schema(jdbc,false);}
    @AfterAll static void close(){if(jdbc!=null && namespace.matches("r292_general_link_[a-f0-9]{12}"))jdbc.execute("DROP SCHEMA "+namespace+" CASCADE");}
    @BeforeEach void fixture(){seed(jdbc,7,"caller","unused");jdbc.execute("UPDATE channels SET platform='max' WHERE id=9");}
    @ParameterizedTest @ValueSource(strings={"forward","reply"})
    void sourceIsReadOnlyFromTheCanonicalGeneralOwnerAndDisappearsOnRevoke(String type) {
        var source=new MessageSourceLink(type,"mid.original+opaque","9007199254740993","Original <author>");
        jdbc.update("UPDATE chat_history SET source_link_json=? WHERE id=1",MessageSourceLinkJson.write(source));
        var reader=new GeneralTicketReadService(jdbc,new GeneralTicketAccessService(jdbc));
        var history=reader.history(auth(7,"caller"),PUBLIC,null,10).orElseThrow();
        assertThat(history.entries().stream().filter(e->e.sourceLink()!=null)).singleElement().satisfies(e->assertThat(e.sourceLink()).isEqualTo(source));
        assertThat(reader.history(auth(7,"caller"),VK_PUBLIC,null,10).orElseThrow().entries()).allSatisfy(e->assertThat(e.sourceLink()).isNull());
        jdbc.execute("UPDATE general_ticket_read_grants SET is_active=false WHERE user_id=7");assertThat(reader.history(auth(7,"caller"),PUBLIC,null,10)).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"deleted","foreign-owner","foreign-public-id"})
    void sourceContextCannotBypassExistingHistoryTupleChecks(String change) {
        jdbc.update("UPDATE chat_history SET source_link_json=? WHERE id=1",MessageSourceLinkJson.write(new MessageSourceLink("reply","mid.secret",null,null)));
        switch(change){case "deleted"->jdbc.execute("UPDATE chat_history SET deleted_at=now() WHERE id=1");case "foreign-owner"->jdbc.update("UPDATE chat_history SET ticket_owner_user_id=? WHERE id=1",OWNER+1);case "foreign-public-id"->jdbc.update("UPDATE chat_history SET ticket_public_id=? WHERE id=1",VK_PUBLIC);}
        var reader=new GeneralTicketReadService(jdbc,new GeneralTicketAccessService(jdbc));
        assertThat(reader.history(auth(7,"caller"),PUBLIC,null,10).orElseThrow().entries()).allSatisfy(e->assertThat(e.sourceLink()).isNull());
    }
}
