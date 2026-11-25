package com.example.panel.service;

import com.example.panel.security.*;
import com.example.panel.support.AccessRecoveryTestDatabase;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AccessRecoveryPostgresqlTest {
    JdbcTemplate jdbc;
    AccessRecoveryService service;
    TransactionTemplate tx;
    AccessRecoveryProperties properties;
    PublicAuthenticationGuard guard;
    final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(4);
    Authentication admin, delegate;

    @BeforeEach void setup() {
        jdbc = AccessRecoveryTestDatabase.fresh();
        tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        properties = new AccessRecoveryProperties(); properties.setMinimumResponseTime(Duration.ZERO);
        guard = new PublicAuthenticationGuard(jdbc, properties);
        service = new AccessRecoveryService(jdbc, passwords, guard, new RequestClientAddressResolver(properties), properties, mock(UiEventStreamService.class));
        long root = account("admin", false);
        long operator = account("operator", false);
        admin = actor(root, "admin", "ROLE_ADMIN"); delegate = actor(operator, "operator", AccessRecoveryService.RIGHT);
    }
    long account(String name, boolean blocked) {
        return jdbc.queryForObject("INSERT INTO users(username,password,is_blocked,enabled) VALUES (?, ?, ?, ?) RETURNING id", Long.class, name, passwords.encode("old-password"), blocked, !blocked);
    }
    Authentication actor(long id, String name, String... rights) {
        return UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(id,name,"",true,
                Arrays.stream(rights).map(SimpleGrantedAuthority::new).toList()), null,
                Arrays.stream(rights).map(SimpleGrantedAuthority::new).toList());
    }
    <T> T transaction(Supplier<T> action) { return tx.execute(status -> action.get()); }
    long submit(String name) {
        var request = new MockHttpServletRequest(); request.setRemoteAddr("192.0.2.10"); request.addHeader("User-Agent", "Fixture browser");
        transaction(() -> { service.submit(Map.of("username",name,"kind","password","full_name","Иван Иванов","contact","known@example.test","comment","Помогите"), request); return null; });
        return jdbc.queryForObject("SELECT id FROM password_reset_requests WHERE lower(requested_by_username) = lower(?) AND status IN ('PENDING','NEEDS_REVIEW')", Long.class, name);
    }
    Map<String,Object> decision(boolean reset, boolean unblock) {
        return Map.of("identity_verified",true,"note","Подтверждено руководителем по известному контакту","reset_password",reset,"unblock_account",unblock);
    }
    @Test void unknownAccountsAreDurableAndAnonymousRepeatsCannotReplaceEvidence() {
        long id = submit("typo");
        var request = new MockHttpServletRequest(); request.setRemoteAddr("192.0.2.11");
        transaction(() -> { service.submit(Map.of("username","TYPO","contact","attacker@example.test","comment","overwrite"),request); return null; });
        var row = jdbc.queryForMap("SELECT * FROM password_reset_requests WHERE id = ?",id);
        assertThat(row.get("status")).isEqualTo("NEEDS_REVIEW");
        assertThat(row.get("user_id")).isNull();
        assertThat(row.get("repeat_count")).isEqualTo(2);
        assertThat(row.get("requester_contact")).isEqualTo("known@example.test");
        assertThat(row.get("requested_note")).isEqualTo("Помогите");
        assertThatThrownBy(() -> transaction(() -> service.approve(id,decision(true,false),admin))).isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE username='typo'",Integer.class)).isZero();
    }
    @Test void resetDoesNotUnblockButExplicitUnblockDoesAndRevokesSessions() {
        long userId = account("blocked",true);
        long id = submit("blocked");
        var result = transaction(() -> service.approve(id,decision(true,false),delegate));
        String temporary = result.get("temporary_password").toString();
        assertThat(result.toString()).doesNotContain(temporary).contains("REDACTED");
        assertThat(temporary).hasSize(14);
        assertThat(passwords.matches(temporary,jdbc.queryForObject("SELECT password FROM users WHERE id=?",String.class,userId))).isTrue();
        assertThat(jdbc.queryForObject("SELECT is_blocked FROM users WHERE id=?",Boolean.class,userId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT enabled FROM users WHERE id=?",Boolean.class,userId)).isFalse();
        long next = submit("blocked");
        String hash = jdbc.queryForObject("SELECT password FROM users WHERE id=?",String.class,userId);
        transaction(() -> service.approve(next,decision(false,true),delegate));
        assertThat(jdbc.queryForObject("SELECT password FROM users WHERE id=?",String.class,userId)).isEqualTo(hash);
        assertThat(jdbc.queryForObject("SELECT is_blocked FROM users WHERE id=?",Boolean.class,userId)).isFalse();
        assertThat(jdbc.queryForObject("SELECT enabled FROM users WHERE id=?",Boolean.class,userId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT session_version FROM users WHERE id=?",Long.class,userId)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT note FROM access_recovery_events WHERE request_id=?",id).toString()).doesNotContain(temporary);
    }
    @Test void verificationAndPrivilegedAccountBoundaryAreEnforced() {
        long id = submit("admin");
        assertThatThrownBy(() -> transaction(() -> service.approve(id,decision(true,false),delegate))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> transaction(() -> service.approve(id,Map.of("reset_password",true),admin))).isInstanceOf(ResponseStatusException.class);
        Authentication denied = actor(2,"operator","ROLE_ADMIN","DENY_"+AccessRecoveryService.RIGHT);
        assertThat(AccessRecoveryService.canRecover(denied)).isFalse();
        assertThat(AccessRecoveryService.canUnblockClient(delegate)).isFalse();
        assertThatThrownBy(() -> service.list(actor(2,"operator","PAGE_CLIENTS"))).isInstanceOf(AccessDeniedException.class);
    }
    @Test void unknownCanBeLinkedOnlyAfterVerificationAndDecisionsCannotRepeat() {
        long target = account("real-login",false), id = submit("typo");
        transaction(() -> service.link(id,Map.of("identity_verified",true,"note","Уточнено у руководителя","username","real-login"),delegate));
        assertThat(jdbc.queryForObject("SELECT user_id FROM password_reset_requests WHERE id=?",Long.class,id)).isEqualTo(target);
        transaction(() -> service.approve(id,decision(true,false),delegate));
        assertThatThrownBy(() -> transaction(() -> service.approve(id,decision(true,false),delegate))).isInstanceOf(ResponseStatusException.class);
        assertThat(service.history(id,delegate)).extracting(row -> row.get("action")).contains("LINKED","PASSWORD_RESET");
    }
    @Test void seenIsPersonalAndOnlyResolutionRemovesPendingCounters() {
        jdbc.update("INSERT INTO client_unblock_requests(status,user_id) VALUES ('pending','123')");
        long id = submit("missing");
        var attention = new AttentionService(jdbc);
        Authentication other = actor(account("other",false),"other",AccessRecoveryService.RIGHT);
        attention.seen(delegate,"recovery",List.of(id));
        assertThat(((Map<?,?>)attention.summary(delegate).get("recovery")).get("pending")).isEqualTo(1L);
        assertThat(((Map<?,?>)attention.summary(delegate).get("recovery")).get("unseen")).isEqualTo(0L);
        assertThat(((Map<?,?>)attention.summary(other).get("recovery")).get("unseen")).isEqualTo(1L);
        assertThat(attention.summary(actor(2,"operator","ROLE_USER"))).containsKey("unblock").doesNotContainKey("recovery");
        transaction(() -> service.reject(id,Map.of("note","Уточните логин у руководителя"),delegate));
        assertThat(((Map<?,?>)attention.summary(other).get("recovery")).get("pending")).isEqualTo(0L);
    }
    @Test void rateLimitsAreSharedAcrossReplicasAndMetadataIsScrubbed() {
        properties.setRequestsPerLogin(2);
        assertThat(guard.allow("recovery","192.0.2.1","same")).isTrue();
        assertThat(new PublicAuthenticationGuard(jdbc,properties).allow("recovery","192.0.2.2","SAME")).isTrue();
        assertThat(guard.allow("recovery","192.0.2.3","same")).isFalse();
        long id = submit("missing");
        jdbc.update("UPDATE password_reset_requests SET last_requested_at = CURRENT_TIMESTAMP - INTERVAL '100 days' WHERE id=?",id);
        transaction(() -> { service.scrubExpiredMetadata(); return null; });
        assertThat(jdbc.queryForMap("SELECT requested_by_ip,requested_user_agent,correlation_id,status FROM password_reset_requests WHERE id=?",id))
                .containsEntry("requested_by_ip",null).containsEntry("requested_user_agent",null).containsEntry("correlation_id",null).containsEntry("status","NEEDS_REVIEW");
    }
    @Test void concurrentDecisionsIssueExactlyOnePassword() throws Exception {
        account("target",false); long id = submit("target");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> resolve = () -> {
                try { transaction(() -> service.approve(id,decision(true,false),delegate)); return true; }
                catch (ResponseStatusException conflict) { return false; }
            };
            var results = pool.invokeAll(List.of(resolve,resolve));
            assertThat(List.of(results.get(0).get(15,TimeUnit.SECONDS),results.get(1).get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM access_recovery_events WHERE request_id=? AND action='PASSWORD_RESET'",Integer.class,id)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void legacyMigrationKeepsHistoryAndMergesPendingDuplicates() {
        JdbcTemplate legacy = AccessRecoveryTestDatabase.base();
        legacy.update("INSERT INTO users(username,password) VALUES ('existing','fixture')");
        legacy.update("""
                INSERT INTO password_reset_requests(user_id,username_snapshot,requested_by_username,status)
                VALUES (1,'existing','existing','PENDING'), (1,'existing','EXISTING','PENDING'),
                       (1,'existing','existing','APPROVED'), (NULL,'missing','missing','PENDING')
                """);
        AccessRecoveryTestDatabase.migrate(legacy);
        assertThat(legacy.queryForObject("SELECT count(*) FROM password_reset_requests",Integer.class)).isEqualTo(4);
        assertThat(legacy.queryForObject("SELECT count(*) FROM password_reset_requests WHERE status='MERGED'",Integer.class)).isEqualTo(1);
        assertThat(legacy.queryForObject("SELECT repeat_count FROM password_reset_requests WHERE status='PENDING'",Integer.class)).isEqualTo(2);
        assertThat(legacy.queryForObject("SELECT status FROM password_reset_requests WHERE username_snapshot='missing'",String.class)).isEqualTo("NEEDS_REVIEW");
        assertThat(legacy.queryForObject("SELECT count(*) FROM password_reset_requests WHERE status='APPROVED'",Integer.class)).isEqualTo(1);
    }
    @Test void forgottenLoginsWithDifferentContactsAreNotMerged() {
        var request = new MockHttpServletRequest(); request.setRemoteAddr("192.0.2.20");
        for(String contact:List.of("one@example.test","two@example.test"))
            transaction(() -> { service.submit(Map.of("username","","kind","other","full_name","Иван","contact",contact),request); return null; });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM password_reset_requests WHERE status='NEEDS_REVIEW'",Integer.class)).isEqualTo(2);
    }
    @Test void directClientUnblockClosesPendingRequestsForThatClient() {
        jdbc.update("INSERT INTO client_unblock_requests(status,user_id) VALUES ('pending','123'),('pending','123'),('pending','456')");
        var repository = mock(com.example.panel.repository.ClientBlacklistRepository.class);
        var entry = new com.example.panel.entity.ClientBlacklist(); entry.setUserId("123"); entry.setBlacklisted(true);
        org.mockito.Mockito.when(repository.findById("123")).thenReturn(Optional.of(entry));
        var clients = new ClientBlacklistService(repository,mock(BlacklistHistoryService.class),mock(DialogNotificationService.class),jdbc,mock(UiEventStreamService.class));
        assertThat(transaction(() -> clients.unblockClient("123","admin",false)).ok()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM client_unblock_requests WHERE user_id='123' AND status='approved'",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM client_unblock_requests WHERE status='pending'",Integer.class)).isEqualTo(1);
    }
    @Test void clientRequestDecisionUsesPostgresTimestampsAndCannotRepeat() {
        jdbc.execute("CREATE TABLE client_blacklist(user_id TEXT PRIMARY KEY,is_blacklisted BOOLEAN,unblock_requested BOOLEAN,unblock_requested_at TIMESTAMPTZ)");
        jdbc.update("INSERT INTO client_blacklist VALUES ('123',TRUE,TRUE,CURRENT_TIMESTAMP)");
        long id = jdbc.queryForObject("INSERT INTO client_unblock_requests(status,user_id) VALUES ('pending','123') RETURNING id",Long.class);
        var clients = new UnblockRequestService(jdbc,mock(BlacklistHistoryService.class),mock(DialogNotificationService.class),mock(UiEventStreamService.class));
        assertThat(transaction(() -> clients.decideRequest(id,true,"Проверено","admin")).success()).isTrue();
        assertThat(transaction(() -> clients.decideRequest(id,true,"Повтор","admin")).success()).isFalse();
        assertThat(jdbc.queryForObject("SELECT is_blacklisted FROM client_blacklist WHERE user_id='123'",Boolean.class)).isFalse();
    }
}
