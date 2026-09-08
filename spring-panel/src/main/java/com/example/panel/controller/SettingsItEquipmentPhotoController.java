package com.example.panel.controller;

import com.example.panel.service.SettingsItEquipmentPhotoService;
import java.io.IOException;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/settings/it-equipment")
public class SettingsItEquipmentPhotoController {

    private final SettingsItEquipmentPhotoService photoService;

    public SettingsItEquipmentPhotoController(SettingsItEquipmentPhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping(value = "/{itemId}/photos", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> uploadPhoto(@PathVariable long itemId,
                                           @RequestParam("file") MultipartFile file,
                                           @RequestParam("category") String category,
                                           @RequestParam("comment") String comment) throws IOException {
        return photoService.uploadPhoto(itemId, file, category, comment);
    }

    @DeleteMapping("/{itemId}/photos/{photoId}")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> deletePhoto(@PathVariable long itemId,
                                           @PathVariable String photoId) {
        return photoService.deletePhoto(itemId, photoId);
    }

    @GetMapping("/photos/file/{storedName:.+}")
    @PreAuthorize("hasAnyAuthority('PAGE_SETTINGS', 'PAGE_OBJECT_PASSPORTS')")
    public ResponseEntity<Resource> downloadPhoto(@PathVariable String storedName) throws IOException {
        return photoService.downloadPhoto(storedName);
    }
}
