package com.example.panel.security;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.controller.BusinessScopeReadController;
import com.example.panel.service.*;
import com.example.panel.storage.AttachmentObjectStorageService;
import java.io.ByteArrayInputStream;
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
import static com.example.panel.support.BusinessChildActorFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual form login/R189 and BUSINESS SQL. Only binary storage is replaced. */
@SpringBootTest(classes=BusinessChildActorWebMvcTest.Application.class,properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class BusinessChildActorWebMvcTest {
    static final String PASSWORD="fixture business child",HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;@Autowired JdbcTemplate jdbc;
    @Autowired AttachmentObjectStorageService storage;@Autowired MultibusinessFeatureFlags flags;
    @SpyBean BusinessScopedTicketReadService reads;
    @Configuration(proxyBeanMethods=false)
    @Import({PreferenceIdentityWebMvcTest.Application.class,BusinessScopeReadController.class,BusinessScopedTicketReadService.class,BusinessAccessService.class})
    static class Application {
        @Bean InitializingBean schema(JdbcTemplate jdbc){return ()->com.example.panel.support.BusinessChildActorFixture.schema(jdbc,true);}
        @Bean MultibusinessFeatureFlags flags(){return new MultibusinessFeatureFlags();}
        @Bean AttachmentObjectStorageService storage(){return mock(AttachmentObjectStorageService.class);}
    }
    @BeforeEach void setup()throws Exception{resetFixture(false);}
    void resetFixture(boolean all)throws Exception{
        seed(jdbc,7,"caller",HASH,all);jdbc.execute("TRUNCATE user_authorities,roles,settings_parameters");jdbc.update("INSERT INTO user_authorities VALUES(7,'PAGE_DIALOGS')");flags.getFoundation().setEnabled(true);reset(storage);
        when(storage.openDialogAttachmentByStorageKey(anyString())).thenAnswer(call->new AttachmentObjectStorageService.StoredBinary(call.getArgument(0),"application/octet-stream",BYTES.length,new ByteArrayInputStream(BYTES)));
    }
    MockHttpSession login()throws Exception{var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(token.find()).isTrue();var result=mvc.perform(post("/login").cookie(page.getResponse().getCookie("XSRF-TOKEN")).param("_csrf",token.group(1)).param("username","caller").param("password",PASSWORD)).andExpect(redirectedUrl("/post-login")).andReturn();return (MockHttpSession)result.getRequest().getSession(false);}
    String path(Surface surface){return "/api/business-scope/tickets/"+publicId(surface)+switch(surface){case card->"";case history->"/history";case answers->"/answers";case workspace->"/workspace";case spans->"/spans";default->"/history/"+historyId(surface)+"/attachment";};}
    static Stream<Arguments> profiles(){return Stream.of(false,true).flatMap(all->Arrays.stream(Surface.values()).map(surface->Arguments.of(all,surface)));}
    @ParameterizedTest @MethodSource("profiles") void realLoginReadsCanonicalChildrenWithEitherScopeSource(boolean all,Surface surface)throws Exception{
        resetFixture(all);var result=mvc.perform(get(path(surface)).session(login())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"));
        switch(surface){case card->result.andExpect(jsonPath("$.problemPreview").value("private problem"));case history->result.andExpect(jsonPath("$.entries.length()").value(3));case answers->result.andExpect(jsonPath("$.answers[0].valueText").value("private answer"));case workspace->result.andExpect(jsonPath("$.responsible").value("private responsible"));case spans->result.andExpect(jsonPath("$.spans[0].durationSeconds").value(5));default->result.andExpect(content().bytes(BYTES)).andExpect(content().contentTypeCompatibleWith("application/octet-stream"));}
    }
    static Stream<Arguments> late(){return Stream.of("rename-reuse","case","blocked","scope").flatMap(mode->Stream.of(false,true).flatMap(all->Arrays.stream(Surface.values()).map(surface->Arguments.of(mode,all,surface))));}
    @ParameterizedTest @MethodSource("late") void changesAfterEntryCannotExposeChildData(String mode,boolean all,Surface surface)throws Exception{
        resetFixture(all);var session=login();var fired=new AtomicBoolean();doAnswer(call->{var id=call.callRealMethod();if(fired.compareAndSet(false,true))change(jdbc,mode);return id;}).when(reads).requireAuthenticatedUserId(any(Authentication.class));
        var result=mvc.perform(get(path(surface)).session(session)).andExpect(status().isNotFound()).andReturn();assertThat(fired).isTrue();assertThat(result.getResponse().getContentAsString()).doesNotContain("private problem","private answer","private responsible",RAW);verifyNoInteractions(storage);
    }
    static Stream<Arguments> duringStorage(){return Stream.of(Surface.telegram,Surface.vk).flatMap(surface->Stream.of("rename-reuse","scope").flatMap(mode->Stream.of(false,true).map(all->Arguments.of(surface,mode,all))));}
    @ParameterizedTest @MethodSource("duringStorage") void hashedBytesAreRecheckedAgainstTheSameSessionActor(Surface surface,String mode,boolean all)throws Exception{
        resetFixture(all);var session=login();var input=spy(new ByteArrayInputStream(BYTES));when(storage.openDialogAttachmentByStorageKey(storageKey(surface))).thenAnswer(call->{change(jdbc,mode);return new AttachmentObjectStorageService.StoredBinary(call.getArgument(0),"application/octet-stream",BYTES.length,input);});mvc.perform(get(path(surface)).session(session)).andExpect(status().isNotFound());verify(input,atLeastOnce()).close();
    }
    @ParameterizedTest @EnumSource(Surface.class) void disabledFoundationRemainsHidden(Surface surface)throws Exception{var session=login();flags.getFoundation().setEnabled(false);mvc.perform(get(path(surface)).session(session)).andExpect(status().isNotFound());verifyNoInteractions(storage);}
    @ParameterizedTest @EnumSource(Surface.class) void r189StillRequiresTheCurrentPageRight(Surface surface)throws Exception{var session=login();jdbc.execute("TRUNCATE user_authorities");mvc.perform(get(path(surface)).session(session)).andExpect(status().isForbidden());verifyNoInteractions(storage);}
}
