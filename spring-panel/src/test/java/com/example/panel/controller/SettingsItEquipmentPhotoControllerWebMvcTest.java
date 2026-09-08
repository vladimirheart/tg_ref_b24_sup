package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.SettingsItEquipmentPhotoService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SettingsItEquipmentPhotoController.class)
@AutoConfigureMockMvc
class SettingsItEquipmentPhotoControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SettingsItEquipmentPhotoService photoService;

    @Test
    void uploadPhotoPassesExplicitTitleReplacementConfirmation() throws Exception {
        when(photoService.uploadPhoto(eq(7L), any(), eq("title"), eq("Вид спереди"), eq(true)))
                .thenReturn(Map.of("success", true, "photo_url", "{}"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "front.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/settings/it-equipment/7/photos")
                .file(file)
                .param("category", "title")
                .param("comment", "Вид спереди")
                .param("replace_title", "true")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(photoService).uploadPhoto(eq(7L), any(), eq("title"), eq("Вид спереди"), eq(true));
    }

    @Test
    void patchPhotoUpdatesTypeAndDescription() throws Exception {
        when(photoService.updatePhoto(7L, "photo-1", "general", "Боковой вид", false))
                .thenReturn(Map.of("success", true, "photo_url", "{}"));

        mockMvc.perform(patch("/api/settings/it-equipment/7/photos/photo-1")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"category":"general","comment":"Боковой вид","replace_title":false}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(photoService).updatePhoto(7L, "photo-1", "general", "Боковой вид", false);
    }

    @Test
    void deletePhotoRequiresSettings() throws Exception {
        when(photoService.deletePhoto(7L, "photo-1"))
                .thenReturn(Map.of("success", true, "photo_url", ""));

        mockMvc.perform(delete("/api/settings/it-equipment/7/photos/photo-1")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void passportReaderCanLoadEquipmentPhotoWithoutSettingsPermission() throws Exception {
        Resource resource = new ByteArrayResource(new byte[]{1, 2, 3});
        ResponseEntity<Resource> response = ResponseEntity.ok(resource);
        when(photoService.downloadPhoto("photo-1.jpg"))
                .thenReturn(response);

        mockMvc.perform(get("/api/settings/it-equipment/photos/file/photo-1.jpg")
                .with(user("viewer").authorities(() -> "PAGE_OBJECT_PASSPORTS")))
            .andExpect(status().isOk());
    }
}
