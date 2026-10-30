package com.example.panel.security;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.controller.*;
import com.example.panel.service.*;
import com.example.panel.storage.AttachmentObjectStorageService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static com.example.panel.support.GeneralEntryIdentityFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real login, R189 refresh, CSRF, current SQL identity and all GENERAL read/admin entry routes. */
@SpringBootTest(classes=GeneralEntryIdentityWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class GeneralEntryIdentityWebMvcTest {
    enum Route {
        PAGE("GET","/api/general-tickets/page",200), CARD("GET","/api/general-tickets/"+PUBLIC,404),
        HISTORY("GET","/api/general-tickets/"+PUBLIC+"/history",404), ANSWERS("GET","/api/general-tickets/"+PUBLIC+"/answers",404),
        ATTACHMENT("GET","/api/general-tickets/"+PUBLIC+"/history/1/attachment",404), QUEUE("GET","/general-dialogs",200),
        ACCOUNTS("GET","/api/general-ticket-admin/access",200), ADMIN_PAGE("GET","/general-dialogs/access",200),
        READ_GRANT("PUT","/api/general-ticket-admin/access/55",204), READ_REVOKE("DELETE","/api/general-ticket-admin/access/55",204),
        OPERATE_GRANT("PUT","/api/general-ticket-admin/access/55/operate",204), OPERATE_REVOKE("DELETE","/api/general-ticket-admin/access/55/operate",204);
        final String method,path;final int status;
        Route(String method,String path,int status){this.method=method;this.path=path;this.status=status;}
        boolean admin(){return ordinal()>=ACCOUNTS.ordinal();}
    }
    private static final String PUBLIC="a".repeat(32),PASSWORD="fixture general entry",HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SharedConfigService shared;
    @Autowired GeneralTicketReadService reads;
    @Autowired AttachmentObjectStorageService storage;
    @SpyBean MultibusinessFeatureFlags flags;
    @Configuration(proxyBeanMethods=false)
    @Import({PreferenceIdentityWebMvcTest.Application.class,GeneralTicketReadController.class,
        GeneralTicketAccessController.class,GeneralTicketPageController.class,GeneralTicketAccessService.class})
    static class Application {
        @Bean InitializingBean generalSchema(JdbcTemplate jdbc){return ()->schema(jdbc,true);}
        @Bean MultibusinessFeatureFlags flags(){return new MultibusinessFeatureFlags();}
        @Bean GeneralTicketReadService reads(){return mock(GeneralTicketReadService.class);}
        @Bean NavigationService navigation(){return mock(NavigationService.class);}
        @Bean AttachmentObjectStorageService storage(){return mock(AttachmentObjectStorageService.class);}
    }
    @BeforeEach void setup() {
        seed(jdbc,7,"caller",HASH);jdbc.execute("TRUNCATE roles,user_authorities,settings_parameters");
        for(String authority:List.of("PAGE_DIALOGS","PAGE_SETTINGS","ROLE_ADMIN"))jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(7,?)",authority);
        reset(shared,reads,storage);when(shared.loadSettings()).thenReturn(Map.of());
        flags.getFoundation().setEnabled(true);flags.setGeneralTicketQueue(true);
        when(reads.page(any(org.springframework.security.core.Authentication.class),anyInt(),anyInt(),nullable(String.class),nullable(String.class)))
            .thenReturn(new GeneralTicketReadService.TicketPage(List.of(),false));
    }
    private MockHttpSession login(String name)throws Exception {
        var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(token.find()).isTrue();
        var cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
        var result=mvc.perform(post("/login").cookie(cookie).param("_csrf",token.group(1)).param("username",name).param("password",PASSWORD))
            .andExpect(redirectedUrl("/post-login")).andReturn();
        return (MockHttpSession)result.getRequest().getSession(false);
    }
    private MockHttpServletRequestBuilder request(Route route,MockHttpSession session)throws Exception {
        var request=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(route.method),route.path).session(session);
        if(!route.method.equals("GET")) {
            var page=mvc.perform(get("/login").session(session)).andExpect(status().isOk()).andReturn();
            var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(token.find()).isTrue();
            request.cookie(page.getResponse().getCookie("XSRF-TOKEN")).param("_csrf",token.group(1));
        }
        return request;
    }
    static Stream<Arguments> changes(){return Stream.of("rename-reuse","delete-reuse","case-change","disabled","blocked","ambiguous")
        .flatMap(kind->Arrays.stream(Route.values()).map(route->Arguments.of(kind,route)));}
    @ParameterizedTest @MethodSource("changes") void accountChangesAfterR189DenyEveryEntry(String kind,Route route)throws Exception {
        var session=login("caller");var request=request(route,session);var fired=new AtomicBoolean();
        var after=new java.util.concurrent.atomic.AtomicReference<Map<String,List<Map<String,Object>>>>();
        // Every controller evaluates this flag after security/session refresh and before account/data access.
        doAnswer(call->{if(fired.compareAndSet(false,true)){change(jdbc,kind);after.set(grants(jdbc));}return call.callRealMethod();})
            .when(flags).isGeneralTicketQueue();
        mvc.perform(request).andExpect(status().isForbidden());
        assertThat(fired).isTrue();assertThat(grants(jdbc)).isEqualTo(after.get());verifyNoInteractions(reads,storage);
    }
    @ParameterizedTest @EnumSource(Route.class) void currentBoundAccountKeepsItsExistingRouteContract(Route route)throws Exception {
        mvc.perform(request(route,login("caller"))).andExpect(status().is(route.status));
        if(route==Route.PAGE)verify(reads).page(argThat(a -> a.getPrincipal() instanceof PanelUserPrincipal p && p.getUserId() == 7L),anyInt(),anyInt(),nullable(String.class),nullable(String.class));
        verifyNoInteractions(storage);
    }
    @ParameterizedTest @EnumSource(value=Route.class,names={"ACCOUNTS","ADMIN_PAGE","READ_GRANT","READ_REVOKE","OPERATE_GRANT","OPERATE_REVOKE"})
    void administratorCanManageOthersWithoutOwnGeneralRead(Route route)throws Exception {
        jdbc.update("DELETE FROM general_ticket_read_grants WHERE user_id=7");
        mvc.perform(request(route,login("caller"))).andExpect(status().is(route.status));
    }
    @ParameterizedTest @EnumSource(Route.class) void refreshedPageAndAdminAuthoritiesRemainRequired(Route route)throws Exception {
        var session=login("caller");jdbc.execute("TRUNCATE user_authorities");
        mvc.perform(request(route,session)).andExpect(status().isForbidden());verifyNoInteractions(reads,storage);
    }
    @ParameterizedTest @EnumSource(Route.class) void disabledFeatureRemainsHidden(Route route)throws Exception {
        flags.getFoundation().setEnabled(false);
        mvc.perform(request(route,login("caller"))).andExpect(status().isNotFound());verifyNoInteractions(reads,storage);
    }
}
