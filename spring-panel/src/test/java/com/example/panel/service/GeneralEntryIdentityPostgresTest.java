package com.example.panel.service;

import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import static com.example.panel.support.GeneralTicketAdminTestSupport.administrator;
import static com.example.panel.support.GeneralEntryIdentityFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneralEntryIdentityPostgresTest {
    static JdbcTemplate jdbc;
    GeneralTicketAccessService access;
    @BeforeAll static void database() {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplate("general_entry_identity");schema(jdbc,false);
    }
    @BeforeEach void setup() { seed(jdbc,7,"caller","unused");access=new GeneralTicketAccessService(jdbc); }

    static Stream<Arguments> names() {
        return Stream.of("caller","CaLlEr"," caller ","имя","anonymousUser","İ","i\u0307")
            .flatMap(name->Stream.of(7L,9007199254740993L).map(id->Arguments.of(id,name)));
    }
    @ParameterizedTest @MethodSource("names") void exactBoundIdAndRawNameArePreserved(long id,String name) {
        seed(jdbc,id,name,"unused");var before=grants(jdbc);
        assertThat(resolve(access,auth(id,name))).isEqualTo(id);assertThat(grants(jdbc)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"rename-reuse","delete-reuse","case-change","disabled","blocked","ambiguous"})
    void currentAccountMustStillBeTheBoundAccount(String kind) {
        var actor=auth(7,"caller");change(jdbc,kind);var before=grants(jdbc);
        assertThatThrownBy(()->resolve(access,actor)).isInstanceOf(AccessDeniedException.class);
        assertThat(grants(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> invalid() {
        return Stream.of(
            Arguments.of("null",null),
            Arguments.of("string",UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of())),
            Arguments.of("unbound-user",UsernamePasswordAuthenticationToken.authenticated(new org.springframework.security.core.userdetails.User("caller","unused",List.of()),null,List.of())),
            Arguments.of("anonymous",new AnonymousAuthenticationToken("key",new PanelUserPrincipal(7,"caller","unused",true,List.of()),List.of(()->"ROLE_ANONYMOUS"))),
            Arguments.of("unauthenticated",UsernamePasswordAuthenticationToken.unauthenticated(new PanelUserPrincipal(7,"caller","unused",true,List.of()),null)),
            Arguments.of("disabled-principal",UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(7,"caller","unused",false,List.of()),null,List.of())),
            Arguments.of("zero",auth(0,"caller")),Arguments.of("negative",auth(-1,"caller")),
            Arguments.of("wrong-id",auth(99,"caller")),Arguments.of("wrong-raw-name",auth(7,"CALLER")),
            Arguments.of("spaces",auth(7," ")));
    }
    @ParameterizedTest @MethodSource("invalid") void noUsernameFallback(String description,Authentication actor) {
        assertThatThrownBy(()->resolve(access,actor)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void nullBlockedRetainsTheExistingGeneralPolicy() {
        jdbc.update("UPDATE users SET is_blocked=NULL WHERE id=7");
        assertThat(resolve(access,auth(7,"caller"))).isEqualTo(7);
        assertThat(access.canRead(7L)).isTrue();assertThat(access.canOperate(7L)).isTrue();
    }
    @Test void entryDoesNotRequireAnyGeneralGrantOrGrantBypass() {
        jdbc.execute("TRUNCATE general_ticket_read_grants,general_ticket_operate_grants");
        assertThat(resolve(access,auth(7,"caller"))).isEqualTo(7);
        assertThat(access.canRead(7L)).isFalse();assertThat(access.canOperate(7L)).isFalse();
        assertThat(access.grantOperate(administrator(jdbc),7)).isFalse();assertThat(access.grantRead(administrator(jdbc),7)).isTrue();
        assertThat(access.grantOperate(administrator(jdbc),7)).isTrue();assertThat(access.revokeRead(administrator(jdbc),7)).isTrue();
        assertThat(access.canRead(7L)).isFalse();assertThat(access.canOperate(7L)).isFalse();
    }
    @Test void databaseFailureIsNotAUsernameFallback() {
        jdbc.execute("ALTER TABLE users RENAME TO isolated_unavailable_users");
        try { assertThatThrownBy(()->resolve(access,auth(7,"caller"))).isInstanceOf(org.springframework.dao.DataAccessException.class); }
        finally { jdbc.execute("ALTER TABLE isolated_unavailable_users RENAME TO users"); }
    }
}
