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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
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
    @MockBean DialogReplyTransportService replyTransport;
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
    @SpyBean GeneralTicketReplyService replies;
    @SpyBean GeneralTicketAssignmentService assignments;
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
    static java.util.stream.Stream<Arguments> lateChanges() {
        return java.util.stream.Stream.of("operate","read","owner","revision")
            .flatMap(mutation->java.util.stream.Stream.of("pending,open","pending,closed","closed,pending")
                .map(edge->Arguments.of(mutation,edge.split(",")[0],edge.split(",")[1])));
    }
    @ParameterizedTest @MethodSource("lateChanges")
    void lateChangesAfterControllerPrecheckAreReprovedInsideWrite(String mutation,String from,String to) throws Exception {
        jdbc.update("UPDATE tickets SET status=? WHERE ticket_public_id=?",from,publicId);
        long version=work.find(publicId,worker).orElseThrow().revision();
        var scope=new GeneralTicketEventScope(owner,raw,publicId);
        doAnswer(call->{
            switch(mutation) {
                case "operate"->access.revokeOperate(workerId);
                case "read"->access.revokeRead(workerId);
                case "owner"->jdbc.update("UPDATE tickets SET is_general=FALSE,general_selection_source=NULL WHERE ticket_public_id=?",publicId);
                case "revision"->{jdbc.update("UPDATE tickets SET status='open' WHERE ticket_public_id=?",publicId);jdbc.update("UPDATE tickets SET status='pending' WHERE ticket_public_id=?",publicId);}
            } return call.callRealMethod();
        }).when(work).transition(eq(scope),eq(worker),anyLong(),anyString(),anyString());
        mvc.perform(actor(change(Long.toString(version),from,to),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
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
    @Test void malformedVersionAndUnsupportedResolveAreClosed() throws Exception {
        mvc.perform(actor(change("bad","pending","open"),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(actor(change("0","pending","resolved"),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
    }
    @Test void renderedCsrfFormsCloseAndReopenWithNewVersionsAndBlockAnOldReply() throws Exception {
        replyDestination();
        var jar=new LinkedHashMap<String,Cookie>();
        var opened=mvc.perform(actor(get(root),worker,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn();
        String html=opened.getResponse().getContentAsString();
        String close=form(html,"data-general-close");
        Cookie[] cookies=captureCookies(jar,opened);
        var closed=mvc.perform(actor(change(hidden(close,"revision"),hidden(close,"expectedStatus"),hidden(close,"nextStatus"))
            .param("_csrf",hidden(close,"_csrf")).cookie(cookies),worker,"PAGE_DIALOGS")).andExpect(redirectedUrl(root)).andReturn();
        cookies=captureCookies(jar,closed);
        var viewed=mvc.perform(actor(get(root).cookie(cookies),worker,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn();
        String closedHtml=viewed.getResponse().getContentAsString();
        assertThat(closedHtml).contains("data-general-reopen").doesNotContain("data-general-close","id=\"generalReplyMessage\"");
        assertThat(work.find(publicId,worker).orElseThrow().status()).isEqualTo("closed");
        mvc.perform(actor(post(root+"/reply").param("revision","0").param("requestKey",hidden(html,"requestKey"))
            .param("destination",hidden(html,"destination")).param("message","Stale reply"),worker,"PAGE_DIALOGS").with(csrf()))
            .andExpect(status().isConflict());
        String reopen=form(closedHtml,"data-general-reopen");
        cookies=captureCookies(jar,viewed);
        var reopened=mvc.perform(actor(change(hidden(reopen,"revision"),hidden(reopen,"expectedStatus"),hidden(reopen,"nextStatus"))
            .param("_csrf",hidden(reopen,"_csrf")).cookie(cookies),worker,"PAGE_DIALOGS")).andExpect(redirectedUrl(root)).andReturn();
        cookies=captureCookies(jar,reopened);
        var reloaded=mvc.perform(actor(get(root).cookie(cookies),worker,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn();
        assertThat(reloaded.getResponse().getContentAsString()).contains("data-general-close","id=\"generalReplyMessage\"");
        assertThat(work.find(publicId,worker).orElseThrow().status()).isEqualTo("pending");
        assertThat(work.find(publicId,worker).orElseThrow().revision()).isEqualTo(2);
        mvc.perform(actor(change("0","pending","closed"),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_work_events WHERE ticket_public_id=?",Integer.class,publicId)).isEqualTo(2);
        verifyNoInteractions(replyTransport);
    }
    @ParameterizedTest @CsvSource({"pending,closed","closed,pending"})
    void closeAndReopenRequireTheirOwnPageCsrfAndOperate(String from,String to) throws Exception {
        jdbc.update("UPDATE tickets SET status=? WHERE ticket_public_id=?",from,publicId);
        String version=Long.toString(work.find(publicId,worker).orElseThrow().revision());
        mvc.perform(actor(change(version,from,to),worker,"PAGE_DIALOGS")).andExpect(redirectedUrl("/error/403"));
        mvc.perform(actor(change(version,from,to),reader,"PAGE_DIALOGS").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(actor(change(version,from,to),worker,"ROLE_ADMIN").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(actor(get(root),reader,"PAGE_DIALOGS")).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("data-general-close"))))
            .andExpect(content().string(not(containsString("data-general-reopen"))));
        assertThat(work.find(publicId,worker).orElseThrow().status()).isEqualTo(from);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_work_events WHERE ticket_public_id=?",Integer.class,publicId)).isZero();
        verifyNoInteractions(replyTransport);
    }
    @Test void renderedCsrfCycleShowsOnlyObservedIntervalsToCurrentReaders() throws Exception {
        jdbc.update("UPDATE tickets SET work_time_total_sec=87654 WHERE ticket_public_id=?",publicId);
        var jar=new LinkedHashMap<String,Cookie>();
        var initial=mvc.perform(actor(get(root),worker,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn();
        String html=initial.getResponse().getContentAsString();
        assertThat(html).contains("data-general-work-time","Интервалов пока нет.","частичный учёт").doesNotContain("87654");
        var opened=mvc.perform(actor(change(hidden(html,"revision"),hidden(html,"expectedStatus"),hidden(html,"nextStatus"))
            .param("_csrf",hidden(html,"_csrf")).cookie(captureCookies(jar,initial)),worker,"PAGE_DIALOGS"))
            .andExpect(redirectedUrl(root)).andReturn();
        var current=mvc.perform(actor(get(root).cookie(captureCookies(jar,opened)),worker,"PAGE_DIALOGS"))
            .andExpect(status().isOk()).andReturn();
        String openHtml=current.getResponse().getContentAsString();
        assertThat(openHtml).contains("data-general-active-interval","В итог он войдёт после завершения.").doesNotContain(raw,"87654");
        mvc.perform(actor(get(root),reader,"PAGE_DIALOGS")).andExpect(status().isOk())
            .andExpect(content().string(containsString("data-general-active-interval")));
        var waited=mvc.perform(actor(change(hidden(openHtml,"revision"),hidden(openHtml,"expectedStatus"),hidden(openHtml,"nextStatus"))
            .param("_csrf",hidden(openHtml,"_csrf")).cookie(captureCookies(jar,current)),worker,"PAGE_DIALOGS"))
            .andExpect(redirectedUrl(root)).andReturn();
        mvc.perform(actor(get(root).cookie(captureCookies(jar,waited)),worker,"PAGE_DIALOGS"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Завершён:")))
            .andExpect(content().string(not(containsString("data-general-active-interval"))))
            .andExpect(content().string(not(containsString("87654"))));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_work_intervals WHERE ticket_public_id=? AND outcome='COMPLETED'",Integer.class,publicId)).isEqualTo(1);
        access.revokeRead(readerId);
        mvc.perform(actor(get(root),reader,"PAGE_DIALOGS")).andExpect(status().isNotFound());
        verifyNoInteractions(replyTransport);
    }
    @Test void renderedAssignmentFormUsesActualCsrfAndReadOnlyHistory() throws Exception {
        String targetName="<staff-"+IDS.incrementAndGet()+">";long target=account(targetName);
        access.grantRead(target);access.grantOperate(target);
        var jar=new LinkedHashMap<String,Cookie>();
        var initial=mvc.perform(actor(get(root),worker,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn();
        String html=initial.getResponse().getContentAsString();String assignmentForm=form(html,"data-general-assignment-form");
        assertThat(html).contains("&lt;staff-").doesNotContain(targetName);
        var saved=mvc.perform(actor(assignPost(hidden(assignmentForm,"revision"),hidden(assignmentForm,"assignmentRevision"),Long.toString(target))
            .param("_csrf",hidden(assignmentForm,"_csrf")).cookie(captureCookies(jar,initial)),worker,"PAGE_DIALOGS"))
            .andExpect(redirectedUrl(root)).andReturn();
        mvc.perform(actor(assignPost("0","0",Long.toString(workerId)),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        mvc.perform(actor(get(root),reader,"PAGE_DIALOGS")).andExpect(status().isOk())
            .andExpect(content().string(containsString("&lt;staff-")))
            .andExpect(content().string(not(containsString("data-general-assignment-form"))));
        access.revokeRead(target);
        var current=mvc.perform(actor(get(root).cookie(captureCookies(jar,saved)),worker,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn();
        String currentForm=form(current.getResponse().getContentAsString(),"data-general-assignment-form");
        assertThat(current.getResponse().getContentAsString()).contains("больше нет действующих прав GENERAL");
        mvc.perform(actor(assignPost(hidden(currentForm,"revision"),hidden(currentForm,"assignmentRevision"),"0")
            .param("_csrf",hidden(currentForm,"_csrf")).cookie(captureCookies(jar,current)),worker,"PAGE_DIALOGS"))
            .andExpect(redirectedUrl(root));
        assertThat(assignments.current(new GeneralTicketEventScope(owner,raw,publicId),reader).orElseThrow().assignee()).isNull();
        assertThat(work.find(publicId,worker).orElseThrow().revision()).isZero();
        verifyNoInteractions(replyTransport);
    }
    @Test void assignmentRequiresPageCsrfOperateAndCurrentTargetRights() throws Exception {
        mvc.perform(actor(assignPost("0","0",Long.toString(workerId)),worker,"PAGE_DIALOGS")).andExpect(redirectedUrl("/error/403"));
        mvc.perform(actor(assignPost("0","0",Long.toString(workerId)),reader,"PAGE_DIALOGS").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(actor(assignPost("0","0",Long.toString(workerId)),worker,"ROLE_ADMIN").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(actor(assignPost("0","0",Long.toString(readerId)),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        mvc.perform(actor(assignPost("bad","0",Long.toString(workerId)),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(actor(assignPost("0","-1",Long.toString(workerId)),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(actor(assignPost("0","0","bad"),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isBadRequest());
        jdbc.update("UPDATE tickets SET status='closed' WHERE ticket_public_id=?",publicId);
        mvc.perform(actor(get(root),worker,"PAGE_DIALOGS")).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("data-general-assignment-form"))));
        mvc.perform(actor(assignPost("1","0",Long.toString(workerId)),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_assignment_events WHERE ticket_public_id=?",Integer.class,publicId)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"actor-read","actor-operate","target-read","target-operate","owner","revision","assignment","flag"})
    void assignmentReprovesChangesAfterControllerPrecheck(String mutation) throws Exception {
        long target=account("assign-target-"+IDS.incrementAndGet());access.grantRead(target);access.grantOperate(target);
        var scope=new GeneralTicketEventScope(owner,raw,publicId);
        doAnswer(call->{
            switch(mutation) {
                case "actor-read"->access.revokeRead(workerId);
                case "actor-operate"->access.revokeOperate(workerId);
                case "target-read"->access.revokeRead(target);
                case "target-operate"->access.revokeOperate(target);
                case "owner"->jdbc.update("UPDATE tickets SET is_general=FALSE,general_selection_source=NULL WHERE ticket_public_id=?",publicId);
                case "revision"->jdbc.update("UPDATE tickets SET status='open' WHERE ticket_public_id=?",publicId);
                case "assignment"->assertThat(assignments.assign(scope,worker,0,0,workerId)).isTrue();
                case "flag"->flags.setGeneralTicketQueue(false);
            }
            return call.callRealMethod();
        }).when(assignments).assign(eq(scope),eq(worker),eq(0L),eq(0L),eq(Long.valueOf(target)));
        mvc.perform(actor(assignPost("0","0",Long.toString(target)),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_assignment_events WHERE ticket_public_id=?",Integer.class,publicId))
            .isEqualTo(mutation.equals("assignment")?1:0);
        verifyNoInteractions(replyTransport);
    }
    private MockHttpServletRequestBuilder assignPost(String version,String assignmentVersion,String target) {
        return post(root+"/assignment").param("revision",version).param("assignmentRevision",assignmentVersion).param("assigneeId",target);
    }
    private String form(String html,String marker) {
        int start=html.indexOf(marker);assertThat(start).isGreaterThan(0);
        int end=html.indexOf("</form>",start);assertThat(end).isGreaterThan(start);
        return html.substring(start,end);
    }
    private Cookie[] captureCookies(Map<String,Cookie> jar,org.springframework.test.web.servlet.MvcResult result) {
        for(Cookie cookie:result.getResponse().getCookies()) {
            if(cookie.getMaxAge()==0)jar.remove(cookie.getName());else jar.put(cookie.getName(),cookie);
        }
        return jar.values().toArray(Cookie[]::new);
    }
    @Test void renderedReplyFormUsesActualCsrfAndDuplicatePostDoesNotSendTwice() throws Exception {
        replyDestination();
        var opened=mvc.perform(actor(get(root),worker,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn();
        String html=opened.getResponse().getContentAsString();String requestKey=hidden(html,"requestKey");String text="<script>private reply</script>";
        for (int i=0;i<2;i++) {
            mvc.perform(actor(post(root+"/reply").param("revision","0").param("requestKey",requestKey).param("message",text)
                .param("destination",hidden(html,"destination"))
                .param("_csrf",hidden(html,"_csrf")).cookie(opened.getResponse().getCookies()),worker,"PAGE_DIALOGS")).andExpect(redirectedUrl(root));
        }
        verify(replyTransport,times(1)).sendTextGuarded(any(),eq(owner),eq(text),isNull(),any());
        mvc.perform(actor(get(root),reader,"PAGE_DIALOGS")).andExpect(status().isOk())
            .andExpect(content().string(containsString("&lt;script&gt;private reply&lt;/script&gt;")))
            .andExpect(content().string(not(containsString("<script>private reply</script>"))))
            .andExpect(content().string(containsString("Провайдер подтвердил отправку")));
        mvc.perform(actor(get("/api/general-tickets/"+publicId+"/history"),worker,"PAGE_DIALOGS"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.entries[0].message").value(text));
    }
    @Test void replyRequiresSeparateOperateCsrfPageAndValidBody() throws Exception {
        replyDestination();
        mvc.perform(actor(replyPost(),worker,"PAGE_DIALOGS")).andExpect(redirectedUrl("/error/403"));
        mvc.perform(actor(replyPost(),reader,"PAGE_DIALOGS").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(actor(replyPost(),worker,"ROLE_ADMIN").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(actor(replyPost(UUID.randomUUID().toString().replace("-",""),""),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(actor(replyPost("bad","Ответ"),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isBadRequest());
        verifyNoInteractions(replyTransport);
    }
    @ParameterizedTest @ValueSource(strings={"read","operate","owner","channel","revision","flag"})
    void replyReprovesLateChangesAfterControllerRead(String mutation) throws Exception {
        replyDestination();var scope=new GeneralTicketEventScope(owner,raw,publicId);
        doAnswer(call->{
            switch(mutation) {
                case "read"->access.revokeRead(workerId);
                case "operate"->access.revokeOperate(workerId);
                case "owner"->jdbc.update("UPDATE tickets SET is_general=FALSE,general_selection_source=NULL WHERE ticket_public_id=?",publicId);
                case "channel"->jdbc.update("UPDATE channels SET is_active=FALSE WHERE id=(SELECT channel_id FROM tickets WHERE ticket_public_id=?)",publicId);
                case "revision"->jdbc.update("UPDATE tickets SET status='open' WHERE ticket_public_id=?",publicId);
                case "flag"->flags.setGeneralTicketQueue(false);
            }
            return call.callRealMethod();
        }).when(replies).submit(eq(scope),eq(worker),anyLong(),anyString(),anyString(),anyString());
        mvc.perform(actor(replyPost(),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        verifyNoInteractions(replyTransport);
    }
    @Test void unknownReplyIsVisibleButNeverReportedAsSentOrRetried() throws Exception {
        replyDestination();
        doReturn(new DialogReplyTransportService.DialogReplyTransportResult("synthetic timeout",null))
            .when(replyTransport).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
        String key=UUID.randomUUID().toString().replace("-","");
        for(int i=0;i<2;i++) mvc.perform(actor(replyPost(key,"Ответ"),worker,"PAGE_DIALOGS").with(csrf())).andExpect(redirectedUrl(root));
        mvc.perform(actor(get(root),worker,"PAGE_DIALOGS")).andExpect(status().isOk())
            .andExpect(content().string(containsString("Результат требует проверки")))
            .andExpect(content().string(not(containsString("Провайдер подтвердил отправку"))));
        verify(replyTransport,times(1)).sendTextGuarded(any(),anyLong(),anyString(),isNull(),any());
    }
    private MockHttpServletRequestBuilder replyPost() {
        return replyPost(UUID.randomUUID().toString().replace("-",""),"Ответ");
    }
    private MockHttpServletRequestBuilder replyPost(String key,String message) {
        return post(root+"/reply").param("revision","0").param("requestKey",key).param("message",message)
            .param("destination",replies.destinationFingerprint(new GeneralTicketEventScope(owner,raw,publicId),worker,0).orElse("0".repeat(64)));
    }
    @Test void staleDestinationFormCannotSilentlyUseReconfiguredChannel() throws Exception {
        replyDestination();
        var opened=mvc.perform(actor(get(root),worker,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn();
        String html=opened.getResponse().getContentAsString();
        jdbc.update("UPDATE channels SET platform='max' WHERE id=(SELECT channel_id FROM tickets WHERE ticket_public_id=?)",publicId);
        mvc.perform(actor(post(root+"/reply").param("revision","0").param("requestKey",hidden(html,"requestKey"))
            .param("message","Ответ").param("destination",hidden(html,"destination")).with(csrf()),worker,"PAGE_DIALOGS"))
            .andExpect(status().isConflict());
        verifyNoInteractions(replyTransport);
    }
    private void replyDestination() {
        long channel=jdbc.queryForObject("INSERT INTO channels(token,channel_name,is_active,platform) VALUES(?,'reply test',TRUE,'telegram') RETURNING id",Long.class,"fake-reply-"+publicId);
        jdbc.update("INSERT INTO messages(group_msg_id,user_id,ticket_id,channel_id) VALUES(?,?,?,?)",owner,owner,raw,channel);
        jdbc.update("UPDATE tickets SET group_msg_id=?,channel_id=? WHERE ticket_public_id=?",owner,channel,publicId);
        when(replyTransport.sendTextGuarded(any(),anyLong(),anyString(),isNull(),any())).thenAnswer(call->{
            assertThat(((java.util.function.BooleanSupplier)call.getArgument(4)).getAsBoolean()).isTrue();
            return DialogReplyTransportService.DialogReplyTransportResult.success(201L,1L);
        });
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
