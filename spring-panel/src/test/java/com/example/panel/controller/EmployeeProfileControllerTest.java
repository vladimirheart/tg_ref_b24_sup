package com.example.panel.controller;

import com.example.panel.repository.PanelUserRepository;
import com.example.panel.service.PanelUserPhotoService;
import com.example.panel.service.SharedSystemAccessService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmployeeProfileController.class)
@Import(EmployeeProfileControllerTest.MethodSecurity.class)
class EmployeeProfileControllerTest {
    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    static class MethodSecurity {}
    @Autowired MockMvc mvc;
    @MockBean PanelUserRepository users;
    @MockBean PanelUserPhotoService photos;
    @MockBean(name = "sharedSystemAccessService") SharedSystemAccessService access;

    @Test void currentOperatorWithoutUserAdministrationSeesWorkContactsAndStatusWithoutCredentials() throws Exception {
        when(access.canAccess(any())).thenReturn(true);
        when(users.findEmployeeProfile("anna")).thenReturn(Optional.of(
            new PanelUserRepository.EmployeeProfile(11L, "anna", "Анна Смирнова", "Поддержка", "legacy.jpg",
                "anna@example.test", "[{\"type\":\"Рабочий\",\"value\":\"+7 999 000-00-00\"}]", java.time.OffsetDateTime.parse("2026-11-25T15:00:00+03:00"), true)));
        when(photos.resolveUserAvatarUrl(11L, "legacy.jpg", null)).thenReturn("/api/attachments/avatars/11.jpg");
        mvc.perform(get("/api/panel-users/profile").param("username", "anna").with(user("operator").authorities(List.of())))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.length()").value(10))
            .andExpect(jsonPath("$.userId").value("11"))
            .andExpect(jsonPath("$.fullName").value("Анна Смирнова"))
            .andExpect(jsonPath("$.photo").value("/api/attachments/avatars/11.jpg"))
            .andExpect(jsonPath("$.email").value("anna@example.test"))
            .andExpect(jsonPath("$.phones[0].label").value("Рабочий"))
            .andExpect(jsonPath("$.phones[0].value").value("+7 999 000-00-00"))
            .andExpect(jsonPath("$.blocked").value(true))
            .andExpect(jsonPath("$.lastPortalActivityAt").value("2026-11-25T12:00:00Z"))
            .andExpect(jsonPath("$.serverTime").isString())
            .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.birthDate").doesNotExist())
            .andExpect(jsonPath("$.role").doesNotExist());
        verify(users, never()).findAll();
    }

    @Test void numericIdsNeverBecomeUsernameLookupsAndSurviveRenames() throws Exception {
        when(access.canAccess(any())).thenReturn(true);
        when(users.findEmployeeProfile(9007199254740993L)).thenReturn(Optional.of(
            new PanelUserRepository.EmployeeProfile(9007199254740993L, "renamed", "Анна", null, null, null, "broken JSON", null, false)));
        mvc.perform(get("/api/panel-users/profile").param("userId", "9007199254740993").with(user("operator")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value("9007199254740993"))
            .andExpect(jsonPath("$.username").value("renamed"))
            .andExpect(jsonPath("$.phones").isEmpty()).andExpect(jsonPath("$.lastPortalActivityAt").isEmpty());
        verify(users, never()).findEmployeeProfile(anyString());
    }

    @Test void contactProjectionIgnoresForeignJsonFieldsAndSupportsLegacyPhoneKeys() throws Exception {
        when(access.canAccess(any())).thenReturn(true);
        when(users.findEmployeeProfile("anna")).thenReturn(Optional.of(
            new PanelUserRepository.EmployeeProfile(11L, "anna", "Анна", null, null, null,
                "[{\"label\":\"Офис\",\"number\":\"+123\",\"password\":\"hidden\"},{\"value\":\" \"},\"bad\"]", null, false)));
        mvc.perform(get("/api/panel-users/profile").param("username", "anna").with(user("operator")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.phones.length()").value(1))
            .andExpect(jsonPath("$.phones[0].label").value("Офис"))
            .andExpect(jsonPath("$.phones[0].value").value("+123"))
            .andExpect(jsonPath("$.phones[0].password").doesNotExist());
    }

    @Test void rejectsMissingAmbiguousAndInvalidIdentifiers() throws Exception {
        when(access.canAccess(any())).thenReturn(true);
        mvc.perform(get("/api/panel-users/profile").with(user("operator"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/panel-users/profile").param("username", "anna").param("userId", "11").with(user("operator")))
            .andExpect(status().isBadRequest());
        for (String id : List.of("0", "-1", "invalid", "9223372036854775808")) {
            mvc.perform(get("/api/panel-users/profile").param("userId", id).with(user("operator"))).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(users, photos);
    }

    @Test void revokedAccountCannotReadProfiles() throws Exception {
        when(access.canAccess(any())).thenReturn(false);
        mvc.perform(get("/api/panel-users/profile").param("username", "anna").with(user("revoked")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(users, photos);
    }

    @Test void anonymousCannotReadProfiles() throws Exception {
        mvc.perform(get("/api/panel-users/profile").param("username", "anna")).andExpect(status().isUnauthorized());
        verifyNoInteractions(users, photos);
    }

    @Test void missingOrAmbiguousIdentityIsNotFound() throws Exception {
        when(access.canAccess(any())).thenReturn(true);
        when(users.findEmployeeProfile("missing")).thenReturn(Optional.empty());
        mvc.perform(get("/api/panel-users/profile").param("username", "missing").with(user("operator")))
            .andExpect(status().isNotFound());
    }

    @Test void rejectsBlankAndUnboundedNamesBeforeRepositoryLookup() throws Exception {
        when(access.canAccess(any())).thenReturn(true);
        for (String name : List.of(" ", "a".repeat(201))) mvc.perform(get("/api/panel-users/profile")
            .param("username", name).with(user("operator"))).andExpect(status().isBadRequest());
        verifyNoInteractions(users, photos);
    }
}
