package com.example.panel.controller;

import com.example.panel.security.PanelAccessPolicy;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.security.SecurityConfig;
import com.example.panel.service.SettingsItEquipmentConfigurationService;
import com.example.panel.service.SettingsParameterService;
import com.example.panel.service.SettingsUpdateService;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({SettingsBridgeController.class, SettingsParametersController.class,
        SettingsItEquipmentConfigurationController.class, UserPermissionsController.class, AuthManagementApiController.class,
        DashboardApiController.class, ChannelApiController.class,
        SettingsPermissionBoundaryWebMvcTest.Operations.class})
@Import({SecurityConfig.class, com.example.panel.service.PermissionService.class, SettingsPermissionBoundaryWebMvcTest.Operations.class})
class SettingsPermissionBoundaryWebMvcTest {
    @Autowired MockMvc mvc;
    @MockBean SettingsUpdateService settings;
    @MockBean SettingsParameterService parameters;
    @MockBean SettingsItEquipmentConfigurationService equipment;
    @MockBean(name = "usersJdbcTemplate") JdbcTemplate users;
    @MockBean com.example.panel.service.SharedConfigService sharedConfig;
    @MockBean com.example.panel.service.PanelUserPhotoService photos;
    @MockBean com.example.panel.service.DialogLookupReadService dialogs;
    @MockBean com.example.panel.service.DashboardAnalyticsService analytics;
    @MockBean com.example.panel.service.ManagerReportService reports;
    @MockBean com.example.panel.service.ChannelTransportService channelTransport;

    static Stream<Arguments> operations() {
        return Stream.of(
                Arguments.of("/api/settings/backup", "backup"),
                Arguments.of("/api/settings/backup/manual", "backup"),
                Arguments.of("/api/settings/backup/probe", "backup"),
                Arguments.of("/api/settings/netbox-sync/run", "netbox"),
                Arguments.of("/api/settings/locations-sync/run", "locations"),
                Arguments.of("/api/settings/notification-routing", "notification_routing"),
                Arguments.of("/api/settings/client-statuses", "appearance"),
                Arguments.of("/api/settings/it-connection-categories", "it_connections"),
                Arguments.of("/api/settings/integration-network/profiles/probe", "network_profiles"),
                Arguments.of("/api/auth/org-structure", "organization"),
                Arguments.of("/api/workforce/positions", "workforce"),
                Arguments.of("/api/channels/123", "channels"),
                Arguments.of("/api/123/public-id/regenerate", "channels"));
    }

    @ParameterizedTest @MethodSource("operations")
    void openingSettingsCannotAuthorizeWritesOrOperationalActions(String path, String block) throws Exception {
        mvc.perform(post(path).with(user("viewer").authorities(() -> "PAGE_SETTINGS")).with(csrf())
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest @MethodSource("operations")
    void eachBlockCanBeGrantedIndependently(String path, String block) throws Exception {
        mvc.perform(post(path).with(user("editor").authorities(() -> "PAGE_SETTINGS",
                        () -> PanelAccessPolicy.editAuthority("settings." + block))).with(csrf())
                .contentType("application/json").content("{}"))
                .andExpect(status().isOk());
    }

    @Test void mixedSettingsRequestDoesNotPartiallyPersistAllowedBlocks() throws Exception {
        mvc.perform(post("/settings").with(user("editor").authorities(() -> "PAGE_SETTINGS", () -> "EDIT_SETTINGS_DIALOGS"))
                .with(csrf()).contentType("application/json")
                .content("{\"dialog_category_templates\":[],\"reporting_config\":{}}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(settings);
    }

    @Test void categoryEditingWorksWithItsExplicitGrant() throws Exception {
        when(settings.updateSettings(anyMap(), any())).thenReturn(Map.of("success", true));
        mvc.perform(patch("/settings/").with(user("editor").authorities(() -> "PAGE_SETTINGS", () -> "EDIT_SETTINGS_DIALOGS"))
                .with(csrf()).contentType("application/json").content("{\"dialog_category_templates\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(settings).updateSettings(anyMap(), any());
    }

    @Test void equipmentDefinitionsRequireBothAReadablePageAndTheEquipmentEditGrant() throws Exception {
        mvc.perform(post("/api/settings/it-equipment/attributes").with(user("ordinary").roles("USER"))
                .with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/settings/it-equipment/attributes").with(user("viewer").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(equipment);
        when(equipment.createAttribute(anyMap())).thenReturn(Map.of("success", true));
        mvc.perform(post("/api/settings/it-equipment/attributes").with(user("editor").authorities(
                        () -> "PAGE_SETTINGS", () -> "EDIT_SETTINGS_EQUIPMENT"))
                .with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isOk());
    }

    @Test void dictionaryEditCannotModifyLegalEntitiesThroughTheSharedEndpoint() throws Exception {
        mvc.perform(post("/api/settings/parameters").with(user("editor").authorities(
                        () -> "PAGE_SETTINGS", () -> "EDIT_SETTINGS_PARAMETERS"))
                .with(csrf()).contentType("application/json").content("{\"param_type\":\"legal_entity\",\"value\":\"Test\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(parameters);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"PATCH", "DELETE"})
    void changingOrDeletingAParameterChecksItsStoredType(String method) throws Exception {
        when(parameters.permissionBlock(15L)).thenReturn("legal_entities");
        mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), "/api/settings/parameters/15")
                .with(user("editor").authorities(() -> "PAGE_SETTINGS", () -> "EDIT_SETTINGS_PARAMETERS"))
                .with(csrf()).contentType("application/json").content("{\"param_type\":\"city\",\"value\":\"Test\"}"))
                .andExpect(status().isForbidden());
        verify(parameters, never()).updateParameter(anyLong(), anyMap());
        verify(parameters, never()).deleteParameter(anyLong());
    }

    @Test void evenAnAuthorizedEditorStillNeedsCsrf() throws Exception {
        mvc.perform(post("/api/settings/backup").with(user("editor").authorities(
                        () -> "PAGE_SETTINGS", () -> "EDIT_SETTINGS_BACKUP")))
                .andExpect(status().isForbidden());
    }

    @Test void userOverrideCannotGrantAPermissionTheEditorDoesNotHave() throws Exception {
        when(users.queryForList(startsWith("SELECT u.id, u.username"), eq(7L)))
                .thenReturn(List.of(Map.of("id", 7L, "username", "target", "role_name", "reader", "permissions", "{}")));
        var actor = new PanelUserPrincipal(1L, "delegated", "credential", true,
                AuthorityUtils.createAuthorityList("PAGE_SETTINGS", "EDIT_SETTINGS_ACCESS"));
        mvc.perform(put("/api/users/7/permissions").with(user(actor)).with(csrf())
                .contentType("application/json").content("{\"EDIT_SETTINGS_BACKUP\":\"allow\"}"))
                .andExpect(status().isForbidden());
        verify(users, never()).update(startsWith("INSERT INTO user_authorities"), any(Object[].class));
        verify(users, never()).update(startsWith("DELETE FROM user_authorities"), any(Object[].class));
    }

    @Test void aUserCannotEditTheirOwnOverrides() throws Exception {
        var actor = new PanelUserPrincipal(7L, "delegated", "credential", true,
                AuthorityUtils.createAuthorityList("PAGE_SETTINGS", "EDIT_SETTINGS_ACCESS", "EDIT_SETTINGS_BACKUP"));
        mvc.perform(put("/api/users/7/permissions").with(user(actor)).with(csrf())
                .contentType("application/json").content("{\"EDIT_SETTINGS_BACKUP\":\"allow\"}"))
                .andExpect(status().isForbidden());
        verify(users, never()).update(startsWith("INSERT INTO user_authorities"), any(Object[].class));
        verify(users, never()).update(startsWith("DELETE FROM user_authorities"), any(Object[].class));
        verify(users, never()).queryForList(anyString(), any(Object[].class));
    }

    @Test void editingOnePermissionGroupCannotReplaceOtherGroupsOfARole() throws Exception {
        roleFixture("support");
        mvc.perform(patch("/api/roles/3").with(delegate("EDIT_ROLE_FIELDS_VIEW"))
                .with(csrf()).contentType("application/json")
                .content("{\"permissions\":{\"pages\":[\"settings\",\"tasks\"],\"fields\":{\"edit\":[],\"view\":[\"user.password\"]}}}"))
                .andExpect(status().isForbidden());
        verify(users, never()).update(startsWith("UPDATE roles"), any(Object[].class));
    }

    @Test void anUnchangedPermissionGroupDoesNotRequireItsEditGrant() throws Exception {
        roleFixture("support");
        mvc.perform(patch("/api/roles/3").with(delegate("EDIT_ROLE_FIELDS_VIEW"))
                .with(csrf()).contentType("application/json")
                .content("{\"permissions\":{\"pages\":[\"settings\"],\"fields\":{\"edit\":[],\"view\":[\"user.password\"]}}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(users).update(eq("UPDATE roles SET permissions = ? WHERE id = ?"), anyString(), eq(3L));
    }

    @Test void unchangedHigherPageGrantsDoNotPreventEditingAnotherPermissionGroup() throws Exception {
        roleFixture("support");
        when(users.queryForList("SELECT name, permissions FROM roles WHERE id = ?", 3L))
                .thenReturn(List.of(Map.of("name", "support", "permissions", "{\"pages\":[\"settings\",\"tasks\"]}")));
        mvc.perform(patch("/api/roles/3").with(delegate("EDIT_ROLE_FIELDS_VIEW"))
                .with(csrf()).contentType("application/json")
                .content("{\"permissions\":{\"pages\":[\"tasks\",\"settings\"],\"fields\":{\"edit\":[],\"view\":[\"user.password\"]}}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"admin", "ADMIN", " adm\u0131n ", "adm\u0130n"})
    void roleRenameCannotCreateASystemAdministrator(String name) throws Exception {
        roleFixture("support");
        mvc.perform(patch("/api/roles/3").with(user("delegate").authorities(
                        () -> "PAGE_SETTINGS", () -> "EDIT_ROLE_NAME"))
                .with(csrf()).contentType("application/json").content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isForbidden());
        verify(users, never()).update(startsWith("UPDATE roles"), any(Object[].class));
    }

    @Test void allowedIndividualGrantIsStoredWithoutReplacingOtherUserAuthorities() throws Exception {
        when(users.queryForList(startsWith("SELECT u.id, u.username"), eq(7L)))
                .thenReturn(List.of(Map.of("id", 7L, "username", "target", "role_name", "reader", "permissions", "{}")));
        var actor = new PanelUserPrincipal(1L, "delegate", "credential", true,
                AuthorityUtils.createAuthorityList("PAGE_SETTINGS", "EDIT_SETTINGS_ACCESS", "EDIT_SETTINGS_BACKUP"));
        mvc.perform(put("/api/users/7/permissions").with(user(actor)).with(csrf())
                .contentType("application/json").content("{\"EDIT_SETTINGS_BACKUP\":\"allow\"}"))
                .andExpect(status().isOk());
        verify(users).update(eq("DELETE FROM user_authorities WHERE user_id = ? AND authority IN (?, ?)"),
                eq(7L), eq("EDIT_SETTINGS_BACKUP"), eq("DENY_EDIT_SETTINGS_BACKUP"));
        verify(users).update(eq("INSERT INTO user_authorities(user_id, authority) VALUES (?, ?)"), eq(7L), eq("EDIT_SETTINGS_BACKUP"));
    }

    @Test void anInvalidIndividualOverrideRejectsTheWholeBatchBeforePersistence() throws Exception {
        when(users.queryForList(startsWith("SELECT u.id, u.username"), eq(7L)))
                .thenReturn(List.of(Map.of("id", 7L, "username", "target", "role_name", "reader", "permissions", "{}")));
        var actor = new PanelUserPrincipal(1L, "delegate", "credential", true,
                AuthorityUtils.createAuthorityList("PAGE_SETTINGS", "EDIT_SETTINGS_ACCESS", "EDIT_SETTINGS_BACKUP"));
        mvc.perform(put("/api/users/7/permissions").with(user(actor)).with(csrf())
                .contentType("application/json").content("{\"EDIT_SETTINGS_BACKUP\":\"allow\",\"ROLE_ADMIN\":\"allow\"}"))
                .andExpect(status().isBadRequest());
        verify(users, never()).update(startsWith("INSERT INTO user_authorities"), any(Object[].class));
        verify(users, never()).update(startsWith("DELETE FROM user_authorities"), any(Object[].class));
    }

    @Test void reportsUseTheirOwnPagePermissionInsteadOfTheDialogsPermission() throws Exception {
        mvc.perform(post("/api/dashboard/data").with(user("operator").authorities(() -> "PAGE_DIALOGS"))
                .with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dialogs, analytics, reports);
        when(dialogs.loadDialogs("reporter")).thenReturn(List.of());
        when(analytics.buildDashboardPayload(anyList(), any())).thenReturn(Map.of());
        mvc.perform(post("/api/dashboard/data").with(user("reporter").authorities(() -> "PAGE_DASHBOARD"))
                .with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isOk());
    }

    @Test void aSettingsViewerCannotUploadAnUnboundUserAvatar() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile("photo", "avatar.png", "image/png", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/users/photo-upload").file(file).with(user("viewer").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(photos);
    }

    @Test void creatingARoleDoesNotBypassItsSeparatePermissionGroupChecks() throws Exception {
        roleFixture("support", "role.create");
        mvc.perform(post("/api/roles").with(delegate("EDIT_ROLE_CREATE")).with(csrf()).contentType("application/json")
                .content("{\"name\":\"new-role\",\"permissions\":{\"pages\":[\"settings\"]}}"))
                .andExpect(status().isForbidden());
        verify(users, never()).update(startsWith("INSERT INTO roles"), any(Object[].class));
    }

    @Test void aDelegatedRoleEditorCannotDeleteASystemRole() throws Exception {
        roleFixture("admin", "role.delete");
        mvc.perform(delete("/api/roles/3").with(delegate("EDIT_ROLE_DELETE")).with(csrf()))
                .andExpect(status().isForbidden());
        verify(users, never()).update(startsWith("DELETE FROM roles"), any(Object[].class));
    }

    @Test void channelTokensAreHiddenFromViewersAndAvailableToTheChannelEditor() throws Exception {
        var body = Map.<String, Object>of("success", true, "channels", List.of(Map.of(
                "channel_name", "Support", "token", "bot-secret", "platform_config", Map.of("secret", "callback-secret"))));
        when(channelTransport.getChannels()).thenReturn(org.springframework.http.ResponseEntity.ok(body));
        mvc.perform(get("/api/channels").with(user("viewer").authorities(() -> "PAGE_SETTINGS")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.channels[0].token").value(""))
                .andExpect(jsonPath("$.channels[0].token_saved").value(true))
                .andExpect(jsonPath("$.channels[0].platform_config.secret").value(""));
        mvc.perform(get("/panel/api/channels").contextPath("/panel").servletPath("/api/channels")
                        .with(user("viewer").authorities(() -> "PAGE_SETTINGS")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.channels[0].token").value(""));
        mvc.perform(get("/api/channels").with(user("editor").authorities(() -> "PAGE_SETTINGS", () -> "EDIT_SETTINGS_CHANNELS")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.channels[0].token").value("bot-secret"));
    }

    private void roleFixture(String name, String... actorFields) {
        when(users.queryForList("SELECT name FROM roles WHERE id = ?", 3L)).thenReturn(List.of(Map.of("name", name)));
        when(users.queryForList("SELECT name, permissions FROM roles WHERE id = ?", 3L))
                .thenReturn(List.of(Map.of("name", name, "permissions", "{\"pages\":[\"settings\"]}")));
        var fields = actorFields.length == 0 ? List.of("role.fields.view") : List.of(actorFields);
        String permissions = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("fields", Map.of("edit", fields))).toString();
        when(users.queryForList(startsWith("SELECT u.*, r.permissions AS permissions"), eq(1L)))
                .thenReturn(List.of(Map.of("id", 1L, "username", "delegate", "enabled", true,
                        "permissions", permissions)));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor delegate(String right) {
        return user(new PanelUserPrincipal(1L, "delegate", "credential", true,
                AuthorityUtils.createAuthorityList("PAGE_SETTINGS", right)));
    }

    @RestController
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    static class Operations {
        @RequestMapping({"/api/settings/backup", "/api/settings/backup/manual", "/api/settings/backup/probe",
                "/api/settings/netbox-sync/run", "/api/settings/locations-sync/run", "/api/settings/notification-routing",
                "/api/settings/client-statuses", "/api/settings/it-connection-categories", "/api/settings/integration-network/profiles/probe",
                "/api/auth/org-structure", "/api/workforce/positions", "/api/channels/123", "/api/123/public-id/regenerate"})
        public Map<String, Object> action() { return Map.of("success", true); }
    }
}
