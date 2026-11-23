package com.example.panel.security;

import com.example.panel.controller.AuthManagementApiController;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL migration, persistence and concurrent revocation; disposable store only. */
class SessionRevocationPostgresTest {
    JdbcTemplate jdbc;
    AuthManagementApiController controller;
    org.springframework.security.core.Authentication actor;

    @BeforeEach void setup() throws Exception {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("session_revocation");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY, username TEXT NOT NULL)");
        jdbc.update("INSERT INTO users VALUES(1,'admin'),(2,'target'),(3,'other')");
        jdbc.execute(new ClassPathResource("db/migration/postgresql/V100__user_session_revocation.sql").getContentAsString(StandardCharsets.UTF_8));
        controller = new AuthManagementApiController(jdbc, null, null, null, null, null);
        var principal = new PanelUserPrincipal(1,"admin","unused",true,AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
        actor = UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities());
    }

    @Test void migrationRetainsAccountsAndInitializesLegacyVersion() {
        assertThat(jdbc.queryForList("SELECT session_version FROM users",Long.class)).containsExactly(0L,0L,0L);
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET session_version=-1 WHERE id=2"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void anotherServiceInstanceAndNewConnectionObserveCommittedRevocation() {
        var first = new PanelSessionVersionService(jdbc);
        assertThat(first.isCurrent(2,"target",0)).isTrue();
        assertThat(controller.revokeUserSessions(actor,2)).containsEntry("affected_users",1);
        var replica = new PanelSessionVersionService(new JdbcTemplate(jdbc.getDataSource()));
        assertThat(replica.isCurrent(2,"target",0)).isFalse();
        assertThat(replica.isCurrent(2,"target",1)).isTrue();
        assertThat(replica.isCurrent(3,"other",0)).isTrue();
        assertThat(replica.isCurrent(2,"TARGET",1)).isFalse();
    }

    @Test void simultaneousSingleAndGlobalRevocationsNeverLoseAnIncrement() throws Exception {
        var executor=Executors.newFixedThreadPool(8);
        try {
            var tasks=new ArrayList<java.util.concurrent.Callable<Void>>();
            for(int i=0;i<24;i++) { final boolean all=i%2==0;tasks.add(() -> {
                if(all)controller.revokeAllSessions(actor);else controller.revokeUserSessions(actor,2);return null;
            }); }
            for(var result:executor.invokeAll(tasks))result.get();
        } finally { executor.shutdownNow(); }
        assertThat(jdbc.queryForObject("SELECT session_version FROM users WHERE id=2",Long.class)).isEqualTo(24);
        assertThat(jdbc.queryForList("SELECT session_version FROM users WHERE id IN (1,3)",Long.class)).containsExactly(12L,12L);
    }

    @Test void missingSchemaAndUnavailableAccountFailClosed() {
        var service=new PanelSessionVersionService(jdbc);
        assertThat(service.isCurrent(999,"missing",0)).isFalse();
        jdbc.execute("ALTER TABLE users DROP COLUMN session_version");
        assertThat(service.isCurrent(2,"target",0)).isFalse();
        assertThatThrownBy(() -> controller.revokeUserSessions(actor,2))
            .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(503));
    }
}
