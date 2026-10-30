package com.example.panel.security;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.controller.*;
import com.example.panel.service.*;
import com.example.panel.storage.AttachmentObjectStorageService;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real form login and R189 refresh; account changes immediately before canonical entry SQL. */
@SpringBootTest(classes=BusinessReadEntryIdentityWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class BusinessReadEntryIdentityWebMvcTest {
    private static final String PASSWORD="fixture business entry",BODY="{\"businessId\":11,\"clientName\":\"Fixture\",\"problem\":\"Fixture\"}";
    private static final String HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SharedConfigService shared;
    @Autowired MultibusinessFeatureFlags flags;
    @Autowired ManualBusinessTicketCreationService creation;
    @SpyBean BusinessScopedTicketReadService reads;
    @Configuration(proxyBeanMethods=false)
    @Import({PreferenceIdentityWebMvcTest.Application.class,BusinessScopeReadController.class,ManualBusinessTicketController.class,BusinessScopedTicketReadService.class})
    static class Application {
        @Bean BusinessAccessService access(){return mock(BusinessAccessService.class);}
        @Bean AttachmentObjectStorageService storage(){return mock(AttachmentObjectStorageService.class);}
        @Bean ManualBusinessTicketCreationService creation(){return mock(ManualBusinessTicketCreationService.class);}
        @Bean MultibusinessFeatureFlags flags(){var f=new MultibusinessFeatureFlags();f.getFoundation().setEnabled(true);f.setManualTicketCreation(true);return f;}
    }
    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE users,user_authorities,roles,settings_parameters");
        jdbc.update("INSERT INTO users(id,username,password) VALUES(7,'caller',?)",HASH);
        jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(7,'PAGE_DIALOGS')");
        reset(shared,creation);when(shared.loadSettings()).thenReturn(Map.of());
        flags.getFoundation().setEnabled(true);flags.setManualTicketCreation(true);
        doReturn(List.of(new BusinessScopedTicketReadService.BusinessOption(11,"private","PRIVATE BUSINESS"))).when(reads).availableBusinesses(org.mockito.ArgumentMatchers.any(org.springframework.security.core.Authentication.class));
        when(creation.create(org.mockito.ArgumentMatchers.any(org.springframework.security.core.Authentication.class), eq(11L), eq("Fixture"), eq("Fixture"), isNull()))
            .thenReturn(new ManualBusinessTicketCreationService.CreatedTicket("a".repeat(32),"fixture",-7,11));
    }
    private MockHttpSession login(String name)throws Exception {
        var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(token.find()).isTrue();
        var result=mvc.perform(post("/login").cookie(page.getResponse().getCookie("XSRF-TOKEN")).param("_csrf",token.group(1)).param("username",name).param("password",PASSWORD)).andExpect(redirectedUrl("/post-login")).andReturn();
        return (MockHttpSession)result.getRequest().getSession(false);
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(boolean write,MockHttpSession session)throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder result;
        if(write) {
            var pageRequest=get("/login");if(session!=null)pageRequest.session(session);
            var page=mvc.perform(pageRequest).andExpect(status().isOk()).andReturn();
            var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(token.find()).isTrue();
            var cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
            result=post("/api/business-scope/tickets/manual").cookie(cookie).param("_csrf",token.group(1)).contentType("application/json").content(BODY);
        } else result=get("/api/business-scope/businesses");
        return session==null?result:result.session(session);
    }
    private void beforeEntry(Runnable mutation,java.util.concurrent.atomic.AtomicBoolean fired)throws Exception {
        var pending=doAnswer(call->{if(fired.compareAndSet(false,true))mutation.run();return call.callRealMethod();}).when(reads);
        var method=Arrays.stream(BusinessScopedTicketReadService.class.getMethods()).filter(m->m.getName().equals("requireAuthenticatedUserId")).findFirst().orElseThrow();
        method.invoke(pending,any(method.getParameterTypes()[0]));
    }
    static java.util.stream.Stream<Arguments> changes(){return java.util.stream.Stream.of("rename-reuse","delete-reuse","case-change","disabled","blocked").flatMap(change->java.util.stream.Stream.of(false,true).map(write->Arguments.of(change,write)));}
    @ParameterizedTest @MethodSource("changes")
    void lateAccountReplacementCannotReadOrCreate(String change,boolean write)throws Exception {
        var session=login("caller");var fired=new java.util.concurrent.atomic.AtomicBoolean();
        beforeEntry(()->{
            switch(change){
                case "rename-reuse"->{jdbc.update("UPDATE users SET username='renamed' WHERE id=7");jdbc.update("INSERT INTO users(id,username,password) VALUES(55,'caller',?)",HASH);}
                case "delete-reuse"->{jdbc.update("DELETE FROM users WHERE id=7");jdbc.update("INSERT INTO users(id,username,password) VALUES(55,'caller',?)",HASH);}
                case "case-change"->jdbc.update("UPDATE users SET username='Caller' WHERE id=7");
                case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=7");
                case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=7");
            }
        },fired);
        mvc.perform(request(write,session)).andExpect(status().isForbidden()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE BUSINESS"))));
        assertThat(fired).isTrue();verify(reads,never()).availableBusinesses(org.mockito.ArgumentMatchers.any(org.springframework.security.core.Authentication.class));verifyNoInteractions(creation);
    }
    @ParameterizedTest @CsvSource({"caller,false","Caller,false","' caller ',false","caller,true","Caller,true","' caller ',true"})
    void exactLoginUsesOnlyItsBoundId(String name,boolean write)throws Exception {
        jdbc.update("UPDATE users SET username=? WHERE id=7",name);var session=login(name);
        mvc.perform(request(write,session)).andExpect(status().is(write?201:200)).andExpect(header().string("Cache-Control","no-store"));
        if(write)verify(creation).create(org.mockito.ArgumentMatchers.argThat((org.springframework.security.core.Authentication a) -> a != null && a.getPrincipal() instanceof com.example.panel.security.PanelUserPrincipal p && p.getUserId() == 7L && p.getUsername().equals(name)), org.mockito.ArgumentMatchers.eq(11L), org.mockito.ArgumentMatchers.eq("Fixture"), org.mockito.ArgumentMatchers.eq("Fixture"), org.mockito.ArgumentMatchers.eq(null));else verify(reads).availableBusinesses(org.mockito.ArgumentMatchers.argThat((org.springframework.security.core.Authentication a) -> a != null && a.getPrincipal() instanceof com.example.panel.security.PanelUserPrincipal p && p.getUserId() == 7L && p.getUsername().equals(name)));
    }
    @ParameterizedTest @CsvSource({"anonymous,false","page,false","flag,false","anonymous,true","page,true","flag,true"})
    void existingRequestGatesStillRunBeforeEntry(String gate,boolean write)throws Exception {
        var session=login("caller");
        if(gate.equals("anonymous"))mvc.perform(request(write,null)).andExpect(redirectedUrl("http://localhost/login"));
        else if(gate.equals("page")){jdbc.update("DELETE FROM user_authorities");mvc.perform(request(write,session)).andExpect(status().isForbidden());}
        else {flags.getFoundation().setEnabled(false);mvc.perform(request(write,session)).andExpect(status().isNotFound());}
        assertThat(mockingDetails(reads).getInvocations()).noneMatch(i->i.getMethod().getName().equals("requireAuthenticatedUserId"));verifyNoInteractions(creation);
    }
    @Test void creationStillRequiresCsrf()throws Exception {
        var session=login("caller");mvc.perform(post("/api/business-scope/tickets/manual").session(session).contentType("application/json").content(BODY)).andExpect(status().isForbidden());verifyNoInteractions(creation);
    }
}
