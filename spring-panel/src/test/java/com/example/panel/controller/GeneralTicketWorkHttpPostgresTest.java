package com.example.panel.controller;

import static com.example.panel.support.GeneralTicketAdminTestSupport.administrator;
import static com.example.panel.support.GeneralTicketActorTestSupport.auth;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.*;
import com.example.panel.security.PanelUserPrincipal;
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
    @MockBean com.example.panel.storage.AttachmentObjectStorageService documentStorage;
    private final Map<String,byte[]> documentObjects=new java.util.concurrent.ConcurrentHashMap<>();
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
    @SpyBean GeneralTicketReplyChangeService changes;
    @SpyBean GeneralTicketDocumentReplyService documents;
    @SpyBean GeneralTicketAssignmentService assignments;
    @SpyBean GeneralTicketParticipantService participants;
    @Autowired UiEventOutboxAppendService participantOutboxIds;
    @SpyBean GeneralManualTicketCreationService manual;
    private String worker,reader,admin,publicId,root,raw;
    private long workerId,readerId,owner;
    @BeforeEach void setup() throws Exception {
        when(documentStorage.providerLabel()).thenReturn("s3");
        when(documentStorage.storeDialogAttachment(anyString(),anyString(),anyString(),any())).thenAnswer(call->{
            String key=call.getArgument(0)+"/"+call.getArgument(1);byte[] bytes=((java.io.InputStream)call.getArgument(3)).readAllBytes();documentObjects.put(key,bytes);
            return new com.example.panel.storage.AttachmentObjectStorageService.StoredBinary(key,"application/octet-stream",bytes.length,new java.io.ByteArrayInputStream(bytes));
        });
        when(documentStorage.openDialogAttachmentByStorageKey(anyString())).thenAnswer(call->{
            byte[] bytes=documentObjects.get(call.getArgument(0));if(bytes==null)throw new java.io.IOException("missing test object");
            return new com.example.panel.storage.AttachmentObjectStorageService.StoredBinary(call.getArgument(0),"application/octet-stream",bytes.length,new java.io.ByteArrayInputStream(bytes));
        });
        flags.setManualTicketCreation(false);
        flags.getFoundation().setEnabled(true);flags.setGeneralTicketQueue(true);
        String prefix="r135-"+IDS.incrementAndGet();worker=prefix+"-worker";reader=prefix+"-reader";admin=prefix+"-admin";
        workerId=account(worker);readerId=account(reader);account(admin);
        access.grantRead(administrator(jdbc),workerId);access.grantRead(administrator(jdbc),readerId);assertThat(access.grantOperate(administrator(jdbc),workerId)).isTrue();
        publicId=UUID.randomUUID().toString().replace("-","");raw="private "+prefix;owner=IDS.incrementAndGet();
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,is_general,general_selection_source,status) VALUES(?,?,?,TRUE,'GENERAL_CHANNEL','pending')",owner,raw,publicId);
        root="/general-dialog-work/"+publicId;
    }
    @AfterEach void resetFlags() {flags.getFoundation().setEnabled(false);flags.setGeneralTicketQueue(false);flags.setManualTicketCreation(false);}
    @Test void actualRenderedCsrfFormChangesStatusAndStaleFormCannotOverwrite() throws Exception {
        var opened=mvc.perform(actor(get(root),worker,"PAGE_DIALOGS")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store")).andExpect(content().string(containsString("data-general-work")))
            .andExpect(content().string(not(containsString(raw)))).andReturn();
        String html=opened.getResponse().getContentAsString();
        mvc.perform(actor(post(root+"/status").param("revision",hidden(html,"revision")).param("expectedStatus",hidden(html,"expectedStatus"))
            .param("nextStatus",hidden(html,"nextStatus")).param("_csrf",hidden(html,"_csrf")).cookie(opened.getResponse().getCookies()),worker,"PAGE_DIALOGS"))
            .andExpect(redirectedUrl(root));
        assertThat(work.find(publicId,auth(jdbc,worker)).orElseThrow().revision()).isEqualTo(1);
        assertThat(work.find(publicId,auth(jdbc,worker)).orElseThrow().status()).isEqualTo("open");
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
        long version=work.find(publicId,auth(jdbc,worker)).orElseThrow().revision();
        var scope=new GeneralTicketEventScope(owner,raw,publicId);
        doAnswer(call->{
            switch(mutation) {
                case "operate"->access.revokeOperate(administrator(jdbc),workerId);
                case "read"->access.revokeRead(administrator(jdbc),workerId);
                case "owner"->jdbc.update("UPDATE tickets SET is_general=FALSE,general_selection_source=NULL WHERE ticket_public_id=?",publicId);
                case "revision"->{jdbc.update("UPDATE tickets SET status='open' WHERE ticket_public_id=?",publicId);jdbc.update("UPDATE tickets SET status='pending' WHERE ticket_public_id=?",publicId);}
            } return call.callRealMethod();
        }).when(work).transition(eq(scope),org.mockito.ArgumentMatchers.argThat(a->a!=null&&a.getName().equals(worker)&&a.getPrincipal() instanceof com.example.panel.security.PanelUserPrincipal p&&p.getUserId()==workerId),anyLong(),anyString(),anyString());
        mvc.perform(actor(change(Long.toString(version),from,to),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_work_events WHERE ticket_public_id=?",Integer.class,publicId)).isZero();
    }
    @Test void administratorManagesSeparateOperateAndReadRestoreDoesNotRestoreIt() throws Exception {
        String target="/api/general-ticket-admin/access/"+workerId;
        access.revokeOperate(administrator(jdbc),workerId);
        mvc.perform(actor(put(target+"/operate"),admin,"PAGE_SETTINGS","ROLE_ADMIN"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
        assertThat(work.find(publicId,auth(jdbc,worker)).orElseThrow().canOperate()).isFalse();
        mvc.perform(actor(put(target+"/operate"),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(actor(put(target+"/operate"),admin,"PAGE_SETTINGS","ROLE_ADMIN").with(csrf())).andExpect(status().isNoContent());
        mvc.perform(actor(delete(target),admin,"PAGE_SETTINGS","ROLE_ADMIN").with(csrf())).andExpect(status().isNoContent());
        mvc.perform(actor(put(target),admin,"PAGE_SETTINGS","ROLE_ADMIN").with(csrf())).andExpect(status().isNoContent());
        assertThat(work.find(publicId,auth(jdbc,worker)).orElseThrow().canOperate()).isFalse();
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
        assertThat(work.find(publicId,auth(jdbc,worker)).orElseThrow().status()).isEqualTo("closed");
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
        assertThat(work.find(publicId,auth(jdbc,worker)).orElseThrow().status()).isEqualTo("pending");
        assertThat(work.find(publicId,auth(jdbc,worker)).orElseThrow().revision()).isEqualTo(2);
        mvc.perform(actor(change("0","pending","closed"),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_work_events WHERE ticket_public_id=?",Integer.class,publicId)).isEqualTo(2);
        verifyNoInteractions(replyTransport);
    }
    @ParameterizedTest @CsvSource({"pending,closed","closed,pending"})
    void closeAndReopenRequireTheirOwnPageCsrfAndOperate(String from,String to) throws Exception {
        jdbc.update("UPDATE tickets SET status=? WHERE ticket_public_id=?",from,publicId);
        String version=Long.toString(work.find(publicId,auth(jdbc,worker)).orElseThrow().revision());
        mvc.perform(actor(change(version,from,to),worker,"PAGE_DIALOGS")).andExpect(redirectedUrl("/error/403"));
        mvc.perform(actor(change(version,from,to),reader,"PAGE_DIALOGS").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(actor(change(version,from,to),worker,"ROLE_ADMIN").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(actor(get(root),reader,"PAGE_DIALOGS")).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("data-general-close"))))
            .andExpect(content().string(not(containsString("data-general-reopen"))));
        assertThat(work.find(publicId,auth(jdbc,worker)).orElseThrow().status()).isEqualTo(from);
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
        access.revokeRead(administrator(jdbc),readerId);
        mvc.perform(actor(get(root),reader,"PAGE_DIALOGS")).andExpect(status().isNotFound());
        verifyNoInteractions(replyTransport);
    }
    @Test void renderedAssignmentFormUsesActualCsrfAndReadOnlyHistory() throws Exception {
        String targetName="<staff-"+IDS.incrementAndGet()+">";long target=account(targetName);
        access.grantRead(administrator(jdbc),target);access.grantOperate(administrator(jdbc),target);
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
        access.revokeRead(administrator(jdbc),target);
        var current=mvc.perform(actor(get(root).cookie(captureCookies(jar,saved)),worker,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn();
        String currentForm=form(current.getResponse().getContentAsString(),"data-general-assignment-form");
        assertThat(current.getResponse().getContentAsString()).contains("больше нет действующих прав GENERAL");
        mvc.perform(actor(assignPost(hidden(currentForm,"revision"),hidden(currentForm,"assignmentRevision"),"0")
            .param("_csrf",hidden(currentForm,"_csrf")).cookie(captureCookies(jar,current)),worker,"PAGE_DIALOGS"))
            .andExpect(redirectedUrl(root));
        assertThat(assignments.current(new GeneralTicketEventScope(owner,raw,publicId),auth(jdbc,reader)).orElseThrow().assignee()).isNull();
        assertThat(work.find(publicId,auth(jdbc,worker)).orElseThrow().revision()).isZero();
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
        long target=account("assign-target-"+IDS.incrementAndGet());access.grantRead(administrator(jdbc),target);access.grantOperate(administrator(jdbc),target);
        var scope=new GeneralTicketEventScope(owner,raw,publicId);
        doAnswer(call->{
            switch(mutation) {
                case "actor-read"->access.revokeRead(administrator(jdbc),workerId);
                case "actor-operate"->access.revokeOperate(administrator(jdbc),workerId);
                case "target-read"->access.revokeRead(administrator(jdbc),target);
                case "target-operate"->access.revokeOperate(administrator(jdbc),target);
                case "owner"->jdbc.update("UPDATE tickets SET is_general=FALSE,general_selection_source=NULL WHERE ticket_public_id=?",publicId);
                case "revision"->jdbc.update("UPDATE tickets SET status='open' WHERE ticket_public_id=?",publicId);
                case "assignment"->assertThat(assignments.assign(scope,auth(jdbc,worker),0,0,workerId)).isTrue();
                case "flag"->flags.setGeneralTicketQueue(false);
            }
            return call.callRealMethod();
        }).when(assignments).assign(eq(scope),org.mockito.ArgumentMatchers.argThat(a->a!=null&&a.getName().equals(worker)&&a.getPrincipal() instanceof com.example.panel.security.PanelUserPrincipal p&&p.getUserId()==workerId),eq(0L),eq(0L),eq(Long.valueOf(target)));
        mvc.perform(actor(assignPost("0","0",Long.toString(target)),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_assignment_events WHERE ticket_public_id=?",Integer.class,publicId))
            .isEqualTo(mutation.equals("assignment")?1:0);
        verifyNoInteractions(replyTransport);
    }
    private MockHttpServletRequestBuilder assignPost(String version,String assignmentVersion,String target) {
        return post(root+"/assignment").param("revision",version).param("assignmentRevision",assignmentVersion).param("assigneeId",target);
    }
    @Test void renderedGeneralCreationFormIsIdempotentAndCreatesOnlyALocalPendingTicket() throws Exception {
        flags.setManualTicketCreation(true);
        mvc.perform(actor(get("/general-dialogs"),worker,"PAGE_DIALOGS")).andExpect(status().isOk())
            .andExpect(content().string(containsString("data-general-create-link")));
        mvc.perform(actor(get("/general-dialogs"),reader,"PAGE_DIALOGS")).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("data-general-create-link"))));
        var opened=mvc.perform(actor(get("/general-dialogs/new"),worker,"PAGE_DIALOGS")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store")).andReturn();
        String html=opened.getResponse().getContentAsString(),key=hidden(html,"requestKey");
        assertThat(html).contains("data-general-create-form","новый локальный клиент").doesNotContain("name=\"businessId\"","name=\"clientId\"");
        String location=null;
        for(int i=0;i<2;i++) {
            var saved=mvc.perform(actor(manualPost(key,"<local client>","Локальная <script>заметка</script>")
                .param("_csrf",hidden(html,"_csrf")).cookie(opened.getResponse().getCookies()),worker,"PAGE_DIALOGS"))
                .andExpect(status().is3xxRedirection()).andReturn();
            String next=saved.getResponse().getRedirectedUrl();
            assertThat(next).matches("/general-dialog-work/[a-f0-9]{32}");if(location!=null)assertThat(next).isEqualTo(location);location=next;
        }
        String id=location.substring(location.lastIndexOf('/')+1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_manual_ticket_requests WHERE request_key=?",Integer.class,key)).isEqualTo(1);
        mvc.perform(actor(get(location),worker,"PAGE_DIALOGS")).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("id=\"generalReplyMessage\""))));
        mvc.perform(actor(get("/api/general-tickets/"+id),reader,"PAGE_DIALOGS")).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("pending")).andExpect(jsonPath("$.clientName").value("<local client>"));
        mvc.perform(actor(get("/api/general-tickets/"+id+"/history"),reader,"PAGE_DIALOGS")).andExpect(status().isOk())
            .andExpect(jsonPath("$.entries[0].messageType").value("operator_note"));
        assertThat(jdbc.queryForMap("SELECT channel_id,business_id,general_selection_source FROM tickets WHERE ticket_public_id=?",id))
            .containsEntry("channel_id",null).containsEntry("business_id",null).containsEntry("general_selection_source","MANUAL");
        verifyNoInteractions(replyTransport);
    }
    @Test void manualCreationRequiresItsFlagPageCsrfAndGeneralOperate() throws Exception {
        mvc.perform(actor(get("/general-dialogs/new"),worker,"PAGE_DIALOGS")).andExpect(status().isNotFound());
        flags.setManualTicketCreation(true);String key=UUID.randomUUID().toString().replace("-","");
        mvc.perform(actor(get("/general-dialogs/new"),reader,"PAGE_DIALOGS")).andExpect(status().isForbidden());
        mvc.perform(actor(get("/general-dialogs/new"),admin,"PAGE_DIALOGS","ROLE_ADMIN")).andExpect(status().isForbidden());
        mvc.perform(actor(get("/general-dialogs/new"),worker,"ROLE_ADMIN")).andExpect(status().isForbidden());
        mvc.perform(actor(manualPost(key,"Клиент","Вопрос"),worker,"PAGE_DIALOGS")).andExpect(redirectedUrl("/error/403"));
        mvc.perform(actor(manualPost(key,"Клиент","Вопрос"),reader,"PAGE_DIALOGS").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(actor(manualPost("bad","Клиент","Вопрос"),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(actor(manualPost(key," ","Вопрос"),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(actor(manualPost(key,"Клиент","x".repeat(10001)),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_manual_ticket_requests WHERE actor_user_id=?",Integer.class,workerId)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"read","operate","disabled","duplicate","manual","foundation","queue"})
    void manualCreationReprovesRightsAndFlagsAfterControllerPrecheck(String mutation) throws Exception {
        flags.setManualTicketCreation(true);String key=UUID.randomUUID().toString().replace("-","");
        doAnswer(call->{
            switch(mutation) {
                case "read"->access.revokeRead(administrator(jdbc),workerId);case "operate"->access.revokeOperate(administrator(jdbc),workerId);
                case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",workerId);
                case "duplicate"->account(worker.toUpperCase(java.util.Locale.ROOT));
                case "manual"->flags.setManualTicketCreation(false);
                case "foundation"->flags.getFoundation().setEnabled(false);
                case "queue"->flags.setGeneralTicketQueue(false);
            }
            return call.callRealMethod();
        }).when(manual).create(org.mockito.ArgumentMatchers.argThat(a->a!=null&&a.getName().equals(worker)&&a.getPrincipal() instanceof com.example.panel.security.PanelUserPrincipal p&&p.getUserId()==workerId),eq(key),anyString(),anyString());
        mvc.perform(actor(manualPost(key,"Клиент","Вопрос"),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_manual_ticket_requests WHERE request_key=?",Integer.class,key)).isZero();
        verifyNoInteractions(replyTransport);
    }
    private MockHttpServletRequestBuilder manualPost(String key,String name,String problem) {
        return post("/general-dialogs/new").param("requestKey",key).param("clientName",name).param("problem",problem);
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
                case "read"->access.revokeRead(administrator(jdbc),workerId);
                case "operate"->access.revokeOperate(administrator(jdbc),workerId);
                case "owner"->jdbc.update("UPDATE tickets SET is_general=FALSE,general_selection_source=NULL WHERE ticket_public_id=?",publicId);
                case "channel"->jdbc.update("UPDATE channels SET is_active=FALSE WHERE id=(SELECT channel_id FROM tickets WHERE ticket_public_id=?)",publicId);
                case "revision"->jdbc.update("UPDATE tickets SET status='open' WHERE ticket_public_id=?",publicId);
                case "flag"->flags.setGeneralTicketQueue(false);
            }
            return call.callRealMethod();
        }).when(replies).submit(eq(scope),org.mockito.ArgumentMatchers.argThat(a->a!=null&&a.getName().equals(worker)&&a.getPrincipal() instanceof com.example.panel.security.PanelUserPrincipal p&&p.getUserId()==workerId),anyLong(),anyString(),anyString(),anyString());
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
            .param("destination",replies.destinationFingerprint(new GeneralTicketEventScope(owner,raw,publicId),auth(jdbc,worker),0).orElse("0".repeat(64)));
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
    private GeneralTicketEventScope participantScope(){return new GeneralTicketEventScope(owner,raw,publicId);}
    private MockHttpServletRequestBuilder participantCommand(String action,String revision,String listRevision,String memberId) {
        return post(root+"/participants/"+action).param("revision",revision).param("participantRevision",listRevision).param("memberId",memberId);
    }
    private long participantEvents(){return jdbc.queryForObject("SELECT count(*) FROM general_ticket_participant_events WHERE ticket_public_id=?",Long.class,publicId);}
    private long participantOutbox(){return jdbc.queryForObject("SELECT count(*) FROM ui_event_outbox WHERE ticket_public_id=?",Long.class,publicId);}
    @Test void realCsrfAddsReadOnlyGeneralMemberAndReloadsHistoryWithoutExposingRawOwner() throws Exception {
        jdbc.update("INSERT INTO ticket_participants(ticket_id,username,added_by) VALUES(?,?,'legacy')",raw,worker);
        var legacyRows=jdbc.queryForList("SELECT * FROM ticket_participants WHERE ticket_id=?",raw);
        var opened=mvc.perform(actor(get(root),worker,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn();
        String html=opened.getResponse().getContentAsString();
        assertThat(html).contains("data-general-participants","id=\"generalParticipant\"").doesNotContain(raw,"name=\"ownerId\"","name=\"rawId\"");
        assertThat(participants.members(participantScope(),auth(jdbc,worker))).isEmpty();
        mvc.perform(actor(participantCommand("add",hidden(html,"revision"),hidden(html,"participantRevision"),Long.toString(readerId))
            .param("_csrf",hidden(html,"_csrf")).cookie(opened.getResponse().getCookies()),worker,"PAGE_DIALOGS")).andExpect(redirectedUrl(root));
        assertThat(participants.members(participantScope(),auth(jdbc,reader))).singleElement().satisfies(m->{assertThat(m.id()).isEqualTo(readerId);assertThat(m.eligible()).isTrue();});
        assertThat(access.canOperate(readerId)).isFalse();
        var read=mvc.perform(actor(get(root),reader,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(read).contains(reader).doesNotContain("id=\"generalParticipant\"","/participants/remove",raw);
        assertThat(jdbc.queryForMap("SELECT * FROM general_ticket_participant_events WHERE ticket_public_id=?",publicId))
            .containsEntry("ticket_user_id",owner).containsEntry("ticket_id",raw).containsEntry("member_user_id",readerId)
            .containsEntry("actor_user_id",workerId).containsEntry("revision",1L).containsEntry("owner_revision",0L).containsEntry("action","add");
        assertThat(jdbc.queryForMap("SELECT * FROM ui_event_outbox WHERE ticket_public_id=?",publicId))
            .containsEntry("event_type","participants_changed").containsEntry("ticket_scope_kind","GENERAL").containsEntry("ticket_business_id",null)
            .containsEntry("ticket_user_id",owner).containsEntry("ticket_public_id",publicId);
        mvc.perform(actor(participantCommand("remove","0","1",Long.toString(readerId)),worker,"PAGE_DIALOGS").with(csrf())).andExpect(redirectedUrl(root));
        assertThat(participants.members(participantScope(),auth(jdbc,worker))).isEmpty();
        assertThat(participants.revision(participantScope(),auth(jdbc,worker))).contains(2L);
        assertThat(jdbc.queryForList("SELECT * FROM ticket_participants WHERE ticket_id=?",raw)).isEqualTo(legacyRows);
        assertThat(work.find(publicId,auth(jdbc,worker)).orElseThrow().revision()).isZero();
        for(String table:new String[]{"ticket_responsibles","general_ticket_assignment_events","general_ticket_work_events","dialog_action_audit"})
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE "+(table.startsWith("general_")?"ticket_public_id":"ticket_id")+"=?",Long.class,table.startsWith("general_")?publicId:raw)).isZero();
        verifyNoInteractions(replyTransport);verify(documents,never()).submit(any(),org.mockito.ArgumentMatchers.any(org.springframework.security.core.Authentication.class),anyLong(),anyString(),anyString(),any(),anyString());
    }
    @Test void participantPageCsrfReadAndOperateAreIndependent() throws Exception {
        mvc.perform(actor(participantCommand("add","0","0",Long.toString(readerId)),worker,"PAGE_DIALOGS")).andExpect(redirectedUrl("/error/403"));
        mvc.perform(actor(participantCommand("add","0","0",Long.toString(readerId)),worker,"ROLE_ADMIN").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(actor(participantCommand("add","0","0",Long.toString(readerId)),reader,"PAGE_DIALOGS","ROLE_ADMIN").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(actor(participantCommand("add","0","0",Long.toString(readerId)),admin,"PAGE_DIALOGS","ROLE_ADMIN").with(csrf())).andExpect(status().isNotFound());
        assertThat(participantEvents()).isZero();assertThat(participantOutbox()).isZero();
    }
    @ParameterizedTest @CsvSource({"revision,bad","revision,-1","participantRevision,9223372036854775808","participantRevision,01","memberId,bad"})
    void malformedParticipantParametersNeverWrite(String field,String value)throws Exception {
        var request=post(root+"/participants/add").param("revision",field.equals("revision")?value:"0")
            .param("participantRevision",field.equals("participantRevision")?value:"0").param("memberId",field.equals("memberId")?value:Long.toString(readerId));
        mvc.perform(actor(request,worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isBadRequest());
        assertThat(participantEvents()).isZero();assertThat(participantOutbox()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"member-read","member-disabled","member-blocked","member-duplicate","member-business-only","member-ai",
        "actor-read","actor-operate","actor-disabled","actor-blocked","actor-duplicate","actor-business-only","actor-ai","padded-principal",
        "owner","scope","generation","collision","closed","revision","flag","foundation"})
    void participantMutationsRequireCurrentGeneralOwnerAndIndependentGrants(String mutation) {
        var expected=participantScope();String identity=worker;
        switch(mutation){
            case "member-read"->access.revokeRead(administrator(jdbc),readerId);
            case "member-disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",readerId);
            case "member-blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",readerId);
            case "member-duplicate"->account(reader.toUpperCase(java.util.Locale.ROOT));
            case "member-business-only"->{access.revokeRead(administrator(jdbc),readerId);jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(?,'ALL_BUSINESSES','ADMIN')",readerId);}
            case "member-ai"->jdbc.update("UPDATE users SET username=' AI_AGENT ' WHERE id=?",readerId);
            case "actor-read"->access.revokeRead(administrator(jdbc),workerId);
            case "actor-operate"->access.revokeOperate(administrator(jdbc),workerId);
            case "actor-disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",workerId);
            case "actor-blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",workerId);
            case "actor-duplicate"->account(worker.toUpperCase(java.util.Locale.ROOT));
            case "actor-business-only"->{access.revokeRead(administrator(jdbc),workerId);jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(?,'ALL_BUSINESSES','ADMIN')",workerId);}
            case "actor-ai"->{identity=" AI_AGENT  ";jdbc.update("UPDATE users SET username=? WHERE id=?",identity,workerId);}
            case "padded-principal"->identity=" "+worker+" ";
            case "owner"->jdbc.update("UPDATE tickets SET user_id=? WHERE ticket_public_id=?",owner+1000000,publicId);
            case "scope"->jdbc.update("UPDATE tickets SET is_general=FALSE,general_selection_source=NULL WHERE ticket_public_id=?",publicId);
            case "generation"->{jdbc.update("DELETE FROM tickets WHERE ticket_public_id=?",publicId);jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,is_general,general_selection_source,status) VALUES(?,?,?,TRUE,'GENERAL_CHANNEL','pending')",owner,raw,UUID.randomUUID().toString().replace("-",""));}
            case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,status) VALUES(?,?,'open')",owner+1,raw);
            case "closed"->jdbc.update("UPDATE tickets SET status='closed' WHERE ticket_public_id=?",publicId);
            case "revision"->jdbc.update("UPDATE tickets SET status='open' WHERE ticket_public_id=?",publicId);
            case "flag"->flags.setGeneralTicketQueue(false);
            case "foundation"->flags.getFoundation().setEnabled(false);
        }
        assertThat(participants.change(expected,auth(jdbc,identity),0,0,readerId,true)).isFalse();
        assertThat(participantEvents()).isZero();assertThat(participantOutbox()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM general_ticket_participants WHERE ticket_public_id=?",Long.class,publicId)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"read","operate","member-read","revision"})
    void participantControllerReadIsNotFinalAuthority(String mutation)throws Exception {
        doAnswer(call->{switch(mutation){
            case "read"->access.revokeRead(administrator(jdbc),workerId);case "operate"->access.revokeOperate(administrator(jdbc),workerId);
            case "member-read"->access.revokeRead(administrator(jdbc),readerId);case "revision"->jdbc.update("UPDATE tickets SET status='open' WHERE ticket_public_id=?",publicId);
        }return call.callRealMethod();}).when(participants).change(eq(participantScope()),org.mockito.ArgumentMatchers.argThat(a->a!=null&&a.getName().equals(worker)&&a.getPrincipal() instanceof com.example.panel.security.PanelUserPrincipal p&&p.getUserId()==workerId),eq(0L),eq(0L),eq(readerId),eq(true));
        mvc.perform(actor(participantCommand("add","0","0",Long.toString(readerId)),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(participantEvents()).isZero();assertThat(participantOutbox()).isZero();
    }
    @ParameterizedTest @CsvSource({"add,actor","add,member","add,owner","add,collision","remove,actor","remove,owner","remove,collision"})
    void finalParticipantSqlRepeatsProofAtTheJdbcBoundary(String action,String mutation) {
        boolean add=action.equals("add");
        if(!add)assertThat(participants.change(participantScope(),auth(jdbc,worker),0,0,readerId,true)).isTrue();
        var before=jdbc.queryForList("SELECT * FROM general_ticket_participants WHERE ticket_public_id=?",publicId);
        var observed=spy(jdbc);var injected=new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call->{
            var creator=call.getArgument(0,org.springframework.jdbc.core.PreparedStatementCreator.class);
            String sql=((org.springframework.jdbc.core.SqlProvider)creator).getSql();
            if((sql.startsWith("INSERT INTO general_ticket_participants")||sql.startsWith("DELETE FROM general_ticket_participants"))&&injected.compareAndSet(false,true)){
                // Same test transaction: deterministic final-statement injection, not a grant lock bypass.
                if(mutation.equals("actor"))access.revokeOperate(administrator(jdbc),workerId);
                if(mutation.equals("member"))access.revokeRead(administrator(jdbc),readerId);
                if(mutation.equals("owner"))jdbc.update("UPDATE tickets SET user_id=? WHERE ticket_public_id=?",owner+1000000,publicId);
                if(mutation.equals("collision"))jdbc.update("INSERT INTO tickets(user_id,ticket_id,status) VALUES(?,?,'open')",owner+1,raw);
            }
            return call.callRealMethod();
        }).when(observed).update(org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.PreparedStatementCreator.class));
        var writer=new GeneralTicketParticipantService(observed,flags,participantOutboxIds);
        assertThat(writer.change(participantScope(),auth(observed,worker),0,add?0:1,readerId,add)).isFalse();assertThat(injected).isTrue();
        assertThat(jdbc.queryForList("SELECT * FROM general_ticket_participants WHERE ticket_public_id=?",publicId)).isEqualTo(before);
        assertThat(participantEvents()).isEqualTo(add?0:1);assertThat(participantOutbox()).isEqualTo(add?0:1);
    }
    @ParameterizedTest @ValueSource(strings={"actor-read","actor-operate","target-read","target-account","root"})
    void waitingParticipantWriterRechecksACommittedRevocationOrRootChange(String mutation)throws Exception {
        var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        try(var connection=jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            String sql=switch(mutation) {
                case "actor-read","target-read"->"UPDATE general_ticket_read_grants SET is_active=FALSE WHERE user_id=?";
                case "actor-operate"->"UPDATE general_ticket_operate_grants SET is_active=FALSE WHERE user_id=?";
                case "target-account"->"UPDATE users SET enabled=FALSE WHERE id=?";
                default->"UPDATE tickets SET status='closed' WHERE ticket_public_id=?";
            };
            try(var update=connection.prepareStatement(sql)) {
                if(mutation.equals("root"))update.setString(1,publicId);
                else update.setLong(1,mutation.startsWith("actor")?workerId:readerId);
                update.executeUpdate();
            }
            var started=new java.util.concurrent.CountDownLatch(1);
            var pending=pool.submit(()->{started.countDown();return participants.change(participantScope(),auth(jdbc,worker),0,0,readerId,true);});
            assertThat(started.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->pending.get(150,java.util.concurrent.TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
            connection.commit();assertThat(pending.get(10,java.util.concurrent.TimeUnit.SECONDS)).isFalse();
            assertThat(participantEvents()).isZero();assertThat(participantOutbox()).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM general_ticket_participants WHERE ticket_public_id=?",Long.class,publicId)).isZero();
        }finally{pool.shutdownNow();}
    }
    @Test void concurrentParticipantFormsHaveOneWinnerAndAbaNeverReusesARevision()throws Exception {
        var start=new java.util.concurrent.CountDownLatch(1);
        java.util.function.Supplier<Boolean> attempt=()->{try{start.await();}catch(InterruptedException ex){throw new IllegalStateException(ex);}return participants.change(participantScope(),auth(jdbc,worker),0,0,readerId,true);};
        var a=java.util.concurrent.CompletableFuture.supplyAsync(attempt);var b=java.util.concurrent.CompletableFuture.supplyAsync(attempt);start.countDown();
        assertThat(java.util.List.of(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        assertThat(participants.change(participantScope(),auth(jdbc,worker),0,1,readerId,true)).isFalse();
        assertThat(participants.change(participantScope(),auth(jdbc,worker),0,1,workerId,false)).isFalse();
        assertThat(participants.change(participantScope(),auth(jdbc,worker),0,1,readerId,false)).isTrue();
        assertThat(participants.change(participantScope(),auth(jdbc,worker),0,0,readerId,true)).isFalse();
        assertThat(participants.change(participantScope(),auth(jdbc,worker),0,2,readerId,true)).isTrue();
        assertThat(participantEvents()).isEqualTo(3);assertThat(participantOutbox()).isEqualTo(3);
    }
    @ParameterizedTest @ValueSource(strings={"audit","outbox"})
    void failedParticipantAuditOrOutboxRollsBackAddAndRemove(String failure) {
        assertThat(participants.change(participantScope(),auth(jdbc,worker),0,0,readerId,true)).isTrue();
        String table=failure.equals("audit")?"general_ticket_participant_events":"ui_event_outbox";
        jdbc.execute("CREATE FUNCTION fail_r165_insert() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic R165 failure'; END; $$");
        jdbc.execute("CREATE TRIGGER fail_r165_insert BEFORE INSERT ON "+table+" FOR EACH ROW EXECUTE FUNCTION fail_r165_insert()");
        try {
            assertThat(participants.change(participantScope(),auth(jdbc,worker),0,1,workerId,true)).isFalse();
            assertThat(participants.change(participantScope(),auth(jdbc,worker),0,1,readerId,false)).isFalse();
            assertThat(participants.members(participantScope(),auth(jdbc,worker))).extracting(GeneralTicketParticipantService.Member::id).containsExactly(readerId);
            assertThat(participants.revision(participantScope(),auth(jdbc,worker))).contains(1L);
            assertThat(participantEvents()).isEqualTo(1);assertThat(participantOutbox()).isEqualTo(1);
        }finally{jdbc.execute("DROP TRIGGER fail_r165_insert ON "+table);jdbc.execute("DROP FUNCTION fail_r165_insert()");}
        assertThat(participants.change(participantScope(),auth(jdbc,worker),0,1,readerId,false)).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"read","disabled","deleted"})
    void operatorCanRemoveAnInaccessibleOrDeletedGeneralMember(String kind) {
        assertThat(participants.change(participantScope(),auth(jdbc,worker),0,0,readerId,true)).isTrue();
        if(kind.equals("read"))access.revokeRead(administrator(jdbc),readerId);
        if(kind.equals("disabled"))jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",readerId);
        if(kind.equals("deleted"))jdbc.update("DELETE FROM users WHERE id=?",readerId);
        assertThat(participants.members(participantScope(),auth(jdbc,worker))).singleElement().satisfies(m->assertThat(m.eligible()).isFalse());
        assertThat(participants.change(participantScope(),auth(jdbc,worker),0,1,readerId,false)).isTrue();
        assertThat(participants.members(participantScope(),auth(jdbc,worker))).isEmpty();
    }
    @Test void numericMembershipDoesNotTransferOnRenameOrReusedUsername() {
        assertThat(participants.change(participantScope(),auth(jdbc,worker),0,0,readerId,true)).isTrue();
        String renamed=reader+" renamed";jdbc.update("UPDATE users SET username=? WHERE id=?",renamed,readerId);
        long reused=account(reader);access.grantRead(administrator(jdbc),reused);
        assertThat(participants.members(participantScope(),auth(jdbc,worker))).singleElement().satisfies(m->{assertThat(m.id()).isEqualTo(readerId);assertThat(m.username()).isEqualTo(renamed);});
        assertThat(participants.candidates(participantScope(),auth(jdbc,worker),0)).extracting(GeneralTicketParticipantService.Candidate::id).contains(reused).doesNotContain(readerId);
    }
    @Test void participantHistoryIdentityAndGenerationAreIsolated() {
        assertThat(participants.change(participantScope(),auth(jdbc,worker),0,0,readerId,true)).isTrue();
        assertThatThrownBy(()->jdbc.update("UPDATE general_ticket_participants SET member_user_id=? WHERE ticket_public_id=?",workerId,publicId)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE general_ticket_participant_events SET actor='changed' WHERE ticket_public_id=?",publicId)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM general_ticket_participant_events WHERE ticket_public_id=?",publicId)).isInstanceOf(RuntimeException.class);
        jdbc.update("DELETE FROM tickets WHERE ticket_public_id=?",publicId);
        String next=UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,is_general,general_selection_source,status) VALUES(?,?,?,TRUE,'GENERAL_CHANNEL','pending')",owner,raw,next);
        var newScope=new GeneralTicketEventScope(owner,raw,next);
        assertThat(participants.members(newScope,auth(jdbc,worker))).isEmpty();assertThat(participants.recent(newScope,auth(jdbc,worker))).isEmpty();
        assertThat(participants.revision(newScope,auth(jdbc,worker))).contains(0L);assertThat(participants.revision(participantScope(),auth(jdbc,worker))).isEmpty();
    }
    @Test void statusAndAssignmentRevisionsRemainIndependentOfTheRoster()throws Exception {
        assertThat(assignments.assign(participantScope(),auth(jdbc,worker),0,0,workerId)).isTrue();
        assertThat(participants.change(participantScope(),auth(jdbc,worker),0,0,readerId,true)).isTrue();
        assertThat(work.transition(participantScope(),auth(jdbc,worker),0,"pending","open")).isTrue();
        assertThat(participants.change(participantScope(),auth(jdbc,worker),0,1,readerId,false)).isFalse();
        assertThat(participants.change(participantScope(),auth(jdbc,worker),1,1,readerId,false)).isTrue();
        assertThat(assignments.current(participantScope(),auth(jdbc,worker)).orElseThrow().revision()).isEqualTo(1);
        assertThat(work.find(publicId,auth(jdbc,worker)).orElseThrow().revision()).isEqualTo(1);
        assertThat(participants.revision(participantScope(),auth(jdbc,worker))).contains(2L);
        jdbc.update("UPDATE tickets SET status='closed' WHERE ticket_public_id=?",publicId);
        var html=mvc.perform(actor(get(root),worker,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("id=\"generalParticipant\"","/participants/remove");
    }
    @Test void generalRosterBoundIsEnforcedAndRemovalFreesCapacity() {
        jdbc.update("INSERT INTO users(username,password) SELECT ?||generate_series(1,200),'unused'",worker+"-bound-");
        jdbc.update("INSERT INTO general_ticket_read_grants(user_id,is_active) SELECT id,TRUE FROM users WHERE username LIKE ?",worker+"-bound-%");
        jdbc.update("INSERT INTO general_ticket_participants(ticket_user_id,ticket_id,ticket_public_id,member_user_id,member,actor_user_id,actor,revision,owner_revision)"
            +" SELECT ?,?,?,id,username,?,?,row_number() OVER(ORDER BY id),0 FROM users WHERE username LIKE ?",owner,raw,publicId,workerId,worker,worker+"-bound-%");
        jdbc.update("INSERT INTO general_ticket_participant_events(ticket_user_id,ticket_id,ticket_public_id,revision,owner_revision,member_user_id,member,actor_user_id,actor,action)"
            +" SELECT ticket_user_id,ticket_id,ticket_public_id,revision,owner_revision,member_user_id,member,actor_user_id,actor,'add' FROM general_ticket_participants WHERE ticket_public_id=?",publicId);
        assertThat(participants.members(participantScope(),auth(jdbc,worker))).hasSize(200);
        assertThat(participants.change(participantScope(),auth(jdbc,worker),0,200,readerId,true)).isFalse();
        assertThat(participants.change(participantScope(),auth(jdbc,worker),0,200,participants.members(participantScope(),auth(jdbc,worker)).get(0).id(),false)).isTrue();
        assertThat(participants.change(participantScope(),auth(jdbc,worker),0,201,readerId,true)).isTrue();
        assertThat(participants.members(participantScope(),auth(jdbc,worker))).hasSize(200);
    }
    @ParameterizedTest @ValueSource(strings={"roster","events"})
    void missingGeneralParticipantSchemaCannotWriteOrAdoptLegacyRows(String kind) {
        jdbc.update("INSERT INTO ticket_participants(ticket_id,username,added_by) VALUES(?,?,'legacy')",raw,reader);
        String table=kind.equals("roster")?"general_ticket_participants":"general_ticket_participant_events";
        jdbc.execute("ALTER TABLE "+table+" RENAME TO r165_hidden_participants");
        try {
            assertThat(participants.change(participantScope(),auth(jdbc,worker),0,0,readerId,true)).isFalse();
            assertThat(participantOutbox()).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_participants WHERE ticket_id=?",Long.class,raw)).isEqualTo(1);
        }finally{jdbc.execute("ALTER TABLE r165_hidden_participants RENAME TO "+table);}
        assertThat(participantEvents()).isZero();
    }
    @Test void v79UpgradePreservesRootsLegacyAndBusinessRosterWithoutCreatingGeneralMembership() {
        var old=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("r165_upgrade");
        org.flywaydb.core.Flyway.configure().dataSource(old.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").target("79").load().migrate();
        long business=old.queryForObject("INSERT INTO businesses(code,display_name) VALUES('upgrade','Upgrade') RETURNING id",Long.class);
        long user=old.queryForObject("INSERT INTO users(username,password) VALUES('old-member','unused') RETURNING id",Long.class);
        String businessPublic=UUID.randomUUID().toString().replace("-",""),generalPublic=UUID.randomUUID().toString().replace("-","");
        old.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,status) VALUES(700,'old-business',?,?,'open')",businessPublic,business);
        old.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,is_general,general_selection_source,status) VALUES(701,'old-general',?,TRUE,'GENERAL_CHANNEL','pending')",generalPublic);
        old.update("INSERT INTO ticket_participants(ticket_id,username,added_by) VALUES('old-general','legacy-member','legacy')");
        old.update("INSERT INTO business_ticket_participants(ticket_user_id,ticket_id,ticket_public_id,business_id,member_user_id,member,actor_user_id,actor,revision)"
            +" VALUES(700,'old-business',?,?,?,'old-member',?,'old-actor',1)",businessPublic,business,user,user);
        old.update("INSERT INTO business_ticket_participant_events(ticket_user_id,ticket_id,ticket_public_id,business_id,member_user_id,member,actor_user_id,actor,revision,action)"
            +" VALUES(700,'old-business',?,?,?,'old-member',?,'old-actor',1,'add')",businessPublic,business,user,user);
        var before=new LinkedHashMap<String,java.util.List<Map<String,Object>>>();
        for(String table:new String[]{"tickets","ticket_participants","business_ticket_participants","business_ticket_participant_events","general_ticket_read_grants","general_ticket_operate_grants"})
            before.put(table,old.queryForList("SELECT * FROM "+table));
        org.flywaydb.core.Flyway.configure().dataSource(old.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").target("80").load().migrate();
        before.forEach((table,rows)->assertThat(old.queryForList("SELECT * FROM "+table)).isEqualTo(rows));
        assertThat(old.queryForObject("SELECT count(*) FROM general_ticket_participants",Long.class)).isZero();
        assertThat(old.queryForObject("SELECT count(*) FROM general_ticket_participant_events",Long.class)).isZero();
        String oldColumns=before.get("tickets").get(0).keySet().stream().map(column->"\""+column.replace("\"","\"\"")+"\"").collect(java.util.stream.Collectors.joining(","));
        org.flywaydb.core.Flyway.configure().dataSource(old.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        assertThat(old.queryForList("SELECT "+oldColumns+" FROM tickets")).isEqualTo(before.get("tickets"));
        before.forEach((table,rows)->{if(!table.equals("tickets"))assertThat(old.queryForList("SELECT * FROM "+table)).isEqualTo(rows);});
        assertThat(old.queryForList("SELECT business_work_revision,business_categories::text AS categories FROM tickets"))
            .allSatisfy(row->assertThat(row).containsEntry("business_work_revision",0L).containsEntry("categories","[]"));
        for(String table:java.util.List.of("general_ticket_participants","general_ticket_participant_events","business_ticket_work_events"))
            assertThat(old.queryForObject("SELECT count(*) FROM "+table,Long.class)).isZero();
    }
    private long account(String name) {return jdbc.queryForObject("INSERT INTO users(username,password,enabled) VALUES(?,'unused',TRUE) RETURNING id",Long.class,name);}
    private MockHttpServletRequestBuilder actor(MockHttpServletRequestBuilder request,String name,String...authorities) {
        long id=jdbc.queryForObject("SELECT id FROM users WHERE username=?",Long.class,name);
        jdbc.update("DELETE FROM user_authorities WHERE user_id=?",id);
        for(String authority:authorities)jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(?,?)",id,authority);
        return request.with(user(new PanelUserPrincipal(id,name,"unused",true,java.util.Arrays.stream(authorities)
            .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList())));
    }
    @Test void unboundSessionStillRedirectsToLogin() throws Exception {
        mvc.perform(get(root).with(user(worker).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("PAGE_DIALOGS"))))
            .andExpect(redirectedUrl("http://localhost/login"));
    }
    @Test void boundFixtureStillRefreshesRevokedPageAuthorities() throws Exception {
        var request=actor(get(root),worker,"PAGE_DIALOGS");
        jdbc.update("DELETE FROM user_authorities WHERE user_id=?",workerId);
        mvc.perform(request).andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings={"disabled","blocked","renamed","deleted"})
    void boundFixtureStillRejectsAccountChangesBeforeTheRequest(String mode) throws Exception {
        var request=actor(get(root),worker,"PAGE_DIALOGS");
        switch(mode){
            case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",workerId);
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",workerId);
            case "renamed"->jdbc.update("UPDATE users SET username=username||'-renamed' WHERE id=?",workerId);
            default->jdbc.update("DELETE FROM users WHERE id=?",workerId);
        }
        mvc.perform(request).andExpect(redirectedUrl("http://localhost/login"));
    }
    private MockHttpServletRequestBuilder change(String version,String from,String to) {return post(root+"/status").param("revision",version).param("expectedStatus",from).param("nextStatus",to);}
    private RequestPostProcessor csrf() {return request->{
        CsrfToken token=CookieCsrfTokenRepository.withHttpOnlyFalse().generateToken(request);request.setCookies(new Cookie("XSRF-TOKEN",token.getToken()));
        new XorCsrfTokenRequestAttributeHandler().handle(request,new MockHttpServletResponse(),()->token);
        CsrfToken masked=(CsrfToken)request.getAttribute(CsrfToken.class.getName());request.setParameter(masked.getParameterName(),masked.getToken());return request;
    };}
    private String hidden(String html,String name) {var matcher=Pattern.compile("<input[^>]*name=\""+name+"\"[^>]*value=\"([^\"]*)\"").matcher(html);assertThat(matcher.find()).isTrue();return HtmlUtils.htmlUnescape(matcher.group(1));}

    private String confirmedReply() {
        replyDestination();String key=UUID.randomUUID().toString().replace("-","");
        var scope=new GeneralTicketEventScope(owner,raw,publicId);
        assertThat(replies.submit(scope,auth(jdbc,worker),0,key,"Original text",replies.destinationFingerprint(scope,auth(jdbc,worker),0).orElseThrow())).isTrue();
        clearInvocations(replyTransport);
        doAnswer(call->{
            assertThat(((java.util.function.BooleanSupplier)call.getArgument(4)).getAsBoolean()).isTrue();
            return DialogReplyTransportService.DialogReplyTransportResult.success(77L,1L);
        }).when(replyTransport).editTelegramMessageGuarded(any(),anyLong(),anyLong(),anyString(),any());
        doAnswer(call->{
            assertThat(((java.util.function.BooleanSupplier)call.getArgument(3)).getAsBoolean()).isTrue();
            return DialogReplyTransportService.DialogReplyTransportResult.success(77L,1L);
        }).when(replyTransport).deleteTelegramMessageGuarded(any(),anyLong(),anyLong(),any());
        return key;
    }
    @ParameterizedTest @ValueSource(strings={"EDIT","DELETE"})
    void renderedChangeFormUsesRealCsrfAndDuplicatePostChangesProviderOnce(String action) throws Exception {
        confirmedReply();var opened=mvc.perform(actor(get(root),worker,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn();
        String html=form(opened.getResponse().getContentAsString(),"EDIT".equals(action)?"data-general-reply-edit":"data-general-reply-delete");
        String message="EDIT".equals(action)?"<script>Changed 😀</script>":"";
        for(int i=0;i<2;i++)mvc.perform(actor(post(root+"/reply-change").param("revision",hidden(html,"revision"))
            .param("requestKey",hidden(html,"requestKey")).param("sourceKey",hidden(html,"sourceKey"))
            .param("expected",hidden(html,"expected")).param("action",action).param("message",message)
            .param("_csrf",hidden(html,"_csrf")).cookie(opened.getResponse().getCookies()),worker,"PAGE_DIALOGS"))
            .andExpect(redirectedUrl(root));
        if("EDIT".equals(action))verify(replyTransport,times(1)).editTelegramMessageGuarded(any(),eq(owner),eq(201L),eq(message),any());
        else verify(replyTransport,times(1)).deleteTelegramMessageGuarded(any(),eq(owner),eq(201L),any());
        var refreshed=mvc.perform(actor(get(root),worker,"PAGE_DIALOGS")).andExpect(status().isOk())
            .andExpect(content().string(containsString("Провайдер подтвердил изменение"))).andExpect(content().string(not(containsString(raw)))).andReturn();
        if("EDIT".equals(action))assertThat(refreshed.getResponse().getContentAsString()).contains("&lt;script&gt;Changed").doesNotContain("<script>Changed");
        else assertThat(refreshed.getResponse().getContentAsString()).doesNotContain("data-general-reply-edit");
        var history=mvc.perform(actor(get("/api/general-tickets/"+publicId+"/history"),reader,"PAGE_DIALOGS")).andExpect(status().isOk());
        if("EDIT".equals(action))history.andExpect(jsonPath("$.entries[0].message").value(message));else history.andExpect(jsonPath("$.entries").isEmpty());
    }
    private MockHttpServletRequestBuilder changeReplyPost(String sourceKey) {
        var target=changes.targets(new GeneralTicketEventScope(owner,raw,publicId),auth(jdbc,worker),0).get(0);
        return post(root+"/reply-change").param("revision","0").param("requestKey",UUID.randomUUID().toString().replace("-",""))
            .param("sourceKey",sourceKey).param("expected",target.fingerprint()).param("action","EDIT").param("message","Changed");
    }
    @Test void changeRequiresPageSeparateOperateRealCsrfAndValidInputs() throws Exception {
        String source=confirmedReply();
        mvc.perform(actor(get(root),reader,"PAGE_DIALOGS")).andExpect(status().isOk()).andExpect(content().string(not(containsString("data-general-reply-edit"))));
        mvc.perform(actor(changeReplyPost(source),worker,"PAGE_DIALOGS")).andExpect(redirectedUrl("/error/403"));
        mvc.perform(actor(changeReplyPost(source),reader,"PAGE_DIALOGS").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(actor(changeReplyPost(source),worker,"ROLE_ADMIN").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(actor(changeReplyPost("invalid"),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isBadRequest());
        verifyNoInteractions(replyTransport);
    }
    @ParameterizedTest @ValueSource(strings={"read","operate","owner","revision","flag","channel","content"})
    void changeReprovesLateChangesAfterControllerLookup(String mutation) throws Exception {
        String source=confirmedReply();var post=changeReplyPost(source);var scope=new GeneralTicketEventScope(owner,raw,publicId);
        doAnswer(call->{
            switch(mutation) {
                case "read"->access.revokeRead(administrator(jdbc),workerId);
                case "operate"->access.revokeOperate(administrator(jdbc),workerId);
                case "owner"->jdbc.update("UPDATE tickets SET is_general=FALSE,general_selection_source=NULL WHERE ticket_public_id=?",publicId);
                case "revision"->jdbc.update("UPDATE tickets SET status='open' WHERE ticket_public_id=?",publicId);
                case "flag"->flags.setGeneralTicketQueue(false);
                case "channel"->jdbc.update("UPDATE channels SET token='rotated' WHERE id=(SELECT channel_id FROM tickets WHERE ticket_public_id=?)",publicId);
                case "content"->jdbc.update("UPDATE chat_history SET message='changed elsewhere' WHERE ticket_public_id=?",publicId);
            }
            return call.callRealMethod();
        }).when(changes).submit(eq(scope),org.mockito.ArgumentMatchers.argThat(a->a!=null&&a.getName().equals(worker)&&a.getPrincipal() instanceof com.example.panel.security.PanelUserPrincipal p&&p.getUserId()==workerId),anyLong(),anyString(),anyString(),anyString(),anyString(),anyString());
        mvc.perform(actor(post,worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());verifyNoInteractions(replyTransport);
    }
    @Test void ambiguousChangeIsVisibleAndHidesFurtherCommands() throws Exception {
        String source=confirmedReply();
        doReturn(null).when(replyTransport).editTelegramMessageGuarded(any(),anyLong(),anyLong(),anyString(),any());
        mvc.perform(actor(changeReplyPost(source),worker,"PAGE_DIALOGS").with(csrf())).andExpect(redirectedUrl(root));
        mvc.perform(actor(get(root),reader,"PAGE_DIALOGS")).andExpect(status().isOk())
            .andExpect(content().string(containsString("Результат изменения требует проверки")))
            .andExpect(content().string(not(containsString("Провайдер подтвердил изменение"))));
        mvc.perform(actor(get(root),worker,"PAGE_DIALOGS")).andExpect(status().isOk()).andExpect(content().string(not(containsString("data-general-reply-edit"))));
    }

    private String documentDestination(){
        replyDestination();
        doAnswer(call->{assertThat(((java.util.function.BooleanSupplier)call.getArgument(4)).getAsBoolean()).isTrue();
            return DialogReplyTransportService.DialogReplyTransportResult.success(301L,1L);
        }).when(replyTransport).sendTelegramDocumentGuarded(any(),anyLong(),any(),anyString(),any());
        return documents.destinationFingerprint(new GeneralTicketEventScope(owner,raw,publicId),auth(jdbc,worker),0).orElseThrow();
    }
    private MockHttpServletRequestBuilder documentPost(String key,String fingerprint){
        return multipart(root+"/document").file(new org.springframework.mock.web.MockMultipartFile("file","Отчёт.pdf","text/html",new byte[]{0,1,2,-1,10}))
            .param("revision","0").param("requestKey",key).param("caption","<script>caption</script>").param("destination",fingerprint);
    }
    @Test void actualMultipartCsrfDuplicatePostAndAuthorizedDownloadPreserveExactBytes()throws Exception{
        documentDestination();var opened=mvc.perform(actor(get(root),worker,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn();
        String html=form(opened.getResponse().getContentAsString(),"data-general-document-form");String key=hidden(html,"requestKey");
        for(int i=0;i<2;i++)mvc.perform(actor(documentPost(key,hidden(html,"destination")).param("_csrf",hidden(html,"_csrf")).cookie(opened.getResponse().getCookies()),worker,"PAGE_DIALOGS"))
            .andExpect(redirectedUrl(root));
        verify(replyTransport,times(1)).sendTelegramDocumentGuarded(any(),eq(owner),any(),eq("<script>caption</script>"),any());
        long history=jdbc.queryForObject("SELECT chat_history_id FROM general_ticket_document_replies WHERE request_key=?",Long.class,key);
        mvc.perform(actor(get("/api/general-tickets/"+publicId+"/history/"+history+"/attachment"),reader,"PAGE_DIALOGS"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"))
            .andExpect(header().string("Content-Disposition",containsString("attachment"))).andExpect(content().contentTypeCompatibleWith("application/octet-stream"))
            .andExpect(content().bytes(new byte[]{0,1,2,-1,10}));
        mvc.perform(actor(get(root),reader,"PAGE_DIALOGS")).andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;caption&lt;/script&gt;")))
            .andExpect(content().string(not(containsString("data-general-document-form")))).andExpect(content().string(containsString("Провайдер подтвердил отправку документа")));
        mvc.perform(actor(get("/api/general-tickets/"+publicId+"/history"),reader,"PAGE_DIALOGS")).andExpect(status().isOk()).andExpect(jsonPath("$.entries[0].hasAttachment").value(true));
    }
    @Test void documentRequiresPageOperateCsrfValidFilenameAndBody()throws Exception{
        String fingerprint=documentDestination();String key=UUID.randomUUID().toString().replace("-","");
        mvc.perform(actor(documentPost(key,fingerprint),worker,"PAGE_DIALOGS")).andExpect(redirectedUrl("/error/403"));
        mvc.perform(actor(documentPost(key,fingerprint),reader,"PAGE_DIALOGS").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(actor(documentPost(key,fingerprint),worker,"ROLE_ADMIN").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(actor(documentPost("bad",fingerprint),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(actor(multipart(root+"/document").file(new org.springframework.mock.web.MockMultipartFile("file","../bad","text/html",new byte[]{1}))
            .param("revision","0").param("requestKey",key).param("destination",fingerprint),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isBadRequest());
        verifyNoInteractions(replyTransport);
    }
    @ParameterizedTest @ValueSource(strings={"read","operate","scope","revision","flag","channel"})
    void documentReprovesLateChangesBeforePersistingOrSending(String mutation)throws Exception{
        String fingerprint=documentDestination();String key=UUID.randomUUID().toString().replace("-","");var scope=new GeneralTicketEventScope(owner,raw,publicId);
        doAnswer(call->{switch(mutation){
            case "read"->access.revokeRead(administrator(jdbc),workerId);case "operate"->access.revokeOperate(administrator(jdbc),workerId);
            case "scope"->jdbc.update("UPDATE tickets SET is_general=FALSE,general_selection_source=NULL WHERE ticket_public_id=?",publicId);
            case "revision"->jdbc.update("UPDATE tickets SET status='open' WHERE ticket_public_id=?",publicId);
            case "flag"->flags.setGeneralTicketQueue(false);
            case "channel"->jdbc.update("UPDATE channels SET token=? WHERE id=(SELECT channel_id FROM tickets WHERE ticket_public_id=?)","rotated-"+publicId,publicId);
        }return call.callRealMethod();}).when(documents).submit(eq(scope),org.mockito.ArgumentMatchers.argThat(a->a!=null&&a.getName().equals(worker)&&a.getPrincipal() instanceof com.example.panel.security.PanelUserPrincipal p&&p.getUserId()==workerId),anyLong(),anyString(),anyString(),any(),anyString());
        mvc.perform(actor(documentPost(key,fingerprint),worker,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM general_ticket_document_replies WHERE request_key=?",Integer.class,key)).isZero();verifyNoInteractions(replyTransport);
    }
    @ParameterizedTest @ValueSource(strings={"bytes","size","late-read","metadata"})
    void downloadRejectsObjectTamperingAndRightsRevocationDuringStorageRead(String mutation)throws Exception{
        String fingerprint=documentDestination();String key=UUID.randomUUID().toString().replace("-","");
        mvc.perform(actor(documentPost(key,fingerprint),worker,"PAGE_DIALOGS").with(csrf())).andExpect(redirectedUrl(root));
        long history=jdbc.queryForObject("SELECT chat_history_id FROM general_ticket_document_replies WHERE request_key=?",Long.class,key);
        String object=publicId+"/general-"+key+".bin";
        switch(mutation){
            case "bytes"->documentObjects.put(object,new byte[5]);case "size"->documentObjects.put(object,new byte[2]);
            case "metadata"->jdbc.update("UPDATE chat_attachment_metadata SET content_hash='bad' WHERE chat_history_id=?",history);
            case "late-read"->doAnswer(call->{access.revokeRead(administrator(jdbc),readerId);byte[] bytes=documentObjects.get(object);
                return new com.example.panel.storage.AttachmentObjectStorageService.StoredBinary(object,"application/octet-stream",bytes.length,new java.io.ByteArrayInputStream(bytes));
            }).when(documentStorage).openDialogAttachmentByStorageKey(eq(object));
        }
        mvc.perform(actor(get("/api/general-tickets/"+publicId+"/history/"+history+"/attachment"),reader,"PAGE_DIALOGS")).andExpect(status().isNotFound());
    }
    @Test void unknownDocumentResultIsVisibleAndSameKeyNeverResends()throws Exception{
        String fingerprint=documentDestination();String key=UUID.randomUUID().toString().replace("-","");
        doReturn(null).when(replyTransport).sendTelegramDocumentGuarded(any(),anyLong(),any(),anyString(),any());
        for(int i=0;i<2;i++)mvc.perform(actor(documentPost(key,fingerprint),worker,"PAGE_DIALOGS").with(csrf())).andExpect(redirectedUrl(root));
        mvc.perform(actor(get(root),reader,"PAGE_DIALOGS")).andExpect(status().isOk()).andExpect(content().string(containsString("Результат отправки документа требует проверки")));
        verify(replyTransport,times(1)).sendTelegramDocumentGuarded(any(),anyLong(),any(),anyString(),any());
    }
}
