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
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static com.example.panel.support.BusinessCatalogActorFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real form login/R189, controllers and SQL, including actual manual inserts. */
@SpringBootTest(classes=BusinessCatalogActorWebMvcTest.Application.class,properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class BusinessCatalogActorWebMvcTest {
    static final String PASSWORD="fixture catalog",HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD),BODY="{\"businessId\":11,\"clientName\":\"New client\",\"problem\":\"New problem\"}";
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired MultibusinessFeatureFlags flags;
    @SpyBean BusinessScopedTicketReadService reads;
    @Configuration(proxyBeanMethods=false)
    @Import({PreferenceIdentityWebMvcTest.Application.class,BusinessScopeReadController.class,ManualBusinessTicketController.class,BusinessScopedTicketReadService.class,ManualBusinessTicketCreationService.class,BusinessAccessService.class})
    static class Application {
        @Bean InitializingBean schema(JdbcTemplate jdbc){return ()->com.example.panel.support.BusinessCatalogActorFixture.schema(jdbc,true);}
        @Bean MultibusinessFeatureFlags flags(){return new MultibusinessFeatureFlags();}
        @Bean AttachmentObjectStorageService storage(){return mock(AttachmentObjectStorageService.class);}
    }
    @BeforeEach void setup(){resetFixture(false);}
    void resetFixture(boolean all){seed(jdbc,7,"caller",HASH,all);jdbc.execute("TRUNCATE user_authorities,roles,settings_parameters");jdbc.update("INSERT INTO user_authorities VALUES(7,'PAGE_DIALOGS')");flags.getFoundation().setEnabled(true);flags.setManualTicketCreation(true);}
    MockHttpSession login()throws Exception{var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(token.find()).isTrue();var result=mvc.perform(post("/login").cookie(page.getResponse().getCookie("XSRF-TOKEN")).param("_csrf",token.group(1)).param("username","caller").param("password",PASSWORD)).andExpect(redirectedUrl("/post-login")).andReturn();return (MockHttpSession)result.getRequest().getSession(false);}
    MockHttpServletRequestBuilder request(Surface surface,MockHttpSession session)throws Exception {
        if(surface!=Surface.manual)return get("/api/business-scope/"+switch(surface){case catalog->"businesses";case list->"tickets";case page->"tickets/page";case export->"tickets/export";case summary->"tickets/summary";case categories->"tickets/categories";default->throw new AssertionError();}).param("business_id","all").session(session);
        var page=mvc.perform(get("/login").session(session)).andExpect(status().isOk()).andReturn();var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(token.find()).isTrue();
        return post("/api/business-scope/tickets/manual").session(session).cookie(page.getResponse().getCookie("XSRF-TOKEN")).param("_csrf",token.group(1)).contentType("application/json").content(BODY);
    }
    static Stream<Arguments> profiles(){return Stream.of(false,true).flatMap(all->Arrays.stream(Surface.values()).map(s->Arguments.of(all,s)));}
    @ParameterizedTest @MethodSource("profiles") void realLoginReadsAndCreatesUsingEitherScopeSource(boolean all,Surface surface)throws Exception {
        resetFixture(all);var result=mvc.perform(request(surface,login())).andExpect(status().is(surface==Surface.manual?201:200)).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"));
        switch(surface){case catalog->result.andExpect(jsonPath("$[0].id").value(11));case list,export->result.andExpect(jsonPath("$.length()").value(2));case page->result.andExpect(jsonPath("$.tickets.length()").value(2));case summary->result.andExpect(jsonPath("$.total").value(2));case categories->result.andExpect(jsonPath("$[0].total").value(2));case manual->{result.andExpect(jsonPath("$.businessId").value(11));assertThat(jdbc.queryForObject("SELECT created_by_user_id FROM manual_business_clients",Long.class)).isEqualTo(7L);}}
    }
    static Stream<Arguments> late(){return Stream.of("rename-reuse","case","blocked","scope").flatMap(mode->Stream.of(false,true).flatMap(all->Arrays.stream(Surface.values()).map(s->Arguments.of(mode,all,s))));}
    @ParameterizedTest @MethodSource("late") void lateAccountChangesCannotExposeOrCreate(String mode,boolean all,Surface surface)throws Exception {
        resetFixture(all);var session=login();var fired=new AtomicBoolean();doAnswer(call->{var id=call.callRealMethod();if(fired.compareAndSet(false,true))change(jdbc,mode);return id;}).when(reads).requireAuthenticatedUserId(any(Authentication.class));
        var result=mvc.perform(request(surface,session)).andExpect(status().is(surface==Surface.manual?403:200));assertThat(fired).isTrue();assertThat(result.andReturn().getResponse().getContentAsString()).doesNotContain("private client","private problem","New problem");
        switch(surface){case catalog,list,export,categories->result.andExpect(jsonPath("$.length()").value(0));case page->result.andExpect(jsonPath("$.tickets.length()").value(0));case summary->result.andExpect(jsonPath("$.total").value(0));default->{}}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM manual_business_clients",Integer.class)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets",Integer.class)).isEqualTo(2);
    }
    @ParameterizedTest @EnumSource(Surface.class) void currentPageRightStillRequired(Surface surface)throws Exception{var session=login();jdbc.execute("TRUNCATE user_authorities");mvc.perform(request(surface,session)).andExpect(status().isForbidden());}
    @ParameterizedTest @EnumSource(Surface.class) void foundationStillHidesEveryEntry(Surface surface)throws Exception{var session=login();flags.getFoundation().setEnabled(false);mvc.perform(request(surface,session)).andExpect(status().isNotFound());}
    @Test void manualFlagAndCsrfRemainRequired()throws Exception{var session=login();mvc.perform(post("/api/business-scope/tickets/manual").session(session).contentType("application/json").content(BODY)).andExpect(status().isForbidden());flags.setManualTicketCreation(false);mvc.perform(request(Surface.manual,session)).andExpect(status().isNotFound());assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM manual_business_clients",Integer.class)).isZero();}
}
