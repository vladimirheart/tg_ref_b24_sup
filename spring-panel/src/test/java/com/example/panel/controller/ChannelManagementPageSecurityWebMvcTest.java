package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.repository.*;
import com.example.panel.security.SecurityConfig;
import com.example.panel.service.*;
import com.example.panel.settings.SettingsPageController;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({ManagementController.class, SettingsPageController.class})
@Import({SecurityConfig.class, NavigationService.class, PermissionService.class, BotSettingsPayloadNormalizer.class, AutoCloseConfigNormalizer.class})
class ChannelManagementPageSecurityWebMvcTest {
    @Autowired private MockMvc mvc;
    @MockBean private TaskRepository tasks;
    @MockBean private ChannelRepository channels;
    @MockBean private PanelUserRepository users;
    @MockBean private ProjectRepository projects;
    @MockBean private AppSettingRepository appSettings;
    @MockBean private SettingsParameterRepository parameters;
    @MockBean private SharedConfigService config;
    @MockBean private SettingsCatalogService catalog;
    @MockBean private IikoDepartmentLocationCatalogService locations;
    @MockBean private UnblockRequestService unblock;
    @MockBean private PanelUserPhotoService photos;
    @MockBean private MultibusinessFeatureFlags flags;

    @BeforeEach void settings() {
        when(config.loadSettings()).thenReturn(Map.of());
        var snapshot = new IikoDepartmentLocationCatalogService.LocationCatalogSnapshot(Map.of(),Map.of(),"fixture",false,List.of());
        when(locations.loadCatalog()).thenReturn(snapshot);
        when(locations.buildEffectiveLocationsPayload(snapshot)).thenReturn(Map.of("tree",Map.of(),"statuses",Map.of()));
    }

    static Stream<Arguments> nonAdmins() {
        return Stream.of(Arguments.of("operator",new String[]{"PAGE_SETTINGS","PAGE_CHANNELS"}),
            Arguments.of("operator",new String[]{"ROLE_PORTAL_ADMIN","PAGE_SETTINGS","PAGE_CHANNELS"}),
            Arguments.of("operator",new String[]{"BUSINESS_ADMIN","ALL_BUSINESSES","PAGE_SETTINGS","PAGE_CHANNELS"}),
            Arguments.of("admin",new String[]{"PAGE_SETTINGS","PAGE_CHANNELS"}),
            Arguments.of(" admin ",new String[]{"PAGE_SETTINGS","PAGE_CHANNELS"}));
    }
    private org.springframework.security.core.GrantedAuthority[] authorities(String... values) {
        return java.util.Arrays.stream(values).map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
            .toArray(org.springframework.security.core.GrantedAuthority[]::new);
    }

    @ParameterizedTest @MethodSource("nonAdmins")
    void channelPageRejectsBeforeCatalogLookup(String username,String[] rights) throws Exception {
        mvc.perform(get("/channels").with(user(username).authorities(authorities(rights))))
            .andExpect(status().isForbidden());
        verifyNoInteractions(channels,config,locations);
    }

    @ParameterizedTest @MethodSource("nonAdmins")
    void settingsStillOpensButChannelTileAndEditorsAreNotRendered(String username,String[] rights) throws Exception {
        mvc.perform(get("/settings?open=channels").with(user(username).authorities(authorities(rights))))
            .andExpect(status().isOk()).andExpect(view().name("settings/index"))
            .andExpect(model().attribute("canManageChannels",false))
            .andExpect(content().string(not(containsString("data-settings-overview-target=\"channelsModal\""))))
            .andExpect(content().string(not(containsString("id=\"channelsModal\""))))
            .andExpect(content().string(not(containsString("id=\"addChannelModal\""))))
            .andExpect(content().string(not(containsString("id=\"channelEditorModal\""))))
            .andExpect(content().string(containsString("id=\"reportingModal\"")));
        verifyNoInteractions(channels);
    }

    @Test void channelOnlyAdminGetsLegacyPageAndUsableNavigationInsteadOfSettingsRedirect() throws Exception {
        mvc.perform(get("/channels").with(user("system").authorities(authorities("ROLE_ADMIN","PAGE_CHANNELS"))))
            .andExpect(status().isOk()).andExpect(view().name("channels/index"))
            .andExpect(model().attribute("canManageChannels",true))
            .andExpect(content().string(containsString("href=\"/channels\"")));
        verify(channels).findAll();verifyNoInteractions(config,locations);
    }

    @Test void settingsAdminCanUseChannelAliasAndTheFullChannelWorkspace() throws Exception {
        var actor=user("system").authorities(authorities("ROLE_ADMIN","PAGE_SETTINGS"));
        mvc.perform(get("/channels").with(actor)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/settings?open=channels"));
        mvc.perform(get("/settings?open=channels").with(actor)).andExpect(status().isOk())
            .andExpect(model().attribute("canManageChannels",true))
            .andExpect(content().string(containsString("data-settings-overview-target=\"channelsModal\"")))
            .andExpect(content().string(containsString("id=\"channelsModal\"")))
            .andExpect(content().string(containsString("id=\"channelEditorModal\"")))
            .andExpect(content().string(containsString("id=\"addChannelModal\"")));
        verifyNoInteractions(channels);
    }

    @Test void settingsModelExcludesAllPrivatePreferenceRecords() throws Exception {
        var owned = new com.example.panel.entity.SettingsParameter(); owned.setParamType("ui_preferences.account.v2");
        var legacy = new com.example.panel.entity.SettingsParameter(); legacy.setParamType("ui_preferences.v1");
        var ordinary = new com.example.panel.entity.SettingsParameter(); ordinary.setParamType("role");
        when(parameters.findAll()).thenReturn(List.of(owned, legacy, ordinary));
        mvc.perform(get("/settings").with(user("system").authorities(authorities("ROLE_ADMIN","PAGE_SETTINGS"))))
            .andExpect(status().isOk()).andExpect(model().attribute("systemParameters", List.of(ordinary)));
    }

    @Test void adminRoleAloneCannotOpenEitherManagementPage() throws Exception {
        var actor=user("system").roles("ADMIN");
        mvc.perform(get("/channels").with(actor)).andExpect(status().isForbidden());
        mvc.perform(get("/settings").with(actor)).andExpect(status().isForbidden());
        verifyNoInteractions(channels,config,locations);
    }
}
