package com.example.panel.security;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.controller.AttachmentController;
import com.example.panel.service.*;
import com.example.panel.storage.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import static com.example.panel.support.LegacyAttachmentIdentityFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real login/R189/HTTP routes and SQL guards; binary backend is isolated and observed. */
@SpringBootTest(classes=LegacyAttachmentReadAccessWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class LegacyAttachmentReadAccessWebMvcTest {
    enum HttpRoute { DIRECT,QUERY,KEY,PATH }
    private static final String PASSWORD="fixture legacy attachment",HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SharedConfigService shared;
    @Autowired MultibusinessFeatureFlags flags;
    @Autowired AttachmentObjectStorageService storage;
    @SpyBean AttachmentService attachments;
    @Configuration(proxyBeanMethods=false)
    @Import({PreferenceIdentityWebMvcTest.Application.class,AttachmentController.class})
    static class Application {
        @Bean InitializingBean attachmentSchema(JdbcTemplate jdbc){return ()->schema(jdbc,true);}
        @Bean MultibusinessFeatureFlags flags(){return new MultibusinessFeatureFlags();}
        @Bean AttachmentObjectStorageService storage(){return mock(AttachmentObjectStorageService.class);}
        @Bean AttachmentService attachments(JdbcTemplate jdbc,MultibusinessFeatureFlags flags,AttachmentObjectStorageService storage)throws Exception {
            return service(jdbc,jdbc,flags,storage,Files.createTempDirectory("legacy-attachment-http-"));
        }
    }
    @BeforeEach void setup()throws Exception {
        seed(jdbc,"caller",HASH);jdbc.execute("TRUNCATE roles,user_authorities,settings_parameters");
        jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(7,'PAGE_DIALOGS')");
        reset(shared,storage);when(shared.loadSettings()).thenReturn(Map.of());flags.getFoundation().setEnabled(false);flags.setStrictReadScope(false);
        when(storage.openDialogAttachment(anyString(),anyString())).thenAnswer(c->binary());
        when(storage.openDialogAttachmentByStorageKey(anyString())).thenAnswer(c->binary());
    }
    private static AttachmentObjectStorageService.StoredBinary binary(){return new AttachmentObjectStorageService.StoredBinary(KEY,"video/mp4",BYTES.length,new ByteArrayInputStream(BYTES));}
    private MockHttpSession login(String name)throws Exception {
        var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(token.find()).isTrue();
        var cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
        var result=mvc.perform(post("/login").cookie(cookie).param("_csrf",token.group(1)).param("username",name).param("password",PASSWORD)).andExpect(redirectedUrl("/post-login")).andReturn();
        return (MockHttpSession)result.getRequest().getSession(false);
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(HttpRoute route) {
        return switch(route){
            case DIRECT->get("/api/attachments/tickets/"+TICKET+"/"+FILE);
            case QUERY->get("/api/attachments/tickets/by-ticket").param("ticketId",TICKET).param("filename",FILE);
            case KEY->get("/api/attachments/tickets/by-storage-key").param("key",KEY);
            case PATH->get("/api/attachments/tickets/by-path").param("path","attachments/"+KEY);
        };
    }
    private void beforeDownload(HttpRoute route,Runnable mutation,AtomicBoolean fired)throws Exception {
        var pending=doAnswer(call->{if(fired.compareAndSet(false,true))mutation.run();return call.callRealMethod();}).when(attachments);
        switch(route){
            case DIRECT,QUERY->pending.downloadTicketAttachment(any(Authentication.class),anyString(),anyString(),nullable(String.class));
            case KEY->pending.downloadTicketAttachmentByStorageKey(any(Authentication.class),anyString(),nullable(String.class));
            case PATH->pending.downloadTicketAttachmentByPath(any(Authentication.class),anyString(),nullable(String.class));
        }
    }
    static Stream<Arguments> changes(){return Stream.of("rename-reuse","delete-reuse","case-change","disabled","blocked","membership","business").flatMap(change->Arrays.stream(HttpRoute.values()).map(route->Arguments.of(change,route)));}
    @ParameterizedTest @MethodSource("changes") void lateAccountAndBusinessChangesCannotOpenStorage(String change,HttpRoute route)throws Exception {
        var session=login("caller");var fired=new AtomicBoolean();beforeDownload(route,()->replace(jdbc,change),fired);
        mvc.perform(request(route).session(session)).andExpect(status().isNotFound()).andExpect(content().bytes(new byte[0]));
        assertThat(fired).isTrue();verifyNoInteractions(storage);
    }
    static Stream<Arguments> denied(){return Stream.of("no-grant","strict-unresolved","general","global-admin","named-admin").flatMap(change->Arrays.stream(HttpRoute.values()).map(route->Arguments.of(change,route)));}
    @ParameterizedTest @MethodSource("denied") void pageOrAdministratorNeverGrantsBusinessData(String kind,HttpRoute route)throws Exception {
        String name=kind.equals("named-admin")?"admin":"caller";jdbc.update("UPDATE users SET username=? WHERE id=7",name);
        if(kind.equals("global-admin"))jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(7,'ROLE_ADMIN')");
        var session=login(name);
        if(kind.equals("strict-unresolved")){flags.setStrictReadScope(true);jdbc.update("UPDATE tickets SET business_id=NULL");}
        else if(kind.equals("general"))jdbc.update("UPDATE tickets SET is_general=TRUE");
        else jdbc.update("DELETE FROM business_memberships");
        var before=snapshot(jdbc);mvc.perform(request(route).session(session)).andExpect(status().isNotFound()).andExpect(content().bytes(new byte[0]));verifyNoInteractions(storage);assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> providers(){return Stream.of("local_fs","s3").flatMap(provider->Arrays.stream(HttpRoute.values()).map(route->Arguments.of(provider,route)));}
    @ParameterizedTest @MethodSource("providers") void currentViewerReadsTheSameRangesForBothStorageProviders(String provider,HttpRoute route)throws Exception {
        jdbc.update("UPDATE chat_attachment_metadata SET storage_provider=?",provider);var session=login("caller");var before=snapshot(jdbc);
        mvc.perform(request(route).session(session).header("Range","bytes=1-3")).andExpect(status().isPartialContent()).andExpect(header().string("Content-Range","bytes 1-3/6")).andExpect(content().bytes(new byte[]{1,2,3}));assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> names(){return Stream.of("Caller"," caller ").flatMap(name->Arrays.stream(HttpRoute.values()).map(route->Arguments.of(name,route)));}
    @ParameterizedTest @MethodSource("names") void storedRawNameIsNotTrimmedByTheDownloadBoundary(String name,HttpRoute route)throws Exception {
        jdbc.update("UPDATE users SET username=? WHERE id=7",name);var session=login(name);
        mvc.perform(request(route).session(session)).andExpect(status().isOk()).andExpect(content().bytes(BYTES));
    }
    @ParameterizedTest @EnumSource(HttpRoute.class) void anonymousRequestsStillRequireLogin(HttpRoute route)throws Exception {
        mvc.perform(request(route)).andExpect(redirectedUrl("http://localhost/login"));verifyNoInteractions(storage);
    }
    @ParameterizedTest @EnumSource(HttpRoute.class) void pagePermissionStillPrecedesOwnerAndBinaryAccess(HttpRoute route)throws Exception {
        var session=login("caller");jdbc.update("DELETE FROM user_authorities");
        mvc.perform(request(route).session(session)).andExpect(status().isForbidden());verifyNoInteractions(storage);
    }
}
