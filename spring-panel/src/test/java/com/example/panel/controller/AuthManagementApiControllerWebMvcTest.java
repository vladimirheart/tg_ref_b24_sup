package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.PermissionService;
import com.example.panel.service.SharedConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;

@WebMvcTest(AuthManagementApiController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "app.storage.avatars=${java.io.tmpdir}/iguana-auth-test-avatars")
class AuthManagementApiControllerWebMvcTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthManagementApiController controller;

    @MockBean(name = "usersJdbcTemplate")
    private JdbcTemplate usersJdbcTemplate;

    @MockBean
    private SharedConfigService sharedConfigService;

    @MockBean
    private PermissionService permissionService;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @Test
    void getAuthStateReturnsOrgStructureAndBasePayloadContract() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        when(permissionService.hasAuthority(any(), anyString())).thenReturn(true);
        when(sharedConfigService.loadOrgStructure()).thenReturn(OBJECT_MAPPER.readTree("""
            {
              "departments": [
                {"id": "support", "title": "Support"}
              ]
            }
            """));
        when(usersJdbcTemplate.queryForList(startsWith("SELECT u.*"))).thenReturn(List.of(Map.ofEntries(
            Map.entry("id", 7L),
            Map.entry("username", "admin"),
            Map.entry("full_name", "Admin User"),
            Map.entry("role_name", "portal-admin"),
            Map.entry("role_id", 1L),
            Map.entry("photo", ""),
            Map.entry("registration_date", "2026-01-01T00:00:00Z"),
            Map.entry("birth_date", ""),
            Map.entry("email", "admin@example.com"),
            Map.entry("department", "Support"),
            Map.entry("phones", "[]"),
            Map.entry("is_blocked", 0)
        )));
        when(usersJdbcTemplate.queryForList(startsWith("SELECT id, name, description, permissions FROM roles"))).thenReturn(List.of(Map.of(
            "id", 1L,
            "name", "portal-admin",
            "description", "Admin",
            "permissions", "{\"pages\":[\"settings\"]}"
        )));
        when(usersJdbcTemplate.queryForList(anyString(), eq("admin")))
            .thenReturn(List.of(Map.of("id", 7L)));

        mockMvc.perform(get("/api/auth/state")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.users[0].username").value("admin"))
            .andExpect(jsonPath("$.roles[0].name").value("portal-admin"))
            .andExpect(jsonPath("$.current_user_id").isEmpty())
            .andExpect(jsonPath("$.org_structure.departments[0].id").value("support"));
    }

    @Test
    void getAuthStateReturnsDecodedPhonesAndAdminCapabilityFlags() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        when(permissionService.hasAuthority(any(), anyString())).thenReturn(true);
        when(sharedConfigService.loadOrgStructure()).thenReturn(OBJECT_MAPPER.valueToTree(Map.of("departments", List.of())));
        when(usersJdbcTemplate.queryForList(startsWith("SELECT u.*"))).thenReturn(List.of(Map.ofEntries(
            Map.entry("id", 11L),
            Map.entry("username", "self"),
            Map.entry("full_name", "Self User"),
            Map.entry("role_name", "admin"),
            Map.entry("role_id", 3L),
            Map.entry("photo", ""),
            Map.entry("registration_date", "2026-01-01T00:00:00Z"),
            Map.entry("birth_date", ""),
            Map.entry("email", "self@example.com"),
            Map.entry("department", "Support"),
            Map.entry("phones", "[{\"label\":\"work\",\"value\":\"+7-900\"}]"),
            Map.entry("is_blocked", 0)
        )));
        when(usersJdbcTemplate.queryForList(startsWith("SELECT id, name, description, permissions FROM roles"))).thenReturn(List.of());
        when(usersJdbcTemplate.queryForList(startsWith("SELECT id FROM users"), eq("self")))
            .thenReturn(List.of(Map.of("id", 11L)));

        mockMvc.perform(get("/api/auth/state")
                .with(user("self").authorities(() -> "PAGE_SETTINGS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.current_user_id").isEmpty())
            .andExpect(jsonPath("$.users[0].can_edit_password").value(true))
            .andExpect(jsonPath("$.users[0].role_is_admin").value(true))
            .andExpect(jsonPath("$.users[0].phones[0].label").value("work"));
    }

    @Test
    void getAuthStateReturnsParsedStoredRolePermissionsPayload() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        when(permissionService.hasAuthority(any(), anyString())).thenReturn(true);
        when(sharedConfigService.loadOrgStructure()).thenReturn(OBJECT_MAPPER.valueToTree(Map.of("departments", List.of())));
        when(usersJdbcTemplate.queryForList(startsWith("SELECT u.*"))).thenReturn(List.of());
        when(usersJdbcTemplate.queryForList(startsWith("SELECT id, name, description, permissions FROM roles"))).thenReturn(List.of(
            Map.of(
                "id", 21L,
                "name", "reviewer",
                "description", "Review role",
                "permissions",
                """
                {
                  "pages": ["dialogs", "settings"],
                  "fields": {
                    "edit": ["user.create", "role.name"],
                    "view": ["user.password"]
                  }
                }
                """
            )
        ));
        when(usersJdbcTemplate.queryForList(startsWith("SELECT id FROM users"), eq("operator")))
            .thenReturn(List.of(Map.of("id", 21L)));

        mockMvc.perform(get("/api/auth/state")
                .with(user("operator").authorities(() -> "PAGE_SETTINGS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles[0].name").value("reviewer"))
            .andExpect(jsonPath("$.roles[0].permissions.pages[0]").value("dialogs"))
            .andExpect(jsonPath("$.roles[0].permissions.fields.edit[0]").value("user.create"))
            .andExpect(jsonPath("$.roles[0].permissions.fields.view[0]").value("user.password"));
    }

    @Test
    void getAuthStateFallsBackToEmptyCapabilitiesWhenStoredRolePermissionsInvalid() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(false);
        when(permissionService.hasAuthority(any(), eq("ROLE_PORTAL_ADMIN"))).thenReturn(false);
        when(permissionService.hasAuthority(any(), eq("PAGE_SETTINGS"))).thenReturn(true);
        when(sharedConfigService.loadOrgStructure()).thenReturn(OBJECT_MAPPER.valueToTree(Map.of("departments", List.of())));
        when(usersJdbcTemplate.queryForList(startsWith("SELECT u.*"))).thenReturn(List.of());
        when(usersJdbcTemplate.queryForList(startsWith("SELECT id, name, description, permissions FROM roles"))).thenReturn(List.of());
        when(usersJdbcTemplate.queryForList(startsWith("SELECT id FROM users"), eq("broken")))
            .thenReturn(List.of(Map.of("id", 22L)));
        when(usersJdbcTemplate.queryForList(startsWith("SELECT r.permissions AS permissions"), eq("broken")))
            .thenReturn(List.of(Map.of("permissions", "{not-json")));

        mockMvc.perform(get("/api/auth/state")
                .with(user("broken").authorities(() -> "PAGE_SETTINGS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.capabilities.fields.edit['user.create']").value(false))
            .andExpect(jsonPath("$.capabilities.fields.edit['role.name']").value(false))
            .andExpect(jsonPath("$.capabilities.fields.view['user.password']").value(false));
    }

    @Test
    void listUsersFallsBackToEmptyPhonesWhenStoredJsonInvalid() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        when(usersJdbcTemplate.queryForList(startsWith("SELECT u.*"))).thenReturn(List.of(Map.ofEntries(
            Map.entry("id", 31L),
            Map.entry("username", "broken-phones"),
            Map.entry("full_name", "Broken Phones"),
            Map.entry("role_name", "operator"),
            Map.entry("role_id", 3L),
            Map.entry("photo", ""),
            Map.entry("registration_date", "2026-01-01T00:00:00Z"),
            Map.entry("birth_date", ""),
            Map.entry("email", "broken@example.com"),
            Map.entry("department", "Support"),
            Map.entry("phones", "{oops"),
            Map.entry("is_blocked", 0)
        )));
        when(usersJdbcTemplate.queryForList(startsWith("SELECT id FROM users"), eq("admin")))
            .thenReturn(List.of(Map.of("id", 7L)));

        mockMvc.perform(get("/api/users")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].username").value("broken-phones"))
            .andExpect(jsonPath("$[0].phones").isArray())
            .andExpect(jsonPath("$[0].phones").isEmpty());
    }

    @Test
    void listUsersUsesSimpleQueryWhenRoleIdColumnIsAbsentAndParsesBlockedFlag() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(false);
        when(permissionService.hasAuthority(any(), eq("ROLE_PORTAL_ADMIN"))).thenReturn(false);
        when(permissionService.hasAuthority(any(), eq("PAGE_SETTINGS"))).thenReturn(true);
        when(usersJdbcTemplate.queryForList(eq("SELECT u.* FROM users u ORDER BY lower(u.username)"))).thenReturn(List.of(Map.ofEntries(
            Map.entry("id", 41L),
            Map.entry("username", "solo"),
            Map.entry("full_name", "Solo User"),
            Map.entry("role", "operator"),
            Map.entry("photo", ""),
            Map.entry("registration_date", "2026-01-01T00:00:00Z"),
            Map.entry("birth_date", ""),
            Map.entry("email", "solo@example.com"),
            Map.entry("department", "Support"),
            Map.entry("phones", "[]"),
            Map.entry("is_blocked", "1")
        )));
        when(usersJdbcTemplate.queryForList(startsWith("SELECT id FROM users"), eq("solo")))
            .thenReturn(List.of(Map.of("id", 41L)));
        when(usersJdbcTemplate.queryForList(startsWith("SELECT r.permissions AS permissions"), eq("solo")))
            .thenReturn(List.of());
        ReflectionTestUtils.setField(controller, "userColumns", Set.of("username", "phones", "is_blocked"));

        mockMvc.perform(get("/api/users")
                .with(user("solo").authorities(() -> "PAGE_SETTINGS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].username").value("solo"))
            .andExpect(jsonPath("$[0].is_blocked").value(true))
            .andExpect(jsonPath("$[0].role").value("operator"));
    }

    @Test
    void listRolesFallsBackToEmptyPermissionsWhenStoredJsonInvalid() throws Exception {
        when(usersJdbcTemplate.queryForList(startsWith("SELECT id, name, description, permissions FROM roles"))).thenReturn(List.of(Map.of(
            "id", 51L,
            "name", "broken-role",
            "description", "Broken permissions",
            "permissions", "{oops"
        )));

        mockMvc.perform(get("/api/roles")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("broken-role"))
            .andExpect(jsonPath("$[0].permissions.pages").isArray())
            .andExpect(jsonPath("$[0].permissions.pages").isEmpty())
            .andExpect(jsonPath("$[0].permissions.fields.edit").isArray())
            .andExpect(jsonPath("$[0].permissions.fields.view").isArray());
    }

    @Test
    void updateOrgStructurePersistsPayloadThroughSharedConfigBoundary() throws Exception {
        mockMvc.perform(post("/api/auth/org-structure")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "org_structure": {
                        "departments": [
                          {"id": "support", "title": "Support"}
                        ]
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.org_structure.departments[0].id").value("support"));

        verify(sharedConfigService).saveOrgStructure(eq(Map.of(
            "departments", List.of(Map.of("id", "support", "title", "Support"))
        )));
    }

    @Test
    void updateOrgStructureAcceptsRawPayloadWithoutWrapper() throws Exception {
        mockMvc.perform(post("/api/auth/org-structure")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "departments": [
                        {"id": "ops", "title": "Operations"}
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.org_structure.departments[0].id").value("ops"));

        verify(sharedConfigService).saveOrgStructure(eq(Map.of(
            "departments", List.of(Map.of("id", "ops", "title", "Operations"))
        )));
    }

    @Test
    void updateOrgStructureSupportsTrailingSlashRoute() throws Exception {
        mockMvc.perform(post("/api/auth/org-structure/")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "departments": []
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(sharedConfigService).saveOrgStructure(eq(Map.of("departments", List.of())));
    }

    @Test
    void createUserRejectsDuplicateUsername() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        when(usersJdbcTemplate.queryForObject(startsWith("SELECT COUNT(*) FROM users"), eq(Integer.class), eq("existing")))
            .thenReturn(1);

        mockMvc.perform(post("/api/users")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "username": "existing",
                      "password": "secret"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Пользователь уже существует"));
    }

    @Test
    void createUserRejectsMissingUsernameOrPassword() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);

        mockMvc.perform(post("/api/users")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "username": "fresh",
                      "password": ""
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Имя пользователя и пароль не могут быть пустыми"));
    }

    @Test
    void createUserRejectsWhenEditPermissionIsMissing() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(false);
        when(permissionService.hasAuthority(any(), eq("ROLE_PORTAL_ADMIN"))).thenReturn(false);
        when(permissionService.hasAuthority(any(), eq("PAGE_SETTINGS"))).thenReturn(false);

        mockMvc.perform(post("/api/users")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "username": "fresh",
                      "password": "secret"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Недостаточно прав для создания пользователя"));
    }

    @Test
    void createUserPersistsHashedPassword() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        when(usersJdbcTemplate.queryForObject(startsWith("SELECT COUNT(*) FROM users"), eq(Integer.class), eq("fresh")))
            .thenReturn(0);
        when(passwordEncoder.encode("secret")).thenReturn("hashed-secret");

        mockMvc.perform(post("/api/users")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "username": "fresh",
                      "password": "secret"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(usersJdbcTemplate).update(startsWith("INSERT INTO users"), eq("fresh"), eq("hashed-secret"));
    }

    @Test
    void createUserSupportsTrailingSlashRoute() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        when(usersJdbcTemplate.queryForObject(startsWith("SELECT COUNT(*) FROM users"), eq(Integer.class), eq("fresh-slash")))
            .thenReturn(0);
        when(passwordEncoder.encode("secret")).thenReturn("hashed-secret");

        mockMvc.perform(post("/api/users/")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "username": "fresh-slash",
                      "password": "secret"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createUserPersistsOptionalFieldsPhonesAndRoleIdWhenColumnsExist() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        ReflectionTestUtils.setField(controller, "userColumns", Set.of(
            "username", "password", "full_name", "email", "department", "phones", "role_id"
        ));
        when(usersJdbcTemplate.queryForObject(startsWith("SELECT COUNT(*) FROM users"), eq(Integer.class), eq("fresh")))
            .thenReturn(0);
        when(passwordEncoder.encode("secret")).thenReturn("hashed-secret");

        mockMvc.perform(post("/api/users")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "username": "fresh",
                      "password": "secret",
                      "full_name": "Fresh User",
                      "email": "fresh@example.com",
                      "department": "Support",
                      "role_id": 4,
                      "phones": [
                        {"label": "work", "value": "+7-900-000-00-00"}
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(usersJdbcTemplate).update(
            eq("INSERT INTO users (username, password, full_name, email, department, phones, role_id) VALUES (?, ?, ?, ?, ?, ?, ?)"),
            eq("fresh"),
            eq("hashed-secret"),
            eq("Fresh User"),
            eq("fresh@example.com"),
            eq("Support"),
            eq("[{\"label\":\"work\",\"value\":\"+7-900-000-00-00\"}]"),
            eq(4)
        );
    }

    @Test
    void updateUserReturnsNoDataWhenPayloadEmpty() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);

        mockMvc.perform(patch("/api/users/7")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Нет данных для обновления"));
    }

    @Test
    void updateUserPersistsBlockAndPasswordChanges() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        when(passwordEncoder.encode("next-secret")).thenReturn("hashed-next-secret");
        ReflectionTestUtils.setField(controller, "userColumns", Set.of("password", "is_blocked"));

        mockMvc.perform(patch("/api/users/7")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "password": "next-secret",
                      "is_blocked": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(usersJdbcTemplate).update(
            eq("UPDATE users SET is_blocked = ?, password = ? WHERE id = ?"),
            eq(1),
            eq("hashed-next-secret"),
            eq(7L)
        );
    }

    @Test
    void updateUserSupportsPutRoute() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        ReflectionTestUtils.setField(controller, "userColumns", Set.of("full_name"));

        mockMvc.perform(put("/api/users/12")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "full_name": "Updated Name"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(usersJdbcTemplate).update(
            eq("UPDATE users SET full_name = ? WHERE id = ?"),
            eq("Updated Name"),
            eq(12L)
        );
    }

    @Test
    void updateUserPersistsPasswordHashWhenColumnExists() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        when(passwordEncoder.encode("next-secret")).thenReturn("hashed-next-secret");
        ReflectionTestUtils.setField(controller, "userColumns", Set.of("password", "password_hash"));

        mockMvc.perform(patch("/api/users/7")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "password": "next-secret"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(usersJdbcTemplate).update(
            eq("UPDATE users SET password = ?, password_hash = ? WHERE id = ?"),
            eq("hashed-next-secret"),
            eq("hashed-next-secret"),
            eq(7L)
        );
    }

    @Test
    void updateUserPersistsBlockedStateAndEnabledFlagWhenEnabledColumnExists() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        ReflectionTestUtils.setField(controller, "userColumns", Set.of("is_blocked", "enabled"));

        mockMvc.perform(patch("/api/users/7")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "is_blocked": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(usersJdbcTemplate).update(
            eq("UPDATE users SET is_blocked = ?, enabled = ? WHERE id = ?"),
            eq(1),
            eq(false),
            eq(7L)
        );
    }

    @Test
    void updateUserPersistsProfileFieldsWhenUsernameEditPermissionIsGranted() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        ReflectionTestUtils.setField(controller, "userColumns", Set.of("full_name", "email", "department", "photo", "birth_date"));

        mockMvc.perform(patch("/api/users/7")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "full_name": "Self User",
                      "email": "self@example.com",
                      "department": "Support",
                      "photo": "/api/attachments/avatars/self.png",
                      "birth_date": "1990-01-01"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(usersJdbcTemplate).update(
            eq("UPDATE users SET full_name = ?, email = ?, department = ?, photo = ?, birth_date = ? WHERE id = ?"),
            eq("Self User"),
            eq("self@example.com"),
            eq("Support"),
            eq("/api/attachments/avatars/self.png"),
            eq("1990-01-01"),
            eq(7L)
        );
    }

    @Test
    void updateUserPersistsPhonesAsJsonWhenColumnExists() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        ReflectionTestUtils.setField(controller, "userColumns", Set.of("phones"));

        mockMvc.perform(patch("/api/users/7")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "phones": [
                        {"label": "work", "value": "+7-900-000-00-00"}
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(usersJdbcTemplate).update(
            eq("UPDATE users SET phones = ? WHERE id = ?"),
            eq("[{\"label\":\"work\",\"value\":\"+7-900-000-00-00\"}]"),
            eq(7L)
        );
    }

    @Test
    void updateUserPersistsRoleIdAndRoleWhenColumnsExist() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        ReflectionTestUtils.setField(controller, "userColumns", Set.of("role_id", "role"));

        mockMvc.perform(patch("/api/users/7")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "role_id": 8,
                      "role": "reviewer"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(usersJdbcTemplate).update(
            eq("UPDATE users SET role_id = ?, role = ? WHERE id = ?"),
            eq(8),
            eq("reviewer"),
            eq(7L)
        );
    }

    @Test
    void createUserPersistsPasswordHashColumnWhenAvailable() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        ReflectionTestUtils.setField(controller, "userColumns", Set.of("username", "password", "password_hash"));
        when(usersJdbcTemplate.queryForObject(startsWith("SELECT COUNT(*) FROM users"), eq(Integer.class), eq("fresh")))
            .thenReturn(0);
        when(passwordEncoder.encode("secret")).thenReturn("hashed-secret");

        mockMvc.perform(post("/api/users")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "username": "fresh",
                      "password": "secret"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(usersJdbcTemplate).update(
            eq("INSERT INTO users (username, password, password_hash) VALUES (?, ?, ?)"),
            eq("fresh"),
            eq("hashed-secret"),
            eq("hashed-secret")
        );
    }

    @Test
    void createUserPersistsEnabledAndRegistrationDateWhenColumnsAreAvailable() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        ReflectionTestUtils.setField(controller, "userColumns", Set.of("username", "password", "enabled", "registration_date"));
        when(usersJdbcTemplate.queryForObject(startsWith("SELECT COUNT(*) FROM users"), eq(Integer.class), eq("fresh")))
            .thenReturn(0);
        when(passwordEncoder.encode("secret")).thenReturn("hashed-secret");

        mockMvc.perform(post("/api/users")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "username": "fresh",
                      "password": "secret"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(usersJdbcTemplate).update(
            eq("INSERT INTO users (username, password, enabled, registration_date) VALUES (?, ?, ?, ?)"),
            eq("fresh"),
            eq("hashed-secret"),
            eq(true),
            anyString()
        );
    }

    @Test
    void updateUserRejectsRoleChangeWhenOnlyPrivilegedFieldsAreProvidedWithoutPermissions() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(false);
        when(permissionService.hasAuthority(any(), anyString())).thenReturn(false);
        ReflectionTestUtils.setField(controller, "userColumns", Set.of("role_id", "is_blocked"));

        mockMvc.perform(patch("/api/users/7")
                .with(user("operator"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "role_id": 5,
                      "is_blocked": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Нет данных для обновления"));
    }

    @Test
    void deleteUserReturnsNotFoundWhenRecordIsMissing() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        when(usersJdbcTemplate.update("DELETE FROM users WHERE id = ?", 41L)).thenReturn(0);

        mockMvc.perform(delete("/api/users/41")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Пользователь не найден"));
    }

    @Test
    void deleteUserRemovesExistingUser() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        when(usersJdbcTemplate.update("DELETE FROM users WHERE id = ?", 42L)).thenReturn(1);

        mockMvc.perform(delete("/api/users/42")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteUserRejectsWhenPermissionIsMissing() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(false);
        when(permissionService.hasAuthority(any(), eq("ROLE_PORTAL_ADMIN"))).thenReturn(false);
        when(permissionService.hasAuthority(any(), eq("PAGE_SETTINGS"))).thenReturn(false);

        mockMvc.perform(delete("/api/users/41")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Недостаточно прав для удаления пользователя"));
    }

    @Test
    void createRoleRejectsDuplicateName() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        when(usersJdbcTemplate.queryForObject(startsWith("SELECT COUNT(*) FROM roles"), eq(Integer.class), eq("portal-admin")))
            .thenReturn(1);

        mockMvc.perform(post("/api/roles")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "name": "portal-admin"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Роль уже существует"));
    }

    @Test
    void createRoleRejectsMissingName() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);

        mockMvc.perform(post("/api/roles")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "name": ""
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Название роли не может быть пустым"));
    }

    @Test
    void createRoleRejectsWhenPermissionIsMissing() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(false);
        when(permissionService.hasAuthority(any(), eq("ROLE_PORTAL_ADMIN"))).thenReturn(false);
        when(permissionService.hasAuthority(any(), eq("PAGE_SETTINGS"))).thenReturn(false);

        mockMvc.perform(post("/api/roles")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "name": "reviewer"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Недостаточно прав для создания роли"));
    }

    @Test
    void createRolePersistsPermissionsPayload() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        when(usersJdbcTemplate.queryForObject(startsWith("SELECT COUNT(*) FROM roles"), eq(Integer.class), eq("reviewer")))
            .thenReturn(0);

        mockMvc.perform(post("/api/roles")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "name": "reviewer",
                      "description": "Review role",
                      "permissions": {
                        "pages": ["dialogs"],
                        "fields": {
                          "edit": ["user.create"],
                          "view": ["user.password"]
                        }
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(usersJdbcTemplate).update(
            eq("INSERT INTO roles(name, description, permissions) VALUES (?, ?, ?)"),
            eq("reviewer"),
            eq("Review role"),
            anyString()
        );
    }

    @Test
    void createRoleStoresEmptyPermissionsWhenPayloadDoesNotContainPermissions() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        when(usersJdbcTemplate.queryForObject(startsWith("SELECT COUNT(*) FROM roles"), eq(Integer.class), eq("empty-perms")))
            .thenReturn(0);

        mockMvc.perform(post("/api/roles")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "name": "empty-perms",
                      "description": "No permissions"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(usersJdbcTemplate).update(
            eq("INSERT INTO roles(name, description, permissions) VALUES (?, ?, ?)"),
            eq("empty-perms"),
            eq("No permissions"),
            eq("{}")
        );
    }

    @Test
    void createRoleSupportsTrailingSlashRoute() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        when(usersJdbcTemplate.queryForObject(startsWith("SELECT COUNT(*) FROM roles"), eq(Integer.class), eq("reviewer-slash")))
            .thenReturn(0);

        mockMvc.perform(post("/api/roles/")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "name": "reviewer-slash",
                      "description": "Role"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateRoleReturnsNoDataWhenPayloadEmpty() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);

        mockMvc.perform(patch("/api/roles/5")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Нет данных для обновления"));
    }

    @Test
    void updateRolePersistsPermissionsPayload() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);

        mockMvc.perform(patch("/api/roles/5")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "permissions": {
                        "pages": ["dialogs", "settings"],
                        "fields": {
                          "edit": ["user.create", "role.pages"],
                          "view": ["user.password"]
                        }
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(usersJdbcTemplate).update(
            eq("UPDATE roles SET permissions = ? WHERE id = ?"),
            anyString(),
            eq(5L)
        );
    }

    @Test
    void updateRoleSupportsPutRoute() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);

        mockMvc.perform(put("/api/roles/6")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "description": "Updated role"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(usersJdbcTemplate).update(
            eq("UPDATE roles SET description = ? WHERE id = ?"),
            eq("Updated role"),
            eq(6L)
        );
    }

    @Test
    void updateRolePersistsNameDescriptionAndPermissionsTogether() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);

        mockMvc.perform(patch("/api/roles/5")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "name": "reviewer",
                      "description": "Review role",
                      "permissions": {
                        "pages": ["dialogs", "settings"]
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(usersJdbcTemplate).update(
            eq("UPDATE roles SET name = ?, description = ?, permissions = ? WHERE id = ?"),
            eq("reviewer"),
            eq("Review role"),
            anyString(),
            eq(5L)
        );
    }

    @Test
    void updateRoleRejectsPermissionsChangeWhenRightsAreMissing() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(false);
        when(permissionService.hasAuthority(any(), eq("ROLE_PORTAL_ADMIN"))).thenReturn(false);
        when(permissionService.hasAuthority(any(), eq("PAGE_SETTINGS"))).thenReturn(false);

        mockMvc.perform(patch("/api/roles/5")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "permissions": {
                        "pages": ["dialogs"]
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Недостаточно прав для изменения разрешений роли"));
    }

    @Test
    void updateRoleStoresEmptyPermissionsWhenPayloadExplicitlySetsNull() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);

        mockMvc.perform(patch("/api/roles/13")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "permissions": null
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(usersJdbcTemplate).update(
            eq("UPDATE roles SET permissions = ? WHERE id = ?"),
            eq("{}"),
            eq(13L)
        );
    }

    @Test
    void updateRoleRejectsNameChangeWhenRightsAreMissing() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(false);
        when(permissionService.hasAuthority(any(), eq("ROLE_PORTAL_ADMIN"))).thenReturn(false);
        when(permissionService.hasAuthority(any(), eq("PAGE_SETTINGS"))).thenReturn(false);

        mockMvc.perform(patch("/api/roles/5")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "name": "new-role"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Недостаточно прав для изменения названия роли"));
    }

    @Test
    void updateRoleRejectsBlankNameEvenWhenPermissionIsGranted() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);

        mockMvc.perform(patch("/api/roles/5")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "name": ""
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Название роли не может быть пустым"));
    }

    @Test
    void updateRoleRejectsDescriptionChangeWhenRightsAreMissing() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(false);
        when(permissionService.hasAuthority(any(), eq("ROLE_PORTAL_ADMIN"))).thenReturn(false);
        when(permissionService.hasAuthority(any(), eq("PAGE_SETTINGS"))).thenReturn(false);

        mockMvc.perform(patch("/api/roles/5")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "description": "new description"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Недостаточно прав для изменения описания роли"));
    }

    @Test
    void deleteRoleReturnsNotFoundWhenRecordIsMissing() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        when(usersJdbcTemplate.update("DELETE FROM roles WHERE id = ?", 77L)).thenReturn(0);

        mockMvc.perform(delete("/api/roles/77")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Роль не найдена"));
    }

    @Test
    void deleteRoleRejectsWhenPermissionIsMissing() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(false);
        when(permissionService.hasAuthority(any(), eq("ROLE_PORTAL_ADMIN"))).thenReturn(false);
        when(permissionService.hasAuthority(any(), eq("PAGE_SETTINGS"))).thenReturn(false);

        mockMvc.perform(delete("/api/roles/77")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Недостаточно прав для удаления роли"));
    }

    @Test
    void deleteRoleRejectsWhenRoleIsStillAssignedToUsers() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        ReflectionTestUtils.setField(controller, "userColumns", Set.of("role_id"));
        when(usersJdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM users WHERE role_id = ?"),
            eq(Integer.class),
            eq(8L)
        )).thenReturn(2);

        mockMvc.perform(delete("/api/roles/8")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Роль используется пользователями"));

        verify(usersJdbcTemplate, never()).update("DELETE FROM roles WHERE id = ?", 8L);
    }

    @Test
    void deleteRoleRemovesUnusedRole() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        ReflectionTestUtils.setField(controller, "userColumns", Set.of("role_id"));
        when(usersJdbcTemplate.queryForObject(
            eq("SELECT COUNT(*) FROM users WHERE role_id = ?"),
            eq(Integer.class),
            eq(9L)
        )).thenReturn(0);
        when(usersJdbcTemplate.update("DELETE FROM roles WHERE id = ?", 9L)).thenReturn(1);

        mockMvc.perform(delete("/api/roles/9")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteRoleSkipsUsageCheckWhenRoleIdColumnIsAbsent() throws Exception {
        when(permissionService.isSuperUser(any())).thenReturn(true);
        ReflectionTestUtils.setField(controller, "userColumns", Set.of("username"));
        when(usersJdbcTemplate.update("DELETE FROM roles WHERE id = ?", 10L)).thenReturn(1);

        mockMvc.perform(delete("/api/roles/10")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(usersJdbcTemplate, never()).queryForObject(
            eq("SELECT COUNT(*) FROM users WHERE role_id = ?"),
            eq(Integer.class),
            eq(10L)
        );
    }

    @Test
    void getUserPasswordAlwaysReturnsStaticDenial() throws Exception {
        mockMvc.perform(get("/api/users/12/password")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Просмотр пароля недоступен"));
    }

    @Test
    void uploadUserPhotoRejectsEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "photo",
            "avatar.png",
            "image/png",
            new byte[0]
        );

        mockMvc.perform(multipart("/api/users/photo-upload")
                .file(file)
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Файл не может быть пустым"));
    }

    @Test
    void uploadUserPhotoRejectsUnsupportedExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "photo",
            "avatar.txt",
            "text/plain",
            "plain".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/users/photo-upload")
                .file(file)
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Поддерживаются изображения PNG, JPG, GIF или WebP."));
    }

    @Test
    void uploadUserPhotoStoresAllowedImageAndReturnsMetadata() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "photo",
            "avatar.png",
            "image/png",
            "png-binary".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/users/photo-upload")
                .file(file)
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("/api/attachments/avatars/")))
            .andExpect(jsonPath("$.filename").isNotEmpty());
    }

    @Test
    void uploadUserPhotoSupportsTrailingSlashRoute() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "photo",
            "avatar.webp",
            "image/webp",
            "webp-binary".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/users/photo-upload/")
                .file(file)
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.filename").value(org.hamcrest.Matchers.endsWith(".webp")));
    }
}
