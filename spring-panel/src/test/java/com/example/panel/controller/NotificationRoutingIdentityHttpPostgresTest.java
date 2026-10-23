package com.example.panel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.*;
import com.example.panel.support.PostgresqlIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest @AutoConfigureMockMvc
class NotificationRoutingIdentityHttpPostgresTest extends PostgresqlIntegrationTestSupport {
    private static final String SETTINGS="/api/settings/notification-routing";
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NotificationRoutingService routing;
    @Autowired private NotificationService notifications;
    @Autowired private SharedConfigService config;
    @Autowired private MultibusinessFeatureFlags flags;
    @Autowired private ObjectMapper mapper;
    @Autowired private MockMvc mvc;
    @MockBean private UiEventStreamService stream;
    private String reader,spaced,department;
    private boolean previousStrict;
    private Map<String,Object> previousSettings;
    private final Set<String> names=new LinkedHashSet<>();

    @BeforeEach void prepare() {
        previousSettings=new LinkedHashMap<>(config.loadSettings());previousStrict=flags.isStrictReadScope();flags.setStrictReadScope(false);
        jdbc.update("DELETE FROM notifications");reader="route-r174-"+UUID.randomUUID();spaced=" "+reader+" ";department="team-"+UUID.randomUUID();
        account(reader);account(spaced);clearInvocations(stream);
    }
    @AfterEach void restore() {
        config.saveSettings(previousSettings);flags.setStrictReadScope(previousStrict);
        for(String name:names)jdbc.update("DELETE FROM notifications WHERE user_identity=?",name);
    }

    @ParameterizedTest @ValueSource(strings={"prefix","suffix","both","tab","newline"})
    void baseAudiencePersistsTheExactRecipientAndRefreshesOnlyThatIdentity(String variant) throws Exception {
        String identity=switch(variant){case "prefix"->" "+reader;case "suffix"->reader+" ";case "tab"->"\t"+reader+"\t";case "newline"->"\n"+reader+"\r";default->spaced;};
        if(!names.contains(identity))account(identity);
        routing.notify("tasks","task_saved",Set.of(identity.toUpperCase(Locale.ROOT)),"recipient private notice","/tasks",null);
        assertThat(jdbc.queryForList("SELECT user_identity FROM notifications",String.class)).containsExactly(identity);
        verify(stream).publishNotificationsChanged(identity,"notification_created");
        mvc.perform(auth(get("/api/notifications"),reader,"PAGE_DIALOGS")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(auth(get("/api/notifications"),identity,"PAGE_DIALOGS")).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].text").value("recipient private notice"));
        mvc.perform(auth(post("/api/notifications/read-all"),reader,"PAGE_DIALOGS").with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(0));
        mvc.perform(auth(post("/api/notifications/read-all"),identity,"PAGE_DIALOGS").with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(1));
    }

    @ParameterizedTest @ValueSource(strings={"employees_only","department_except"})
    void settingsSaveReloadAndDispatchKeepLiteralArrayNames(String mode) throws Exception {
        String json=payload(mode);
        mvc.perform(auth(post(SETTINGS).contentType(MediaType.APPLICATION_JSON).content(json),"route-admin","PAGE_SETTINGS").with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.notification_routing.tasks.task_saved.employeeUsernames[0]").value(spaced))
            .andExpect(jsonPath("$.notification_routing.tasks.task_saved.excludeUsernames[0]").value(reader));
        mvc.perform(auth(get(SETTINGS),"route-admin","PAGE_SETTINGS")).andExpect(status().isOk())
            .andExpect(jsonPath("$.notification_routing.tasks.task_saved.employeeUsernames[0]").value(spaced));
        var fresh=new NotificationRoutingService(config,notifications,jdbc,jdbc);
        assertThat(fresh.loadRoute("tasks","task_saved").employeeUsernames()).containsExactly(spaced);
        assertThat(fresh.loadRoute("tasks","task_saved").excludeUsernames()).containsExactly(reader);
        fresh.notify("tasks","task_saved",Set.of(reader),"literal account notice","/tasks",null);
        assertThat(jdbc.queryForList("SELECT user_identity FROM notifications",String.class)).containsExactly(spaced);
        verify(stream).publishNotificationsChanged(spaced,"notification_created");
        assertThat(notifications.findForUser(reader)).isEmpty();assertThat(notifications.findForUser(spaced)).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings={"disabled","blocked","department","deleted"})
    void currentDirectoryStateIsCheckedAgainAfterSettingsWereSaved(String mutation) throws Exception {
        mvc.perform(auth(post(SETTINGS).contentType(MediaType.APPLICATION_JSON).content(payload("employees_only")),"route-admin","PAGE_SETTINGS").with(csrf())).andExpect(status().isOk());
        routing.notify("tasks","task_saved",Set.of(),"first","/tasks",null);
        assertThat(jdbc.queryForList("SELECT user_identity FROM notifications",String.class)).containsExactly(spaced);
        jdbc.update("DELETE FROM notifications");clearInvocations(stream);
        switch(mutation) {
            case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE username=?",spaced);
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE username=?",spaced);
            case "department"->jdbc.update("UPDATE users SET department='other' WHERE username=?",spaced);
            case "deleted"->jdbc.update("DELETE FROM users WHERE username=?",spaced);
            default->throw new IllegalArgumentException(mutation);
        }
        routing.notify("tasks","task_saved",Set.of(),"late","/tasks",null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications",Long.class)).isZero();verifyNoInteractions(stream);
    }

    @Test void excludedIdentityAndCaseDuplicatesDoNotCollapseTheTwoAccounts() {
        routing.notify("tasks","task_saved",Set.of(reader,reader.toUpperCase(Locale.ROOT),spaced,spaced.toUpperCase(Locale.ROOT)),"own","/tasks",spaced.toUpperCase(Locale.ROOT));
        assertThat(jdbc.queryForList("SELECT user_identity FROM notifications",String.class)).containsExactly(reader);
        verify(stream).publishNotificationsChanged(reader,"notification_created");verifyNoMoreInteractions(stream);
    }

    @Test void settingsWritesStillRequirePageAuthorityAndCsrf() throws Exception {
        var before=config.loadSettings();String json=payload("employees_only");
        mvc.perform(auth(post(SETTINGS).contentType(MediaType.APPLICATION_JSON).content(json),"route-user","PAGE_DIALOGS").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(auth(post(SETTINGS).contentType(MediaType.APPLICATION_JSON).content(json),"route-admin","PAGE_SETTINGS")).andExpect(status().isForbidden());
        assertThat(config.loadSettings()).isEqualTo(before);verifyNoInteractions(stream);
    }

    private void account(String username) {
        jdbc.update("INSERT INTO users(username,password,department) VALUES (?,'not-used',?)",username,department);names.add(username);
    }
    private String payload(String mode) throws Exception {
        return mapper.writeValueAsString(Map.of(NotificationRoutingService.SETTINGS_KEY,Map.of("tasks",Map.of("task_saved",
            Map.of("enabled",true,"audienceStrategy","route_only","department",department,"targetMode",mode,"deliveryMode","all",
                "employeeUsernames",List.of(spaced.toUpperCase(Locale.ROOT)),"excludeUsernames",List.of(reader.toUpperCase(Locale.ROOT)))))));
    }
    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request,String identity,String authority) {
        return request.with(user(identity).authorities(new SimpleGrantedAuthority(authority)));
    }
}
