package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.BusinessAdministrationService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;

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

@WebMvcTest(BusinessAdministrationApiController.class)
@Import(BusinessAdministrationApiControllerWebMvcTest.MethodSecurity.class)
class BusinessAdministrationApiControllerWebMvcTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
    }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private BusinessAdministrationService service;

    @MockBean
    private MultibusinessFeatureFlags flags;

    @Test
    void businessAdminRoleDoesNotReplaceSystemAdminOrPageAuthority() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        mvc.perform(get("/api/business-admin/overview")
                .with(user("tenant-admin").authorities(() -> "PAGE_SETTINGS", () -> "BUSINESS_ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/business-admin/overview")
                .with(user("system-admin").authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isForbidden());
        verify(service, never()).overview();
    }

    @Test
    void disabledFoundationHidesManagementAndAuthorizedAdminCanCreate() throws Exception {
        mvc.perform(get("/api/business-admin/overview")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN")))
                .andExpect(status().isNotFound());
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(service.overview()).thenReturn(new BusinessAdministrationService.Overview(
                List.of(), List.of(), List.of(), List.of(), List.of()));
        mvc.perform(get("/api/business-admin/overview")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businesses").isArray());

        when(service.createBusiness("autoservice", "Автосервис")).thenReturn(
                new BusinessAdministrationService.Business(3L, "autoservice", "Автосервис", "active"));
        mvc.perform(post("/api/business-admin/businesses")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"code\":\"autoservice\",\"displayName\":\"Автосервис\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("autoservice"));
    }

    @Test
    void locationsRequireGlobalAdminAndEnabledFoundation() throws Exception {
        String path = "/api/business-admin/businesses/3/locations";
        when(flags.isFoundationEnabled()).thenReturn(true);
        mvc.perform(post(path)
                .with(user("viewer").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf()).contentType("application/json")
                .content("{\"locationName\":\"Центр\"}"))
                .andExpect(status().isForbidden());
        when(flags.isFoundationEnabled()).thenReturn(false);
        mvc.perform(post(path)
                .with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN"))
                .with(csrf()).contentType("application/json")
                .content("{\"locationName\":\"Центр\"}"))
                .andExpect(status().isNotFound());
        verify(service, never()).createLocation(3L, "Центр", null, null);
    }

    @Test
    void adminCanCreateAndArchiveBusinessLocation() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        String path = "/api/business-admin/businesses/3/locations";
        when(service.createLocation(3L, "Центр", "", "Москва")).thenReturn(Optional.of(
                new BusinessAdministrationService.Location(9L, 3L, "Центр", "", "Москва", "active")));
        mvc.perform(post(path)
                .with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN"))
                .with(csrf()).contentType("application/json")
                .content("{\"locationName\":\"Центр\",\"locationType\":\"\",\"city\":\"Москва\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessId").value(3))
                .andExpect(jsonPath("$.id").value(9));

        when(service.setLocationStatus(3L, 9L, "archived")).thenReturn(true);
        mvc.perform(put(path + "/9/status")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN"))
                .with(csrf()).contentType("application/json")
                .content("{\"status\":\"archived\"}"))
                .andExpect(status().isNoContent());
        when(service.setLocationStatus(3L, 9L, "deleted"))
                .thenThrow(new IllegalArgumentException("Invalid location status"));
        mvc.perform(put(path + "/9/status")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN"))
                .with(csrf()).contentType("application/json")
                .content("{\"status\":\"deleted\"}"))
                .andExpect(status().isBadRequest());
    }
}
