package com.example.panel.controller;

import com.example.panel.security.SecurityConfig;
import com.example.panel.service.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({SettingsApiController.class,SettingsParametersController.class})
@Import(SecurityConfig.class)
class SettingsReadAccessWebMvcTest {
    @Autowired MockMvc mvc;
    @MockBean(name="usersJdbcTemplate") JdbcTemplate users;
    @MockBean SettingsPageDataService data;
    @MockBean SettingsClientStatusService statuses;
    @MockBean SettingsItConnectionCategoryService categories;
    @MockBean SettingsIntegrationNetworkProbeService network;
    @MockBean SettingsParameterService parameters;

    @Test void aPageViewerCannotReadAnyGroupedSettings() throws Exception {
        for(String section:List.of("admin","channels","locations","parameters"))
            mvc.perform(get("/api/settings/page-data/"+section).with(user("viewer").authorities(()->"PAGE_SETTINGS")))
                    .andExpect(status().isForbidden());
        verifyNoInteractions(data);
    }
    @Test void templateEditorsReceiveOnlyTheirPermittedPartOfTheChannelAggregate() throws Exception {
        when(data.loadSection("channels")).thenReturn(Map.of(
                "botSettings",Map.of("template","allowed"),"botPresetDefinitions",Map.of("locations",List.of("reference")),
                "integrationNetworkProfiles",List.of(Map.of("password","denied-secret")),"assignmentRoutingCatalog",Map.of("users",List.of("denied"))));
        mvc.perform(get("/api/settings/page-data/channels").with(user("templates").authorities(()->"PAGE_SETTINGS",()->"EDIT_SETTINGS_BOT_TEMPLATES")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.botSettings.template").value("allowed"))
                .andExpect(jsonPath("$.data.botPresetDefinitions.locations[0]").value("reference"))
                .andExpect(jsonPath("$.data.integrationNetworkProfiles").doesNotExist())
                .andExpect(jsonPath("$.data.assignmentRoutingCatalog").doesNotExist());
    }
    @Test void reportingCanUseMinimalChannelReferencesWithoutOpeningManagerBindings() throws Exception {
        when(data.loadSection("admin")).thenReturn(Map.of(
                "reportingConfig",Map.of("enabled",true),"notificationChannels",List.of(Map.of("id","1","channel_name","Support")),
                "managerLocationBindings",List.of(Map.of("manager","denied"))));
        mvc.perform(get("/api/settings/page-data/admin").with(user("reports").authorities(()->"PAGE_SETTINGS",()->"EDIT_SETTINGS_REPORTING")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reportingConfig.enabled").value(true))
                .andExpect(jsonPath("$.data.notificationChannels[0].channel_name").value("Support"))
                .andExpect(jsonPath("$.data.managerLocationBindings").doesNotExist());
    }
    @Test void dictionaryReadsOmitGroupsOwnedByOtherBlocks() throws Exception {
        when(parameters.listParameters(true)).thenReturn(Map.of("success",true,"data",Map.of(
                "role",List.of(Map.of("value","allowed")),"legal_entity",List.of(Map.of("value","denied")))));
        mvc.perform(get("/api/settings/parameters").with(user("dictionaries").authorities(()->"PAGE_SETTINGS",()->"EDIT_SETTINGS_PARAMETERS")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role[0].value").value("allowed"))
                .andExpect(jsonPath("$.data.legal_entity").doesNotExist());
        mvc.perform(get("/api/settings/parameters").with(user("viewer").authorities(()->"PAGE_SETTINGS")))
                .andExpect(status().isForbidden());
        verify(parameters,times(1)).listParameters(true);
    }
    @Test void anExplicitParentDenyWinsOverAChildGrant() throws Exception {
        mvc.perform(get("/api/settings/page-data/channels").with(user("denied").authorities(
                        ()->"PAGE_SETTINGS",()->"DENY_PAGE_SETTINGS",()->"EDIT_SETTINGS_BOT_TEMPLATES")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(data);
    }
}
