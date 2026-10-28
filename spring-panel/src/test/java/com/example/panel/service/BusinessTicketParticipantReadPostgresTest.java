package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.example.panel.security.PanelUserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.*;

class BusinessTicketParticipantReadPostgresTest {
    private static final String RAW=" R169/Ю?%#+ ", PUBLIC="a".repeat(32);
    private JdbcTemplate jdbc;
    private BusinessTicketParticipantReadService read;
    private BusinessTicketParticipantService write;
    private final UiEventTicketScope owner=new UiEventTicketScope(42L,RAW,PUBLIC,"BUSINESS",11L);
    // Login input resolves to the exact stored name; statement-time SQL still checks ambiguity.
    private Authentication auth(String name) {
        String stored="VIEWER".equals(name)?"viewer":name;
        long id=switch(stored){case "operator"->1;case "viewer"->2;case "all-reader"->3;case "foreign"->4;case "none"->5;default->2;};
        var principal=new PanelUserPrincipal(id,stored,"unused",true,AuthorityUtils.createAuthorityList("PAGE_DIALOGS"));
        return UsernamePasswordAuthenticationToken.authenticated(principal,"unused",principal.getAuthorities());
    }
    @BeforeEach void fixture() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("business_participant_read");
        jdbc.execute("""
            CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE);
            CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT DEFAULT 'active');
            CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,
                channel_id BIGINT,status TEXT DEFAULT 'open',PRIMARY KEY(user_id,ticket_id));
            CREATE TABLE ticket_participants(ticket_id TEXT,username TEXT);
            CREATE TABLE ui_event_outbox(id BIGINT PRIMARY KEY,event_type TEXT,ticket_id TEXT,channel_id BIGINT,created_at TIMESTAMPTZ,
                ticket_user_id BIGINT,ticket_public_id TEXT,ticket_scope_kind TEXT,ticket_business_id BIGINT);
            INSERT INTO users(id,username) VALUES(1,'operator'),(2,'viewer'),(3,'all-reader'),(4,'foreign'),(5,'none');
            INSERT INTO businesses(id) VALUES(11),(22);
            INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(1,11,'OPERATOR'),(2,11,'VIEWER'),(4,22,'ADMIN');
            INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(3,'ALL_BUSINESSES','VIEWER');
            """);
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V79__business_ticket_participants.sql")));
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,channel_id) VALUES(42,?,?,11,9),(88,?,?,22,19)",RAW,PUBLIC,RAW.trim(),"b".repeat(32));
        jdbc.update("INSERT INTO ticket_participants VALUES(?,'Legacy secret')",RAW);
        var flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setUiSelector(true);
        write=new BusinessTicketParticipantService(jdbc,flags,new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc)));
        read=new BusinessTicketParticipantReadService(jdbc);
    }
    private void add() { assertThat(write.change(owner,org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(new com.example.panel.security.PanelUserPrincipal(1,"operator","unused",true,List.of()),null,List.of()),0,2L,true)).isTrue(); }
    private void event(long revision,String action) {
        jdbc.update("INSERT INTO business_ticket_participant_events(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,member_user_id,member,actor_user_id,actor,action) VALUES(42,?, ?,11,?,2,'viewer',1,'operator',?)",RAW,PUBLIC,revision,action);
    }
    @Test void persistedRosterAndImmutableHistoryAreVisibleToReadersWithoutOperate() {
        var old=jdbc.queryForList("SELECT * FROM ticket_participants");
        assertThat(read.find(PUBLIC,auth("viewer")).orElseThrow().members()).isEmpty();
        add();
        for(String identity:List.of("viewer","VIEWER","all-reader")) {
            var roster=read.find(PUBLIC,auth(identity)).orElseThrow();
            assertThat(roster.owner()).isEqualTo(new BusinessTicketParticipantReadService.Owner("42",RAW,PUBLIC,"11"));
            assertThat(roster.revision()).isEqualTo(1);
            assertThat(roster.members()).singleElement().satisfies(member->{
                assertThat(member.id()).isEqualTo(2);assertThat(member.snapshot()).isEqualTo("viewer");
                assertThat(member.username()).isEqualTo("viewer");assertThat(member.eligible()).isTrue();
            });
        }
        assertThat(write.change(owner,org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(new com.example.panel.security.PanelUserPrincipal(1,"operator","unused",true,List.of()),null,List.of()),1,2L,false)).isTrue();
        assertThat(read.find(PUBLIC,auth("viewer")).orElseThrow().members()).isEmpty();
        assertThat(read.history(PUBLIC,auth("viewer"),null,50).orElseThrow().events())
            .extracting(BusinessTicketParticipantReadService.Event::action).containsExactly("remove","add");
        assertThat(jdbc.queryForList("SELECT * FROM ticket_participants")).isEqualTo(old);
    }
    @ParameterizedTest @ValueSource(strings={"collision","closed","resolved","no-legacy-table"})
    void canonicalRosterReadsDoNotNeedRawUniquenessOpenStateOrLegacyTable(String change) {
        add();
        switch(change) {
            case "collision" -> jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(99,?,?,22)",RAW,"c".repeat(32));
            case "closed","resolved" -> jdbc.update("UPDATE tickets SET status=? WHERE user_id=42",change);
            case "no-legacy-table" -> jdbc.execute("DROP TABLE ticket_participants");
        }
        assertThat(read.find(PUBLIC,auth("viewer")).orElseThrow().members()).hasSize(1);
        assertThat(read.history(PUBLIC,auth("viewer"),null,50).orElseThrow().events()).hasSize(1);
        assertThat(read.find("b".repeat(32),auth("all-reader")).orElseThrow().members()).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"membership","all-grant","disabled","blocked","ambiguous","archived","role","general-grant","foreign","space"})
    void readersNeedCurrentUniqueEnabledUnblockedBusinessRead(String denied) {
        add();String identity="viewer";
        switch(denied) {
            case "membership" -> jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=2");
            case "all-grant" -> {identity="all-reader";jdbc.update("UPDATE business_access_grants SET is_active=FALSE");}
            case "disabled" -> jdbc.update("UPDATE users SET enabled=FALSE WHERE id=2");
            case "blocked" -> jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=2");
            case "ambiguous" -> jdbc.update("INSERT INTO users VALUES(6,'VIEWER',TRUE,FALSE)");
            case "archived" -> jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");
            case "role" -> jdbc.update("UPDATE business_memberships SET role_code='UNKNOWN' WHERE user_id=2");
            case "general-grant" -> {identity="none";jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(5,'GENERAL_TICKETS','ADMIN')");}
            case "foreign" -> identity="foreign";
            case "space" -> identity=" viewer ";
        }
        assertThat(read.find(PUBLIC,auth(identity))).isEmpty();
        assertThat(read.history(PUBLIC,auth(identity),null,50)).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"business","public","raw","user","general","unresolved","recreate","public-collision"})
    void rosterAndEventsCannotFollowAnotherOwnerOrGeneration(String change) {
        add();String publicId=PUBLIC,identity="all-reader";
        switch(change) {
            case "business" -> jdbc.update("UPDATE tickets SET business_id=22 WHERE user_id=42");
            case "public" -> {publicId="c".repeat(32);jdbc.update("UPDATE tickets SET ticket_public_id=? WHERE user_id=42",publicId);}
            case "raw" -> jdbc.update("UPDATE tickets SET ticket_id='new' WHERE user_id=42");
            case "user" -> jdbc.update("UPDATE tickets SET user_id=43 WHERE user_id=42");
            case "general" -> jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE WHERE user_id=42");
            case "unresolved" -> jdbc.update("UPDATE tickets SET business_id=NULL WHERE user_id=42");
            case "recreate" -> {jdbc.update("DELETE FROM tickets WHERE user_id=42");publicId="c".repeat(32);jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(42,?,?,11)",RAW,publicId);}
            case "public-collision" -> jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(99,'duplicate',?,22)",PUBLIC);
        }
        if(List.of("general","unresolved","public-collision").contains(change)) {
            assertThat(read.find(publicId,auth(identity))).isEmpty();
            assertThat(read.history(publicId,auth(identity),null,50)).isEmpty();
        } else {
            assertThat(read.find(publicId,auth(identity)).orElseThrow().members()).isEmpty();
            assertThat(read.history(publicId,auth(identity),null,50).orElseThrow().events()).isEmpty();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_ticket_participant_events",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_ticket_participants",Long.class)).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"revoked","disabled","blocked","deleted-reused","renamed","duplicate-name","viewer"})
    void memberIdentityRemainsNumericAndParticipationDoesNotRestoreRights(String change) {
        add();
        switch(change) {
            case "revoked" -> jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=2");
            case "disabled" -> jdbc.update("UPDATE users SET enabled=FALSE WHERE id=2");
            case "blocked" -> jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=2");
            case "deleted-reused" -> {jdbc.update("DELETE FROM users WHERE id=2");jdbc.update("INSERT INTO users VALUES(6,'viewer',TRUE,FALSE)");}
            case "renamed" -> jdbc.update("UPDATE users SET username='<img src=x>' WHERE id=2");
            case "duplicate-name" -> jdbc.update("INSERT INTO users VALUES(6,'VIEWER',TRUE,FALSE)");
        }
        var before=jdbc.queryForList("SELECT * FROM business_memberships ORDER BY user_id");
        var member=read.find(PUBLIC,auth("operator")).orElseThrow().members().get(0);
        assertThat(member.id()).isEqualTo(2);assertThat(member.snapshot()).isEqualTo("viewer");
        assertThat(member.eligible()).isEqualTo(change.equals("renamed")||change.equals("viewer"));
        if(change.equals("deleted-reused"))assertThat(member.username()).isNull();
        if(change.equals("renamed"))assertThat(member.username()).isEqualTo("<img src=x>");
        assertThat(read.history(PUBLIC,auth("operator"),null,50).orElseThrow().events().get(0).member()).isEqualTo("viewer");
        assertThat(jdbc.queryForList("SELECT * FROM business_memberships ORDER BY user_id")).isEqualTo(before);
    }
    @Test void historyPagesAreNewestFirstBoundedAndNeverRepeatTheCursorRevision() {
        for(int i=1;i<=7;i++)event(i,i%2==0?"remove":"add");
        var first=read.history(PUBLIC,auth("viewer"),null,3).orElseThrow();
        assertThat(first.events()).extracting(BusinessTicketParticipantReadService.Event::revision).containsExactly(7L,6L,5L);
        assertThat(first.hasMore()).isTrue();assertThat(first.nextBeforeRevision()).isEqualTo(5);
        var next=read.history(PUBLIC,auth("viewer"),first.nextBeforeRevision(),3).orElseThrow();
        assertThat(next.events()).extracting(BusinessTicketParticipantReadService.Event::revision).containsExactly(4L,3L,2L);
        var last=read.history(PUBLIC,auth("viewer"),next.nextBeforeRevision(),3).orElseThrow();
        assertThat(last.events()).extracting(BusinessTicketParticipantReadService.Event::revision).containsExactly(1L);
        assertThat(last.hasMore()).isFalse();assertThat(last.nextBeforeRevision()).isNull();
        assertThat(read.history(PUBLIC,auth("viewer"),1L,100).orElseThrow().events()).isEmpty();
    }
    @Test void bigintsRemainExactAndDefaultHistoryIncludesMaximumRevision() throws Exception {
        event(Long.MAX_VALUE,"add");
        var history=read.history(PUBLIC,auth("viewer"),null,1).orElseThrow();
        assertThat(history.events().get(0).revision()).isEqualTo(Long.MAX_VALUE);
        var json=new ObjectMapper().registerModule(new JavaTimeModule()).readTree(new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(history));
        assertThat(json.get("revision").asText()).isEqualTo(Long.toString(Long.MAX_VALUE));
        assertThat(json.get("revision").isTextual()).isTrue();
        assertThat(json.get("events").get(0).get("memberId").isTextual()).isTrue();
    }
    @Test void rosterBoundIs200AndOverflowIsAnExplicitRefusal() {
        jdbc.execute("INSERT INTO business_ticket_participants(ticket_user_id,ticket_id,ticket_public_id,business_id,member_user_id,member,actor_user_id,actor,revision) "
            +"SELECT 42,'"+RAW+"','"+PUBLIC+"',11,n,'member-'||n,1,'operator',n FROM generate_series(1000,1199) n");
        assertThat(read.find(PUBLIC,auth("viewer")).orElseThrow().members()).hasSize(200);
        jdbc.update("INSERT INTO business_ticket_participants VALUES(42,?,?,11,1200,'overflow',1,'operator',1200,CURRENT_TIMESTAMP)",RAW,PUBLIC);
        assertThatThrownBy(()->read.find(PUBLIC,auth("viewer"))).isInstanceOf(BusinessTicketParticipantReadService.RosterTooLargeException.class);
    }
    @Test void missingCanonicalSchemaDoesNotBecomeAnEmptyOrLegacyRoster() {
        jdbc.execute("DROP TABLE business_ticket_participants");
        assertThatThrownBy(()->read.find(PUBLIC,auth("viewer"))).isInstanceOf(BusinessTicketParticipantReadService.ReadUnavailableException.class);
        jdbc.execute("DROP TABLE business_ticket_participant_events");
        assertThatThrownBy(()->read.history(PUBLIC,auth("viewer"),null,50)).isInstanceOf(BusinessTicketParticipantReadService.ReadUnavailableException.class);
    }
    @Test void invalidIdentityLocatorAndPagingDoNotOpenAnotherScope() {
        assertThat(read.find("bad",auth("viewer"))).isEmpty();assertThat(read.find(PUBLIC,null)).isEmpty();
        assertThat(read.history(null,auth("viewer"),null,50)).isEmpty();
        assertThatThrownBy(()->read.history(PUBLIC,auth("viewer"),0L,50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->read.history(PUBLIC,auth("viewer"),null,101)).isInstanceOf(IllegalArgumentException.class);
    }
}
