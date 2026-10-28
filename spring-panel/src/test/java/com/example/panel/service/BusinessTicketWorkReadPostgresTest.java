package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.example.panel.security.PanelUserPrincipal;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import static org.assertj.core.api.Assertions.*;

class BusinessTicketWorkReadPostgresTest {
    private static final String RAW=" R171/Ю?%#+ ",PUBLIC="a".repeat(32);
    private JdbcTemplate jdbc;
    private BusinessTicketWorkReadService read;
    @BeforeEach void fixture() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("business_work_read");
        jdbc.execute("""
            CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE);
            CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT DEFAULT 'active');
            CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,
                channel_id BIGINT,status TEXT DEFAULT 'open',PRIMARY KEY(user_id,ticket_id));
            CREATE TABLE ticket_categories(ticket_id TEXT,category TEXT);
            INSERT INTO users(id,username) VALUES(1,'operator'),(2,'viewer'),(3,'all-reader'),(4,'foreign'),(5,'none');
            INSERT INTO businesses(id) VALUES(11),(22);
            INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(1,11,'OPERATOR'),(2,11,'VIEWER'),(4,22,'ADMIN');
            INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(3,'ALL_BUSINESSES','VIEWER');
            """);
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V81__business_ticket_manual_work.sql")));
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(42,?,?,11),(88,?,?,22)",RAW,PUBLIC,RAW.trim(),"b".repeat(32));
        jdbc.update("INSERT INTO ticket_categories VALUES(?,'Legacy secret')",RAW);
        read=new BusinessTicketWorkReadService(jdbc,new ObjectMapper());
    }
    private void event(long revision,String action) {
        jdbc.update("INSERT INTO business_ticket_work_events(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,action,from_status,to_status,previous_categories,categories,actor_user_id,actor) VALUES(42,?,?,11,?,?,?,?,'[\"Old\"]',CAST(? AS JSONB),9223372036854775807,'<script>actor</script>')",
            RAW,PUBLIC,revision,action,action.equals("reopen")?"resolved":"open",action.equals("close")?"resolved":action.equals("reopen")?"pending":"open",
            action.equals("reopen")?"[\"Old\"]":"[\"Billing\",\"<img src=x>\"]");
    }
    // Login resolves case-insensitive input to the exact stored account name; SQL still checks ambiguity.
    private Authentication auth(String name) {
        String stored="VIEWER".equals(name)?"viewer":name;
        long id=switch(stored){case "operator"->1;case "viewer"->2;case "all-reader"->3;case "foreign"->4;case "none"->5;default->2;};
        var principal=new PanelUserPrincipal(id,stored,"unused",true,AuthorityUtils.createAuthorityList("PAGE_DIALOGS"));
        return UsernamePasswordAuthenticationToken.authenticated(principal,"unused",principal.getAuthorities());
    }
    private BusinessTicketWorkReadService.History history() { return read.history(PUBLIC,auth("viewer"),null,50).orElseThrow(); }
    @Test void allActionsKeepOrderedCategorySnapshotsAndDoNotReadOrMutateLegacyRows() throws Exception {
        event(1,"categories");event(2,"close");event(3,"reopen");
        var before=jdbc.queryForList("SELECT * FROM ticket_categories");
        for(String identity:List.of("viewer","VIEWER","all-reader")) {
            var page=read.history(PUBLIC,auth(identity),null,50).orElseThrow();
            assertThat(page.owner()).isEqualTo(new BusinessTicketWorkReadService.Owner("42",RAW,PUBLIC,"11"));
            assertThat(page.revision()).isZero();assertThat(page.latestEventRevision()).isEqualTo(3);
            assertThat(page.events()).extracting(BusinessTicketWorkReadService.Event::action).containsExactly("reopen","close","categories");
            assertThat(page.events().get(0).previousCategories()).containsExactly("Old");
            assertThat(page.events().get(0).categories()).containsExactly("Old");
            assertThat(page.events().get(1).categories()).containsExactly("Billing","<img src=x>");
            assertThat(page.events().get(1).fromStatus()).isEqualTo("open");assertThat(page.events().get(1).toStatus()).isEqualTo("resolved");
            assertThat(new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(page)).doesNotContain("Legacy secret");
        }
        assertThat(jdbc.queryForList("SELECT * FROM ticket_categories")).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"collision","closed","resolved","no-legacy-table"})
    void canonicalHistoryDoesNotNeedRawUniquenessOpenStateOrLegacyTable(String change) {
        event(1,"close");
        switch(change) {
            case "collision" -> jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(99,?,?,22)",RAW,"c".repeat(32));
            case "closed","resolved" -> jdbc.update("UPDATE tickets SET status=? WHERE user_id=42",change);
            case "no-legacy-table" -> jdbc.execute("DROP TABLE ticket_categories");
        }
        assertThat(history().events()).hasSize(1);
        assertThat(read.history("b".repeat(32),auth("all-reader"),null,50).orElseThrow().events()).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"membership","all-grant","disabled","blocked","ambiguous","archived","role","general-grant","foreign","space"})
    void currentReaderAccountAndBusinessReadAreRequired(String denied) {
        event(1,"close");String identity="viewer";
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
        assertThat(read.history(PUBLIC,auth(identity),null,50)).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"business","public","raw","user","general","unresolved","recreate","public-collision"})
    void auditCannotFollowAnotherFullOwnerOrGeneration(String change) {
        event(1,"close");String publicId=PUBLIC;
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
        var page=read.history(publicId,auth("all-reader"),null,50);
        if(List.of("general","unresolved","public-collision").contains(change)) assertThat(page).isEmpty();
        else assertThat(page.orElseThrow().events()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_ticket_work_events",Long.class)).isEqualTo(1);
    }
    @Test void pagedHistoryIncludesAllActionsAndStrictCursorWithoutAssumingContiguousRootRevisions() {
        for(int i=1;i<=7;i++)event(i,i%3==0?"reopen":i%2==0?"close":"categories");
        var first=read.history(PUBLIC,auth("viewer"),null,3).orElseThrow();
        assertThat(first.events()).extracting(BusinessTicketWorkReadService.Event::revision).containsExactly(7L,6L,5L);
        assertThat(first.hasMore()).isTrue();assertThat(first.nextBeforeRevision()).isEqualTo(5);
        var next=read.history(PUBLIC,auth("viewer"),first.nextBeforeRevision(),3).orElseThrow();
        assertThat(next.events()).extracting(BusinessTicketWorkReadService.Event::revision).containsExactly(4L,3L,2L);
        assertThat(next.revision()).isEqualTo(first.revision());assertThat(next.latestEventRevision()).isEqualTo(7);
        var last=read.history(PUBLIC,auth("viewer"),next.nextBeforeRevision(),3).orElseThrow();
        assertThat(last.events()).extracting(BusinessTicketWorkReadService.Event::revision).containsExactly(1L);
        assertThat(last.hasMore()).isFalse();assertThat(last.nextBeforeRevision()).isNull();
        assertThat(read.history(PUBLIC,auth("viewer"),1L,100).orElseThrow().events()).isEmpty();
        jdbc.update("UPDATE tickets SET channel_id=9 WHERE user_id=42");
        assertThat(history().revision()).isEqualTo(1);assertThat(history().latestEventRevision()).isEqualTo(7);
        event(8,"categories");assertThat(history().revision()).isEqualTo(1);assertThat(history().latestEventRevision()).isEqualTo(8);
    }
    @Test void maximumBigintsRemainExactAndActorSnapshotDoesNotFollowCurrentAccounts() throws Exception {
        jdbc.update("INSERT INTO users VALUES(9223372036854775807,'<script>actor</script>',TRUE,FALSE)");
        event(Long.MAX_VALUE,"close");
        jdbc.update("UPDATE users SET username='renamed actor' WHERE id=9223372036854775807");
        assertThat(history().events().get(0).actor()).isEqualTo("<script>actor</script>");
        jdbc.update("DELETE FROM users WHERE id=9223372036854775807");jdbc.update("INSERT INTO users VALUES(6,'<script>actor</script>',TRUE,FALSE)");
        var page=history();assertThat(page.events().get(0).actorId()).isEqualTo(Long.MAX_VALUE);assertThat(page.events().get(0).actor()).isEqualTo("<script>actor</script>");
        var mapper=new ObjectMapper().registerModule(new JavaTimeModule());var json=mapper.readTree(mapper.writeValueAsString(page));
        for(String field:List.of("revision","latestEventRevision"))assertThat(json.get(field).isTextual()).isTrue();
        assertThat(json.get("latestEventRevision").asText()).isEqualTo(Long.toString(Long.MAX_VALUE));
        for(String field:List.of("revision","actorId"))assertThat(json.get("events").get(0).get(field).isTextual()).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"previous-number","next-number","next-null","next-object","next-array-null"})
    void malformedCategorySnapshotFailsClosedWithoutStringCoercionOrRawFallback(String invalid) {
        jdbc.execute("CREATE OR REPLACE FUNCTION valid_business_ticket_categories(items JSONB) RETURNS BOOLEAN LANGUAGE SQL IMMUTABLE AS $$ SELECT TRUE $$");
        String value=switch(invalid){case "next-null"->"null";case "next-object"->"{}";case "next-array-null"->"[null]";default->"[1]";};
        jdbc.update("INSERT INTO business_ticket_work_events(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,action,from_status,to_status,previous_categories,categories,actor_user_id,actor) VALUES(42,?,?,11,1,'categories','open','open',CAST(? AS JSONB),CAST(? AS JSONB),1,'operator')",RAW,PUBLIC,
            invalid.startsWith("previous")?value:"[\"Old\"]",invalid.startsWith("previous")?"[\"New\"]":value);
        assertThatThrownBy(()->history()).isInstanceOf(BusinessTicketWorkReadService.ReadUnavailableException.class);
    }
    @Test void missingAuditIsUnavailableRatherThanEmpty() {
        jdbc.execute("DROP TABLE business_ticket_work_events");
        assertThatThrownBy(()->history()).isInstanceOf(BusinessTicketWorkReadService.ReadUnavailableException.class);
    }
    @Test void emptyHistoryAndInvalidInputsStayBounded() {
        assertThat(history().events()).isEmpty();assertThat(history().latestEventRevision()).isZero();
        assertThat(read.history("bad",auth("viewer"),null,50)).isEmpty();assertThat(read.history(PUBLIC,null,null,50)).isEmpty();
        assertThatThrownBy(()->read.history(PUBLIC,auth("viewer"),0L,50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->read.history(PUBLIC,auth("viewer"),null,101)).isInstanceOf(IllegalArgumentException.class);
    }
}
