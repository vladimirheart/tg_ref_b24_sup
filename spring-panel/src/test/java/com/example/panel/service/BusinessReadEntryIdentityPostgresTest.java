package com.example.panel.service;

import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import static org.assertj.core.api.Assertions.*;

/** The canonical read/manual entry must never resolve a different account by a reused name. */
class BusinessReadEntryIdentityPostgresTest {
    private static JdbcTemplate jdbc;
    private static SingleConnectionDataSource connection;
    private BusinessScopedTicketReadService service;

    @BeforeAll static void schema() throws Exception {
        var fresh=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("business_entry_identity");
        connection=new SingleConnectionDataSource(fresh.getDataSource().getConnection(),true);
        jdbc=new JdbcTemplate(connection);
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT UNIQUE,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE)");
    }
    @AfterAll static void close() { if(connection!=null)connection.destroy(); }
    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE users");
        jdbc.update("INSERT INTO users(id,username) VALUES(7,'caller')");
        service=new BusinessScopedTicketReadService(jdbc,new BusinessAccessService(jdbc));
    }
    private static Authentication actor(long id,String name) {
        return UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(id,name,"unused",true,List.of()),null,List.of());
    }
    // Frozen bridge: the same test source and bytecode execute before and after the API change.
    private long resolve(Authentication authentication) {
        var method=java.util.Arrays.stream(BusinessScopedTicketReadService.class.getMethods())
            .filter(m->m.getName().equals("requireAuthenticatedUserId")).findFirst().orElseThrow();
        Object input=method.getParameterTypes()[0]==String.class
            ? (authentication==null?null:authentication.getName()) : authentication;
        try { return (Long)method.invoke(service,input); }
        catch(InvocationTargetException ex) { if(ex.getCause() instanceof RuntimeException cause)throw cause;throw new AssertionError(ex.getCause()); }
        catch(ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private String snapshot() { return jdbc.queryForObject("SELECT COALESCE(jsonb_agg(to_jsonb(u) ORDER BY id)::text,'[]') FROM users u",String.class); }
    private void denied(Authentication authentication) {
        String before=snapshot();
        assertThatThrownBy(()->resolve(authentication)).isInstanceOf(AccessDeniedException.class);
        assertThat(snapshot()).isEqualTo(before);
    }
    static Stream<Arguments> exactAccounts() {
        return Stream.of("caller","Caller"," caller ","имя","anonymousUser","\u0130","i\u0307")
            .flatMap(name->Stream.of(7L,9007199254740993L).map(id->Arguments.of(id,name)));
    }
    @ParameterizedTest @MethodSource("exactAccounts")
    void exactStoredNamesAndBigintIdsRemainUsable(long id,String name) {
        jdbc.update("UPDATE users SET id=?,username=? WHERE id=7",id,name);
        String before=snapshot();assertThat(resolve(actor(id,name))).isEqualTo(id);assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"rename-reuse","delete-reuse","case-change","disabled","blocked","null-enabled","null-blocked","missing","wrong-id"})
    void changedAccountCannotSelectAReplacement(String change) {
        switch(change) {
            case "rename-reuse"->{jdbc.update("UPDATE users SET username='renamed' WHERE id=7");jdbc.update("INSERT INTO users(id,username) VALUES(55,'caller')");}
            case "delete-reuse"->{jdbc.update("DELETE FROM users WHERE id=7");jdbc.update("INSERT INTO users(id,username) VALUES(55,'caller')");}
            case "case-change"->jdbc.update("UPDATE users SET username='Caller' WHERE id=7");
            case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=7");
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=7");
            case "null-enabled"->jdbc.update("UPDATE users SET enabled=NULL WHERE id=7");
            case "null-blocked"->jdbc.update("UPDATE users SET is_blocked=NULL WHERE id=7");
            case "missing"->jdbc.update("DELETE FROM users");
            case "wrong-id"->jdbc.update("UPDATE users SET id=55 WHERE id=7");
        }
        denied(actor(7,"caller"));
    }
    @ParameterizedTest @ValueSource(strings={"CALLER"," caller","caller ","other","caller\u00a0","caller\t"})
    void principalNameMustMatchTheRawStoredName(String name) { denied(actor(7,name)); }
    @ParameterizedTest @ValueSource(strings={"null","unbound","anonymous","unauthenticated","disabled-principal","zero-id","negative-id","blank-name"})
    void invalidPrincipalsCannotFallBackToNames(String kind) {
        Authentication value=switch(kind) {
            case "null"->null;
            case "unbound"->UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of());
            case "anonymous"->new AnonymousAuthenticationToken("key","caller",List.of(()->"ROLE_ANONYMOUS"));
            case "unauthenticated"->UsernamePasswordAuthenticationToken.unauthenticated(new PanelUserPrincipal(7,"caller","unused",true,List.of()),null);
            case "disabled-principal"->UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(7,"caller","unused",false,List.of()),null,List.of());
            case "zero-id"->actor(0,"caller");
            case "negative-id"->actor(-7,"caller");
            default->actor(7," ");
        };
        denied(value);
    }
    @ParameterizedTest @ValueSource(strings={"active","disabled","blocked"})
    void existingCaseInsensitiveAmbiguityStillFailsClosed(String state) {
        jdbc.update("INSERT INTO users(id,username,enabled,is_blocked) VALUES(55,'CALLER',?,?)",!state.equals("disabled"),state.equals("blocked"));
        denied(actor(7,"caller"));
    }
    @Test void unavailableAccountStorageDoesNotUseAnIdentityFallback() {
        jdbc.execute("ALTER TABLE users RENAME TO unavailable_users");
        try { assertThatThrownBy(()->resolve(actor(7,"caller"))).isInstanceOf(DataAccessException.class); }
        finally { jdbc.execute("ALTER TABLE unavailable_users RENAME TO users"); }
    }
}
