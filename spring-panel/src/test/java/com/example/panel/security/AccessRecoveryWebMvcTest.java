package com.example.panel.security;

import com.example.panel.controller.*;
import com.example.panel.repository.PanelUserRepository;
import com.example.panel.service.*;
import com.example.panel.support.AccessRecoveryTestDatabase;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.bind.annotation.GetMapping;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes=AccessRecoveryWebMvcTest.Application.class, properties={
        "spring.session.store-type=none", "app.access-recovery.minimum-response-time=10ms",
        "management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class AccessRecoveryWebMvcTest {
    @Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,FlywayAutoConfiguration.class})
    @EnableTransactionManagement
    @Import({SecurityConfig.class,SecurityHeadersFilter.class,PasswordResetRequestApiController.class,
            AccessRecoveryPageController.class,AttentionApiController.class,AccessRecoveryExceptionHandler.class,
            UnblockRequestsController.class,UnblockRequestsApiController.class,ClientBlacklistApiController.class,
            AccessRecoveryService.class,AttentionService.class,NavigationService.class,LoginPage.class})
    static class Application {
        @Bean(name="usersJdbcTemplate") JdbcTemplate jdbc() { return AccessRecoveryTestDatabase.fresh(); }
        @Bean PlatformTransactionManager transactionManager(JdbcTemplate jdbc) { return new DataSourceTransactionManager(jdbc.getDataSource()); }
        @Bean PermissionService permissions() { return new PermissionService(); }
        @Bean com.example.panel.config.MultibusinessFeatureFlags flags() { return mock(com.example.panel.config.MultibusinessFeatureFlags.class); }
        @Bean PanelUserRepository users() { return mock(PanelUserRepository.class); }
        @Bean PanelUserPhotoService photos() { return mock(PanelUserPhotoService.class); }
        @Bean UnblockRequestService unblocks() { return mock(UnblockRequestService.class); }
        @Bean ClientBlacklistService blacklist() { return mock(ClientBlacklistService.class); }
    }
    @Controller static class LoginPage { @GetMapping("/login") String login() { return "auth/login"; } }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired org.springframework.security.web.FilterChainProxy filters;
    @Autowired ClientBlacklistService blacklist;
    @Autowired UnblockRequestService unblocks;
    @org.springframework.boot.test.mock.mockito.MockBean UiEventStreamService events;
    @BeforeEach void fixtures() {
        // SecurityMockMvc csrf() substitutes its repository; restore production cookies between methods.
        filters.getFilters("/login").stream().filter(org.springframework.security.web.csrf.CsrfFilter.class::isInstance)
                .map(org.springframework.security.web.csrf.CsrfFilter.class::cast)
                .forEach(filter -> org.springframework.test.util.ReflectionTestUtils.setField(filter, "tokenRepository",
                        org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse()));
        jdbc.execute("TRUNCATE users RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE public_auth_rate_limits, public_auth_events, client_unblock_requests RESTART IDENTITY");
        jdbc.update("INSERT INTO users(username,password) VALUES ('admin',?),('operator',?),('blocked',?)",
                passwords.encode("known-password"),passwords.encode("known-password"),passwords.encode("known-password"));
        jdbc.update("UPDATE users SET enabled=FALSE,is_blocked=TRUE WHERE username='blocked'");
        jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES (1,'ROLE_ADMIN'),(2,'ROLE_USER')");
        when(unblocks.loadRequests(any())).thenReturn(List.of());
        reset(blacklist);
    }
    PanelUserPrincipal operator(String... rights) {
        jdbc.update("DELETE FROM user_authorities WHERE user_id=2");
        for(String right:rights) jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES (2,?)",right);
        return new PanelUserPrincipal(2,"operator","",true,Arrays.stream(rights).map(SimpleGrantedAuthority::new).toList());
    }
    @Test void publicSubmissionIsGenericForKnownBlockedAndUnknownAccountsAndRequiresCsrf() throws Exception {
        String expected=null;
        for(String name:List.of("operator","blocked","missing")) {
            var result=mvc.perform(post("/api/password-reset-requests/public").with(csrf()).contentType("application/json")
                    .content("{\"username\":\""+name+"\",\"kind\":\"password\"}"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
            if(expected==null)expected=result.getResponse().getContentAsString();
            else assertThat(result.getResponse().getContentAsString()).isEqualTo(expected);
        }
        mvc.perform(post("/api/password-reset-requests/public").contentType("application/json").content("{\"username\":\"operator\"}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM password_reset_requests",Integer.class)).isEqualTo(3);
    }
    @Test void realCookieCsrfHeaderFromRenderedLoginIsAccepted() throws Exception {
        var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        String html=page.getResponse().getContentAsString();
        var matcher=java.util.regex.Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);
        assertThat(matcher.find()).isTrue();
        mvc.perform(post("/api/password-reset-requests/public").cookie(page.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN",matcher.group(1)).contentType("application/json").content("{\"username\":\"missing\"}"))
                .andExpect(status().isOk());
    }
    @Test void deniedRightsCannotBeBypassedThroughListDecisionPageOrDirectClientApi() throws Exception {
        var actor=operator("PAGE_CLIENTS","ROLE_ADMIN","DENY_EDIT_ACCESS_RECOVER","DENY_EDIT_CLIENT_UNBLOCK");
        mvc.perform(get("/api/password-reset-requests").with(user(actor))).andExpect(status().isForbidden());
        mvc.perform(get("/access-recovery-requests").with(user(actor))).andExpect(redirectedUrl("/error/403"));
        mvc.perform(post("/api/password-reset-requests/1/approve").with(user(actor)).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/unblock-requests/decision").with(user(actor)).with(csrf()).param("request_id","1").param("decision","approve"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/blacklist/remove").with(user(actor)).with(csrf()).param("user_id","123"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(blacklist);
    }
    @Test void delegatedRecoveryWorksWithoutSettingsAndOrdinaryUsersOnlyGetClientAttention() throws Exception {
        var delegated=operator(AccessRecoveryService.RIGHT);
        mvc.perform(get("/access-recovery-requests").with(user(delegated))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-recovery-page")));
        mvc.perform(get("/api/attention").with(user(delegated))).andExpect(status().isOk()).andExpect(jsonPath("$.recovery.pending").value(0));
        var ordinary=operator("ROLE_USER");
        mvc.perform(get("/api/attention").with(user(ordinary))).andExpect(status().isOk())
                .andExpect(jsonPath("$.unblock.pending").value(0)).andExpect(jsonPath("$.recovery").doesNotExist());
        mvc.perform(get("/unblock-requests").with(user(ordinary))).andExpect(status().isOk());
    }
    @Test void authenticationFailuresUseSameRedirectAndNeverCreateAuthenticatedContext() throws Exception {
        for(String name:List.of("operator","blocked","missing"))
            mvc.perform(post("/login").with(csrf()).param("username",name).param("password","wrong-password"))
                    .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
        mvc.perform(post("/login").with(csrf()).param("username","blocked").param("password","known-password"))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public_auth_events",Integer.class)).isEqualTo(4);
    }
    @Test void approvalSerializesTheCredentialOnlyToAuthorizedOperator() throws Exception {
        mvc.perform(post("/api/password-reset-requests/public").with(csrf()).contentType("application/json").content("{\"username\":\"blocked\",\"kind\":\"password\"}"))
                .andExpect(status().isOk());
        long id=jdbc.queryForObject("SELECT id FROM password_reset_requests WHERE user_id=3",Long.class);
        var actor=operator(AccessRecoveryService.RIGHT);
        var response=mvc.perform(post("/api/password-reset-requests/"+id+"/approve").with(user(actor)).with(csrf()).contentType("application/json")
                .content("{\"identity_verified\":true,\"note\":\"Проверено у руководителя\",\"reset_password\":true,\"unblock_account\":false}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString()).get("temporary_password").asText()).hasSize(14);
        assertThat(jdbc.queryForObject("SELECT is_blocked FROM users WHERE id=3",Boolean.class)).isTrue();
    }
}
