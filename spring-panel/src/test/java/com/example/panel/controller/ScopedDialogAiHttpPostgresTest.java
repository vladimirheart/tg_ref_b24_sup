package com.example.panel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.*;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.csrf.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.*;
import org.springframework.web.util.HtmlUtils;

@SpringBootTest @AutoConfigureMockMvc
class ScopedDialogAiHttpPostgresTest {

    @ParameterizedTest @ValueSource(strings={"prefix","suffix","both","tab"})
    void spacedPrincipalCannotReadCurateOrControlAnotherBusinessDialog(String variant) throws Exception {
        String forged=switch(variant){case "prefix"->" "+admin;case "suffix"->admin+" ";case "tab"->"\t"+admin+"\t";default->" "+admin+" ";};
        if(variant.equals("both"))jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(?,?,'ADMIN')",account(forged),other);
        assertThat(sources.createKnowledge(business,admin,"principal-proof","refund","private refund knowledge")).isTrue();
        assertThat(sources.revise(business,admin,ScopedAiSourceService.Kind.KNOWLEDGE,"principal-proof",1,"refund","private refund knowledge",ScopedAiSourceService.Status.APPROVED)).isTrue();
        assertThat(knowledge.record(scope,"refund")).isTrue();
        mvc.perform(auth(get(root),forged,true)).andExpect(status().isNotFound());
        mvc.perform(auth(control(""),forged,true).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(auth(memory(UUID.randomUUID().toString()),forged,true).with(csrf())).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_ai_scoped_control WHERE ticket_public_id=?",Long.class,publicId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_scoped_sources WHERE business_id=?",Long.class,business)).isEqualTo(1);
        assertThat(sources.find(business,admin,ScopedAiSourceService.Kind.KNOWLEDGE,"principal-proof").orElseThrow().revision()).isEqualTo(2);
    }
    @Test void literalSpacedPrincipalCanControlAndCurateOnlyItsOwnDialog() throws Exception {
        String original=admin,spaced=" "+admin+" ";jdbc.update("UPDATE users SET username=? WHERE id=?",spaced,adminId);
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(?,?,'ADMIN')",account(original),other);
        mvc.perform(auth(get(root),spaced,true)).andExpect(status().isOk());
        mvc.perform(auth(get(root),original,true)).andExpect(status().isNotFound());
        mvc.perform(auth(control(""),spaced,true).with(csrf())).andExpect(redirectedUrl(root));
        String key=UUID.randomUUID().toString();
        mvc.perform(auth(memory(key),spaced,true).with(csrf())).andExpect(redirectedUrl(root));
        assertThat(jdbc.queryForMap("SELECT updated_by_user_id,updated_by FROM ticket_ai_scoped_control WHERE ticket_public_id=?",publicId))
            .containsEntry("updated_by_user_id",adminId).containsEntry("updated_by",spaced);
        assertThat(jdbc.queryForMap("SELECT updated_by_user_id,updated_by FROM ai_scoped_sources WHERE business_id=? AND source_key=?",business,key))
            .containsEntry("updated_by_user_id",adminId).containsEntry("updated_by",spaced);
    }

    private static final AtomicLong IDS=new AtomicLong(9433000);
    @MockBean BotProcessService botProcessService;
    @MockBean(name="taskScheduler") TaskScheduler taskScheduler;
    @DynamicPropertySource
    static void isolatedPostgresql(DynamicPropertyRegistry registry) throws Exception {
        // Separate schema, not a readiness bypass: other HTTP fixtures retain their own data.
        var dataSource=(DriverManagerDataSource)PostgresqlJdbcTestSupport
            .freshJdbcTemplateDefaultStringType("r133_dialog_http").getDataSource();
        var config=Files.createTempDirectory("r133-dialog-config-");
        var storage=Files.createTempDirectory("r133-dialog-storage-");
        registry.add("app.datasource.mode",()->"postgresql");
        registry.add("spring.datasource.url",dataSource::getUrl);
        registry.add("spring.datasource.username",dataSource::getUsername);
        registry.add("spring.datasource.password",dataSource::getPassword);
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        registry.add("spring.flyway.locations",()->"classpath:db/migration/postgresql");
        registry.add("shared-config.dir",config::toString);
        for (String key:new String[]{"attachments","knowledge-base","passport-photos","avatars"})
            registry.add("app.storage."+key,()->storage.resolve(key).toString());
        registry.add("app.storage.object.required-for-postgresql",()->"false");
        registry.add("app.coordination.required-for-postgresql",()->"false");
        registry.add("app.bots.internal-api.token",()->"r133-test-internal-bot-api-token");
        registry.add("app.security.remember-me-key",()->"r133-test-remember-me-key");
        registry.add("app.security.bootstrap-admin.username",()->"r133_test_bootstrap_admin");
        registry.add("app.security.bootstrap-admin.password",()->"r133-test-bootstrap-password");
        registry.add("app.bots.auto-start-enabled",()->"false");
        registry.add("monitoring.credentials.master-key",()->"r133-test-monitoring-master-key");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MultibusinessFeatureFlags flags;
    @Autowired ScopedAiSourceService sources;
    @Autowired ScopedAiHandoffKnowledgeService knowledge;
    @SpyBean ScopedDialogAiPersistenceService persistence;
    @SpyBean ScopedDialogAiWorkbenchService work;
    private long business,other,adminId,qId,aId;
    private String admin,viewer,foreign,publicId,root,question,answer,raw;
    private UiEventTicketScope scope;
    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT current_schema()",String.class)).startsWith("r133_dialog_http_");
        flags.getFoundation().setEnabled(true); flags.setUiSelector(true);
        String prefix="r133-http-"+IDS.incrementAndGet();
        business=jdbc.queryForObject("INSERT INTO businesses(code,display_name) VALUES(?,'AI business') RETURNING id",Long.class,prefix);
        other=jdbc.queryForObject("INSERT INTO businesses(code,display_name) VALUES(?,'Foreign secret') RETURNING id",Long.class,prefix+"-b");
        admin=prefix+"-admin";viewer=prefix+"-viewer";foreign=prefix+"-foreign";
        adminId=account(admin); long viewerId=account(viewer),foreignId=account(foreign);
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(?,?,'ADMIN'),(?,?,'VIEWER'),(?,?,'ADMIN')",
            adminId,business,viewerId,business,foreignId,other);
        publicId=UUID.randomUUID().toString().replace("-",""); raw=" private origin "+prefix;
        long owner=IDS.incrementAndGet();
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,is_general) VALUES(?,?,?,?,FALSE)",owner,raw,publicId,business);
        scope=new UiEventTicketScope(owner,raw,publicId,"BUSINESS",business);
        qId=history(owner,"client","private <script>prompt</script>","text");
        aId=history(owner,"operator","reviewed solution","text");
        history(null,"operator","UNLINKED_SECRET","text"); history(owner,"operator","AI_SECRET","ai_agent");
        question=qId+":"+fingerprint(qId); answer=aId+":"+fingerprint(aId);
        root="/business-dialog-ai/"+publicId;
    }
    @AfterEach void reset() { flags.getFoundation().setEnabled(false); flags.setUiSelector(false); }
    @Test void renderedFormControlCasAndCuratedDraftRoundtrip() throws Exception {
        var opened=mvc.perform(auth(get(root),admin,true)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control",containsString("no-store")))
            .andExpect(content().string(containsString("data-scoped-dialog-ai")))
            .andExpect(content().string(containsString("private &lt;script&gt;prompt&lt;/script&gt;")))
            .andExpect(content().string(not(containsString("UNLINKED_SECRET"))))
            .andExpect(content().string(not(containsString("AI_SECRET"))))
            .andExpect(content().string(not(containsString(raw)))).andReturn();
        String html=opened.getResponse().getContentAsString();
        String key=hidden(html,"key"),token=hidden(html,"_csrf");
        Cookie[] cookies=opened.getResponse().getCookies();
        mvc.perform(auth(memory(key),admin,true).cookie(cookies).param("_csrf",token).param("status","APPROVED").param("actor",foreign))
            .andExpect(redirectedUrl(root));
        var draft=sources.find(business,admin,ScopedAiSourceService.Kind.MEMORY,key).orElseThrow();
        assertThat(draft.status()).isEqualTo(ScopedAiSourceService.Status.DRAFT);
        assertThat(draft.origin()).isEqualTo(scope);
        mvc.perform(auth(control(""),admin,true).with(csrf())).andExpect(redirectedUrl(root));
        var control=persistence.findControl(scope,admin).orElseThrow();
        assertThat(control.aiDisabled()).isTrue();
        mvc.perform(auth(control(""),admin,true).with(csrf())).andExpect(status().isConflict());
        mvc.perform(auth(control(control.updatedAt(),"updated"),admin,true).with(csrf())).andExpect(redirectedUrl(root));
        assertThat(persistence.findControl(scope,admin).orElseThrow().reason()).isEqualTo("updated");
        assertThat(persistence.findEvents(scope,admin,10)).extracting(e->e.get("event_type")).contains("control_updated");
    }
    @Test void viewerSeesReadOnlyAndCannotWrite() throws Exception {
        mvc.perform(auth(get(root),viewer,true)).andExpect(status().isOk())
            .andExpect(content().string(containsString("AI: только чтение")))
            .andExpect(content().string(not(containsString("data-memory-curation"))));
        mvc.perform(auth(control(""),viewer,true).with(csrf())).andExpect(status().isConflict());
        mvc.perform(auth(memory(UUID.randomUUID().toString()),viewer,true).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void pageAuthorityAndActualCsrfAreRequired() throws Exception {
        mvc.perform(get(root)).andExpect(status().is3xxRedirection());
        mvc.perform(get(root).with(user(admin).authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isForbidden());
        mvc.perform(auth(get(root),admin,false)).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("data-memory-curation"))));
        mvc.perform(auth(memory(UUID.randomUUID().toString()),admin,false).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(auth(control(""),admin,true)).andExpect(redirectedUrl("/error/403"));
        mvc.perform(auth(control(""),admin,true).with(csrf()).with(r->{r.setParameter("_csrf","bad");return r;}))
            .andExpect(redirectedUrl("/error/403"));
        assertThat(persistence.findControl(scope,admin).orElseThrow().updatedAt()).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"foreign","blocked","revoked","general","unresolved","collision","missing"})
    void deniedBusinessOrOwnerDoesNotRevealDialogData(String state) throws Exception {
        String actor=admin;
        switch(state) {
            case "foreign"->actor=foreign;
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",adminId);
            case "revoked"->jdbc.update("DELETE FROM business_memberships WHERE user_id=?",adminId);
            case "general"->jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE,general_selection_source='USER_SELECTION' WHERE ticket_public_id=?",publicId);
            case "unresolved"->jdbc.update("UPDATE tickets SET business_id=NULL WHERE ticket_public_id=?",publicId);
            case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,business_id,ticket_public_id,is_general) VALUES(?,?,?,?,FALSE)",IDS.incrementAndGet(),raw,other,UUID.randomUUID().toString().replace("-",""));
            case "missing"->{
                jdbc.update("DELETE FROM chat_history WHERE ticket_owner_user_id=? AND ticket_id=?",scope.ticketUserId(),raw);
                jdbc.update("DELETE FROM tickets WHERE ticket_public_id=?",publicId);
            }
        }
        mvc.perform(auth(get(root),actor,true)).andExpect(status().isNotFound());
        mvc.perform(auth(memory(UUID.randomUUID().toString()),actor,true).with(csrf())).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings={"owner","grant","edit","deleted"})
    void lateCurationChangesAreReprovedInsideInsert(String mutation) throws Exception {
        doAnswer(call->{
            switch(mutation) {
                case "owner"->jdbc.update("UPDATE tickets SET business_id=? WHERE ticket_public_id=?",other,publicId);
                case "grant"->jdbc.update("DELETE FROM business_memberships WHERE user_id=?",adminId);
                case "edit"->jdbc.update("UPDATE chat_history SET message='changed' WHERE id=?",aId);
                case "deleted"->jdbc.update("UPDATE chat_history SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",aId);
            }
            return call.callRealMethod();
        }).when(work).createMemory(eq(scope),eq(admin),anyString(),anyLong(),anyLong(),anyString(),anyString(),anyString(),anyString(),anyBoolean());
        mvc.perform(auth(memory(UUID.randomUUID().toString()),admin,true).with(csrf())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_scoped_sources WHERE business_id=?",Integer.class,business)).isZero();
    }
    @Test void lateControlGrantRevokeCannotUsePrecheckOrOverwrite() throws Exception {
        doAnswer(call->{jdbc.update("DELETE FROM business_memberships WHERE user_id=?",adminId);return call.callRealMethod();})
            .when(persistence).setControlExpected(eq(scope),org.mockito.ArgumentMatchers.<org.springframework.security.core.Authentication>argThat(a->a!=null && a.getPrincipal() instanceof com.example.panel.security.PanelUserPrincipal p && p.getUserId()==adminId && p.getUsername().equals(admin)),anyString(),anyBoolean(),anyBoolean(),anyString());
        mvc.perform(auth(control(""),admin,true).with(csrf())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket_ai_scoped_control WHERE ticket_public_id=?",Integer.class,publicId)).isZero();
    }
    @Test void malformedEvidenceWrongBusinessAndFeatureGatesAreClosed() throws Exception {
        mvc.perform(auth(memory(UUID.randomUUID().toString(),other,question),admin,true).with(csrf()))
            .andExpect(status().isConflict());
        mvc.perform(auth(memory(UUID.randomUUID().toString(),business,"not-evidence"),admin,true).with(csrf()))
            .andExpect(status().isBadRequest());
        flags.setUiSelector(false);
        mvc.perform(auth(get(root),admin,true)).andExpect(status().isNotFound());
        mvc.perform(auth(control(""),admin,true).with(csrf())).andExpect(status().isNotFound());
    }
    @Test void approvedReferencesAppearButNotAfterRevisionOrArchive() throws Exception {
        assertThat(sources.createKnowledge(business,admin,"proof","Approved <material>","refund")).isTrue();
        assertThat(sources.revise(business,admin,ScopedAiSourceService.Kind.KNOWLEDGE,"proof",1,"Approved <material>","refund",ScopedAiSourceService.Status.APPROVED)).isTrue();
        assertThat(knowledge.record(scope,"refund")).isTrue();
        mvc.perform(auth(get(root),admin,true)).andExpect(status().isOk()).andExpect(content().string(containsString("Approved &lt;material&gt;")));
        assertThat(sources.revise(business,admin,ScopedAiSourceService.Kind.KNOWLEDGE,"proof",2,"Approved <material>","refund",ScopedAiSourceService.Status.ARCHIVED)).isTrue();
        mvc.perform(auth(get(root),admin,true)).andExpect(status().isOk()).andExpect(content().string(not(containsString("Approved &lt;material&gt;"))));
    }
    private long account(String name) { return jdbc.queryForObject("INSERT INTO users(username,password,enabled) VALUES(?,'unused',TRUE) RETURNING id",Long.class,name); }
    private long history(Long owner,String sender,String text,String type) {
        return jdbc.queryForObject("INSERT INTO chat_history(ticket_owner_user_id,ticket_id,sender,message,message_type) VALUES(?,?,?,?,?) RETURNING id",
            Long.class,owner,raw,sender,text,type);
    }
    private String fingerprint(long id) { return jdbc.queryForObject("SELECT encode(sha256(convert_to(message,'UTF8')),'hex') FROM chat_history WHERE id=?",String.class,id); }
    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder r,String actor,boolean library) {
        return r.with(user(actor).authorities(library ? new SimpleGrantedAuthority[]{new SimpleGrantedAuthority("PAGE_DIALOGS"),new SimpleGrantedAuthority("PAGE_KNOWLEDGE_BASE")}
            : new SimpleGrantedAuthority[]{new SimpleGrantedAuthority("PAGE_DIALOGS")}));
    }
    private MockHttpServletRequestBuilder control(String stamp) {
        return control(stamp,"review");
    }
    private MockHttpServletRequestBuilder control(String stamp,String reason) {
        return post(root+"/control").param("businessId",Long.toString(business)).param("expectedUpdatedAt",stamp)
            .param("aiDisabled","true").param("autoReplyBlocked","true").param("reason",reason);
    }
    private MockHttpServletRequestBuilder memory(String key) {
        return memory(key,business,question);
    }
    private MockHttpServletRequestBuilder memory(String key,long expectedBusiness,String questionEvidence) {
        return post(root+"/memory").param("businessId",Long.toString(expectedBusiness)).param("key",key).param("question",questionEvidence).param("answer",answer)
            .param("title","Curated title").param("body","Curated safe resolution").param("reviewed","true");
    }
    private RequestPostProcessor csrf() { return request->{
        CsrfToken token=CookieCsrfTokenRepository.withHttpOnlyFalse().generateToken(request);
        request.setCookies(new Cookie("XSRF-TOKEN",token.getToken()));
        new XorCsrfTokenRequestAttributeHandler().handle(request,new MockHttpServletResponse(),()->token);
        CsrfToken masked=(CsrfToken)request.getAttribute(CsrfToken.class.getName());
        request.setParameter(masked.getParameterName(),masked.getToken());return request;
    }; }
    private String hidden(String html,String name) {
        var m=Pattern.compile("<input[^>]*name=\""+name+"\"[^>]*value=\"([^\"]*)\"").matcher(html);
        assertThat(m.find()).as(name).isTrue();return HtmlUtils.htmlUnescape(m.group(1));
    }
}
