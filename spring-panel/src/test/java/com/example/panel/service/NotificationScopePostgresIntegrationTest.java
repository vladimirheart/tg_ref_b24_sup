package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.entity.Notification;
import com.example.panel.repository.NotificationRepository;
import com.example.panel.support.PostgresqlIntegrationTestSupport;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class NotificationScopePostgresIntegrationTest extends PostgresqlIntegrationTestSupport {
    private static final AtomicLong IDS = new AtomicLong(910000);
    private static final String READER = "notification-scope-reader";
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NotificationService service;
    @Autowired private NotificationRepository repository;
    @Autowired private MultibusinessFeatureFlags flags;
    @Autowired private MockMvc mvc;
    @MockBean private UiEventStreamService stream;
    private long sushi;
    private long bliny;

    @BeforeEach
    void prepare() {
        flags.setStrictReadScope(false);
        jdbc.update("DELETE FROM notifications");
        sushi = jdbc.queryForObject("SELECT id FROM businesses WHERE code = 'sushi'", Long.class);
        bliny = jdbc.queryForObject("SELECT id FROM businesses WHERE code = 'bliny'", Long.class);
        long reader = jdbc.queryForObject("""
            INSERT INTO users(username, password, enabled) VALUES (?, 'not-used', TRUE)
            ON CONFLICT (username) DO UPDATE SET enabled = TRUE, is_blocked = FALSE RETURNING id
            """, Long.class, READER);
        jdbc.update("""
            INSERT INTO business_memberships(user_id, business_id, role_code) VALUES (?, ?, 'VIEWER')
            ON CONFLICT (user_id, business_id) DO UPDATE SET is_active = TRUE, role_code = 'VIEWER'
            """, reader, sushi);
        clearInvocations(stream);
    }

    @AfterEach
    void restoreFlags() { flags.setStrictReadScope(false); }

    @Test
    void writerFreezesCanonicalTuplePublicIdAndBusinessInNotification() {
        Owner owner = owner(sushi, false);

        assertThat(service.notifyDialogUsers(owner.ticketId(), Set.of(READER), "business notice",
            service.buildDialogUrl(owner.ticketId()))).containsExactly(READER);

        Notification saved = repository.findAll().get(0);
        assertThat(saved.getTicketUserId()).isEqualTo(owner.userId());
        assertThat(saved.getTicketId()).isEqualTo(owner.ticketId());
        assertThat(saved.getTicketPublicId()).isEqualTo(owner.publicId());
        assertThat(saved.getTicketScopeKind()).isEqualTo("BUSINESS");
        assertThat(saved.getTicketBusinessId()).isEqualTo(sushi);
        assertThat(service.findForUser(READER)).extracting(n -> n.id()).containsExactly(saved.getId());
    }

    @Test
    void unresolvedWriterAndGenericJpaWriterNeverGuessBusinessFromDialogUrl() {
        Owner unresolved = owner(null, false, false);
        assertThat(service.notifyDialogUsers(unresolved.ticketId(), Set.of(READER), "unresolved",
            service.buildDialogUrl(unresolved.ticketId()))).containsExactly(READER);
        Notification typed = repository.findAll().get(0);
        assertThat(typed.getTicketScopeKind()).isEqualTo("UNRESOLVED");
        assertThat(typed.getTicketBusinessId()).isNull();
        assertThat(typed.getTicketPublicId()).isNull();
        assertThat(typed.getTicketUserId()).isEqualTo(unresolved.userId());
        assertThat(typed.getTicketId()).isEqualTo(unresolved.ticketId());

        Owner business = owner(sushi, false);
        service.notifyUser(READER, "generic notice", service.buildDialogUrl(business.ticketId()));
        Notification generic = repository.findAll().stream()
            .filter(n -> !n.getId().equals(typed.getId())).findFirst().orElseThrow();
        assertThat(generic.getTicketUserId()).isNull();
        assertThat(generic.getTicketId()).isNull();
        assertThat(generic.getTicketPublicId()).isNull();
        assertThat(generic.getTicketScopeKind()).isNull();
        assertThat(generic.getTicketBusinessId()).isNull();
        assertThat(service.findForUser(READER)).hasSize(2);
        flags.setStrictReadScope(true);
        assertThat(service.findForUser(READER)).isEmpty();
        assertThat(service.markAllAsRead(READER)).isZero();
    }

    @Test
    void httpBellApiUsesTheSameScopeForPagesCountsAndAcknowledgments() throws Exception {
        Owner business = owner(sushi, false);
        Owner general = owner(null, true);
        long visible = typed(business, "BUSINESS", sushi, OffsetDateTime.now());
        long hidden = typed(general, "GENERAL", null, OffsetDateTime.now());
        flags.setStrictReadScope(true);
        var principal = new TestingAuthenticationToken(READER, "n/a");

        mvc.perform(get("/api/notifications").principal(principal))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(visible))
            .andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(get("/api/notifications/unread_count").principal(principal))
            .andExpect(status().isOk()).andExpect(jsonPath("$.unread").value(1));
        mvc.perform(post("/api/notifications/" + hidden + "/read").principal(principal))
            .andExpect(status().isOk());
        mvc.perform(post("/api/notifications/read-all").principal(principal))
            .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(1));
        mvc.perform(get("/api/notifications/unread_count").principal(principal))
            .andExpect(status().isOk()).andExpect(jsonPath("$.unread").value(0));
        assertThat(jdbc.queryForObject("SELECT is_read FROM notifications WHERE id = ?", Boolean.class, hidden)).isFalse();
    }

    @Test
    void changedToGeneralDisappearsFromReadsCountsAndConditionalReadUpdates() {
        Owner owner = owner(sushi, false);
        long id = typed(owner, "BUSINESS", sushi, OffsetDateTime.now());
        assertThat(service.summary(READER).unreadCount()).isEqualTo(1);
        jdbc.update("UPDATE tickets SET business_id = NULL, is_general = TRUE, general_selection_source = 'USER_SELECTION' "
            + "WHERE user_id = ? AND ticket_id = ?", owner.userId(), owner.ticketId());

        assertThat(service.findForUser(READER)).isEmpty();
        assertThat(service.findPageForUser(READER, null, 2).items()).isEmpty();
        assertThat(service.summary(READER).unreadCount()).isZero();
        service.markAsRead(READER, id);
        assertThat(service.markAllAsRead(READER)).isZero();
        assertThat(jdbc.queryForObject("SELECT is_read FROM notifications WHERE id = ?", Boolean.class, id)).isFalse();
        verifyNoInteractions(stream);
    }

    @Test
    void scopeFilterRunsBeforePaginationAndReadAllNeverTouchesHiddenRows() {
        Owner visible = owner(sushi, false);
        Owner general = owner(null, true);
        List<Long> expected = new ArrayList<>();
        OffsetDateTime old = OffsetDateTime.parse("2026-10-15T10:00:00Z");
        for (int i = 0; i < 4; i++) expected.add(0, typed(visible, "BUSINESS", sushi, old));
        for (int i = 0; i < 25; i++) typed(general, "GENERAL", null, old.plusDays(1));

        var first = service.findPageForUser(READER, null, 2);
        assertThat(first.items()).extracting(n -> n.id()).containsExactlyElementsOf(expected.subList(0, 2));
        assertThat(first.hasMore()).isTrue();
        var second = service.findPageForUser(READER, first.nextCursor(), 2);
        assertThat(second.items()).extracting(n -> n.id()).containsExactlyElementsOf(expected.subList(2, 4));
        assertThat(second.hasMore()).isFalse();
        assertThat(service.summary(READER).unreadCount()).isEqualTo(4);
        assertThat(service.markAllAsRead(READER)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE is_read = FALSE", Long.class)).isEqualTo(25);
    }

    @Test
    void legacyAndUnresolvedCompatibilityEndsWhenStrictReadIsEnabled() {
        Owner business = owner(sushi, false);
        Owner unresolved = owner(null, false);
        typed(business, "BUSINESS", sushi, OffsetDateTime.now());
        typed(unresolved, "UNRESOLVED", null, OffsetDateTime.now());
        long legacyId = jdbc.queryForObject("INSERT INTO notifications(user_identity, text, is_read, created_at) "
            + "VALUES (?, 'old unclassified', FALSE, CURRENT_TIMESTAMP) RETURNING id", Long.class, READER);
        assertThat(service.summary(READER).unreadCount()).isEqualTo(3);

        flags.setStrictReadScope(true);

        assertThat(service.findForUser(READER)).hasSize(1);
        assertThat(service.summary(READER).unreadCount()).isEqualTo(1);
        service.markAsRead(READER, legacyId);
        assertThat(service.markAllAsRead(READER)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT is_read FROM notifications WHERE id = ?", Boolean.class, legacyId)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE is_read = FALSE", Long.class)).isEqualTo(2);
    }

    @Test
    void canonicalOwnerNeverResolvesFromRawIdSiblingAndPublicIdRejectsRecreatedOwner() {
        Owner original = owner(sushi, false);
        long notification = typed(original, "BUSINESS", sushi, OffsetDateTime.now());
        long sibling = IDS.incrementAndGet();
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, is_general, general_selection_source) "
            + "VALUES (?, ?, TRUE, 'USER_SELECTION')", sibling, original.ticketId());
        assertThat(service.findForUser(READER)).extracting(n -> n.id()).containsExactly(notification);
        jdbc.update("DELETE FROM tickets WHERE user_id = ? AND ticket_id = ?", original.userId(), original.ticketId());
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, business_id, ticket_public_id) VALUES (?, ?, ?, ?)",
            original.userId(), original.ticketId(), sushi, String.format("%032x", IDS.incrementAndGet()));

        assertThat(service.findForUser(READER)).isEmpty();
        assertThat(service.summary(READER).unreadCount()).isZero();
        service.markAsRead(READER, notification);
        assertThat(jdbc.queryForObject("SELECT is_read FROM notifications WHERE id = ?", Boolean.class, notification)).isFalse();
    }

    @Test
    void reassignedBusinessAndWrongRecipientCannotReadOrAcknowledgeSnapshot() {
        Owner owner = owner(sushi, false);
        long id = typed(owner, "BUSINESS", sushi, OffsetDateTime.now());
        service.markAsRead("another-operator", id);
        assertThat(service.findForUser("another-operator")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT is_read FROM notifications WHERE id = ?", Boolean.class, id)).isFalse();
        jdbc.update("UPDATE tickets SET business_id = ? WHERE user_id = ? AND ticket_id = ?", bliny, owner.userId(), owner.ticketId());
        assertThat(service.summary(READER).unreadCount()).isZero();
        assertThat(service.markAllAsRead(READER)).isZero();
        jdbc.update("UPDATE tickets SET business_id = ? WHERE user_id = ? AND ticket_id = ?", sushi, owner.userId(), owner.ticketId());
        service.markAsRead(READER, id);
        assertThat(jdbc.queryForObject("SELECT is_read FROM notifications WHERE id = ?", Boolean.class, id)).isTrue();
    }

    private Owner owner(Long business, boolean general) {
        return owner(business, general, true);
    }

    private Owner owner(Long business, boolean general, boolean withPublicId) {
        long id = IDS.incrementAndGet();
        Owner result = new Owner(id, " notice/" + id + " ", withPublicId ? String.format("%032x", id) : null);
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, ticket_public_id, business_id, is_general, general_selection_source) "
            + "VALUES (?, ?, ?, ?, ?, ?)", id, result.ticketId(), result.publicId(), business, general, general ? "USER_SELECTION" : null);
        return result;
    }

    private long typed(Owner owner, String kind, Long business, OffsetDateTime created) {
        return jdbc.queryForObject("""
            INSERT INTO notifications(user_identity, text, url, is_read, created_at,
                ticket_user_id, ticket_id, ticket_public_id, ticket_scope_kind, ticket_business_id)
            VALUES (?, 'scoped notice', '/dialogs', FALSE, ?, ?, ?, ?, ?, ?) RETURNING id
            """, Long.class, READER, created, owner.userId(), owner.ticketId(), owner.publicId(), kind, business);
    }

    private record Owner(long userId, String ticketId, String publicId) {}
}
