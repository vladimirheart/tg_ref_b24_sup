package com.example.panel.controller;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.*;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.web.csrf.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.*;
import org.springframework.web.util.HtmlUtils;

@SpringBootTest @AutoConfigureMockMvc
class GeneralTicketWorkHttpPostgresTest {
    private static final AtomicLong IDS=new AtomicLong(9550000);
    @MockBean BotProcessService botProcessService;
    @MockBean(name="taskScheduler") TaskScheduler scheduler;
    @DynamicPropertySource static void isolatedDatabase(DynamicPropertyRegistry registry) throws Exception {
        var source=(DriverManagerDataSource)PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("general_work_http").getDataSource();
        var config=Files.createTempDirectory("general-work-config-");var storage=Files.createTempDirectory("general-work-storage-");
        registry.add("app.datasource.mode",()->"postgresql");
        registry.add("spring.datasource.url",source::getUrl);registry.add("spring.datasource.username",source::getUsername);
        registry.add("spring.datasource.password",source::getPassword);registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        registry.add("shared-config.dir",config::toString);
        for(String key:new String[]{"attachments","knowledge-base","passport-photos","avatars"})registry.add("app.storage."+key,()->storage.resolve(key).toString());
        registry.add("app.storage.object.required-for-postgresql",()->"false");registry.add("app.coordination.required-for-postgresql",()->"false");
        registry.add("app.bots.internal-api.token",()->"r135-test-internal-token");registry.add("app.security.remember-me-key",()->"r135-test-remember-key");
        registry.add("app.security.bootstrap-admin.username",()->"r135_test_admin");registry.add("app.security.bootstrap-admin.password",()->"r135-test-admin-password");
        registry.add("app.bots.auto-start-enabled",()->"false");registry.add("monitoring.credentials.master-key",()->"r135-test-monitoring-key");
    }
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired MultibusinessFeatureFlags flags;
    @Autowired GeneralTicketAccessService access;@SpyBean GeneralTicketWorkService work;
    private String worker,reader,admin,publicId,root,raw;
    private long workerId,readerId,owner;
    @BeforeEach void setup() {
        flags.getFoundation().setEnabled(true);flags.setGeneralTicketQueue(true);
        String prefix="r135-"+IDS.incrementAndGet();worker=prefix+"-worker";reader=prefix+"-reader";admin=prefix+"-admin";
        workerId=account(worker);readerId=account(reader);account(admin);
        access.grantRead(workerId);access.grantRead(readerId);assertThat(access.grantOperate(workerId)).isTrue();
        publicId=UUID.randomUUID().toString().replace("-","");raw="private "+prefix;owner=IDS.incrementAndGet();
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,is_general,general_selection_source,status) VALUES(?,?,?,TRUE,'GENERAL_CHANNEL','pending')",owner,raw,publicId);
        root="/general-dialog-work/"+publicId;
    }
    @AfterEach void resetFlags() {flags.getFoundation().setEnabled(false);flags.setGeneralTicketQueue(false);}
    @Test void actualRenderedCsrfFormChangesStatusAndStaleFormCannotOverwrite() throws Exception {
        var opened=mvc.perform(actor(get(root),worker,"PAGE_DIALOGS")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store")).andExpect(content().string(containsString("data-general-work")))
            .andExpect(content().string(not(containsString(raw)))).andReturn();
        String html=opened.getResponse().getContentAsString();
        mvc.perform(actor(post(root+"/status").param("revision",hidden(html,"revision")).param("expectedStatus",hidden(html,"expectedStatus"))
            .param("nextStatus",hidden(html,"nextStatus")).param("_csrf",hidden(html,"_csrf")).cookie(opened.getResponse().getCookies()),worker,"PAGE_DIALOGS"))
            .andExpect(redirectedUrl(root));
        assertThat(work.find(publicId,worker).orElseThrow().revision()).isEqualTo(1);
        assertThat(work.find(publicId,worker).orElseThrow().status()).isEqualTo("open");
        mvc.perform(actor(change("0","pending","open"),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_work_events WHERE ticket_public_id=?",Integer.class,publicId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT ticket_scope_kind FROM ui_event_outbox WHERE ticket_public_id=?",String.class,publicId)).isEqualTo("GENERAL");
    }
    @Test void readOnlyAndGlobalAdminDoNotInheritWork() throws Exception {
        mvc.perform(actor(get(root),reader,"PAGE_DIALOGS")).andExpect(status().isOk()).andExpect(content().string(containsString("Только просмотр")));
        mvc.perform(actor(change("0","pending","open"),reader,"PAGE_DIALOGS").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(actor(get(root),admin,"PAGE_DIALOGS","ROLE_ADMIN")).andExpect(status().isNotFound());
        mvc.perform(actor(get(root),worker,"ROLE_ADMIN")).andExpect(status().isForbidden());
    }
    @Test void actualSecurityRequiresCsrfAndFeatureGate() throws Exception {
        mvc.perform(actor(change("0","pending","open"),worker,"PAGE_DIALOGS")).andExpect(redirectedUrl("/error/403"));
        flags.setGeneralTicketQueue(false);
        mvc.perform(actor(get(root),worker,"PAGE_DIALOGS")).andExpect(status().isNotFound());
        mvc.perform(actor(change("0","pending","open"),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings={"operate","read","owner","revision"})
    void lateChangesAfterControllerPrecheckAreReprovedInsideWrite(String mutation) throws Exception {
        var scope=new GeneralTicketEventScope(owner,raw,publicId);
        doAnswer(call->{
            switch(mutation) {
                case "operate"->access.revokeOperate(workerId);
                case "read"->access.revokeRead(workerId);
                case "owner"->jdbc.update("UPDATE tickets SET is_general=FALSE,general_selection_source=NULL WHERE ticket_public_id=?",publicId);
                case "revision"->{jdbc.update("UPDATE tickets SET status='open' WHERE ticket_public_id=?",publicId);jdbc.update("UPDATE tickets SET status='pending' WHERE ticket_public_id=?",publicId);}
            } return call.callRealMethod();
        }).when(work).transition(eq(scope),eq(worker),anyLong(),anyString(),anyString());
        mvc.perform(actor(change("0","pending","open"),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_work_events WHERE ticket_public_id=?",Integer.class,publicId)).isZero();
    }
    @Test void administratorManagesSeparateOperateAndReadRestoreDoesNotRestoreIt() throws Exception {
        String target="/api/general-ticket-admin/access/"+workerId;
        access.revokeOperate(workerId);
        mvc.perform(actor(put(target+"/operate"),admin,"PAGE_SETTINGS","ROLE_ADMIN"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
        assertThat(work.find(publicId,worker).orElseThrow().canOperate()).isFalse();
        mvc.perform(actor(put(target+"/operate"),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(actor(put(target+"/operate"),admin,"PAGE_SETTINGS","ROLE_ADMIN").with(csrf())).andExpect(status().isNoContent());
        mvc.perform(actor(delete(target),admin,"PAGE_SETTINGS","ROLE_ADMIN").with(csrf())).andExpect(status().isNoContent());
        mvc.perform(actor(put(target),admin,"PAGE_SETTINGS","ROLE_ADMIN").with(csrf())).andExpect(status().isNoContent());
        assertThat(work.find(publicId,worker).orElseThrow().canOperate()).isFalse();
        mvc.perform(actor(put("/api/general-ticket-admin/access/"+account("no-read-"+IDS.incrementAndGet())+"/operate"),admin,"PAGE_SETTINGS","ROLE_ADMIN").with(csrf()))
            .andExpect(status().isConflict());
    }
    @Test void malformedVersionAndUnsupportedCloseAreClosed() throws Exception {
        mvc.perform(actor(change("bad","pending","open"),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(actor(change("0","pending","resolved"),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
    }
    private long account(String name) {return jdbc.queryForObject("INSERT INTO users(username,password,enabled) VALUES(?,'unused',TRUE) RETURNING id",Long.class,name);}
    private MockHttpServletRequestBuilder actor(MockHttpServletRequestBuilder request,String name,String...authorities) {
        return request.with(user(name).authorities(java.util.Arrays.stream(authorities).map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toArray(org.springframework.security.core.GrantedAuthority[]::new)));
    }
    private MockHttpServletRequestBuilder change(String version,String from,String to) {return post(root+"/status").param("revision",version).param("expectedStatus",from).param("nextStatus",to);}
    private RequestPostProcessor csrf() {return request->{
        CsrfToken token=CookieCsrfTokenRepository.withHttpOnlyFalse().generateToken(request);request.setCookies(new Cookie("XSRF-TOKEN",token.getToken()));
        new XorCsrfTokenRequestAttributeHandler().handle(request,new MockHttpServletResponse(),()->token);
        CsrfToken masked=(CsrfToken)request.getAttribute(CsrfToken.class.getName());request.setParameter(masked.getParameterName(),masked.getToken());return request;
    };}
    private String hidden(String html,String name) {var matcher=Pattern.compile("<input[^>]*name=\""+name+"\"[^>]*value=\"([^\"]*)\"").matcher(html);assertThat(matcher.find()).isTrue();return HtmlUtils.htmlUnescape(matcher.group(1));}
}
