package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.SettingsItEquipmentService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SettingsItEquipmentController.class)
@AutoConfigureMockMvc
class SettingsItEquipmentControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SettingsItEquipmentService settingsItEquipmentService;

    @Test
    void listEquipmentReturnsItems() throws Exception {
        when(settingsItEquipmentService.listItEquipment())
            .thenReturn(Map.of("success", true, "items", List.of(Map.of("id", 3, "equipment_model", "ThinkPad"))));

        mockMvc.perform(get("/api/settings/it-equipment").with(user("admin").authorities(() -> "PAGE_SETTINGS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.items[0].equipment_model").value("ThinkPad"));
    }

    @Test
    void createEquipmentPassesActorFromAuthentication() throws Exception {
        when(settingsItEquipmentService.createItEquipment(anyMap(), eq("admin")))
            .thenReturn(Map.of("success", true, "items", List.of(Map.of("equipment_vendor", "Lenovo"))));

        mockMvc.perform(post("/api/settings/it-equipment")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "equipment_type": "Ноутбук",
                      "equipment_vendor": "Lenovo",
                      "equipment_model": "ThinkPad"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.items[0].equipment_vendor").value("Lenovo"));
    }

    @Test
    void updateEquipmentSupportsPutAndActorPropagation() throws Exception {
        when(settingsItEquipmentService.updateItEquipment(eq(9L), anyMap(), eq("admin")))
            .thenReturn(Map.of("success", true, "items", List.of(Map.of("id", 9, "equipment_model", "Latitude"))));

        mockMvc.perform(put("/api/settings/it-equipment/9")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    { "equipment_model": "Latitude" }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.items[0].equipment_model").value("Latitude"));
    }

    @Test
    void deleteEquipmentDelegatesToService() throws Exception {
        when(settingsItEquipmentService.deleteItEquipment(12L, "admin"))
            .thenReturn(Map.of("success", true, "items", List.of()));

        mockMvc.perform(delete("/api/settings/it-equipment/12")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }
}
