package com.example.panel.controller;

import static com.example.panel.service.ScopedAiSourceService.Kind.KNOWLEDGE;
import static com.example.panel.service.ScopedAiSourceService.Kind.MEMORY;
import static com.example.panel.service.ScopedAiSourceService.Status.APPROVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.ScopedAiSourceService;
import com.example.panel.service.ScopedAiSourceService.Source;
import com.example.panel.service.UiEventTicketScope;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.util.HtmlUtils;

@SpringBootTest
@AutoConfigureMockMvc
class ScopedAiSourceHttpPostgresTest {
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.panel.service.BotProcessService botProcessService;
    @org.springframework.boot.test.mock.mockito.MockBean(name="taskScheduler")
    private org.springframework.scheduling.TaskScheduler taskScheduler;
    @org.springframework.test.context.DynamicPropertySource
    static void isolatedPostgresql(org.springframework.test.context.DynamicPropertyRegistry registry)throws Exception {
        var dataSource=(org.springframework.jdbc.datasource.DriverManagerDataSource)PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("r214_source_http").getDataSource();
        var config=Files.createTempDirectory("r214-source-config-");
        var storage=Files.createTempDirectory("r214-source-storage-");
        registry.add("app.datasource.mode",()->"postgresql");
        registry.add("spring.datasource.url",dataSource::getUrl);
        registry.add("spring.datasource.username",dataSource::getUsername);
        registry.add("spring.datasource.password",dataSource::getPassword);
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        registry.add("spring.flyway.locations",()->"classpath:db/migration/postgresql");
        registry.add("shared-config.dir",config::toString);
        for(String key:new String[]{"attachments","knowledge-base","passport-photos","avatars"})registry.add("app.storage."+key,()->storage.resolve(key).toString());
        registry.add("app.storage.object.required-for-postgresql",()->"false");
        registry.add("app.coordination.required-for-postgresql",()->"false");
        registry.add("app.bots.internal-api.token",()->"r214-test-internal-bot-api-token");
        registry.add("app.security.remember-me-key",()->"r214-test-remember-me-key");
        registry.add("app.security.bootstrap-admin.username",()->"r214_test_bootstrap_admin");
        registry.add("app.security.bootstrap-admin.password",()->"r214-test-bootstrap-password");
        registry.add("app.bots.auto-start-enabled",()->"false");
        registry.add("monitoring.credentials.master-key",()->"r214-test-monitoring-master-key");
    }


    @ParameterizedTest @ValueSource(strings={"prefix","suffix","both","tab"})
    void spacedPrincipalCannotReadApproveOrCreateInAnotherBusiness(String variant) throws Exception {
        String forged=switch(variant){case "prefix"->" "+admin;case "suffix"->admin+" ";case "tab"->"\t"+admin+"\t";default->" "+admin+" ";};
        long forgedId=account(forged);
        if(variant.equals("both"))membership(forgedId,businessB,"ADMIN");
        mvc.perform(auth(get(ROOT).param("businessId",Long.toString(businessA)),forged)).andExpect(status().isForbidden());
        mvc.perform(auth(edit(sourceA),forged)).andExpect(status().isForbidden());
        mvc.perform(auth(create("principal-denied"),forged).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(auth(revise(sourceA),forged).with(csrf())).andExpect(status().isForbidden());
        assertUnchanged(sourceA);
        assertThat(sources.find(businessA,com.example.panel.support.AiControlIdentityFixture.login(jdbc,admin),KNOWLEDGE,"principal-denied")).isEmpty();
    }
    @Test void literalSpacedPrincipalCanReviewOnlyItsOwnBusinessSources() throws Exception {
        String original=admin,spaced=" "+admin+" ";jdbc.update("UPDATE users SET username=? WHERE id=?",spaced,adminId);
        membership(account(original),businessB,"ADMIN");
        mvc.perform(auth(edit(sourceA),spaced)).andExpect(status().isOk());
        mvc.perform(auth(edit(sourceA),original)).andExpect(status().isForbidden());
        mvc.perform(auth(create("literal-spaced"),spaced).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(auth(revise(sourceA),spaced).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(sources.find(businessA,com.example.panel.support.AiControlIdentityFixture.login(jdbc,spaced),KNOWLEDGE,KEY).orElseThrow().revision()).isEqualTo(2);
        assertThat(jdbc.queryForMap("SELECT updated_by_user_id,updated_by FROM ai_scoped_sources WHERE id=?",sourceA.id()))
            .containsEntry("updated_by_user_id",adminId).containsEntry("updated_by",spaced);
        assertThat(sources.find(businessA,com.example.panel.support.AiControlIdentityFixture.login(jdbc,spaced),KNOWLEDGE,"literal-spaced")).isPresent();
    }

    private static final AtomicLong IDS=new AtomicLong(9320000);
    private static final String ROOT="/business-ai-sources";
    private static final String KEY=" opaque/Ю?%#&=+ ";
    private static final String BODY="PRIVATE_A </textarea><script>window.pwned=1</script>";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MultibusinessFeatureFlags flags;
    @SpyBean private ScopedAiSourceService sources;
    @SpyBean private com.example.panel.service.ScopedAiReplyRuleService replyRules;
    @Autowired private com.example.panel.service.integration.InboundClientMessageIngestionService incoming;
    private long businessA,businessB,adminId,viewerId;
    private String admin,viewer,operator,manager,foreign,global,all;
    private Source sourceA,sourceB;
    private UiEventTicketScope origin;

    @BeforeEach
    void setUp() {
        assertThat(jdbc.queryForObject("SELECT current_schema()",String.class)).startsWith("r214_source_http_");
        flags.getFoundation().setEnabled(true);
        flags.setUiSelector(true);
        flags.setStrictReadScope(false);
        String prefix="ai-r132-"+IDS.incrementAndGet();
        businessA=business(prefix+"-a","Business A <private>");
        businessB=business(prefix+"-b","Business B secret");
        admin=prefix+"-admin"; viewer=prefix+"-viewer"; operator=prefix+"-operator";
        manager=prefix+"-manager"; foreign=prefix+"-foreign"; global=prefix+"-global"; all=prefix+"-all";
        adminId=account(admin); viewerId=account(viewer);
        membership(adminId,businessA,"ADMIN"); membership(viewerId,businessA,"VIEWER");
        membership(account(operator),businessA,"OPERATOR"); membership(account(manager),businessA,"MANAGER");
        membership(account(foreign),businessB,"ADMIN");
        jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(?,'ROLE_ADMIN')",account(global));
        jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(?,'ALL_BUSINESSES','ADMIN')",account(all));
        assertThat(sources.createKnowledge(businessA,com.example.panel.support.AiControlIdentityFixture.login(jdbc,admin),KEY,"Title A <unsafe>",BODY)).isTrue();
        assertThat(sources.createKnowledge(businessB,com.example.panel.support.AiControlIdentityFixture.login(jdbc,foreign),KEY,"Title B secret","PRIVATE_B_SECRET")).isTrue();
        sourceA=sources.find(businessA,com.example.panel.support.AiControlIdentityFixture.login(jdbc,admin),KNOWLEDGE,KEY).orElseThrow();
        sourceB=sources.find(businessB,com.example.panel.support.AiControlIdentityFixture.login(jdbc,foreign),KNOWLEDGE,KEY).orElseThrow();
        String raw=" private origin/Ю?% "+prefix;
        String publicId=UUID.randomUUID().toString().replace("-","");
        long owner=IDS.incrementAndGet();
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,is_general,business_id) VALUES(?,?,?,FALSE,?)",owner,raw,publicId,businessA);
        origin=new UiEventTicketScope(owner,raw,publicId,"BUSINESS",businessA);
    }

    @AfterEach
    void restoreFlags() { flags.getFoundation().setEnabled(false); flags.setUiSelector(false); flags.setStrictReadScope(false); }

    @Test
    void catalogRequiresExplicitBusinessAndEscapesTitlesWithoutExposingOtherBusinessOrBody() throws Exception {
        mvc.perform(auth(get(ROOT),viewer)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control",containsString("no-store")))
            .andExpect(content().string(containsString("Выберите один доступный бизнес")))
            .andExpect(content().string(not(containsString("Title A"))));
        var result=mvc.perform(auth(get(ROOT).param("businessId",Long.toString(businessA)),viewer))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Title A &lt;unsafe&gt;")))
            .andExpect(content().string(not(containsString("PRIVATE_B_SECRET"))))
            .andExpect(content().string(not(containsString("Title B secret"))))
            .andExpect(content().string(not(containsString(BODY))))
            .andExpect(content().string(not(containsString("data-ai-source-create")))).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("data-page-key=\"ai-sources\"");
        mvc.perform(auth(get(ROOT).param("businessId",Long.toString(businessB)),all))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Title B secret")))
            .andExpect(content().string(not(containsString("Title A"))));
    }

    @Test void explicitReplyRuleRequiresReviewAndCanBeRevokedWithCsrf() throws Exception {
        approveReplySource();
        var page=mvc.perform(auth(rulePage(),admin)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control",containsString("no-store")))
            .andExpect(content().string(containsString("data-ai-reply-rule-create")))
            .andExpect(content().string(containsString("&lt;script&gt;"))).andReturn();
        String rule=hidden(page.getResponse().getContentAsString(),"ruleKey");
        mvc.perform(auth(ruleCreate(rule),admin)).andExpect(redirectedUrl("/error/403"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_scoped_reply_rules WHERE business_id=?",Integer.class,businessA)).isZero();
        mvc.perform(auth(ruleCreate(rule).param("reviewed","false"),admin).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(auth(ruleCreate(rule).param("reviewed","true"),admin).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForMap("SELECT source_id,source_revision,reviewed_by_user_id,reviewed_by FROM ai_scoped_reply_rules WHERE rule_key=?",rule))
            .containsEntry("source_id",sourceA.id()).containsEntry("source_revision",2L).containsEntry("reviewed_by_user_id",adminId).containsEntry("reviewed_by",admin);
        var revoke=post("/business-ai-rules/revoke").param("businessId",Long.toString(businessA)).param("kind","KNOWLEDGE").param("key",KEY).param("ruleKey",rule).param("revision","1");
        mvc.perform(auth(revoke,admin).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT active FROM ai_scoped_reply_rules WHERE rule_key=?",Boolean.class,rule)).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"viewer","operator","manager","foreign","global"})
    void replyRulePageAndMutationRequireBusinessConfigure(String role)throws Exception {
        approveReplySource();String identity=switch(role){case "viewer"->viewer;case "operator"->operator;case "manager"->manager;case "foreign"->foreign;default->global;};
        mvc.perform(auth(rulePage(),identity)).andExpect(status().isForbidden());
        mvc.perform(auth(ruleCreate(UUID.randomUUID().toString()).param("reviewed","true"),identity).with(csrf())).andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings={"revision","grant","blocked","page"})
    void preparedRuleFormCannotSurviveSourceOrAuthorityChange(String change)throws Exception {
        approveReplySource();var request=auth(ruleCreate(UUID.randomUUID().toString()).param("reviewed","true"),admin).with(csrf());
        if(change.equals("revision"))jdbc.update("UPDATE ai_scoped_sources SET revision=revision+1,body='new unreviewed body' WHERE id=?",sourceA.id());
        else if(change.equals("page"))jdbc.update("DELETE FROM user_authorities WHERE user_id=? AND authority='PAGE_KNOWLEDGE_BASE'",adminId);
        else if(change.equals("grant"))mutate("revoke");else mutate("blocked");
        mvc.perform(request).andExpect(change.equals("revision")?status().isConflict():change.equals("blocked")?redirectedUrl("http://localhost/login"):status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_scoped_reply_rules WHERE business_id=?",Integer.class,businessA)).isZero();
    }
    @Test void ruleWriteRepeatsSourceRevisionAfterControllerPrecheck()throws Exception {
        approveReplySource();
        doAnswer(call->{jdbc.update("UPDATE ai_scoped_sources SET revision=revision+1,body='late change' WHERE id=?",sourceA.id());return call.callRealMethod();})
            .when(replyRules).create(eq(businessA),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),eq(sourceA.id()),eq(2L),eq(true));
        mvc.perform(auth(ruleCreate(UUID.randomUUID().toString()).param("reviewed","true"),admin).with(csrf())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_scoped_reply_rules WHERE business_id=?",Integer.class,businessA)).isZero();
    }
    @Test void replyRuleUiStaysHiddenWhenRolloutIsOff()throws Exception {
        flags.setUiSelector(false);mvc.perform(auth(rulePage(),admin)).andExpect(status().isNotFound());
    }
    private void approveReplySource(){
        assertThat(sources.reviseExpected(businessA,com.example.panel.support.AiControlIdentityFixture.login(jdbc,admin),KNOWLEDGE,KEY,sourceA.id(),1,sourceA.title(),sourceA.body(),APPROVED)).isTrue();
    }
    @Test void realIngestionPersistsPublicHistoryAndItsEventInOneTransactionWithoutDuplicateReplay() {
        long owner=origin.ticketUserId();
        long channel=jdbc.queryForObject("INSERT INTO channels(token,channel_name,platform,is_active) VALUES(?,'fixture','telegram',TRUE) RETURNING id",Long.class,"fake-r313-"+owner);
        jdbc.update("INSERT INTO messages(group_msg_id,user_id,ticket_id,channel_id,problem) VALUES(?,?,?,?,'fixture')",owner,owner,origin.ticketId(),channel);
        jdbc.update("UPDATE tickets SET group_msg_id=?,channel_id=? WHERE user_id=? AND ticket_id=?",owner,channel,owner,origin.ticketId());
        var event=new com.example.panel.service.integration.InboundClientMessageEvent(UUID.randomUUID().toString(),"client_message.active_ticket","telegram",channel,origin.ticketId(),owner,"fixture","fixture","Fixture","New question","text",null,null,"88",null,null,java.time.OffsetDateTime.now());
        incoming.ingest(event,"integration.inbound.telegram");incoming.ingest(event,"integration.inbound.telegram");
        var history=jdbc.queryForList("SELECT id,ticket_owner_user_id,ticket_public_id FROM chat_history WHERE ticket_id=?",origin.ticketId());
        assertThat(history).hasSize(1);assertThat(history.get(0)).containsEntry("ticket_owner_user_id",owner).containsEntry("ticket_public_id",origin.ticketPublicId());
        var captured=jdbc.queryForMap("SELECT incoming_history_id,ticket_user_id,ticket_public_id,ticket_business_id,message_text FROM ui_event_outbox WHERE ticket_id=? AND event_type='client_message_created'",origin.ticketId());
        assertThat(captured).containsEntry("incoming_history_id",history.get(0).get("id")).containsEntry("ticket_user_id",owner)
            .containsEntry("ticket_public_id",origin.ticketPublicId()).containsEntry("ticket_business_id",businessA).containsEntry("message_text","New question");
    }
    private MockHttpServletRequestBuilder rulePage(){return get("/business-ai-rules").param("businessId",Long.toString(businessA)).param("kind","KNOWLEDGE").param("key",KEY);}
    private MockHttpServletRequestBuilder ruleCreate(String rule){return post("/business-ai-rules/create").param("businessId",Long.toString(businessA)).param("kind","KNOWLEDGE").param("key",KEY).param("sourceId",Long.toString(sourceA.id())).param("sourceRevision","2").param("ruleKey",rule).param("trigger","Точный вопрос?");}

    @ParameterizedTest @ValueSource(strings={"VIEWER","OPERATOR","MANAGER"})
    void nonConfigureRolesCanReadButCannotOpenCreateOrSubmitMutations(String role) throws Exception {
        String identity=switch(role) { case "VIEWER"->viewer; case "OPERATOR"->operator; default->manager; };
        mvc.perform(auth(edit(sourceA),identity)).andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"sourceReadOnly\"")))
            .andExpect(content().string(not(containsString("data-ai-source-form"))));
        mvc.perform(auth(get(ROOT+"/new").param("businessId",Long.toString(businessA)),identity)).andExpect(status().isForbidden());
        mvc.perform(auth(create("new"),identity).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(auth(revise(sourceA),identity).with(csrf())).andExpect(status().isForbidden());
        assertUnchanged(sourceA);
    }

    @Test
    void pageAuthorityAnonymousAndCsrfUseTheActualSecurityFilterChain() throws Exception {
        mvc.perform(get(ROOT)).andExpect(status().is3xxRedirection());
        String roleOnly=admin+"-role-only",dialogsOnly=admin+"-dialogs-only";long roleId=account(roleOnly),dialogsId=account(dialogsOnly);
        jdbc.update("DELETE FROM user_authorities WHERE user_id IN (?,?)",roleId,dialogsId);
        jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(?,'ROLE_ADMIN'),(?,'PAGE_DIALOGS')",roleId,dialogsId);
        mvc.perform(auth(get(ROOT),roleOnly)).andExpect(status().isForbidden());
        mvc.perform(auth(create("no-page"),dialogsOnly).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(auth(create("no-csrf"),admin)).andExpect(redirectedUrl("/error/403"));
        mvc.perform(auth(create("bad-csrf"),admin).with(csrf()).with(r->{r.setParameter("_csrf","invalid");return r;}))
            .andExpect(redirectedUrl("/error/403"));
        assertThat(sources.find(businessA,com.example.panel.support.AiControlIdentityFixture.login(jdbc,admin),KNOWLEDGE,"no-csrf")).isEmpty();
        assertUnchanged(sourceA);
    }

    @ParameterizedTest @ValueSource(strings={"foreign","global","missing"})
    void pageOrGlobalAdminAuthorityDoesNotGrantBusinessData(String actor) throws Exception {
        String identity=actor.equals("foreign")?foreign:actor.equals("global")?global:"absent-canonical-account";
        if(actor.equals("missing")) {
            var principal=new com.example.panel.security.PanelUserPrincipal(Long.MAX_VALUE,identity,"unused",true,List.of(new SimpleGrantedAuthority("PAGE_KNOWLEDGE_BASE")));
            for(var request:List.of(get(ROOT).param("businessId",Long.toString(businessA)),edit(sourceA),create("denied").with(csrf()),revise(sourceA).with(csrf())))mvc.perform(request.with(user(principal))).andExpect(redirectedUrl("http://localhost/login"));
            org.mockito.Mockito.verify(sources,org.mockito.Mockito.never()).availableBusinesses(org.mockito.ArgumentMatchers.any(org.springframework.security.core.Authentication.class));assertUnchanged(sourceA);return;
        }
        mvc.perform(auth(get(ROOT).param("businessId",Long.toString(businessA)),identity)).andExpect(status().isForbidden());
        mvc.perform(auth(edit(sourceA),identity)).andExpect(status().isForbidden());
        mvc.perform(auth(create("denied"),identity).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(auth(revise(sourceA),identity).with(csrf())).andExpect(status().isForbidden());
        assertUnchanged(sourceA);
    }

    @Test
    void actualRenderedFormTokenCreatesDraftThenApprovalAndEditReloadPersistNewVersions() throws Exception {
        var opened=mvc.perform(auth(get(ROOT+"/new").param("businessId",Long.toString(businessA)),admin)).andExpect(status().isOk()).andReturn();
        String form=opened.getResponse().getContentAsString();
        var cookieJar=new LinkedHashMap<String,Cookie>();
        Cookie[] cookies=captureCookies(cookieJar,opened);
        assertThat(cookieJar).containsKey("XSRF-TOKEN");
        String token=hidden(form,"_csrf");
        String key=hidden(form,"key");
        var created=mvc.perform(auth(create(key),admin).cookie(cookies).param("_csrf",token)
                .param("status","APPROVED").param("kind","MEMORY").param("actor",foreign))
            .andExpect(status().is3xxRedirection()).andReturn();
        Source saved=sources.find(businessA,com.example.panel.support.AiControlIdentityFixture.login(jdbc,admin),KNOWLEDGE,key).orElseThrow();
        assertThat(saved.status()).isEqualTo(ScopedAiSourceService.Status.DRAFT);
        assertThat(saved.revision()).isEqualTo(1);
        assertThat(sources.history(businessA,com.example.panel.support.AiControlIdentityFixture.login(jdbc,admin),KNOWLEDGE,key,1).get(0)).containsEntry("actor",admin);
        String location=created.getResponse().getRedirectedUrl();
        cookies=captureCookies(cookieJar,created);
        var rendered=mvc.perform(auth(get(URI.create(location)),admin).cookie(cookies))
            .andExpect(status().isOk()).andReturn();
        String html=rendered.getResponse().getContentAsString();
        assertThat(hidden(html,"sourceId")).isEqualTo(Long.toString(saved.id()));
        assertThat(hidden(html,"revision")).isEqualTo("1");
        assertThat(html).contains("&lt;/textarea&gt;&lt;script&gt;window.pwned=1&lt;/script&gt;")
            .doesNotContain("<script>window.pwned=1");
        assertThat(html.indexOf("data-ai-source-draft")).isGreaterThan(0).isLessThan(html.indexOf("data-ai-source-approve"));
        cookies=captureCookies(cookieJar,rendered);
        var reviewed=mvc.perform(auth(revise(saved),admin).cookie(cookies).param("_csrf",hidden(html,"_csrf")))
            .andExpect(redirectedUrl(location)).andReturn();
        Source approved=sources.find(businessA,com.example.panel.support.AiControlIdentityFixture.login(jdbc,admin),KNOWLEDGE,key).orElseThrow();
        assertThat(approved.status()).isEqualTo(APPROVED);
        assertThat(approved.revision()).isEqualTo(2);
        cookies=captureCookies(cookieJar,reviewed);
        var reopened=mvc.perform(auth(get(URI.create(location)),admin).cookie(cookies)).andExpect(status().isOk()).andReturn();
        cookies=captureCookies(cookieJar,reopened);
        mvc.perform(auth(replace(revise(approved),"status","DRAFT"),admin).cookie(cookies)
            .param("_csrf",hidden(reopened.getResponse().getContentAsString(),"_csrf"))).andExpect(redirectedUrl(location));
        Source draft=sources.find(businessA,com.example.panel.support.AiControlIdentityFixture.login(jdbc,admin),KNOWLEDGE,key).orElseThrow();
        assertThat(draft.revision()).isEqualTo(3);
        assertThat(draft.status()).isEqualTo(ScopedAiSourceService.Status.DRAFT);
        Files.writeString(Path.of("..","..","r132-ai-source-preview.html"),html);
    }

    @Test
    void opaqueKeysSurviveRedirectsAndStaleRevisionOrCrossBusinessFormCannotOverwrite() throws Exception {
        var result=mvc.perform(auth(revise(sourceA),admin).with(csrf())).andExpect(status().is3xxRedirection()).andReturn();
        assertThat(result.getResponse().getRedirectedUrl()).contains("key=%20opaque%2F").doesNotContain("uiPreferences","PRIVATE_A");
        mvc.perform(auth(get(URI.create(result.getResponse().getRedirectedUrl())),admin)).andExpect(status().isOk());
        mvc.perform(auth(revise(sourceA),admin).with(csrf())).andExpect(status().isConflict());
        mvc.perform(auth(replace(revise(sourceA),"businessId",Long.toString(businessB)),all).with(csrf())).andExpect(status().isConflict());
        assertUnchanged(sourceB);
        assertThat(sources.find(businessA,com.example.panel.support.AiControlIdentityFixture.login(jdbc,admin),KNOWLEDGE,KEY).orElseThrow().revision()).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(strings={"revoke","disabled","blocked","collision","archive"})
    void staleAuthenticatedSessionsLoseCatalogAndWritesOnTheNextSqlOperation(String mutation) throws Exception {
        mvc.perform(auth(edit(sourceA),admin)).andExpect(status().isOk());
        mutate(mutation);
        // R189 rejects a disabled/blocked account before the source controller; other denials remain403.
        var denied=List.of("disabled","blocked").contains(mutation)?redirectedUrl("http://localhost/login"):status().isForbidden();
        mvc.perform(auth(edit(sourceA),admin)).andExpect(denied);
        mvc.perform(auth(create("late"),admin).with(csrf())).andExpect(denied);
        mvc.perform(auth(revise(sourceA),admin).with(csrf())).andExpect(denied);
        assertUnchanged(sourceA);
    }

    @ParameterizedTest @ValueSource(strings={"revoke","disabled","blocked","archive","recreated"})
    void finalMutationReprovesAuthorityAndSourceIdentityAfterTheControllerPrecheck(String mutation) throws Exception {
        doAnswer(call->{
            boolean allowed=(Boolean)call.callRealMethod();
            if (mutation.equals("recreated")) {
                jdbc.update("DELETE FROM ai_scoped_sources WHERE id=?",sourceA.id());
                assertThat(sources.createKnowledge(businessA,com.example.panel.support.AiControlIdentityFixture.login(jdbc,admin),KEY,"Replacement","Replacement body")).isTrue();
            } else mutate(mutation);
            return allowed;
        }).when(sources).canConfigure(eq(businessA),org.mockito.ArgumentMatchers.argThat((org.springframework.security.core.Authentication actor)->actor!=null && actor.getPrincipal() instanceof com.example.panel.security.PanelUserPrincipal p && p.getUserId()==adminId && admin.equals(p.getUsername())));
        mvc.perform(auth(revise(sourceA),admin).with(csrf())).andExpect(status().isConflict());
        var row=jdbc.queryForMap("SELECT title,body,revision FROM ai_scoped_sources WHERE business_id=? AND source_key=?",businessA,KEY);
        assertThat(row).containsEntry("revision",1L).containsEntry("body",mutation.equals("recreated")?"Replacement body":BODY);
    }

    @Test
    void memoryCanBeReviewedButOriginChangeClosesExistingPageAndMutation() throws Exception {
        assertThat(sources.createMemory(origin,com.example.panel.support.AiControlIdentityFixture.login(jdbc,admin),"memory","Memory title","Curated solution")).isTrue();
        Source memory=sources.find(businessA,com.example.panel.support.AiControlIdentityFixture.login(jdbc,admin),MEMORY,"memory").orElseThrow();
        mvc.perform(auth(edit(memory),admin)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Решение из обращения")))
            .andExpect(content().string(not(containsString(origin.ticketId()))));
        mvc.perform(auth(revise(memory),admin).with(csrf())).andExpect(status().is3xxRedirection());
        jdbc.update("UPDATE tickets SET business_id=? WHERE user_id=? AND ticket_id=?",businessB,origin.ticketUserId(),origin.ticketId());
        mvc.perform(auth(edit(memory),admin)).andExpect(status().isNotFound());
        mvc.perform(auth(replace(revise(memory),"revision","2"),admin).with(csrf())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT revision FROM ai_scoped_sources WHERE id=?",Long.class,memory.id())).isEqualTo(2);
    }

    @Test
    void catalogPaginationAndFiltersKeepOneBusinessAndDoNotShowDraftBody() throws Exception {
        for (int i=0;i<27;i++) assertThat(sources.createKnowledge(businessA,com.example.panel.support.AiControlIdentityFixture.login(jdbc,admin),"page-"+i,"Page title "+i,"Not catalog body")).isTrue();
        var first=mvc.perform(auth(get(ROOT).param("businessId",Long.toString(businessA)).param("q","Page title"),viewer))
            .andExpect(status().isOk()).andReturn();
        String html=first.getResponse().getContentAsString();
        assertThat(html.split("data-ai-source-row",-1)).hasSize(26);
        assertThat(html).contains("data-ai-source-next").doesNotContain("Not catalog body","Title B secret");
        mvc.perform(auth(get(ROOT).param("businessId",Long.toString(businessA)).param("status","APPROVED"),viewer))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Материалы не найдены")));
    }

    @ParameterizedTest @ValueSource(strings={"foundation","selector"})
    void existingFlagsCloseAllNewEntrypointsWithoutChangingTheirValuesInRuntime(String disabled) throws Exception {
        if (disabled.equals("foundation")) flags.getFoundation().setEnabled(false); else flags.setUiSelector(false);
        mvc.perform(auth(get(ROOT),admin)).andExpect(status().isNotFound());
        mvc.perform(auth(edit(sourceA),admin)).andExpect(status().isNotFound());
        mvc.perform(auth(get(ROOT+"/new").param("businessId",Long.toString(businessA)),admin)).andExpect(status().isNotFound());
        mvc.perform(auth(create("disabled"),admin).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(auth(revise(sourceA),admin).with(csrf())).andExpect(status().isNotFound());
        assertUnchanged(sourceA);
    }

    @Test
    void malformedBoundsEnumsAndContentAreRejectedWithoutWrites() throws Exception {
        mvc.perform(auth(get(ROOT).param("businessId","all"),admin)).andExpect(status().isBadRequest());
        mvc.perform(auth(get(ROOT).param("businessId",Long.toString(businessA)).param("before","0"),admin)).andExpect(status().isBadRequest());
        mvc.perform(auth(get(ROOT).param("businessId",Long.toString(businessA)).param("kind","GLOBAL"),admin)).andExpect(status().isBadRequest());
        mvc.perform(auth(replace(create("invalid"),"title"," "),admin).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(auth(replace(revise(sourceA),"sourceId","0"),admin).with(csrf())).andExpect(status().isBadRequest());
        assertUnchanged(sourceA);
    }

    @Test
    void renderedErasureFormRequiresExplicitConfirmationAndRealCsrfAndClearsOnlyOneBusiness() throws Exception {
        var opened=mvc.perform(auth(edit(sourceA),admin)).andExpect(status().isOk()).andReturn();
        String html=opened.getResponse().getContentAsString();
        var form=Pattern.compile("<form[^>]*data-ai-source-erase-form[^>]*>([\\s\\S]*?)</form>").matcher(html);
        assertThat(form.find()).isTrue();
        String fields=form.group(1);
        assertThat(fields).contains("name=\"confirmed\"","required","Восстановить их из панели будет нельзя");
        var before=sourceRows();
        mvc.perform(auth(replace(erase(sourceA),"confirmed","false"),admin).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(auth(erase(sourceA).with(r->{r.removeParameter("confirmed");return r;}),admin).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(auth(erase(sourceA),admin)).andExpect(redirectedUrl("/error/403"));
        assertThat(sourceRows()).isEqualTo(before);
        mvc.perform(auth(erase(sourceA),admin).cookie(opened.getResponse().getCookies()).param("_csrf",hidden(fields,"_csrf")))
            .andExpect(redirectedUrl(ROOT+"?businessId="+businessA))
            .andExpect(header().string("Cache-Control",containsString("no-store")));
        var cleared=sources.find(businessA,com.example.panel.support.AiControlIdentityFixture.login(jdbc,admin),KNOWLEDGE,KEY).orElseThrow();
        assertThat(cleared.title()).isEqualTo("Материал очищен");
        assertThat(cleared.body()).isEqualTo("Содержимое очищено.");
        assertThat(cleared.status()).isEqualTo(ScopedAiSourceService.Status.ARCHIVED);
        assertThat(cleared.revision()).isEqualTo(2);
        assertThat(sources.find(businessB,com.example.panel.support.AiControlIdentityFixture.login(jdbc,foreign),KNOWLEDGE,KEY)).contains(sourceB);
        mvc.perform(auth(edit(cleared),admin)).andExpect(status().isOk()).andExpect(content().string(not(containsString("PRIVATE_A"))));
        var after=sourceRows();
        mvc.perform(auth(erase(sourceA),admin).with(csrf())).andExpect(status().isConflict());
        mvc.perform(auth(revise(sourceA),admin).with(csrf())).andExpect(status().isConflict());
        assertThat(sourceRows()).isEqualTo(after);
    }

    @ParameterizedTest @ValueSource(strings={"viewer","operator","manager","foreign","global","all"})
    void erasureUsesTheBusinessAdminMatrix(String actor) throws Exception {
        String identity=switch(actor) {case "viewer"->viewer;case "operator"->operator;case "manager"->manager;case "foreign"->foreign;case "global"->global;default->all;};
        var before=sourceRows();
        mvc.perform(auth(erase(sourceA),identity).with(csrf()))
            .andExpect(actor.equals("all")?status().is3xxRedirection():status().isForbidden());
        if (!actor.equals("all")) assertThat(sourceRows()).isEqualTo(before);
        mvc.perform(auth(edit(sourceB),foreign)).andExpect(status().isOk());
    }

    @Test
    void erasureIsAbsentForReadOnlyAndNewFormsAndCannotBeRetargetedByAnAllAdmin() throws Exception {
        mvc.perform(auth(edit(sourceA),viewer)).andExpect(status().isOk()).andExpect(content().string(not(containsString("data-ai-source-erase"))));
        mvc.perform(auth(get(ROOT+"/new").param("businessId",Long.toString(businessA)),admin)).andExpect(status().isOk()).andExpect(content().string(not(containsString("data-ai-source-erase"))));
        var before=sourceRows();
        mvc.perform(auth(replace(erase(sourceA),"businessId",Long.toString(businessB)),all).with(csrf())).andExpect(status().isConflict());
        mvc.perform(auth(replace(erase(sourceA),"sourceId",Long.toString(sourceB.id())),all).with(csrf())).andExpect(status().isConflict());
        mvc.perform(auth(replace(erase(sourceA),"revision","0"),all).with(csrf())).andExpect(status().isBadRequest());
        assertThat(sourceRows()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings={"revoke","disabled","blocked","archive","collision"})
    void erasureDeniesRevocationBetweenControllerPrecheckAndFinalSql(String mutation) throws Exception {
        var before=sourceRows();
        doAnswer(call->{mutate(mutation);return call.callRealMethod();}).when(sources).eraseContentExpected(eq(businessA),
            org.mockito.ArgumentMatchers.any(org.springframework.security.core.Authentication.class),eq(KNOWLEDGE),eq(KEY),eq(sourceA.id()),eq(1L));
        mvc.perform(auth(erase(sourceA),admin).with(csrf())).andExpect(status().isConflict());
        assertThat(sourceRows()).isEqualTo(before);
    }

    @Test
    void existingMemoryFormCanEraseContentAfterOriginMovesWithoutReadingTheNewBusiness() throws Exception {
        assertThat(sources.createMemory(origin,com.example.panel.support.AiControlIdentityFixture.login(jdbc,admin),"erase-memory","Memory title","Private memory")).isTrue();
        var memory=sources.find(businessA,com.example.panel.support.AiControlIdentityFixture.login(jdbc,admin),MEMORY,"erase-memory").orElseThrow();
        mvc.perform(auth(edit(memory),admin)).andExpect(status().isOk());
        jdbc.update("UPDATE tickets SET business_id=? WHERE user_id=? AND ticket_id=?",businessB,origin.ticketUserId(),origin.ticketId());
        mvc.perform(auth(edit(memory),admin)).andExpect(status().isNotFound());
        mvc.perform(auth(erase(memory),admin).with(csrf())).andExpect(redirectedUrl(ROOT+"?businessId="+businessA));
        assertThat(jdbc.queryForMap("SELECT business_id,body,status FROM ai_scoped_sources WHERE id=?",memory.id()))
            .containsEntry("business_id",businessA).containsEntry("body","Содержимое очищено.").containsEntry("status","ARCHIVED");
        assertThat(jdbc.queryForObject("SELECT business_id FROM tickets WHERE user_id=? AND ticket_id=?",Long.class,origin.ticketUserId(),origin.ticketId())).isEqualTo(businessB);
    }

    @ParameterizedTest @ValueSource(strings={"foundation","selector"})
    void erasureRespectsTheExistingFeatureGates(String disabled) throws Exception {
        if (disabled.equals("foundation")) flags.getFoundation().setEnabled(false); else flags.setUiSelector(false);
        var before=sourceRows();
        mvc.perform(auth(erase(sourceA),admin).with(csrf())).andExpect(status().isNotFound());
        assertThat(sourceRows()).isEqualTo(before);
    }


    private Source orphanFixture(boolean move) {
        String key="orphan-"+IDS.incrementAndGet();
        var actor=com.example.panel.support.AiControlIdentityFixture.login(jdbc,admin);
        assertThat(sources.createMemory(origin,actor,key,"ORPHAN_PRIVATE_TITLE",BODY)).isTrue();
        var draft=sources.find(businessA,actor,MEMORY,key).orElseThrow();
        assertThat(sources.reviseExpected(businessA,actor,MEMORY,key,draft.id(),1,"ORPHAN_PRIVATE_TITLE",BODY,APPROVED)).isTrue();
        var memory=sources.find(businessA,actor,MEMORY,key).orElseThrow();
        if(move)jdbc.update("UPDATE tickets SET business_id=? WHERE user_id=? AND ticket_id=?",businessB,origin.ticketUserId(),origin.ticketId());
        return memory;
    }

    private MockHttpServletRequestBuilder orphanErase(Source memory) {
        return post(ROOT+"/erase-orphan-content").param("businessId",memory.businessId().toString())
            .param("key",memory.key()).param("sourceId",Long.toString(memory.id()))
            .param("revision",Long.toString(memory.revision())).param("confirmed","true");
    }

    @Test void orphanCatalogRedactsContentAndRenderedCsrfFormErasesThenReloads()throws Exception {
        Source memory=orphanFixture(true);
        mvc.perform(auth(get(ROOT).param("businessId",Long.toString(businessA)),admin))
            .andExpect(content().string(containsString("data-ai-source-orphans")));
        mvc.perform(auth(get(ROOT).param("businessId",Long.toString(businessA)),viewer))
            .andExpect(content().string(not(containsString("data-ai-source-orphans"))));
        var opened=mvc.perform(auth(get(ROOT+"/orphans").param("businessId",Long.toString(businessA)),admin))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control",containsString("no-store")))
            .andExpect(content().string(containsString("data-ai-orphan-erase"))).andReturn();
        String html=opened.getResponse().getContentAsString();
        assertThat(html).doesNotContain(BODY,"ORPHAN_PRIVATE_TITLE",origin.ticketId(),origin.ticketPublicId(),"PRIVATE_B_SECRET","Business B secret");
        assertThat(hidden(html,"sourceId")).isEqualTo(Long.toString(memory.id()));
        assertThat(hidden(html,"key")).isEqualTo(memory.key());
        assertThat(hidden(html,"revision")).isEqualTo("2");
        var roots=jdbc.queryForList("SELECT to_jsonb(t)::TEXT FROM tickets t WHERE user_id=?",String.class,origin.ticketUserId());
        mvc.perform(auth(orphanErase(memory),admin).cookie(opened.getResponse().getCookies()).param("_csrf",hidden(html,"_csrf")))
            .andExpect(redirectedUrl(ROOT+"/orphans?businessId="+businessA))
            .andExpect(header().string("Cache-Control",containsString("no-store")));
        assertThat(jdbc.queryForMap("SELECT title,body,status,revision FROM ai_scoped_sources WHERE id=?",memory.id()))
            .containsEntry("title","Материал очищен").containsEntry("body","Содержимое очищено.").containsEntry("status","ARCHIVED").containsEntry("revision",3L);
        assertThat(jdbc.queryForList("SELECT to_jsonb(t)::TEXT FROM tickets t WHERE user_id=?",String.class,origin.ticketUserId())).isEqualTo(roots);
        mvc.perform(auth(get(ROOT+"/orphans").param("businessId",Long.toString(businessA)),admin))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Содержимое уже очищено.")))
            .andExpect(content().string(not(containsString("data-ai-orphan-erase"))));
        mvc.perform(auth(orphanErase(memory),admin).with(csrf())).andExpect(status().isConflict());
        assertThat(jdbc.queryForList("SELECT revision FROM ai_scoped_source_events WHERE source_id=? ORDER BY revision",Long.class,memory.id())).containsExactly(1L,2L,3L);
    }

    @ParameterizedTest @ValueSource(strings={"viewer","operator","manager","foreign","global","all"})
    void orphanRecoveryUsesBusinessAdminPolicyNotSystemOrReadRole(String role)throws Exception {
        Source memory=orphanFixture(true);
        String identity=switch(role){case"viewer"->viewer;case"operator"->operator;case"manager"->manager;case"foreign"->foreign;case"global"->global;default->all;};
        var before=sourceRows();
        if(role.equals("all")){
            mvc.perform(auth(get(ROOT+"/orphans").param("businessId",Long.toString(businessA)),identity)).andExpect(status().isOk());
            mvc.perform(auth(orphanErase(memory),identity).with(csrf())).andExpect(status().is3xxRedirection());
        }else{
            mvc.perform(auth(get(ROOT+"/orphans").param("businessId",Long.toString(businessA)),identity)).andExpect(status().isForbidden());
            mvc.perform(auth(orphanErase(memory),identity).with(csrf())).andExpect(status().isForbidden());
            assertThat(sourceRows()).isEqualTo(before);
        }
    }

    @Test void orphanFormsRequireCsrfConfirmationFrozenSourceAndStillLostOrigin()throws Exception {
        Source memory=orphanFixture(false);
        var before=sourceRows();
        mvc.perform(auth(orphanErase(memory),admin).with(csrf())).andExpect(status().isConflict());
        jdbc.update("UPDATE tickets SET business_id=? WHERE user_id=?",businessB,origin.ticketUserId());
        mvc.perform(auth(orphanErase(memory),admin)).andExpect(redirectedUrl("/error/403"));
        mvc.perform(auth(replace(orphanErase(memory),"confirmed","false"),admin).with(csrf())).andExpect(status().isBadRequest());
        for(var request:List.of(replace(orphanErase(memory),"sourceId",Long.toString(sourceA.id())),
                replace(orphanErase(memory),"revision","1"),replace(orphanErase(memory),"key","other-key"),
                replace(orphanErase(memory),"businessId",Long.toString(businessB))))
            mvc.perform(auth(request,all).with(csrf())).andExpect(status().isConflict());
        mvc.perform(auth(get(ROOT+"/orphans").param("businessId",Long.toString(businessA)).param("before","0"),admin)).andExpect(status().isBadRequest());
        assertThat(sourceRows()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings={"foundation","selector"})
    void orphanRoutesRemainBehindExistingRolloutFlags(String disabled)throws Exception {
        Source memory=orphanFixture(true);var before=sourceRows();
        if(disabled.equals("foundation"))flags.getFoundation().setEnabled(false);else flags.setUiSelector(false);
        mvc.perform(auth(get(ROOT+"/orphans").param("businessId",Long.toString(businessA)),admin)).andExpect(status().isNotFound());
        mvc.perform(auth(orphanErase(memory),admin).with(csrf())).andExpect(status().isNotFound());
        assertThat(sourceRows()).isEqualTo(before);
    }

    @Test void restoredOriginAfterPrecheckRejectsOrphanEraseWithoutDataChange()throws Exception {
        Source memory=orphanFixture(true);var before=sourceRows();
        doAnswer(call->{jdbc.update("UPDATE tickets SET business_id=? WHERE user_id=?",businessA,origin.ticketUserId());return true;})
            .when(sources).canConfigure(eq(businessA),org.mockito.ArgumentMatchers.any(org.springframework.security.core.Authentication.class));
        mvc.perform(auth(orphanErase(memory),admin).with(csrf())).andExpect(status().isConflict());
        assertThat(sourceRows()).isEqualTo(before);
    }

    @Test void orphanCatalogPaginatesTwentyFiveAndPostsExactLargeId()throws Exception {
        jdbc.execute("ALTER SEQUENCE ai_scoped_sources_id_seq RESTART WITH 9007199254740993");
        var actor=com.example.panel.support.AiControlIdentityFixture.login(jdbc,admin);
        var ids=new java.util.ArrayList<Long>();
        for(int i=0;i<26;i++){String key="orphan-page-"+i;assertThat(sources.createMemory(origin,actor,key,"PAGE_PRIVATE_TITLE",BODY)).isTrue();ids.add(sources.find(businessA,actor,MEMORY,key).orElseThrow().id());}
        jdbc.update("DELETE FROM tickets WHERE user_id=?",origin.ticketUserId());
        var first=mvc.perform(auth(get(ROOT+"/orphans").param("businessId",Long.toString(businessA)),admin)).andExpect(status().isOk()).andReturn();
        String html=first.getResponse().getContentAsString();
        assertThat(html.split("data-ai-orphan-row",-1).length-1).isEqualTo(25);
        assertThat(html).contains("data-ai-orphan-next").doesNotContain("PAGE_PRIVATE_TITLE",BODY);
        assertThat(hidden(html,"sourceId")).isEqualTo(Long.toString(ids.get(25)));
        long cursor=ids.get(1);
        var second=mvc.perform(auth(get(ROOT+"/orphans").param("businessId",Long.toString(businessA)).param("before",Long.toString(cursor)),admin))
            .andExpect(status().isOk()).andReturn();
        String last=second.getResponse().getContentAsString();
        assertThat(last.split("data-ai-orphan-row",-1).length-1).isEqualTo(1);
        assertThat(last).doesNotContain("data-ai-orphan-next");
        assertThat(hidden(last,"sourceId")).isEqualTo("9007199254740993");
        mvc.perform(auth(post(ROOT+"/erase-orphan-content").param("businessId",Long.toString(businessA))
                .param("key","orphan-page-0").param("sourceId",hidden(last,"sourceId")).param("revision","1").param("confirmed","true"),admin)
                .cookie(second.getResponse().getCookies()).param("_csrf",hidden(last,"_csrf")))
            .andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT body FROM ai_scoped_sources WHERE id=9007199254740993",String.class)).isEqualTo("Содержимое очищено.");
    }


    private MockHttpServletRequestBuilder erase(Source source) {
        return post(ROOT+"/erase-content").param("businessId",source.businessId().toString()).param("kind",source.kind().name())
            .param("key",source.key()).param("sourceId",Long.toString(source.id())).param("revision",Long.toString(source.revision())).param("confirmed","true");
    }

    // Exercise the production cookie repository and XOR handler without replacing the filter's repository,
    // which SecurityMockMvcRequestPostProcessors.csrf() does. The rendered-form test also uses an actual GET token.
    private RequestPostProcessor csrf() {
        return request->{
            CsrfToken token=CookieCsrfTokenRepository.withHttpOnlyFalse().generateToken(request);
            request.setCookies(new Cookie("XSRF-TOKEN",token.getToken()));
            new XorCsrfTokenRequestAttributeHandler().handle(request,new MockHttpServletResponse(),()->token);
            CsrfToken masked=(CsrfToken)request.getAttribute(CsrfToken.class.getName());
            request.setParameter(masked.getParameterName(),masked.getToken());
            return request;
        };
    }

    private Cookie[] captureCookies(Map<String,Cookie> jar,MvcResult result) {
        for (Cookie cookie:result.getResponse().getCookies()) {
            if (cookie.getMaxAge()==0) jar.remove(cookie.getName()); else jar.put(cookie.getName(),cookie);
        }
        return jar.values().toArray(Cookie[]::new);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request,String identity) {
        // Capture fixture ID/raw name before execution; request handling refreshes DB rights without repair.
        var ids=jdbc.queryForList("SELECT id FROM users WHERE username=?",Long.class,identity);assertThat(ids).as("exact fixture account: %s",identity).hasSize(1);
        var granted=identity.equals(global)?List.of(new SimpleGrantedAuthority("PAGE_KNOWLEDGE_BASE"),new SimpleGrantedAuthority("ROLE_ADMIN")):List.of(new SimpleGrantedAuthority("PAGE_KNOWLEDGE_BASE"));
        return request.with(user(new com.example.panel.security.PanelUserPrincipal(ids.get(0),identity,"unused",true,granted)));
    }
    private MockHttpServletRequestBuilder replace(MockHttpServletRequestBuilder request,String name,String value) {
        return request.with(r->{r.setParameter(name,value);return r;});
    }
    private MockHttpServletRequestBuilder create(String key) {
        return post(ROOT+"/create").param("businessId",Long.toString(businessA)).param("key",key).param("title","New title").param("body",BODY);
    }
    private MockHttpServletRequestBuilder edit(Source source) {
        return get(ROOT+"/edit").param("businessId",source.businessId().toString()).param("kind",source.kind().name()).param("key",source.key());
    }
    private MockHttpServletRequestBuilder revise(Source source) {
        return post(ROOT+"/revise").param("businessId",source.businessId().toString()).param("kind",source.kind().name())
            .param("key",source.key()).param("sourceId",Long.toString(source.id())).param("revision",Long.toString(source.revision()))
            .param("title",source.title()).param("body",source.body()).param("status","APPROVED");
    }
    private String hidden(String html,String name) {
        var matcher=Pattern.compile("<input[^>]*name=\""+name+"\"[^>]*value=\"([^\"]*)\"").matcher(html);
        assertThat(matcher.find()).as("rendered hidden field %s",name).isTrue();
        return HtmlUtils.htmlUnescape(matcher.group(1));
    }
    @ParameterizedTest @ValueSource(strings={"disabled","blocked","deleted","renamed","rename-reuse","delete-reuse"})
    void preparedBoundSourceRequestsCannotSurviveAccountRevocation(String change)throws Exception {
        var requests=preparedRequests();var before=sourceRows();
        switch(change){case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",adminId);case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",adminId);case "renamed","rename-reuse"->jdbc.update("UPDATE users SET username=? WHERE id=?","renamed-"+admin,adminId);default->jdbc.update("DELETE FROM users WHERE id=?",adminId);}
        if(change.endsWith("reuse"))membership(account(admin),businessA,"ADMIN");
        for(var request:requests)mvc.perform(request).andExpect(redirectedUrl("http://localhost/login"));
        org.mockito.Mockito.verify(sources,org.mockito.Mockito.never()).availableBusinesses(org.mockito.ArgumentMatchers.any(org.springframework.security.core.Authentication.class));
        assertThat(sourceRows()).isEqualTo(before);
        if(change.endsWith("reuse"))mvc.perform(auth(edit(sourceA),admin)).andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(strings={"unbound","foreign-id","case-changed","padded"})
    void invalidPrincipalsCannotReachSourceControllers(String kind)throws Exception {
        var granted=List.of(new SimpleGrantedAuthority("PAGE_KNOWLEDGE_BASE"));
        long id=kind.equals("foreign-id")?jdbc.queryForObject("SELECT id FROM users WHERE username=?",Long.class,foreign):adminId;
        String name=kind.equals("case-changed")?admin.toUpperCase(java.util.Locale.ROOT):kind.equals("padded")?" "+admin+" ":admin;
        var principal=new com.example.panel.security.PanelUserPrincipal(id,name,"unused",true,granted);
        RequestPostProcessor identity=kind.equals("unbound")?user(admin).authorities(granted):user(principal);var before=sourceRows();
        for(var request:List.of(get(ROOT).param("businessId",Long.toString(businessA)),edit(sourceA),get(ROOT+"/new").param("businessId",Long.toString(businessA)),create("mismatched-principal").with(csrf()),revise(sourceA).with(csrf()),erase(sourceA).with(csrf())))mvc.perform(request.with(identity)).andExpect(redirectedUrl("http://localhost/login"));
        org.mockito.Mockito.verify(sources,org.mockito.Mockito.never()).availableBusinesses(org.mockito.ArgumentMatchers.any(org.springframework.security.core.Authentication.class));assertThat(sourceRows()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"removed","role-only"})
    void preparedRequestsLoseTheDbPageRightWithoutFixtureRepair(String mode)throws Exception {
        var requests=preparedRequests();var before=sourceRows();jdbc.update("DELETE FROM user_authorities WHERE user_id=? AND authority='PAGE_KNOWLEDGE_BASE'",adminId);
        if(mode.equals("role-only"))jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(?,'ROLE_ADMIN')",adminId);
        for(var request:requests)mvc.perform(request).andExpect(status().isForbidden());
        org.mockito.Mockito.verify(sources,org.mockito.Mockito.never()).availableBusinesses(org.mockito.ArgumentMatchers.any(org.springframework.security.core.Authentication.class));assertThat(sourceRows()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_authorities WHERE user_id=? AND authority='PAGE_KNOWLEDGE_BASE'",Integer.class,adminId)).isZero();
    }
    @Test void realJdbcLoginCreatesReviewsReloadsAndThenLosesSourcePageAccess()throws Exception {
        String password="fixture source HTTP session R214";jdbc.update("UPDATE users SET password=? WHERE id=?",new com.example.panel.security.LegacyCompatiblePasswordEncoder().encode(password),adminId);
        var login=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        var signedIn=mvc.perform(post("/login").cookie(login.getResponse().getCookies()).param("_csrf",hidden(login.getResponse().getContentAsString(),"_csrf")).param("username",admin.toUpperCase(java.util.Locale.ROOT)).param("password",password)).andExpect(redirectedUrl("/post-login")).andReturn();
        var session=signedIn.getResponse().getCookie("SESSION");assertThat(session).as("real JDBC session cookie").isNotNull();
        var cookies=new LinkedHashMap<String,Cookie>();remember(cookies,login);remember(cookies,signedIn);
        var form=mvc.perform(get(ROOT+"/new").param("businessId",Long.toString(businessA)).cookie(cookies.values().toArray(Cookie[]::new))).andExpect(status().isOk()).andReturn();remember(cookies,form);
        String html=form.getResponse().getContentAsString(),key=hidden(html,"key");
        var created=mvc.perform(create(key).cookie(cookies.values().toArray(Cookie[]::new)).param("_csrf",hidden(html,"_csrf"))).andExpect(status().is3xxRedirection()).andExpect(header().string("Cache-Control",containsString("no-store"))).andReturn();remember(cookies,created);
        var draft=sources.find(businessA,com.example.panel.support.AiControlIdentityFixture.login(jdbc,admin),KNOWLEDGE,key).orElseThrow();assertThat(draft.status()).isEqualTo(ScopedAiSourceService.Status.DRAFT);
        var opened=mvc.perform(get(URI.create(created.getResponse().getRedirectedUrl())).cookie(cookies.values().toArray(Cookie[]::new))).andExpect(status().isOk()).andExpect(content().string(not(containsString("PRIVATE_B_SECRET")))).andReturn();remember(cookies,opened);
        var reviewed=mvc.perform(revise(draft).cookie(cookies.values().toArray(Cookie[]::new)).param("_csrf",hidden(opened.getResponse().getContentAsString(),"_csrf"))).andExpect(status().is3xxRedirection()).andReturn();remember(cookies,reviewed);
        var approved=sources.find(businessA,com.example.panel.support.AiControlIdentityFixture.login(jdbc,admin),KNOWLEDGE,key).orElseThrow();assertThat(approved.status()).isEqualTo(APPROVED);assertThat(approved.revision()).isEqualTo(2);
        assertThat(jdbc.queryForMap("SELECT updated_by_user_id,updated_by FROM ai_scoped_sources WHERE id=?",approved.id())).containsEntry("updated_by_user_id",adminId).containsEntry("updated_by",admin);
        assertThat(jdbc.queryForList("SELECT actor_user_id FROM ai_scoped_source_events WHERE source_id=? ORDER BY revision",Long.class,approved.id())).containsExactly(adminId,adminId);
        var reloaded=mvc.perform(get(URI.create(reviewed.getResponse().getRedirectedUrl())).cookie(cookies.values().toArray(Cookie[]::new))).andExpect(status().isOk()).andReturn();remember(cookies,reloaded);
        var before=sourceRows();jdbc.update("DELETE FROM user_authorities WHERE user_id=? AND authority='PAGE_KNOWLEDGE_BASE'",adminId);
        mvc.perform(edit(approved).cookie(cookies.values().toArray(Cookie[]::new))).andExpect(status().isForbidden());
        mvc.perform(revise(approved).cookie(cookies.values().toArray(Cookie[]::new)).param("_csrf",hidden(reloaded.getResponse().getContentAsString(),"_csrf"))).andExpect(status().isForbidden());assertThat(sourceRows()).isEqualTo(before);
        mvc.perform(post("/logout").cookie(cookies.values().toArray(Cookie[]::new)).param("_csrf",hidden(reloaded.getResponse().getContentAsString(),"_csrf"))).andExpect(status().is3xxRedirection());
        mvc.perform(get(ROOT).cookie(session)).andExpect(redirectedUrl("http://localhost/login"));
    }
    private List<MockHttpServletRequestBuilder> preparedRequests() {
        return List.of(auth(get(ROOT).param("businessId",Long.toString(businessA)),admin),auth(edit(sourceA),admin),auth(get(ROOT+"/new").param("businessId",Long.toString(businessA)),admin),auth(create("prepared-session"),admin).with(csrf()),auth(revise(sourceA),admin).with(csrf()),auth(erase(sourceA),admin).with(csrf()),
            auth(get(ROOT+"/orphans").param("businessId",Long.toString(businessA)),admin),auth(orphanErase(sourceA),admin).with(csrf()));
    }
    private Map<String,List<String>> sourceRows() {
        return Map.of("sources",jdbc.queryForList("SELECT to_jsonb(s)::TEXT FROM ai_scoped_sources s WHERE business_id IN (?,?) ORDER BY id",String.class,businessA,businessB),"events",jdbc.queryForList("SELECT to_jsonb(s)::TEXT FROM ai_scoped_source_events s WHERE business_id IN (?,?) ORDER BY id",String.class,businessA,businessB));
    }
    private void remember(LinkedHashMap<String,Cookie> jar,MvcResult result) {
        for(Cookie cookie:result.getResponse().getCookies()){if(cookie.getMaxAge()==0)jar.remove(cookie.getName());else jar.put(cookie.getName(),cookie);}
    }

    private long account(String username) {
        long id=jdbc.queryForObject("INSERT INTO users(username,password,enabled) VALUES(?,'not-used',TRUE) RETURNING id",Long.class,username);
        jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(?,'PAGE_KNOWLEDGE_BASE')",id);return id;
    }
    private long business(String code,String name) {
        return jdbc.queryForObject("INSERT INTO businesses(code,display_name) VALUES(?,?) RETURNING id",Long.class,code,name);
    }
    private void membership(long userId,long businessId,String role) {
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(?,?,?)",userId,businessId,role);
    }
    private void assertUnchanged(Source source) {
        assertThat(jdbc.queryForObject("SELECT revision FROM ai_scoped_sources WHERE id=?",Long.class,source.id())).isEqualTo(source.revision());
    }
    private void mutate(String mutation) {
        switch (mutation) {
            case "revoke" -> jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=? AND business_id=?",adminId,businessA);
            case "disabled" -> jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",adminId);
            case "blocked" -> jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",adminId);
            case "collision" -> jdbc.update("INSERT INTO users(username,password,enabled) VALUES(?,'not-used',FALSE)",admin.toUpperCase(java.util.Locale.ROOT));
            case "archive" -> jdbc.update("UPDATE businesses SET status='archived' WHERE id=?",businessA);
            default -> throw new IllegalArgumentException(mutation);
        }
    }
}
