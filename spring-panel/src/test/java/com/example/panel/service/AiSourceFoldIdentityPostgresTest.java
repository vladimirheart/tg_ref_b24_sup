package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import static com.example.panel.service.ScopedAiSourceService.Kind.*;
import static com.example.panel.service.ScopedAiSourceService.Status.*;
import static com.example.panel.support.AiControlIdentityFixture.SCOPE;
import static com.example.panel.support.AiControlIdentityFixture.auth;
import static com.example.panel.support.AiMemoryIdentityFixture.*;
import static org.assertj.core.api.Assertions.*;

/** PostgreSQL and Java may fold U+0130 differently; a folded neighbour must never supply rights. */
class AiSourceFoldIdentityPostgresTest {
    private static final String RAW="\u0130";
    enum Operation { availableBusinesses,canConfigure,catalog,find,history,createKnowledge,createMemory,revise,reviseExpected }
    private static JdbcTemplate jdbc;
    private ScopedAiSourceService service;
    @BeforeAll static void schemaOnce()throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("ai_source_fold_identity");schema(jdbc,false);jdbc.execute("ALTER TABLE businesses ADD COLUMN display_name TEXT DEFAULT 'Private business'");
    }
    @BeforeEach void setup() {
        seed(jdbc,"caller","unused");service=new ScopedAiSourceService(jdbc,new MultibusinessFeatureFlags());
        assertThat(service.createKnowledge(11L,auth(7,"caller"),KEY,"Private title","private answer")).isTrue();
        assertThat(service.createMemory(SCOPE,auth(7,"caller"),KEY,"Private memory","private memory answer")).isTrue();
        jdbc.update("UPDATE users SET username=? WHERE id=7",RAW);
        jdbc.update("INSERT INTO users(id,username) VALUES(55,?)",RAW.toLowerCase(Locale.ROOT));
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'ADMIN')");
    }
    static Stream<Arguments> cases(){return Stream.of("disabled","blocked","no-rights").flatMap(s->Arrays.stream(Operation.values()).map(o->Arguments.of(s,o)));}
    @ParameterizedTest @MethodSource("cases")
    void anotherFoldedAccountCannotSupplyStatusOrAuthority(String status,Operation operation)throws Exception {
        if(status.equals("disabled"))jdbc.update("UPDATE users SET enabled=FALSE WHERE id=7");else if(status.equals("blocked"))jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=7");else jdbc.update("DELETE FROM business_memberships WHERE user_id=7");
        var before=snapshot(jdbc);assertThat(visibleOrChanged(operation)).isFalse();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    private boolean visibleOrChanged(Operation operation)throws Exception {
        var method=Arrays.stream(ScopedAiSourceService.class.getMethods()).filter(m->m.getName().equals(operation.name())).findFirst().orElseThrow();
        int index=operation==Operation.availableBusinesses?0:1;var actor=auth(7,RAW);Object identity=method.getParameterTypes()[index]==Authentication.class?actor:actor.getName();
        long source=jdbc.queryForObject("SELECT id FROM ai_scoped_sources WHERE source_kind='KNOWLEDGE'",Long.class);
        Object[] args=switch(operation){
            case availableBusinesses->new Object[]{identity};case canConfigure->new Object[]{11L,identity};
            case catalog->new Object[]{11L,identity,KNOWLEDGE,null,"",null,25};case find->new Object[]{11L,identity,KNOWLEDGE,KEY};case history->new Object[]{11L,identity,KNOWLEDGE,KEY,10};
            case createKnowledge->new Object[]{11L,identity,KEY+"-new","Forged","Forged"};case createMemory->new Object[]{SCOPE,identity,KEY+"-new","Forged","Forged"};
            case revise->new Object[]{11L,identity,KNOWLEDGE,KEY,1L,"Forged","Forged",APPROVED};case reviseExpected->new Object[]{11L,identity,KNOWLEDGE,KEY,source,1L,"Forged","Forged",APPROVED};
        };
        Object value;
        try{value=method.invoke(service,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception cause)throw cause;throw new AssertionError(e.getCause());}
        if(value instanceof Boolean flag)return flag;if(value instanceof Optional<?> optional)return optional.isPresent();return !((List<?>)value).isEmpty();
    }
}
