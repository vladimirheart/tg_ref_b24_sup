package com.example.panel.controller;

import com.example.panel.service.PermissionService;
import com.example.panel.storage.AttachmentObjectStorageService;
import com.example.panel.storage.AttachmentService;
import com.example.panel.storage.ObjectStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttachmentControllerMediaWebMvcTest {

    @TempDir
    Path tempDir;

    @Test
    void nestedStorageKeysServeImageAudioAndVideoRangeThroughMvc() throws Exception {
        Path attachmentsRoot = tempDir.resolve("attachments");
        Path knowledgeRoot = tempDir.resolve("knowledge");
        Path passportRoot = tempDir.resolve("passport");
        Path avatarsRoot = tempDir.resolve("avatars");

        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setMode("local_fs");
        AttachmentObjectStorageService objectStorage = new AttachmentObjectStorageService(
                properties,
                attachmentsRoot.toString(),
                knowledgeRoot.toString(),
                passportRoot.toString(),
                avatarsRoot.toString()
        );

        PermissionService permissionService = mock(PermissionService.class);
        Authentication authentication = mock(Authentication.class);
        when(permissionService.hasAuthority(authentication, "PAGE_DIALOGS")).thenReturn(true);

        AttachmentService attachmentService = new AttachmentService(
                permissionService,
                objectStorage,
                attachmentsRoot.toString(),
                knowledgeRoot.toString()
        );
        MockMvc mockMvc = createMockMvc(new AttachmentController(attachmentService), authentication);

        String photoKey = "3d543ab982d2f414aa9dc8b135805291/2026/08/24/photo.jpg";
        byte[] photoPayload = new byte[] {(byte) 0xff, (byte) 0xd8, 1, 2, (byte) 0xff, (byte) 0xd9};
        writeAttachment(attachmentsRoot, photoKey, photoPayload);

        mockMvc.perform(get("/api/attachments/tickets/by-storage-key")
                        .principal(authentication)
                        .queryParam("key", photoKey))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, photoPayload.length))
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(photoPayload));

        String voiceKey = "3d543ab982d2f414aa9dc8b135805291/2026/08/24/voice.ogg";
        byte[] voicePayload = new byte[] {'O', 'g', 'g', 'S', 0, 1, 2, 3, 4, 5, 6, 7};
        writeAttachment(attachmentsRoot, voiceKey, voicePayload);

        mockMvc.perform(get("/api/attachments/tickets/by-storage-key")
                        .principal(authentication)
                        .queryParam("key", voiceKey))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(content().contentType(MediaType.parseMediaType("audio/ogg")))
                .andExpect(content().bytes(voicePayload));

        String videoKey = "3d543ab982d2f414aa9dc8b135805291/2026/08/24/video-note.mp4";
        byte[] videoPayload = new byte[256];
        for (int i = 0; i < videoPayload.length; i++) {
            videoPayload[i] = (byte) i;
        }
        writeAttachment(attachmentsRoot, videoKey, videoPayload);

        mockMvc.perform(get("/api/attachments/tickets/by-storage-key")
                        .principal(authentication)
                        .queryParam("key", videoKey)
                        .header(HttpHeaders.RANGE, "bytes=0-99"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-99/256"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 100))
                .andExpect(content().contentType(MediaType.parseMediaType("video/mp4")))
                .andExpect(content().bytes(Arrays.copyOfRange(videoPayload, 0, 100)));
    }

    private static MockMvc createMockMvc(AttachmentController controller, Authentication authentication) {
        HandlerMethodArgumentResolver authenticationResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return Authentication.class.isAssignableFrom(parameter.getParameterType());
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return authentication;
            }
        };
        return MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(authenticationResolver)
                .build();
    }

    private static void writeAttachment(Path root, String storageKey, byte[] payload) throws Exception {
        Path target = root.resolve(storageKey);
        Files.createDirectories(target.getParent());
        Files.write(target, payload);
    }
}
