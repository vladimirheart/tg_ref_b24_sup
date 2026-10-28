package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BusinessCategoryProjectionPostgresTest {
    private static final String RAW = " R168/Ю?%#+ ", PUBLIC = "a".repeat(32);
    private static final BusinessAccessService.OneBusiness BUSINESS = new BusinessAccessService.OneBusiness(11L);
    private JdbcTemplate jdbc;
    private BusinessScopedTicketReadService read;
    private BusinessTicketWorkService work;
    private final UiEventTicketScope owner = new UiEventTicketScope(42L, RAW, PUBLIC, "BUSINESS", 11L);

    @BeforeEach void fixture() throws Exception {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("business_categories");
        jdbc.execute("""
            CREATE TABLE users(id BIGINT PRIMARY KEY, username TEXT, enabled BOOLEAN DEFAULT TRUE, is_blocked BOOLEAN DEFAULT FALSE);
            CREATE TABLE businesses(id BIGINT PRIMARY KEY,code TEXT,display_name TEXT,status TEXT DEFAULT 'active');
            CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,
                group_msg_id BIGINT,channel_id BIGINT,status TEXT DEFAULT 'open',created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                resolved_at TIMESTAMPTZ,resolved_by TEXT,closed_count INT DEFAULT 0,reopen_count INT DEFAULT 0,last_reopen_at TIMESTAMPTZ,
                PRIMARY KEY(user_id,ticket_id));
            CREATE TABLE messages(group_msg_id BIGINT,user_id BIGINT,ticket_id TEXT,client_name TEXT,problem TEXT);
            CREATE TABLE ticket_categories(ticket_id TEXT,category TEXT);
            CREATE TABLE ticket_responsibles(ticket_id TEXT,responsible TEXT);
            CREATE TABLE ticket_participants(ticket_id TEXT,username TEXT);
            CREATE TABLE ticket_attributes(ticket_id TEXT,question_id TEXT,question_text TEXT,input_type TEXT,value_label TEXT,value_text TEXT);
            CREATE TABLE ticket_spans(ticket_id TEXT,id BIGINT,span_no INT,started_at TIMESTAMPTZ,ended_at TIMESTAMPTZ,duration_seconds INT);
            CREATE TABLE chat_history(id BIGINT,ticket_owner_user_id BIGINT,ticket_id TEXT,sender TEXT,message TEXT,
                timestamp TIMESTAMPTZ,message_type TEXT,attachment TEXT,deleted_at TIMESTAMPTZ);
            CREATE TABLE ui_event_outbox(id BIGINT PRIMARY KEY,event_type TEXT,ticket_id TEXT,channel_id BIGINT,created_at TIMESTAMPTZ,
                ticket_user_id BIGINT,ticket_public_id TEXT,ticket_scope_kind TEXT,ticket_business_id BIGINT);
            INSERT INTO users(id,username) VALUES(1,'operator'),(2,'viewer'),(3,'all'),(4,'foreign'),(5,'none');
            INSERT INTO businesses(id,code,display_name) VALUES(11,'one','Same name'),(22,'two','Same name');
            INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(1,11,'OPERATOR'),(2,11,'VIEWER'),(4,22,'ADMIN');
            INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(3,'ALL_BUSINESSES','VIEWER');
            """);
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V81__business_ticket_manual_work.sql")));
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(42,?,?,11),(88,?,?,22)",
                RAW,PUBLIC,RAW.trim(),"b".repeat(32));
        jdbc.update("INSERT INTO ticket_categories VALUES(?,'Legacy secret'),(?,'Foreign legacy')", RAW,RAW.trim());
        var flags = new MultibusinessFeatureFlags();
        flags.getFoundation().setEnabled(true); flags.setUiSelector(true);
        read = new BusinessScopedTicketReadService(jdbc,new BusinessAccessService(jdbc));
        work = new BusinessTicketWorkService(jdbc,flags,new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc)),new ObjectMapper());
    }

    private org.springframework.security.core.Authentication boundActor(){
        return org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
            new com.example.panel.security.PanelUserPrincipal(1,"operator","unused",true,List.of()),null,List.of());
    }
    private void save(List<String> categories) {
        var view=work.find(PUBLIC,boundActor()).orElseThrow();
        assertThat(work.change(owner,boundActor(),view.revision(),view.status(),"categories",categories)).isTrue();
    }
    private void allProjections(List<String> expected) {
        assertThat(read.findByPublicId(2L,PUBLIC).orElseThrow().categories()).isEqualTo(expected);
        assertThat(read.listTickets(2L,BUSINESS,50,0).get(0).categories()).isEqualTo(expected);
        assertThat(read.pageTickets(2L,BUSINESS,50,0,null,null).tickets().get(0).categories()).isEqualTo(expected);
        assertThat(read.exportTickets(2L,BUSINESS,null,null,null).get(0).categories()).isEqualTo(expected);
        var workspace=read.findWorkspaceByPublicId(2L,PUBLIC).orElseThrow();
        assertThat(workspace.categories()).isEqualTo(expected);
        assertThat(workspace.ticket().categories()).isEqualTo(expected);
        assertThat(read.findHistoryByPublicId(2L,PUBLIC,null,50).orElseThrow().ticket().categories()).isEqualTo(expected);
        assertThat(read.findAnswersByPublicId(2L,PUBLIC).orElseThrow().ticket().categories()).isEqualTo(expected);
        assertThat(read.findSpansByPublicId(2L,PUBLIC).orElseThrow().ticket().categories()).isEqualTo(expected);
    }
    @Test void saveCloseReopenClearAndEveryReadUseTheSameCanonicalCategories() {
        var legacy=jdbc.queryForList("SELECT * FROM ticket_categories ORDER BY ticket_id");
        allProjections(List.of());
        save(List.of(" Delivery ","Delivery","<script>Markup</script>","😀".repeat(160)));
        var expected=List.of("Delivery","<script>Markup</script>","😀".repeat(160));
        allProjections(expected);
        var view=work.find(PUBLIC,boundActor()).orElseThrow();
        assertThat(work.change(owner,boundActor(),view.revision(),"open","close",expected)).isTrue();
        allProjections(expected);
        assertThat(work.change(owner,boundActor(),view.revision()+1,"resolved","reopen",List.of())).isTrue();
        allProjections(expected);
        save(List.of()); allProjections(List.of());
        assertThat(read.summarizeCategories(2L,BUSINESS)).isEmpty();
        assertThat(jdbc.queryForList("SELECT * FROM ticket_categories ORDER BY ticket_id")).isEqualTo(legacy);
    }
    @Test void collidingRawIdsCannotMixCanonicalCategoriesOrSuppressTheirRead() {
        save(List.of("Own"));
        jdbc.update("UPDATE tickets SET business_categories='[\"Foreign\"]' WHERE user_id=88");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,business_categories) VALUES(99,?,?,22,'[\"Collision\"]')",RAW,"c".repeat(32));
        allProjections(List.of("Own"));
        assertThat(read.findWorkspaceByPublicId(2L,PUBLIC).orElseThrow().relationVerified()).isFalse();
        var counts=read.summarizeCategories(3L,BusinessAccessService.AllAccessibleBusinesses.INSTANCE);
        assertThat(counts).extracting(BusinessScopedTicketReadService.CategoryCount::category).containsExactly("Own","Collision","Foreign");
        assertThat(read.exportTickets(2L,BUSINESS,null,null,null,"Foreign")).isEmpty();
    }
    static java.util.stream.Stream<String> exactCategories() {
        return java.util.stream.Stream.of("Delivery","A%_B","O'Reilly","<img src=x>","😀".repeat(160));
    }
    @ParameterizedTest @MethodSource("exactCategories")
    void exactFilterIsLiteralCaseSensitiveAndSharedByListPageAndExport(String category) {
        save(List.of(category,"Other"));
        assertThat(read.listTickets(2L,BUSINESS,50,0,"open",RAW,null,category)).hasSize(1);
        assertThat(read.pageTickets(2L,BUSINESS,1,0,null,PUBLIC,null,category).tickets()).hasSize(1);
        assertThat(read.exportTickets(2L,BUSINESS,null,null,null,category)).hasSize(1);
        assertThat(read.listTickets(2L,BUSINESS,50,0,null,null,null,"Missing "+category.hashCode())).isEmpty();
        assertThat(read.listTickets(2L,BUSINESS,50,0,"closed",null,null,category)).isEmpty();
        assertThat(read.listTickets(2L,BUSINESS,50,0,null,null,null,"Legacy secret")).isEmpty();
    }
    @Test void searchUsesCanonicalCategorySubstringsWithoutLegacyFallback() {
        save(List.of("DeLiVeRy 50%_"));
        assertThat(read.listTickets(2L,BUSINESS,50,0,null,null,"livery 50%_")).hasSize(1);
        assertThat(read.listTickets(2L,BUSINESS,50,0,null,null,"Legacy secret")).isEmpty();
        assertThat(read.exportTickets(2L,BUSINESS,null,null,"livery","Different")).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"business","public","raw","user","general","unresolved","recreate"})
    void categoriesDoNotFollowAnotherOwnerOrGeneration(String change) {
        save(List.of("Old owner"));
        switch(change) {
            case "business" -> jdbc.update("UPDATE tickets SET business_id=22 WHERE user_id=42");
            case "public" -> jdbc.update("UPDATE tickets SET ticket_public_id=? WHERE user_id=42","c".repeat(32));
            case "raw" -> jdbc.update("UPDATE tickets SET ticket_id='new' WHERE user_id=42");
            case "user" -> jdbc.update("UPDATE tickets SET user_id=43 WHERE user_id=42");
            case "general" -> jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE WHERE user_id=42");
            case "unresolved" -> jdbc.update("UPDATE tickets SET business_id=NULL WHERE user_id=42");
            case "recreate" -> {jdbc.update("DELETE FROM tickets WHERE user_id=42");jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(42,?,?,11)",RAW,"c".repeat(32));}
        }
        var all=BusinessAccessService.AllAccessibleBusinesses.INSTANCE;
        assertThat(read.summarizeCategories(3L,all)).isEmpty();
        assertThat(read.exportTickets(3L,all,null,null,null)).allSatisfy(t->assertThat(t.categories()).isEmpty());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_ticket_work_events",Long.class)).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"membership","all-grant","archived"})
    void finalReadSqlRejectsRevocationEvenWithAStaleAccessResolver(String denied) {
        save(List.of("Private"));
        long reader=denied.equals("all-grant")?3L:2L;
        var stale=mock(BusinessAccessService.class);
        when(stale.resolveAccessibleBusinessIds(reader)).thenReturn(Set.of(11L));
        when(stale.requireSelectedBusiness(eq(reader),any())).thenReturn(new BusinessAccessService.SelectedBusinessScope(Set.of(11L),11L));
        if(denied.equals("all-grant"))jdbc.update("UPDATE business_access_grants SET is_active=FALSE");
        else if(denied.equals("membership"))jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=2");
        else jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");
        var service=new BusinessScopedTicketReadService(jdbc,stale);
        assertThat(service.summarizeCategories(reader,BUSINESS)).isEmpty();
        assertThat(service.exportTickets(reader,BUSINESS,null,null,null,"Private")).isEmpty();
        assertThat(service.findByPublicId(reader,PUBLIC)).isEmpty();
        assertThat(service.findWorkspaceByPublicId(reader,PUBLIC)).isEmpty();
        assertThat(service.findHistoryByPublicId(reader,PUBLIC,null,50)).isEmpty();
        assertThat(service.findAnswersByPublicId(reader,PUBLIC)).isEmpty();
        assertThat(service.findSpansByPublicId(reader,PUBLIC)).isEmpty();
    }
    @Test void summaryCountsTicketsPerBusinessAndNeverCombinesEqualBusinessLabels() {
        save(List.of("Shared","Own"));
        jdbc.update("UPDATE tickets SET business_categories='[\"Shared\",\"Foreign\"]' WHERE user_id=88");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,business_categories) VALUES(100,'third',?,11,'[\"Shared\"]')","d".repeat(32));
        assertThat(read.summarizeCategories(2L,BUSINESS)).containsExactly(
            new BusinessScopedTicketReadService.CategoryCount(11,"one","Same name","Own",1),
            new BusinessScopedTicketReadService.CategoryCount(11,"one","Same name","Shared",2));
        assertThat(read.summarizeCategories(3L,BusinessAccessService.AllAccessibleBusinesses.INSTANCE)).hasSize(4);
        assertThat(read.summarizeCategories(5L,BusinessAccessService.AllAccessibleBusinesses.INSTANCE)).isEmpty();
        assertThatThrownBy(()->read.summarizeCategories(2L,new BusinessAccessService.OneBusiness(22L))).isInstanceOf(AccessDeniedException.class);
    }
    @Test void summaryRejectsOverflowInsteadOfReturningPartialCounts() {
        jdbc.execute("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,business_categories) "
            +"SELECT n,'bulk-'||n,lpad(to_hex(n),32,'0'),11,jsonb_build_array('category-'||n) FROM generate_series(1000,1999) n");
        assertThat(read.summarizeCategories(2L,BUSINESS)).hasSize(1000);
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,business_categories) VALUES(2000,'overflow',?,11,'[\"overflow\"]')","e".repeat(32));
        assertThatThrownBy(()->read.summarizeCategories(2L,BUSINESS)).isInstanceOf(BusinessScopedTicketReadService.CategorySummaryTooLargeException.class);
    }
    @Test void readModelDoesNotDependOnTheLegacyCategoryTable() {
        save(List.of("Own")); jdbc.execute("DROP TABLE ticket_categories");
        allProjections(List.of("Own"));
    }
}
