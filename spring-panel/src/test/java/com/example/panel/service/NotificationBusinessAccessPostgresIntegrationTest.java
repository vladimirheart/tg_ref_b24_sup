package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.repository.NotificationRepository;
import com.example.panel.support.PostgresqlIntegrationTestSupport;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class NotificationBusinessAccessPostgresIntegrationTest extends PostgresqlIntegrationTestSupport {
    private static final AtomicLong IDS = new AtomicLong(2200000);
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NotificationService service;
    @Autowired private NotificationRepository repository;
    @Autowired private MultibusinessFeatureFlags flags;
    @Autowired private MockMvc mvc;
    @MockBean private UiEventStreamService stream;
    private long viewerId;
    private String viewer;
    private String administrator;
    private long businessA;
    private long businessB;

    @BeforeEach
    void prepare() {
        flags.setStrictReadScope(false);
        jdbc.update("DELETE FROM notifications");
        viewer = "notice-r122-" + IDS.incrementAndGet();
        administrator = "notice-r122-admin-" + IDS.incrementAndGet();
        viewerId = account(viewer);
        long administratorId = account(administrator);
        jdbc.update("INSERT INTO user_authorities(user_id, authority) VALUES (?, 'ROLE_ADMIN')", administratorId);
        businessA = business();
        businessB = business();
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES (?,?,'VIEWER')",
            viewerId, businessA);
        clearInvocations(stream);
    }

    @AfterEach
    void restoreFlags() { flags.setStrictReadScope(false); }

    @Test
    void deliveryAndEveryReadSurfaceRequireTheRecipientsBusinessRead() {
        Owner allowed = owner(businessA, false);
        Owner foreign = owner(businessB, false);
        assertThat(service.notifyDialogUsers(allowed.ticketId(), Set.of(" " + viewer.toUpperCase(Locale.ROOT) + " ", administrator),
            "allowed notice", "/dialogs")).containsExactly(viewer);
        long visible = repository.findAll().get(0).getId();
        clearInvocations(stream);
        assertThat(service.notifyDialogUsers(foreign.ticketId(), Set.of(viewer, administrator, "missing-account"),
            "foreign notice", "/dialogs")).isEmpty();
        verifyNoInteractions(stream);
        long hidden = typed(foreign, "BUSINESS", businessB, OffsetDateTime.now());
        assertThat(service.findForUser(viewer)).extracting(n -> n.id()).containsExactly(visible);
        assertThat(service.findPageForUser(viewer, null, 20).items()).extracting(n -> n.id()).containsExactly(visible);
        assertThat(service.summary(viewer).unreadCount()).isEqualTo(1);
        service.markAsRead(viewer, hidden);
        assertThat(service.markAllAsRead(viewer)).isEqualTo(1);
        assertUnread(hidden);
    }

    @ParameterizedTest
    @ValueSource(strings = {"inactive", "deleted"})
    void membershipRevocationImmediatelyAffectsAnExistingHttpSession(String mutation) throws Exception {
        Owner owner = owner(businessA, false);
        service.notifyDialogUsers(owner.ticketId(), Set.of(viewer), "notice", "/dialogs");
        long id = repository.findAll().get(0).getId();
        var session = new TestingAuthenticationToken(viewer.toUpperCase(Locale.ROOT), "not-used", "ROLE_ADMIN");
        if ("deleted".equals(mutation)) {
            jdbc.update("DELETE FROM business_memberships WHERE user_id=?", viewerId);
        } else {
            jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=?", viewerId);
        }
        clearInvocations(stream);
        mvc.perform(get("/api/notifications").principal(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(get("/api/notifications/unread_count").principal(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.unread").value(0));
        mvc.perform(post("/api/notifications/" + id + "/read").principal(session)).andExpect(status().isOk());
        mvc.perform(post("/api/notifications/read-all").principal(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.updated").value(0));
        assertUnread(id);
        assertThat(service.notifyDialogUsers(owner.ticketId(), Set.of(viewer), "late notice", "/dialogs")).isEmpty();
        verifyNoInteractions(stream);
        jdbc.update("""
            INSERT INTO business_memberships(user_id,business_id,role_code) VALUES (?,?,'VIEWER')
            ON CONFLICT (user_id,business_id) DO UPDATE SET is_active=TRUE
            """, viewerId, businessA);
        assertThat(service.findForUser(viewer)).extracting(n -> n.id()).containsExactly(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "blocked", "archived", "missing-account"})
    void accountAndBusinessLifecycleAreRecheckedForDeliveryReadsAndUpdates(String mutation) {
        Owner owner = owner(businessA, false);
        service.notifyDialogUsers(owner.ticketId(), Set.of(viewer), "notice", "/dialogs");
        long id = repository.findAll().get(0).getId();
        switch (mutation) {
            case "disabled" -> jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?", viewerId);
            case "blocked" -> jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?", viewerId);
            case "archived" -> jdbc.update("UPDATE businesses SET status='archived' WHERE id=?", businessA);
            case "missing-account" -> jdbc.update("DELETE FROM users WHERE id=?", viewerId);
            default -> throw new IllegalArgumentException(mutation);
        }
        clearInvocations(stream);
        assertThat(service.findForUser(viewer)).isEmpty();
        assertThat(service.findPageForUser(viewer, null, 2).items()).isEmpty();
        assertThat(service.summary(viewer).unreadCount()).isZero();
        service.markAsRead(viewer, id);
        assertThat(service.markAllAsRead(viewer)).isZero();
        assertThat(service.notifyDialogUsers(owner.ticketId(), Set.of(viewer), "late", "/dialogs")).isEmpty();
        assertUnread(id);
        verifyNoInteractions(stream);
    }

    @ParameterizedTest
    @ValueSource(strings = {"VIEWER", "OPERATOR", "MANAGER", "ADMIN"})
    void allSupportedBusinessRolesProvideRead(String role) {
        jdbc.update("UPDATE business_memberships SET role_code=? WHERE user_id=?", role, viewerId);
        Owner owner = owner(businessA, false);
        assertThat(service.notifyDialogUsers(owner.ticketId(), Set.of(viewer), "notice", "/dialogs"))
            .containsExactly(viewer);
        assertThat(service.summary(viewer).unreadCount()).isEqualTo(1);
    }

    @Test
    void allBusinessesIncludesFutureBusinessesButCannotOpenGeneralOrStrictLegacy() {
        jdbc.update("DELETE FROM business_memberships WHERE user_id=?", viewerId);
        jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES (?,'ALL_BUSINESSES','VIEWER')", viewerId);
        long future = business();
        for (long business : new long[]{businessA, businessB, future}) {
            Owner owner = owner(business, false);
            assertThat(service.notifyDialogUsers(owner.ticketId(), Set.of(viewer), "notice", "/dialogs"))
                .containsExactly(viewer);
        }
        Owner general = owner(null, true);
        jdbc.update("INSERT INTO general_ticket_read_grants(user_id) VALUES (?)", viewerId);
        assertThat(service.notifyDialogUsers(general.ticketId(), Set.of(viewer), "general", "/dialogs")).isEmpty();
        long hiddenGeneral = typed(general, "GENERAL", null, OffsetDateTime.now());
        typed(owner(null, false), "UNRESOLVED", null, OffsetDateTime.now());
        service.notifyUser(viewer, "system", "/settings");
        flags.setStrictReadScope(true);
        assertThat(service.findForUser(viewer)).hasSize(3);
        assertThat(service.summary(viewer).unreadCount()).isEqualTo(3);
        jdbc.update("UPDATE business_access_grants SET is_active=FALSE WHERE user_id=?", viewerId);
        clearInvocations(stream);
        assertThat(service.findForUser(viewer)).isEmpty();
        assertThat(service.markAllAsRead(viewer)).isZero();
        assertThat(service.notifyDialogUsers(owner(future, false).ticketId(), Set.of(viewer), "late", "/dialogs")).isEmpty();
        assertUnread(hiddenGeneral);
        verifyNoInteractions(stream);
    }

    @Test
    void accountCaseCollisionCannotSelectAnArbitraryGrantHolder() {
        Owner owner = owner(businessA, false);
        service.notifyDialogUsers(owner.ticketId(), Set.of(viewer), "notice", "/dialogs");
        long id = repository.findAll().get(0).getId();
        long duplicate = account(viewer.toUpperCase(Locale.ROOT));
        jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?", duplicate);
        clearInvocations(stream);
        assertThat(service.findForUser(viewer)).isEmpty();
        assertThat(service.summary(viewer).unreadCount()).isZero();
        service.markAsRead(viewer, id);
        assertThat(service.markAllAsRead(viewer)).isZero();
        assertThat(service.notifyDialogUsers(owner.ticketId(), Set.of(viewer), "late", "/dialogs")).isEmpty();
        assertUnread(id);
        verifyNoInteractions(stream);
    }

    @Test
    void membershipRevokedAfterTicketProofCannotInsertOrEmitARefresh() {
        Owner owner = owner(businessA, false);
        var guard = spy(new LegacyTicketIdJdbcGuard(jdbc));
        doAnswer(call -> {
            assertThat((boolean) call.callRealMethod()).isTrue();
            jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=?", viewerId);
            return true;
        }).when(guard).hasUniqueNonGeneralTicket(owner.ticketId());
        var guarded = new NotificationService(repository, jdbc, jdbc, stream, guard);
        assertThat(guarded.notifyDialogUsers(owner.ticketId(), Set.of(viewer), "private", "/dialogs")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Long.class)).isZero();
        verifyNoInteractions(stream);
    }

    @Test
    void authorizationPrecedesCursorPaginationCountsAndConditionalReadAll() {
        Owner allowed = owner(businessA, false);
        Owner denied = owner(businessB, false);
        List<Long> expected = new ArrayList<>();
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-10-15T10:00:00Z");
        for (int i=0; i<4; i++) expected.add(0, typed(allowed, "BUSINESS", businessA, timestamp));
        for (int i=0; i<25; i++) typed(denied, "BUSINESS", businessB, timestamp.plusDays(1));
        var first = service.findPageForUser(viewer, null, 2);
        assertThat(first.items()).extracting(n -> n.id()).containsExactlyElementsOf(expected.subList(0,2));
        assertThat(first.hasMore()).isTrue();
        var second = service.findPageForUser(viewer, first.nextCursor(), 2);
        assertThat(second.items()).extracting(n -> n.id()).containsExactlyElementsOf(expected.subList(2,4));
        assertThat(second.hasMore()).isFalse();
        assertThat(service.summary(viewer).unreadCount()).isEqualTo(4);
        assertThat(service.markAllAsRead(viewer)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE is_read=FALSE", Long.class)).isEqualTo(25);
    }

    @Test
    void eitherIndependentGrantCanProvideReadUntilBothAreRevoked() {
        Owner owner = owner(businessA, false);
        service.notifyDialogUsers(owner.ticketId(), Set.of(viewer), "notice", "/dialogs");
        jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES (?,'ALL_BUSINESSES','OPERATOR')", viewerId);
        jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=?", viewerId);
        assertThat(service.summary(viewer).unreadCount()).isEqualTo(1);
        jdbc.update("UPDATE business_memberships SET is_active=TRUE WHERE user_id=?", viewerId);
        jdbc.update("UPDATE business_access_grants SET is_active=FALSE WHERE user_id=?", viewerId);
        assertThat(service.summary(viewer).unreadCount()).isEqualTo(1);
        jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=?", viewerId);
        assertThat(service.summary(viewer).unreadCount()).isZero();
    }

    private long account(String name) {
        return jdbc.queryForObject("INSERT INTO users(username,password) VALUES (?,'not-used') RETURNING id", Long.class, name);
    }

    private long business() {
        return jdbc.queryForObject("INSERT INTO businesses(code,display_name) VALUES (?,'Notice business') RETURNING id",
            Long.class, "notice-r122-" + IDS.incrementAndGet());
    }

    private Owner owner(Long business, boolean general) {
        long id = IDS.incrementAndGet();
        Owner owner = new Owner(id, " notice/" + id + " + Ю ", String.format("%032x", id));
        jdbc.update("""
            INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,is_general,general_selection_source)
            VALUES (?,?,?,?,?,?)
            """, id, owner.ticketId(), owner.publicId(), business, general, general ? "USER_SELECTION" : null);
        return owner;
    }

    private long typed(Owner owner, String kind, Long business, OffsetDateTime timestamp) {
        return jdbc.queryForObject("""
            INSERT INTO notifications(user_identity,text,url,is_read,created_at,
                ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id)
            VALUES (?,'scoped notice','/dialogs',FALSE,?,?,?,?,?,?) RETURNING id
            """, Long.class, viewer, timestamp, owner.userId(), owner.ticketId(), owner.publicId(), kind, business);
    }

    private void assertUnread(long id) {
        assertThat(jdbc.queryForObject("SELECT is_read FROM notifications WHERE id=?", Boolean.class, id)).isFalse();
    }

    private record Owner(long userId, String ticketId, String publicId) { }
}
