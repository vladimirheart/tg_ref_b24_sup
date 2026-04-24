package com.example.panel.controller;

import com.example.panel.service.UiPreferenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileApiController.class)
@AutoConfigureMockMvc
class ProfileApiControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    @Qualifier("usersJdbcTemplate")
    private JdbcTemplate usersJdbcTemplate;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private UiPreferenceService uiPreferenceService;

    @Test
    void loadUiPreferencesReturnsServerBackedPayload() throws Exception {
        when(uiPreferenceService.loadForUser("operator"))
                .thenReturn(Map.of("theme", "dark", "themePalette", "catppuccin"));

        mockMvc.perform(get("/profile/ui-preferences")
                        .with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.preferences.theme").value("dark"))
                .andExpect(jsonPath("$.preferences.themePalette").value("catppuccin"));
    }

    @Test
    void loadUiPreferencesReturnsUnauthorizedWhenPrincipalIsMissing() throws Exception {
        mockMvc.perform(get("/profile/ui-preferences"))
                .andExpect(status().isUnauthorized());

        verify(uiPreferenceService, never()).loadForUser(anyString());
    }

    @Test
    void updateUiPreferencesPersistsNormalizedPayload() throws Exception {
        when(uiPreferenceService.saveForUser(eq("operator"), anyMap()))
                .thenReturn(Map.of(
                        "theme", "dark",
                        "themePalette", "amber-minimal",
                        "sidebarNavOrder", List.of("dialogs", "settings")
                ));

        mockMvc.perform(put("/profile/ui-preferences")
                        .with(user("operator"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "theme": "dark",
                                  "themePalette": "amber-minimal",
                                  "sidebarNavOrder": ["dialogs", "settings"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.preferences.themePalette").value("amber-minimal"))
                .andExpect(jsonPath("$.preferences.sidebarNavOrder[0]").value("dialogs"));
    }

    @Test
    void updateUiPreferencesReturnsUnauthorizedWhenPrincipalIsMissing() throws Exception {
        mockMvc.perform(put("/profile/ui-preferences")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "theme": "dark"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verify(uiPreferenceService, never()).saveForUser(anyString(), anyMap());
    }

    @Test
    void updatePasswordReturnsUnauthorizedWhenPrincipalIsMissing() throws Exception {
        mockMvc.perform(post("/profile/password")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "current_password": "old",
                                  "new_password": "next",
                                  "confirm_password": "next"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verify(usersJdbcTemplate, never()).queryForList(anyString(), anyString());
    }

    @Test
    void updatePasswordRejectsMissingCurrentPassword() throws Exception {
        mockMvc.perform(post("/profile/password")
                        .with(user("operator"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "current_password": "",
                                  "new_password": "next",
                                  "confirm_password": "next"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Укажите текущий пароль."));
    }

    @Test
    void updatePasswordRejectsMissingNewPassword() throws Exception {
        mockMvc.perform(post("/profile/password")
                        .with(user("operator"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "current_password": "old",
                                  "new_password": "",
                                  "confirm_password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Новый пароль не может быть пустым."));
    }

    @Test
    void updatePasswordRejectsMismatchedConfirmation() throws Exception {
        mockMvc.perform(post("/profile/password")
                        .with(user("operator"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "current_password": "old",
                                  "new_password": "next",
                                  "confirm_password": "wrong"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Новый пароль и подтверждение не совпадают."));
    }

    @Test
    void updatePasswordReturnsNotFoundWhenUserDoesNotExist() throws Exception {
        when(usersJdbcTemplate.queryForList(anyString(), eq("operator"))).thenReturn(List.of());

        mockMvc.perform(post("/profile/password")
                        .with(user("operator"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "current_password": "old",
                                  "new_password": "next",
                                  "confirm_password": "next"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Пользователь не найден."));
    }

    @Test
    void updatePasswordRejectsInvalidCurrentPassword() throws Exception {
        when(usersJdbcTemplate.queryForList(anyString(), eq("operator")))
                .thenReturn(List.of(Map.of("password", "stored-hash")));
        when(passwordEncoder.matches("old", "stored-hash")).thenReturn(false);

        mockMvc.perform(post("/profile/password")
                        .with(user("operator"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "current_password": "old",
                                  "new_password": "next",
                                  "confirm_password": "next"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Текущий пароль введён неверно."));
    }

    @Test
    void updatePasswordPersistsHashedPasswordWithoutPasswordHashColumn() throws Exception {
        when(usersJdbcTemplate.queryForList(anyString(), eq("operator")))
                .thenReturn(List.of(Map.of("password", "stored-hash")));
        when(passwordEncoder.matches("old", "stored-hash")).thenReturn(true);
        when(passwordEncoder.encode("next")).thenReturn("hashed-next");

        mockMvc.perform(post("/profile/password")
                        .with(user("operator"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "current_password": "old",
                                  "new_password": "next",
                                  "confirm_password": "next"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Пароль успешно обновлён."));

        verify(usersJdbcTemplate).update(
                eq("UPDATE users SET password = ? WHERE lower(username) = lower(?)"),
                eq("hashed-next"),
                eq("operator")
        );
    }

    @Test
    void updatePasswordPersistsPasswordHashWhenColumnExists() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(usersJdbcTemplate.queryForList(anyString(), eq("operator")))
                .thenReturn(List.of(Map.of("password", "stored-hash")));
        when(passwordEncoder.matches("old", "stored-hash")).thenReturn(true);
        when(passwordEncoder.encode("next")).thenReturn("hashed-next");
        when(usersJdbcTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getColumns(any(), any(), eq("users"), eq("password_hash"))).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);

        mockMvc.perform(post("/profile/password")
                        .with(user("operator"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "current_password": "old",
                                  "new_password": "next",
                                  "confirm_password": "next"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(usersJdbcTemplate).update(
                eq("UPDATE users SET password = ?, password_hash = ? WHERE lower(username) = lower(?)"),
                eq("hashed-next"),
                eq("hashed-next"),
                eq("operator")
        );
    }
}
