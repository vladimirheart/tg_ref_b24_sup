package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.ObjectPassportService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ObjectPassportApiController.class)
@AutoConfigureMockMvc
class ObjectPassportApiControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ObjectPassportService objectPassportService;

    @Test
    void createPassportDelegatesToService() throws Exception {
        when(objectPassportService.createPassport(anyMap()))
                .thenReturn(Map.of(
                        "success", true,
                        "id", 15,
                        "passport", Map.of("id", 15, "department", "Ленина 1")
                ));

        mockMvc.perform(post("/api/object_passports")
                        .with(user("operator").authorities(() -> "PAGE_OBJECT_PASSPORTS"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "department": "Ленина 1",
                                  "city": "Смоленск"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.id").value(15))
                .andExpect(jsonPath("$.passport.department").value("Ленина 1"));
    }

    @Test
    void getPassportReturnsPayload() throws Exception {
        when(objectPassportService.getPassport(42L))
                .thenReturn(Map.of(
                        "success", true,
                        "passport", Map.of("id", 42, "department", "Гагарина 9")
                ));

        mockMvc.perform(get("/api/object_passports/42")
                        .with(user("operator").authorities(() -> "PAGE_OBJECT_PASSPORTS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passport.id").value(42))
                .andExpect(jsonPath("$.passport.department").value("Гагарина 9"));
    }

    @Test
    void updatePassportSupportsPutRoute() throws Exception {
        when(objectPassportService.updatePassport(eq(7L), anyMap()))
                .thenReturn(Map.of(
                        "success", true,
                        "id", 7,
                        "passport", Map.of("id", 7, "department", "Современник")
                ));

        mockMvc.perform(put("/api/object_passports/7")
                        .with(user("operator").authorities(() -> "PAGE_OBJECT_PASSPORTS"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "department": "Современник"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.passport.id").value(7))
                .andExpect(jsonPath("$.passport.department").value("Современник"));
    }

    @Test
    void passportCasesEndpointReturnsItemsPayload() throws Exception {
        when(objectPassportService.getEmptyCasesPayload(9L))
                .thenReturn(Map.of(
                        "success", true,
                        "items", List.of(),
                        "total_minutes", 0,
                        "total_display", "0 мин"
                ));

        mockMvc.perform(get("/api/object_passports/9/cases")
                        .with(user("operator").authorities(() -> "PAGE_OBJECT_PASSPORTS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.items").isArray());
    }
}
