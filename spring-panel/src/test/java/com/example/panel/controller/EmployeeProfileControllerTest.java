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

    @Test void currentOperatorWithoutUserAdministrationSeesOnlyWorkIdentity() throws Exception {
        when(access.canAccess(any())).thenReturn(true);
        when(users.findEmployeeProfile("anna")).thenReturn(Optional.of(
            new PanelUserRepository.EmployeeProfile(11L, "anna", "Анна Смирнова", "Поддержка", "legacy.jpg")));
        when(photos.resolveUserAvatarUrl(11L, "legacy.jpg", null)).thenReturn("/api/attachments/avatars/11.jpg");
        mvc.perform(get("/api/panel-users/profile").param("username", "anna").with(user("operator").authorities(List.of())))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.length()").value(4))
            .andExpect(jsonPath("$.fullName").value("Анна Смирнова"))
            .andExpect(jsonPath("$.photo").value("/api/attachments/avatars/11.jpg"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist())
            .andExpect(jsonPath("$.phones").doesNotExist()).andExpect(jsonPath("$.birthDate").doesNotExist())
            .andExpect(jsonPath("$.role").doesNotExist());
        verify(users, never()).findAll();
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
