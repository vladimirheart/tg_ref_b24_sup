package com.example.panel.controller;

import com.example.panel.service.IncidentRelatedObjectService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IncidentRelatedObjectController.class)
@org.springframework.context.annotation.Import(IncidentRelatedObjectControllerWebMvcTest.MethodSecurity.class)
class IncidentRelatedObjectControllerWebMvcTest {
    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    static class MethodSecurity {}
    @Autowired MockMvc mvc;
    @MockBean IncidentRelatedObjectService service;

    @Test void searchRequiresIncidentPageAndPreservesLiteralQuery() throws Exception {
        mvc.perform(get("/api/incidents/related-objects").param("type", "ticket").with(user("reader").authorities(() -> "PAGE_SETTINGS"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
        when(service.search("ticket", " T/1?&%# Ю ", 20)).thenReturn(Map.of("items", List.of(Map.of("value", " T/1?&%# Ю ", "label", "20261026-001"))));
        mvc.perform(get("/api/incidents/related-objects").param("type", "ticket").param("query", " T/1?&%# Ю ").param("limit", "20")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].value").value(" T/1?&%# Ю "));
    }
    @Test void createRequiresGlobalAdminAndPageAndCsrf() throws Exception {
        String path = "/api/incidents/external-objects";
        for (String[] authorities : List.of(new String[]{"PAGE_DIALOGS"}, new String[]{"ROLE_ADMIN"}, new String[]{"PAGE_TASKS", "BUSINESS_ADMIN", "ALL_BUSINESSES"})) {
            mvc.perform(post(path).with(user("user").authorities(java.util.Arrays.stream(authorities).map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList()))
                    .with(csrf()).contentType("application/json").content("{\"name\":\"Сервер\"}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post(path).with(user("admin").authorities(() -> "ROLE_ADMIN", () -> "PAGE_DIALOGS")).contentType("application/json").content("{\"name\":\"Сервер\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        when(service.createExternal(anyMap())).thenReturn(Map.of("item", Map.of("value", "9007199254740993", "label", "Сервер")));
        mvc.perform(post(path).with(user("admin").authorities(() -> "ROLE_ADMIN", () -> "PAGE_DIALOGS")).with(csrf()).contentType("application/json").content("{\"name\":\"Сервер\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.item.value").value("9007199254740993"));
    }
}
