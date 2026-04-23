package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.PermissionService;
import com.example.panel.service.SharedConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthManagementApiController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "app.storage.avatars=${java.io.tmpdir}/iguana-auth-test-avatars")
class AuthManagementApiControllerWebMvcTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

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
    void updateOrgStructurePersistsPayloadThroughSharedConfigBoundary() throws Exception {
        mockMvc.perform(post("/api/auth/org-structure")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
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
}
