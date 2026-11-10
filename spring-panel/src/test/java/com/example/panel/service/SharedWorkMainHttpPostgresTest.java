package com.example.panel.service;

import com.example.panel.controller.*;
import com.example.panel.entity.*;
import com.example.panel.repository.*;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import jakarta.persistence.EntityManagerFactory;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

class SharedWorkMainHttpPostgresTest {
    static JdbcTemplate jdbc;
    static AnnotationConfigWebApplicationContext context;
    static MockMvc mvc;
    static final long ADMIN=9007199254740993L,USER=11,VIEWER=12;
    static final String ADMIN_NAME=" SysAdmin/Ю ";
    static final com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();
    @BeforeAll static void start() {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("shared_main_http");
        String schema=jdbc.queryForObject("SELECT current_schema()",String.class);
        Flyway.configure().dataSource(jdbc.getDataSource()).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration/postgresql").target("89").load().migrate();
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new org.springframework.mock.web.MockServletContext());
        context.register(Config.class);context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
    }
    @AfterEach void clearIdentity(){org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    @AfterAll static void stop(){if(context!=null)context.close();}
    @BeforeEach void data(){
        jdbc.execute("TRUNCATE users,tasks,projects,task_seq,shared_work_access_audit RESTART IDENTITY CASCADE;INSERT INTO task_seq VALUES(1,0)");
        jdbc.update("INSERT INTO users(id,username,password,role_id) OVERRIDING SYSTEM VALUE VALUES(?,?,'unused',NULL),(11,'owner','unused',NULL),(12,'viewer','unused',NULL)",ADMIN,ADMIN_NAME);
        jdbc.update("INSERT INTO user_authorities VALUES(?,'ROLE_ADMIN'),(11,'PAGE_TASKS'),(12,'PAGE_TASKS')",ADMIN);
        jdbc.execute("INSERT INTO shared_work_grants(user_id,can_read) VALUES(11,TRUE),(12,TRUE)");
        reset(context.getBean(NotificationRoutingService.class),context.getBean(UiEventTicketAccessService.class),context.getBean(UiEventFanoutPublisher.class));
    }
    static Authentication actor(long id) {
        String name=id==ADMIN?ADMIN_NAME:id==USER?"owner":"viewer";
        var roles=List.of(new SimpleGrantedAuthority(id==ADMIN?"ROLE_ADMIN":"PAGE_TASKS"));
        return UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(id,name,"unused",true,roles),null,roles);
    }
    static long id(MvcResult result,String path)throws Exception{
        var value=json.readTree(result.getResponse().getContentAsByteArray());
        for(String key:path.split("\\."))value=value.path(key);return value.asLong();
    }
    static long task(long who,String title)throws Exception {
        return id(mvc.perform(multipart("/api/tasks").param("title",title).param("body_html","<p>description</p>")
            .param("assignee","viewer").param("creator","forged creator").param("status","Новая")
            .with(authentication(actor(who))).with(csrf())).andExpect(status().isOk()).andReturn(),"id");
    }
    static long project(long who,String name)throws Exception{
        return id(mvc.perform(post("/api/projects").contentType("application/json").content(json.writeValueAsString(Map.of("name",name)))
            .with(authentication(actor(who))).with(csrf())).andExpect(status().isOk()).andReturn(),"project.id");
    }
    static void creationGrant(long who)throws Exception{
        mvc.perform(put("/api/shared-work-admin/access/"+who).contentType("application/json")
            .content("{\"canRead\":true,\"canCreateTask\":true,\"canCreateProject\":true}")
            .with(authentication(actor(ADMIN))).with(csrf())).andExpect(status().isNoContent());
    }
    static void member(String kind,long object,long who,String role)throws Exception{
        mvc.perform(put("/api/shared-work/"+kind+"/"+object+"/members/"+who).contentType("application/json")
            .content(json.writeValueAsString(Map.of("role",role))).with(authentication(actor(ADMIN))).with(csrf())).andExpect(status().isNoContent());
    }
    static MvcResult save(long who,long object,String title,String projects)throws Exception{
        return mvc.perform(multipart("/api/tasks").param("id",Long.toString(object)).param("title",title)
            .param("body_html","<p>changed</p>").param("assignee","viewer").param("project_ids",projects)
            .with(authentication(actor(who))).with(csrf())).andReturn();
    }
    @Test void panelCapabilitiesUseCurrentAdminAndStringIdentity()throws Exception {
        mvc.perform(get("/api/shared-work/context").with(authentication(actor(ADMIN))))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.userId").value(Long.toString(ADMIN)))
            .andExpect(jsonPath("$.systemAdmin").value(true)).andExpect(jsonPath("$.canCreateTask").value(true))
            .andExpect(jsonPath("$.canCreateProject").value(true));
        mvc.perform(get("/api/shared-work/context").with(authentication(actor(USER))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.systemAdmin").value(false))
            .andExpect(jsonPath("$.canCreateTask").value(false));
        creationGrant(USER);
        mvc.perform(get("/api/shared-work/context").with(authentication(actor(USER))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.canCreateTask").value(true));
        jdbc.update("DELETE FROM user_authorities WHERE user_id=? AND authority='ROLE_ADMIN'",ADMIN);
        mvc.perform(get("/api/shared-work/context").with(authentication(actor(ADMIN)))).andExpect(status().isNotFound());
    }
    @Test void ownerPickerIsScopedCurrentAndPreservesExactNumericIdentity()throws Exception {
        creationGrant(USER);long task=task(USER,"picker");
        member("TASK",task,VIEWER,"VIEWER");
        jdbc.execute("INSERT INTO users(id,username,password) OVERRIDING SYSTEM VALUE VALUES(13,'business-admin','unused');"+
            "INSERT INTO user_authorities VALUES(13,'PAGE_TASKS'),(13,'ROLE_PORTAL_ADMIN')");
        String route="/api/shared-work/TASK/"+task;
        mvc.perform(get(route+"/access").with(authentication(actor(VIEWER))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(Long.toString(task)))
            .andExpect(jsonPath("$.canManage").value(false)).andExpect(jsonPath("$.canWrite").value(false));
        mvc.perform(get(route+"/people").with(authentication(actor(VIEWER)))).andExpect(status().isNotFound());
        mvc.perform(get(route+"/people").param("q"," SysAdmin/Ю ").with(authentication(actor(USER))))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$[0].userId").value(Long.toString(ADMIN))).andExpect(jsonPath("$[0].username").value(ADMIN_NAME))
            .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(route+"/people").param("q","business-admin").with(authentication(actor(USER))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",VIEWER);
        mvc.perform(get(route+"/people").param("q","viewer").with(authentication(actor(USER))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(route+"/people").param("q","x".repeat(101)).with(authentication(actor(USER)))).andExpect(status().isBadRequest());
    }
    @Test void panelGrantMemberTransferAndRevokeRoundTrip()throws Exception {
        creationGrant(USER);long project=project(USER,"round trip");
        String route="/api/shared-work/PROJECT/"+project;
        mvc.perform(get(route+"/access").with(authentication(actor(USER))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.canManage").value(true));
        mvc.perform(put(route+"/members/"+VIEWER).contentType("application/json").content("{\"role\":\"EDITOR\"}")
            .with(authentication(actor(USER))).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get(route+"/access").with(authentication(actor(VIEWER))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.canWrite").value(true)).andExpect(jsonPath("$.canManage").value(false));
        mvc.perform(put(route+"/owner/"+VIEWER).with(authentication(actor(USER))).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get(route+"/access").with(authentication(actor(USER))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.canWrite").value(true)).andExpect(jsonPath("$.canManage").value(false));
        mvc.perform(get(route+"/access").with(authentication(actor(VIEWER))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.canManage").value(true));
        mvc.perform(delete(route+"/members/"+USER).with(authentication(actor(VIEWER))).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get(route+"/access").with(authentication(actor(USER)))).andExpect(status().isNotFound());
        mvc.perform(put("/api/shared-work-admin/access/"+VIEWER).contentType("application/json")
            .content("{\"canRead\":false,\"canCreateTask\":false,\"canCreateProject\":false}")
            .with(authentication(actor(ADMIN))).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get(route+"/access").with(authentication(actor(VIEWER)))).andExpect(status().isNotFound());
        mvc.perform(get(route+"/access").with(authentication(actor(ADMIN))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.canManage").value(true));
    }

    @Test void systemAdminHasFullTaskCrudWithoutPagesGrantsOrMembership()throws Exception{
        long task=task(ADMIN,"Admin only");
        assertThat(jdbc.queryForObject("SELECT shared_owner_user_id FROM tasks WHERE id=?",Long.class,task)).isEqualTo(ADMIN);
        assertThat(jdbc.queryForObject("SELECT creator FROM tasks WHERE id=?",String.class,task)).isEqualTo(ADMIN_NAME);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared_work_grants WHERE user_id=?",Integer.class,ADMIN)).isZero();
        mvc.perform(get("/api/tasks/"+task).with(authentication(actor(ADMIN)))).andExpect(status().isOk());
        assertThat(save(ADMIN,task,"Admin changed","").getResponse().getStatus()).isEqualTo(200);
        mvc.perform(multipart("/api/tasks/"+task+"/comments").param("html","comment").param("author","forged")
            .with(authentication(actor(ADMIN))).with(csrf())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT author FROM task_comments WHERE task_id=?",String.class,task)).isEqualTo(ADMIN_NAME);
        mvc.perform(delete("/api/tasks/"+task).with(authentication(actor(ADMIN))).with(csrf())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tasks",Integer.class)).isZero();
    }
    @Test void missingUpdateIdNeverCreatesAReplacementTask()throws Exception{
        assertThat(save(ADMIN,999,"must not create","").getResponse().getStatus()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tasks",Integer.class)).isZero();
    }
    @Test void ordinaryOwnershipAndPaginationAreEnforced()throws Exception{
        creationGrant(USER);long own=task(USER,"Visible own");long hidden=task(ADMIN,"Hidden foreign");
        mvc.perform(get("/api/tasks").param("page_size","1").with(authentication(actor(USER))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].title").value("Visible own"));
        mvc.perform(get("/api/tasks/"+hidden).with(authentication(actor(USER)))).andExpect(status().isNotFound());
        assertThat(save(USER,hidden,"attack","").getResponse().getStatus()).isEqualTo(404);
        assertThat(save(USER,own,"Own changed","").getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT title FROM tasks WHERE id=?",String.class,hidden)).isEqualTo("Hidden foreign");
    }
    @Test void pagePermissionAloneCannotCreateEitherKind()throws Exception{
        mvc.perform(multipart("/api/tasks").param("title","Denied").param("body_html","<p>x</p>").param("assignee","viewer")
            .with(authentication(actor(USER))).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/projects").contentType("application/json").content("{\"name\":\"Denied\"}")
            .with(authentication(actor(USER))).with(csrf())).andExpect(status().isNotFound());
    }
    @Test void viewersCannotWriteAndEditorsCan()throws Exception{
        long task=task(ADMIN,"Role protected");member("TASK",task,VIEWER,"VIEWER");
        mvc.perform(get("/api/tasks/"+task).with(authentication(actor(VIEWER)))).andExpect(status().isOk());
        assertThat(save(VIEWER,task,"Denied edit","").getResponse().getStatus()).isEqualTo(404);
        mvc.perform(multipart("/api/tasks/"+task+"/comments").param("html","denied")
            .with(authentication(actor(VIEWER))).with(csrf())).andExpect(status().isNotFound());
        member("TASK",task,VIEWER,"EDITOR");assertThat(save(VIEWER,task,"Editor change","").getResponse().getStatus()).isEqualTo(200);
    }
    @Test void adminCanRecoverAnUnownedProjectAndUseEveryBoardOperation()throws Exception{
        jdbc.execute("INSERT INTO projects(id,name) OVERRIDING SYSTEM VALUE VALUES(100,'Legacy unowned')");
        mvc.perform(get("/api/projects/100").with(authentication(actor(ADMIN)))).andExpect(status().isOk());
        mvc.perform(patch("/api/projects/100").contentType("application/json").content("{\"description\":\"admin update\"}")
            .with(authentication(actor(ADMIN))).with(csrf())).andExpect(status().isOk());
        var ensured=mvc.perform(post("/api/task-boards/project/100/ensure").with(authentication(actor(ADMIN))).with(csrf())).andExpect(status().isOk()).andReturn();
        long board=id(ensured,"board.id");
        var created=mvc.perform(post("/api/task-boards/"+board+"/columns").contentType("application/json").content("{\"name\":\"Admin stage\"}")
            .with(authentication(actor(ADMIN))).with(csrf())).andExpect(status().isOk()).andReturn();
        long column=json.readTree(created.getResponse().getContentAsByteArray()).path("board").path("columns").findValues("name").isEmpty()?0:
            jdbc.queryForObject("SELECT id FROM board_columns WHERE board_id=? AND name='Admin stage'",Long.class,board);
        mvc.perform(patch("/api/task-boards/"+board+"/columns/"+column).contentType("application/json").content("{\"name\":\"Renamed stage\"}")
            .with(authentication(actor(ADMIN))).with(csrf())).andExpect(status().isOk());
        mvc.perform(delete("/api/task-boards/"+board+"/columns/"+column).with(authentication(actor(ADMIN))).with(csrf())).andExpect(status().isOk());
        mvc.perform(put("/api/shared-work/PROJECT/100/owner/11").with(authentication(actor(ADMIN))).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/projects/100").with(authentication(actor(USER)))).andExpect(status().isOk());
        mvc.perform(delete("/api/projects/100").with(authentication(actor(ADMIN))).with(csrf())).andExpect(status().isOk());
    }
    @Test void projectReadDoesNotExposeTasksOrDestroyOtherReadersPlacements()throws Exception{
        long project=project(ADMIN,"Shared board"),task=task(ADMIN,"Secret card");
        assertThat(save(ADMIN,task,"Secret card",Long.toString(project)).getResponse().getStatus()).isEqualTo(200);
        long board=id(mvc.perform(post("/api/task-boards/project/"+project+"/ensure").with(authentication(actor(ADMIN))).with(csrf()))
            .andExpect(status().isOk()).andReturn(),"board.id");
        member("PROJECT",project,USER,"VIEWER");
        mvc.perform(get("/api/projects/"+project).with(authentication(actor(USER)))).andExpect(status().isOk()).andExpect(jsonPath("$.task_count").value(0));
        String response=mvc.perform(get("/api/task-boards/"+board).with(authentication(actor(USER)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("Secret card");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM board_task_placements WHERE board_id=?",Integer.class,board)).isEqualTo(1);
        mvc.perform(post("/api/task-boards/"+board+"/columns").contentType("application/json").content("{\"name\":\"denied\"}")
            .with(authentication(actor(USER))).with(csrf())).andExpect(status().isNotFound());
        member("PROJECT",project,USER,"EDITOR");member("TASK",task,USER,"VIEWER");
        long column=jdbc.queryForObject("SELECT min(id) FROM board_columns WHERE board_id=?",Long.class,board);
        String move=json.writeValueAsString(Map.of("task_id",task,"column_id",column));
        mvc.perform(post("/api/task-boards/"+board+"/move").contentType("application/json").content(move)
            .with(authentication(actor(USER))).with(csrf())).andExpect(status().isNotFound());
        member("TASK",task,USER,"EDITOR");
        mvc.perform(post("/api/task-boards/"+board+"/move").contentType("application/json").content(move)
            .with(authentication(actor(USER))).with(csrf())).andExpect(status().isOk());
    }
    @Test void linksRequireProjectWriteAndHiddenLinksSurviveUnrelatedTaskEdits()throws Exception{
        creationGrant(USER);long task=task(USER,"Owned"),project=project(ADMIN,"Hidden project");
        assertThat(save(USER,task,"Denied link",Long.toString(project)).getResponse().getStatus()).isEqualTo(404);
        member("PROJECT",project,USER,"VIEWER");
        assertThat(save(USER,task,"Read cannot link",Long.toString(project)).getResponse().getStatus()).isEqualTo(404);
        member("PROJECT",project,USER,"EDITOR");
        assertThat(save(USER,task,"Linked",Long.toString(project)).getResponse().getStatus()).isEqualTo(200);
        mvc.perform(delete("/api/shared-work/PROJECT/"+project+"/members/11").with(authentication(actor(ADMIN))).with(csrf())).andExpect(status().isNoContent());
        assertThat(save(USER,task,"Unrelated edit","").getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM task_project_memberships WHERE task_id=? AND project_id=?",Integer.class,task,project)).isEqualTo(1);
        String dto=mvc.perform(get("/api/tasks/"+task).with(authentication(actor(USER)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(dto).doesNotContain("Hidden project");
    }
    @Test void roleRevocationOverridesAStaleAdministratorSession()throws Exception{
        long task=task(ADMIN,"Admin private");
        jdbc.update("DELETE FROM user_authorities WHERE user_id=? AND authority='ROLE_ADMIN'",ADMIN);
        mvc.perform(get("/api/tasks/"+task).with(authentication(actor(ADMIN)))).andExpect(status().isNotFound());
        assertThat(save(ADMIN,task,"Denied","").getResponse().getStatus()).isEqualTo(404);
        mvc.perform(get("/api/shared-work-admin/access").with(authentication(actor(ADMIN)))).andExpect(status().isForbidden());
    }
    @Test void analyticsAndCsvUseTheSameTaskScope()throws Exception{
        creationGrant(USER);task(USER,"Visible analytics");task(ADMIN,"Secret analytics");
        String csv=mvc.perform(get("/api/task-analytics/export.csv").with(authentication(actor(USER))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("Visible analytics").doesNotContain("Secret analytics");
        mvc.perform(get("/api/task-analytics").with(authentication(actor(ADMIN)))).andExpect(status().isOk());
    }
    @Test void adminMayManageAnotherUsersPersonalBoard()throws Exception{
        long board=id(mvc.perform(post("/api/task-boards/mine/ensure").with(authentication(actor(USER))).with(csrf()))
            .andExpect(status().isOk()).andReturn(),"board.id");
        assertThat(jdbc.queryForObject("SELECT scope_key FROM project_boards WHERE id=?",String.class,board)).isEqualTo("PERSONAL_USER:11");
        mvc.perform(get("/api/task-boards/"+board).with(authentication(actor(VIEWER)))).andExpect(status().isNotFound());
        mvc.perform(post("/api/task-boards/"+board+"/columns").contentType("application/json").content("{\"name\":\"Recovery stage\"}")
            .with(authentication(actor(ADMIN))).with(csrf())).andExpect(status().isOk());
    }
    @Test void projectUpdateAndTaskLinkUseOneMutationOrder()throws Exception{
        long project=project(ADMIN,"Concurrent project"),task=task(ADMIN,"Concurrent task");
        assertThat(save(ADMIN,task,"Concurrent task",Long.toString(project)).getResponse().getStatus()).isEqualTo(200);
        var start=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try{
            var update=executor.submit(()->{start.await();return mvc.perform(patch("/api/projects/"+project)
                .contentType("application/json").content(json.writeValueAsString(Map.of("description","parallel"))).with(authentication(actor(ADMIN))).with(csrf())).andReturn().getResponse().getStatus();});
            var save=executor.submit(()->{start.await();return save(ADMIN,task,"Concurrent changed",Long.toString(project)).getResponse().getStatus();});
            start.countDown();assertThat(update.get(15,TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(save.get(15,TimeUnit.SECONDS)).isEqualTo(200);
        }finally{executor.shutdownNow();}
    }
    @Test void serverRenderedTaskPageUsesTheSameScope()throws Exception{
        creationGrant(USER);task(USER,"Own page task");task(ADMIN,"Hidden page task");
        var view=mvc.perform(get("/tasks").with(authentication(actor(USER)))).andExpect(status().isOk()).andExpect(view().name("tasks/index")).andReturn();
        @SuppressWarnings("unchecked") var tasks=(List<Task>)view.getModelAndView().getModel().get("tasks");
        assertThat(tasks).extracting(Task::getTitle).containsExactly("Own page task");
        mvc.perform(get("/tasks").with(authentication(actor(ADMIN)))).andExpect(status().isOk());
    }
    @Test void notificationsUseExactCurrentlyReadableRecipients()throws Exception{
        long task=task(ADMIN,"Private notification");
        verify(context.getBean(NotificationRoutingService.class)).notifyTask(eq("task_saved"),eq(task),eq(Set.of(ADMIN_NAME)),anyString(),anyString(),eq(ADMIN_NAME));
        assertThat(jdbc.queryForObject("SELECT actor FROM task_events WHERE task_id=? AND event_type='TASK_CREATED'",String.class,task)).isEqualTo(ADMIN_NAME);
    }
    @Test void csrfStillProtectsAnUnrestrictedAdministrator()throws Exception{
        mvc.perform(post("/api/projects").contentType("application/json").content("{\"name\":\"csrf denied\"}")
            .with(authentication(actor(ADMIN)))).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM projects",Integer.class)).isZero();
    }
    static <T> T as(long who,java.util.function.Supplier<T> action) {
        var security=org.springframework.security.core.context.SecurityContextHolder.getContext();
        var previous=security.getAuthentication();security.setAuthentication(actor(who));
        try{return action.get();}finally{security.setAuthentication(previous);}
    }
    @Test void auditRoundTripIsImmutableIdempotentAndOwnerOnly()throws Exception {
        creationGrant(USER);long object=task(USER,"audited");
        member("TASK",object,VIEWER,"EDITOR");member("TASK",object,VIEWER,"EDITOR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared_work_access_audit WHERE kind='TASK' AND object_id=? AND action='MEMBER_SET'",Integer.class,object)).isEqualTo(1);
        mvc.perform(get("/api/shared-work/TASK/"+object+"/history").with(authentication(actor(USER))))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].action").value("MEMBER_SET"))
            .andExpect(jsonPath("$[0].actorUserId").value(Long.toString(ADMIN)));
        mvc.perform(get("/api/shared-work/TASK/"+object+"/history").with(authentication(actor(VIEWER)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/shared-work-admin/history").with(authentication(actor(ADMIN))))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/shared-work-admin/history").with(authentication(actor(USER)))).andExpect(status().isForbidden());
        assertThatThrownBy(()->jdbc.update("UPDATE shared_work_access_audit SET actor_name='forged'")).hasMessageContaining("append-only");
        assertThatThrownBy(()->jdbc.update("DELETE FROM shared_work_access_audit")).hasMessageContaining("append-only");
    }
    @Test void typedNotificationsFilterBeforePagingCountsAndReadAfterRevoke()throws Exception {
        long object=task(ADMIN,"secret");member("TASK",object,USER,"VIEWER");
        var notifications=context.getBean(NotificationService.class);
        notifications.notifySharedWork(SharedWorkAccessService.Kind.TASK,object,Set.of("owner","viewer"),null,"private title","/tasks#task="+object);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT shared_recipient_user_id FROM notifications",Long.class)).isEqualTo(USER);
        as(USER,()->{assertThat(notifications.summary("owner").unreadCount()).isEqualTo(1);assertThat(notifications.findPageForUser("owner",null,1).items()).hasSize(1);return null;});
        mvc.perform(delete("/api/shared-work/TASK/"+object+"/members/"+USER).with(authentication(actor(ADMIN))).with(csrf())).andExpect(status().isNoContent());
        as(USER,()->{
            assertThat(notifications.findForUser("owner")).isEmpty();assertThat(notifications.findPageForUser("owner",null,1).items()).isEmpty();
            assertThat(notifications.summary("owner").unreadCount()).isZero();notifications.markAllAsRead("owner");return null;
        });
        assertThat(jdbc.queryForObject("SELECT is_read FROM notifications",Boolean.class)).isFalse();
    }
    @Test void routeExpansionNeverAddsAnUnreadableRecipient()throws Exception {
        long object=task(ADMIN,"route");member("TASK",object,USER,"VIEWER");
        var config=mock(SharedConfigService.class);
        when(config.loadSettings()).thenReturn(Map.of("notification_routing",Map.of("tasks",Map.of("task_saved",Map.of(
            "enabled",true,"audienceStrategy","base_plus_route","targetMode","employees_only","employeeUsernames",List.of("viewer"),"deliveryMode","all")))));
        var routing=new NotificationRoutingService(config,context.getBean(NotificationService.class),jdbc,jdbc);
        routing.notifyTask("task_saved",object,Set.of("owner"),"private title","/tasks#task="+object,null);
        assertThat(jdbc.queryForList("SELECT user_identity FROM notifications",String.class)).containsExactly("owner");
    }
    @Test void untypedTaskNoticeAndDifferentNumericAccountStayHidden()throws Exception {
        long object=task(ADMIN,"same name");member("TASK",object,USER,"VIEWER");
        var notifications=context.getBean(NotificationService.class);
        notifications.notifySharedWork(SharedWorkAccessService.Kind.TASK,object,Set.of("owner"),null,"private","/tasks#task="+object);
        jdbc.update("INSERT INTO notifications(user_identity,text,url,is_read,created_at) VALUES('owner','legacy private','/tasks',FALSE,CURRENT_TIMESTAMP)");
        as(USER,()->{assertThat(notifications.findForUser("owner")).hasSize(1);return null;});
        jdbc.update("DELETE FROM users WHERE id=?",USER);
        jdbc.update("INSERT INTO users(id,username,password) OVERRIDING SYSTEM VALUE VALUES(13,'owner','unused')");
        var stale=actor(USER);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(stale);
        assertThat(notifications.findForUser("owner")).isEmpty();
        assertThatThrownBy(()->jdbc.update("INSERT INTO notifications(user_identity,text,shared_work_kind) VALUES('owner','invalid','TASK')"))
            .hasMessageContaining("notification_shared_work_scope");
    }
    static class RecordingEmitter extends org.springframework.web.servlet.mvc.method.annotation.SseEmitter {
        final List<Object> data=new ArrayList<>();
        @Override public void send(SseEventBuilder event) {
            for(var part:event.build())if(part.getData() instanceof Map<?,?>)data.add(part.getData());
        }
    }
    @SuppressWarnings("unchecked")
    @Test void delayedSharedRealtimeRechecksReadAndNeverSendsPrivateFields()throws Exception {
        long object=task(ADMIN,"late");member("TASK",object,USER,"VIEWER");
        var stream=context.getBean(UiEventStreamService.class);var emitter=new RecordingEmitter();
        var registry=(ConcurrentMap<UiEventRecipient,java.util.concurrent.CopyOnWriteArraySet<org.springframework.web.servlet.mvc.method.annotation.SseEmitter>>)
            org.springframework.test.util.ReflectionTestUtils.getField(stream,"emittersByUser");
        registry.clear();registry.put(new UiEventRecipient(USER,"owner"),new java.util.concurrent.CopyOnWriteArraySet<>(List.of(emitter)));
        stream.publishSharedWorkNotificationsChanged("owner",SharedWorkAccessService.Kind.TASK,object,USER);
        assertThat(emitter.data).hasSize(1);
        assertThat(((Map<?,?>)emitter.data.get(0)).containsKey("reason")).isTrue();
        assertThat(((Map<?,?>)emitter.data.get(0)).keySet().size()).isEqualTo(2);
        mvc.perform(delete("/api/shared-work/TASK/"+object+"/members/"+USER).with(authentication(actor(ADMIN))).with(csrf())).andExpect(status().isNoContent());
        stream.publishSharedWorkNotificationsChanged("owner",SharedWorkAccessService.Kind.TASK,object,USER);
        assertThat(emitter.data).hasSize(1);registry.clear();
    }
    @Test void backgroundTaskHasNumericOwnerScopeAndProjectWithoutSession()throws Exception {
        long project=project(ADMIN,"background");member("PROJECT",project,USER,"EDITOR");
        var scope=new UiEventTicketScope(901L," source/Ю ","a".repeat(32),"BUSINESS",7L);
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,is_general) VALUES(?,?,?,FALSE)",scope.ticketUserId(),scope.ticketId(),scope.ticketPublicId());
        when(context.getBean(UiEventTicketAccessService.class).canReadTicketForRecipient(any(),eq(scope))).thenReturn(true);
        var payload=new PanelTaskService.TaskPayload("follow up",null,"forged creator","owner",null,null,null,"dialog_auto_close",
            List.of(),List.of(),List.of(scope.ticketId()),List.of(project));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        var task=context.getBean(PanelTaskService.class).createFollowUpTask(payload,scope);
        assertThat(task.getSharedOwnerUserId()).isEqualTo(USER);assertThat(task.getCreator()).isEqualTo("auto_close");
        assertThat(jdbc.queryForObject("SELECT access_role FROM shared_task_members WHERE task_id=? AND user_id=?",String.class,task.getId(),USER)).isEqualTo("OWNER");
        assertThat(jdbc.queryForObject("SELECT ticket_id FROM task_links WHERE task_id=?",String.class,task.getId())).isEqualTo(scope.ticketId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM task_project_memberships WHERE task_id=? AND project_id=?",Integer.class,task.getId(),project)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT actor_user_id FROM shared_work_access_audit WHERE kind='TASK' AND object_id=? AND action='SYSTEM_CREATED'",Long.class,task.getId())).isNull();
    }
    @Test void backgroundDenialRollsBackRootSequenceAndAudit()throws Exception {
        long project=project(ADMIN,"private project");
        var scope=new UiEventTicketScope(901L,"source","a".repeat(32),"BUSINESS",7L);
        when(context.getBean(UiEventTicketAccessService.class).canReadTicketForRecipient(any(),eq(scope))).thenReturn(true);
        var payload=new PanelTaskService.TaskPayload("follow up",null,"forged","owner",null,null,null,"dialog_auto_close",
            List.of(),List.of(),List.of("source"),List.of(project));
        assertThatThrownBy(()->context.getBean(PanelTaskService.class).createFollowUpTask(payload,scope)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tasks",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT val FROM task_seq WHERE id=1",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared_work_access_audit WHERE kind='TASK'",Integer.class)).isZero();
    }

    @Test void taskSearchAndOlapCountsUseSameCurrentSharedRead()throws Exception {
        long shown=task(ADMIN,"visible"),hidden=task(ADMIN,"hidden");member("TASK",shown,USER,"VIEWER");
        var catalog=context.getBean(IncidentRelatedObjectService.class);
        as(USER,()->{
            var rows=(List<?>)catalog.search("task","",30).get("items");assertThat(rows).hasSize(1);
            assertThat(catalog.selectionLabel("task",Long.toString(shown))).contains("visible");
            assertThatThrownBy(()->catalog.selectionLabel("task",Long.toString(hidden))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            var filters=new DashboardAnalyticsService.DashboardFilters(null,null,List.of());
            var result=context.getBean(ManagerReportService.class).buildOlapPreview(List.of(),filters,new ManagerReportService.OlapPreviewRequest("location",false,true,true));
            assertThat(((Map<?,?>)result.get("totals")).get("tasks")).isEqualTo(1L);
            return null;
        });
        as(ADMIN,()->{assertThat((List<?>)catalog.search("task","",30).get("items")).hasSize(2);return null;});
    }
    @Test void linkedDialogHistoryCannotExposeUnreadableTaskHistory()throws Exception {
        long shown=task(ADMIN,"visible"),hidden=task(ADMIN,"hidden");member("TASK",shown,USER,"VIEWER");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,is_general) VALUES(901,'context-source',?,FALSE)","b".repeat(32));
        jdbc.update("INSERT INTO task_links(task_id,user_id,ticket_id) VALUES(?,901,'context-source'),(?,901,'context-source')",shown,hidden);
        jdbc.update("DELETE FROM task_history");
        jdbc.update("INSERT INTO task_history(task_id,at,text) VALUES(?,CURRENT_TIMESTAMP,'visible workflow'),(?,CURRENT_TIMESTAMP,'hidden workflow')",shown,hidden);
        var service=context.getBean(DialogClientContextReadService.class);
        as(USER,()->{
            var rows=service.loadRelatedEvents("context-source",30);
            assertThat(rows).hasSize(1);assertThat(rows.toString()).contains("visible workflow").doesNotContain("hidden workflow");return null;
        });
        as(ADMIN,()->{assertThat(service.loadRelatedEvents("context-source",30)).hasSize(2);return null;});
    }

    @Configuration(proxyBeanMethods=false) @EnableWebSecurity @EnableMethodSecurity @EnableWebMvc @EnableTransactionManagement
    static class Config {
        @Bean JdbcTemplate jdbcTemplate(){return jdbc;}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(){
            var bean=new LocalContainerEntityManagerFactoryBean();bean.setDataSource(jdbc.getDataSource());
            bean.setManagedTypes(org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes.of(
                Task.class.getName(),TaskSequence.class.getName(),TaskComment.class.getName(),TaskHistory.class.getName(),
                Notification.class.getName(),TaskPerson.class.getName(),Project.class.getName(),TaskProjectMembership.class.getName(),com.example.panel.entity.Tag.class.getName(),
                TaskTag.class.getName(),TaskEvent.class.getName(),ProjectBoard.class.getName(),BoardColumn.class.getName(),BoardTaskPlacement.class.getName()));
            bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            bean.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.dialect","org.hibernate.dialect.PostgreSQLDialect","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            return bean;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory){
            var manager=new JpaTransactionManager(factory);manager.setDataSource(jdbc.getDataSource());return manager;
        }
        <T>T repo(EntityManagerFactory factory,Class<T> type){return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory)).getRepository(type);}
        @Bean TaskRepository tasks(EntityManagerFactory f){return repo(f,TaskRepository.class);}
        @Bean TaskSequenceRepository sequences(EntityManagerFactory f){return repo(f,TaskSequenceRepository.class);}
        @Bean TaskCommentRepository comments(EntityManagerFactory f){return repo(f,TaskCommentRepository.class);}
        @Bean TaskHistoryRepository history(EntityManagerFactory f){return repo(f,TaskHistoryRepository.class);}
        @Bean TaskPersonRepository people(EntityManagerFactory f){return repo(f,TaskPersonRepository.class);}
        @Bean ProjectRepository projects(EntityManagerFactory f){return repo(f,ProjectRepository.class);}
        @Bean TaskProjectMembershipRepository memberships(EntityManagerFactory f){return repo(f,TaskProjectMembershipRepository.class);}
        @Bean TagRepository tags(EntityManagerFactory f){return repo(f,TagRepository.class);}
        @Bean TaskTagRepository taskTags(EntityManagerFactory f){return repo(f,TaskTagRepository.class);}
        @Bean TaskEventRepository events(EntityManagerFactory f){return repo(f,TaskEventRepository.class);}
        @Bean ProjectBoardRepository boards(EntityManagerFactory f){return repo(f,ProjectBoardRepository.class);}
        @Bean BoardColumnRepository columns(EntityManagerFactory f){return repo(f,BoardColumnRepository.class);}
        @Bean BoardTaskPlacementRepository placements(EntityManagerFactory f){return repo(f,BoardTaskPlacementRepository.class);}
        @Bean IncidentRelatedObjectService relatedObjects(){return new IncidentRelatedObjectService(jdbc,mock(BusinessAccessService.class));}
        @Bean DialogClientContextReadService dialogContext(LegacyTicketIdJdbcGuard guard){return new DialogClientContextReadService(jdbc,new com.example.panel.support.PanelTimestampSqlSupport(),guard);}
        @Bean ManagerReportService managerReport(TaskRepository t,TaskHistoryRepository h){
            var dashboard=mock(DashboardAnalyticsService.class);
            when(dashboard.filterDialogs(anyList(),any())).thenReturn(List.of());
            return new ManagerReportService(mock(SharedConfigService.class),t,h,dashboard);
        }
        @Bean NotificationRepository notificationRows(EntityManagerFactory f){return repo(f,NotificationRepository.class);}
        @Bean TaskLinkRepository taskLinks(){
            var links=mock(TaskLinkRepository.class);
            when(links.save(any(TaskLink.class))).thenAnswer(inv->{
                TaskLink link=inv.getArgument(0);var key=link.getId();
                jdbc.update("INSERT INTO task_links(task_id,user_id,ticket_id) VALUES(?,?,?)",key.getTaskId(),key.getUserId(),key.getTicketId());
                return link;
            });return links;
        }
        @Bean com.example.panel.config.MultibusinessFeatureFlags flags(){return new com.example.panel.config.MultibusinessFeatureFlags();}
        @Bean LegacyTicketIdJdbcGuard guard(){return new LegacyTicketIdJdbcGuard(jdbc);}
        @Bean UiEventStreamService streams(UiEventFanoutPublisher fanout,LegacyTicketIdJdbcGuard g,UiEventTicketAccessService a){return new UiEventStreamService(fanout,g,a);}
        @Bean NotificationService notificationService(NotificationRepository n,UiEventStreamService s,LegacyTicketIdJdbcGuard g){return new NotificationService(n,jdbc,jdbc,s,g);}
        @Bean PanelTaskService systemTasks(TaskRepository t,TaskSequenceRepository s,TaskPersonRepository p,TaskHistoryRepository h,TaskLinkRepository l,NotificationRepository n,TicketRepository tickets,TaskDomainFoundationService d){return new PanelTaskService(t,s,p,h,l,n,tickets,d);}
        @Bean SharedWorkAccessService access(){return new SharedWorkAccessService(jdbc);}
        @Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor externalServices(){
            return factory->{
                factory.registerSingleton("notifications",mock(NotificationRoutingService.class));
                factory.registerSingleton("ticketAccess",mock(UiEventTicketAccessService.class));
                factory.registerSingleton("fanout",mock(UiEventFanoutPublisher.class));
                factory.registerSingleton("ticketRepository",mock(TicketRepository.class));
                factory.registerSingleton("dialogLookup",mock(DialogLookupReadService.class));
                factory.registerSingleton("incidents",mock(IncidentService.class));
                factory.registerSingleton("navigation",mock(NavigationService.class));
                factory.registerSingleton("panelUsers",mock(PanelUserRepository.class));
                factory.registerSingleton("channels",mock(ChannelRepository.class));
                factory.registerSingleton("permissions",mock(PermissionService.class));
                factory.registerSingleton("views",mock(TaskAnalyticsViewService.class));
                var display=mock(DialogTaskDisplayService.class);
                when(display.normalizeTitle(any())).thenAnswer(i->i.getArgument(0));
                when(display.normalizeBodyHtml(any())).thenAnswer(i->i.getArgument(0));
                factory.registerSingleton("display",display);
            };
        }
        @Bean TaskDomainFoundationService domain(ProjectRepository p,TaskProjectMembershipRepository m,TagRepository t,TaskTagRepository tt,TaskEventRepository e,SharedWorkAccessService a){return new TaskDomainFoundationService(p,m,t,tt,e,a);}
        @Bean TaskQueryService query(TaskRepository t,TaskDomainFoundationService d,DialogTaskDisplayService v,SharedWorkAccessService a){return new TaskQueryService(t,d,v,a);}
        @Bean ProjectService projectService(ProjectRepository p,TaskProjectMembershipRepository m,SharedWorkAccessService a){return new ProjectService(p,m,a);}
        @Bean TaskBoardService boardService(ProjectBoardRepository b,BoardColumnRepository c,BoardTaskPlacementRepository placements,ProjectRepository p,TaskRepository t,TaskPersonRepository people,TaskProjectMembershipRepository m,TaskEventRepository e,TaskDomainFoundationService d,DialogTaskDisplayService display,SharedWorkAccessService a){return new TaskBoardService(b,c,placements,p,t,people,m,e,d,display,a);}
        @Bean TaskAnalyticsService analytics(SharedWorkAccessService a){return new TaskAnalyticsService(jdbc,a);}
        @Bean TaskApiController taskApi(TaskRepository t,TaskSequenceRepository s,TaskCommentRepository c,TaskHistoryRepository h,TaskPersonRepository p,NotificationRoutingService n,IncidentService i,TaskDomainFoundationService d,TaskQueryService q,DialogTaskDisplayService v,SharedWorkAccessService a){return new TaskApiController(t,s,c,h,p,n,i,d,q,v,a);}
        @Bean ManagementController pages(NavigationService n,TaskRepository t,ChannelRepository c,PanelUserRepository p,PermissionService permissions,SharedWorkAccessService a){return new ManagementController(n,t,c,p,permissions,a);}
        @Bean ProjectApiController projectApi(ProjectService p){return new ProjectApiController(p);}
        @Bean TaskBoardApiController boardApi(TaskBoardService b){return new TaskBoardApiController(b);}
        @Bean SharedWorkAccessController accessApi(SharedWorkAccessService a){return new SharedWorkAccessController(a);}
        @Bean TaskAnalyticsApiController analyticsApi(TaskAnalyticsService a,TaskAnalyticsViewService v){return new TaskAnalyticsApiController(a,v);}
        @Bean SecurityFilterChain security(HttpSecurity http)throws Exception{
            return http.authorizeHttpRequests(r->r.anyRequest().authenticated())
                .exceptionHandling(e->e.authenticationEntryPoint((request,response,error)->response.sendError(401))).build();
        }
    }
}
