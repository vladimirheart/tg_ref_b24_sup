package com.example.panel.security;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.controller.GeneralTicketReadController;
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
import static com.example.panel.support.GeneralReadActorFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual form login/R189/controller/SQL; only binary storage is replaced. */
@SpringBootTest(classes=GeneralReadActorWebMvcTest.Application.class,properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class GeneralReadActorWebMvcTest {
    static final String PASSWORD="fixture read actor",HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AttachmentObjectStorageService storage;
    @Autowired MultibusinessFeatureFlags flags;
    @SpyBean GeneralTicketAccessService access;
    @Configuration(proxyBeanMethods=false)
    @Import({PreferenceIdentityWebMvcTest.Application.class,GeneralTicketReadController.class,GeneralTicketReadService.class,GeneralTicketAccessService.class})
    static class Application {
        @Bean InitializingBean schema(JdbcTemplate jdbc){return ()->com.example.panel.support.GeneralReadActorFixture.schema(jdbc,true);}
        @Bean MultibusinessFeatureFlags flags(){return new MultibusinessFeatureFlags();}
        @Bean AttachmentObjectStorageService storage(){return mock(AttachmentObjectStorageService.class);}
    }
    @BeforeEach void setup()throws Exception{
        seed(jdbc,7,"caller",HASH);jdbc.execute("TRUNCATE user_authorities,roles,settings_parameters");
        jdbc.update("INSERT INTO user_authorities VALUES(7,'PAGE_DIALOGS')");flags.getFoundation().setEnabled(true);flags.setGeneralTicketQueue(true);reset(storage);
        when(storage.openDialogAttachmentByStorageKey(anyString())).thenAnswer(call->new AttachmentObjectStorageService.StoredBinary(call.getArgument(0),"application/octet-stream",BYTES.length,new ByteArrayInputStream(BYTES)));
    }
    MockHttpSession login()throws Exception{
        var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(token.find()).isTrue();
        var result=mvc.perform(post("/login").cookie(page.getResponse().getCookie("XSRF-TOKEN")).param("_csrf",token.group(1)).param("username","caller").param("password",PASSWORD)).andExpect(redirectedUrl("/post-login")).andReturn();return (MockHttpSession)result.getRequest().getSession(false);
    }
    String path(Surface surface){return "/api/general-tickets/"+switch(surface){case page->"page";case find->PUBLIC;case history->PUBLIC+"/history";case answers->PUBLIC+"/answers";default->publicId(surface)+"/history/"+historyId(surface)+"/attachment";};}
    @ParameterizedTest @EnumSource(Surface.class) void realLoginPreservesEveryReadContract(Surface surface)throws Exception{
        var result=mvc.perform(get(path(surface)).session(login())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"));
        switch(surface){case page->result.andExpect(jsonPath("$.tickets.length()").value(2));case find->result.andExpect(jsonPath("$.problem").value("private problem"));case history->result.andExpect(jsonPath("$.entries.length()").value(3));case answers->result.andExpect(jsonPath("$.answers[0].valueText").value("private answer"));default->result.andExpect(content().bytes(BYTES)).andExpect(content().contentTypeCompatibleWith("application/octet-stream"));}
    }
    static Stream<Arguments> late(){return Stream.of("rename-reuse","case","read","blocked").flatMap(mode->Arrays.stream(Surface.values()).map(surface->Arguments.of(mode,surface)));}
    @ParameterizedTest @MethodSource("late") void aChangeAfterControllerIdentityCheckCannotExposeData(String mode,Surface surface)throws Exception{
        var session=login();var fired=new AtomicBoolean();
        doAnswer(call->{var id=call.callRealMethod();if(fired.compareAndSet(false,true))change(jdbc,mode);return id;}).when(access).requireAuthenticatedUserId(any(Authentication.class));
        int code=surface==Surface.page?(Set.of("read","blocked").contains(mode)?403:200):404;
        var result=mvc.perform(get(path(surface)).session(session)).andExpect(status().is(code)).andReturn();assertThat(fired).isTrue();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private problem","private answer","private history",RAW);verifyNoInteractions(storage);
        if(code==200)assertThat(result.getResponse().getContentAsString()).contains("\"tickets\":[]");
    }
    static Stream<Arguments> binaryChanges(){return Stream.of(Surface.telegram,Surface.vk,Surface.document).flatMap(surface->Stream.of("rename-reuse","read").map(mode->Arguments.of(surface,mode)));}
    @ParameterizedTest @MethodSource("binaryChanges") void verifiedBinaryIsNotReturnedAfterAccountOrGrantChangesDuringStorageRead(Surface surface,String mode)throws Exception{
        var session=login();var input=spy(new ByteArrayInputStream(BYTES));
        when(storage.openDialogAttachmentByStorageKey(storageKey(surface))).thenAnswer(call->{change(jdbc,mode);return new AttachmentObjectStorageService.StoredBinary(call.getArgument(0),"application/octet-stream",BYTES.length,input);});
        mvc.perform(get(path(surface)).session(session)).andExpect(status().isNotFound());verify(input,atLeastOnce()).close();
    }
    @ParameterizedTest @EnumSource(Surface.class) void disabledFeatureStaysHidden(Surface surface)throws Exception{var session=login();flags.setGeneralTicketQueue(false);mvc.perform(get(path(surface)).session(session)).andExpect(status().isNotFound());verifyNoInteractions(storage);}
    @ParameterizedTest @EnumSource(Surface.class) void r189StillRequiresCurrentPageAuthority(Surface surface)throws Exception{var session=login();jdbc.execute("TRUNCATE user_authorities");mvc.perform(get(path(surface)).session(session)).andExpect(status().isForbidden());verifyNoInteractions(storage);}
}
