package com.example.panel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.NotificationService;
import com.example.panel.service.UiEventStreamService;
import com.example.panel.service.UiEventTicketScope;
import com.example.panel.support.PostgresqlIntegrationTestSupport;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest @AutoConfigureMockMvc
class NotificationPrincipalIdentityHttpPostgresTest extends PostgresqlIntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NotificationService service;
    @Autowired private MultibusinessFeatureFlags flags;
    @Autowired private MockMvc mvc;
    @MockBean private UiEventStreamService stream;
    private String reader,spaced;
    private long spacedId,plainNotice,businessA,businessB;
    private UiEventTicketScope own,foreign;

    @BeforeEach void prepare() {
        flags.setStrictReadScope(true);jdbc.update("DELETE FROM notifications");
        reader="notice-r173-"+UUID.randomUUID();spaced=" "+reader+" ";
        long readerId=account(reader);spacedId=account(spaced);
        businessA=business();businessB=business();
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES (?,?,'VIEWER'),(?,?,'VIEWER')",readerId,businessA,spacedId,businessB);
        own=owner(businessA);foreign=owner(businessB);
        assertThat(service.notifyScopedDialogUsers(own,Set.of(reader),"plain private notice","/dialogs")).containsExactly(reader);
        plainNotice=jdbc.queryForObject("SELECT id FROM notifications",Long.class);clearInvocations(stream);
    }
    @AfterEach void restore() {
        flags.setStrictReadScope(false);
        jdbc.update("DELETE FROM notifications WHERE user_identity IN (?,?)",reader,spaced);
        for (UiEventTicketScope scope : Arrays.asList(own,foreign)) {
            if (scope == null) continue;
            jdbc.update("DELETE FROM business_ticket_participants WHERE ticket_user_id=? AND ticket_id=? AND ticket_public_id=? AND business_id=?",
                scope.ticketUserId(),scope.ticketId(),scope.ticketPublicId(),scope.businessId());
            jdbc.update("DELETE FROM tickets WHERE user_id=? AND ticket_id=? AND ticket_public_id=?",
                scope.ticketUserId(),scope.ticketId(),scope.ticketPublicId());
        }
    }

    @ParameterizedTest @ValueSource(strings={"prefix","suffix","both","tab","newline"})
    void authenticatedWhitespacePrincipalCannotReadOrMarkAnotherAccountsNotifications(String variant) throws Exception {
        String identity=variant(variant);
        var before=jdbc.queryForList("SELECT * FROM notifications ORDER BY id");
        assertThat(service.findForUser(identity)).isEmpty();
        assertThat(service.findPageForUser(identity,null,1).items()).isEmpty();
        assertThat(service.summary(identity).unreadCount()).isZero();
        service.markAsRead(identity,plainNotice);assertThat(service.markAllAsRead(identity)).isZero();
        mvc.perform(auth(get("/api/notifications").param("limit","1"),identity)).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(auth(get("/api/notifications/unread_count"),identity)).andExpect(status().isOk()).andExpect(jsonPath("$.unread").value(0));
        mvc.perform(auth(post("/api/notifications/"+plainNotice+"/read"),identity).with(csrf())).andExpect(status().isOk());
        mvc.perform(auth(post("/api/notifications/read-all"),identity).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(0));
        assertThat(jdbc.queryForList("SELECT * FROM notifications ORDER BY id")).isEqualTo(before);verifyNoInteractions(stream);
    }

    @ParameterizedTest @ValueSource(strings={"prefix","suffix","both","tab","newline"})
    void deliveryCannotBorrowTheTrimmedRecipientsBusinessGrant(String variant) {
        var before=jdbc.queryForList("SELECT * FROM notifications ORDER BY id");
        assertThat(service.notifyScopedDialogUsers(own,Set.of(variant(variant)),"private","/dialogs")).isEmpty();
        assertThat(service.notifyDialogUsers(own.ticketId(),Set.of(variant(variant)),"private","/dialogs")).isEmpty();
        assertThat(jdbc.queryForList("SELECT * FROM notifications ORDER BY id")).isEqualTo(before);verifyNoInteractions(stream);
    }

    @Test void genuineSpacedRecipientCanReceiveReadAndMarkOnlyItsOwnBusinessNotice() throws Exception {
        assertThat(service.notifyScopedDialogUsers(foreign,Set.of(spaced.toUpperCase(Locale.ROOT),reader),"own private notice","/dialogs"))
            .containsExactly(spaced);
        verify(stream).publishNotificationsChanged(spaced,"notification_created");clearInvocations(stream);
        long id=jdbc.queryForObject("SELECT id FROM notifications WHERE user_identity=?",Long.class,spaced);
        assertThat(service.findForUser(spaced.toUpperCase(Locale.ROOT))).extracting(n->n.id()).containsExactly(id);
        mvc.perform(auth(get("/api/notifications"),spaced.toUpperCase(Locale.ROOT))).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(id));
        mvc.perform(auth(post("/api/notifications/"+plainNotice+"/read"),spaced).with(csrf())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT is_read FROM notifications WHERE id=?",Boolean.class,plainNotice)).isFalse();
        mvc.perform(auth(post("/api/notifications/read-all"),spaced).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(1));
        assertThat(jdbc.queryForObject("SELECT is_read FROM notifications WHERE id=?",Boolean.class,id)).isTrue();
        verify(stream).publishNotificationsChanged(spaced,"notifications_marked_read");
        assertThat(service.summary(reader.toUpperCase(Locale.ROOT)).unreadCount()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings={"revoked","disabled","blocked","archived","duplicate"})
    void currentSpacedRecipientAccessIsRecheckedForDeliveryReadAndMark(String mutation) {
        assertThat(service.notifyScopedDialogUsers(foreign,Set.of(spaced),"own","/dialogs")).containsExactly(spaced);
        switch(mutation) {
            case "revoked" -> jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=?",spacedId);
            case "disabled" -> jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",spacedId);
            case "blocked" -> jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",spacedId);
            case "archived" -> jdbc.update("UPDATE businesses SET status='archived' WHERE id=?",businessB);
            case "duplicate" -> account(spaced.toUpperCase(Locale.ROOT));
            default -> throw new IllegalArgumentException(mutation);
        }
        clearInvocations(stream);var before=jdbc.queryForList("SELECT * FROM notifications ORDER BY id");
        assertThat(service.findForUser(spaced)).isEmpty();assertThat(service.summary(spaced).unreadCount()).isZero();
        assertThat(service.markAllAsRead(spaced)).isZero();
        assertThat(service.notifyScopedDialogUsers(foreign,Set.of(spaced),"late","/dialogs")).isEmpty();
        assertThat(jdbc.queryForList("SELECT * FROM notifications ORDER BY id")).isEqualTo(before);verifyNoInteractions(stream);
    }

    @Test void canonicalRosterAndExclusionPreserveTheNumericMembersCurrentSpacedName() {
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) SELECT id,?,'VIEWER' FROM users WHERE username=?",businessB,reader);
        jdbc.update("INSERT INTO business_ticket_participants(ticket_user_id,ticket_id,ticket_public_id,business_id,member_user_id,member,actor_user_id,actor,revision) VALUES (?,?,?,?,?,'historical name',?,'actor',1)",
            foreign.ticketUserId(),foreign.ticketId(),foreign.ticketPublicId(),businessB,spacedId,spacedId);
        assertThat(service.findScopedDialogRecipients(foreign)).containsExactly(spaced);
        assertThat(service.findAllOperatorRecipients()).contains(reader,spaced);
        var before=jdbc.queryForList("SELECT * FROM notifications ORDER BY id");
        service.notifyScopedDialogParticipants(foreign,"excluded","/dialogs",spaced.toUpperCase(Locale.ROOT));
        assertThat(jdbc.queryForList("SELECT * FROM notifications ORDER BY id")).isEqualTo(before);verifyNoInteractions(stream);
        service.notifyScopedDialogParticipants(foreign,"own roster","/dialogs",reader.toUpperCase(Locale.ROOT));
        assertThat(jdbc.queryForList("SELECT user_identity FROM notifications WHERE ticket_public_id=?",String.class,foreign.ticketPublicId()))
            .containsExactly(spaced);
        verify(stream).publishNotificationsChanged(spaced,"notification_created");
    }

    @Test void genericRecipientSetsAndExclusionsKeepSimilarAccountsSeparate() {
        flags.setStrictReadScope(false);
        service.notifyUsersExcluding(Set.of(reader.toUpperCase(Locale.ROOT),spaced.toUpperCase(Locale.ROOT)),spaced,"system plain","/settings");
        service.notifyUser(spaced.toUpperCase(Locale.ROOT),"system spaced","/settings");
        assertThat(jdbc.queryForList("SELECT user_identity FROM notifications WHERE ticket_scope_kind IS NULL ORDER BY id",String.class))
            .containsExactly(reader,spaced);
        assertThat(service.findForUser(spaced)).singleElement().satisfies(n->assertThat(n.text()).isEqualTo("system spaced"));
        assertThat(service.summary(reader).unreadCount()).isEqualTo(2);
        verify(stream).publishNotificationsChanged(spaced,"notification_created");
    }

    private String variant(String variant) { return switch(variant) {
        case "prefix"->" "+reader;case "suffix"->reader+" ";case "tab"->"\t"+reader+"\t";case "newline"->"\n"+reader+"\r";default->spaced;}; }
    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request,String identity) {
        return request.with(user(identity).authorities(new SimpleGrantedAuthority("PAGE_DIALOGS"),new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
    private long account(String username) { return jdbc.queryForObject("INSERT INTO users(username,password) VALUES (?,'not-used') RETURNING id",Long.class,username); }
    private long business() { return jdbc.queryForObject("INSERT INTO businesses(code,display_name) VALUES (?,'Notice business') RETURNING id",Long.class,"r173-"+UUID.randomUUID()); }
    private UiEventTicketScope owner(long business) {
        long user=System.nanoTime();String raw=" notice/"+UUID.randomUUID()+" Ю ",pub=UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES (?,?,?,?)",user,raw,pub,business);
        return new UiEventTicketScope(user,raw,pub,"BUSINESS",business);
    }
}
