package com.example.panel.controller;

import com.example.panel.service.PermissionService;
import com.example.panel.service.LegacyTicketIdJdbcGuard;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.example.panel.storage.AttachmentObjectStorageService;
import com.example.panel.storage.AttachmentService;
import com.example.panel.storage.TicketAttachmentOwnerJdbcGuard;
import com.example.panel.storage.ObjectStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
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
        LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard = mock(LegacyTicketIdJdbcGuard.class);
        TicketAttachmentOwnerJdbcGuard ownerGuard = mock(TicketAttachmentOwnerJdbcGuard.class);
        Authentication authentication = mock(Authentication.class);
        when(permissionService.hasAuthority(authentication, "PAGE_DIALOGS")).thenReturn(true);
        when(legacyTicketIdJdbcGuard.hasUniqueTicket("3d543ab982d2f414aa9dc8b135805291")).thenReturn(true);
        when(legacyTicketIdJdbcGuard.hasUniqueNonGeneralTicket("3d543ab982d2f414aa9dc8b135805291")).thenReturn(true);
        for (String filename : new String[] {"photo.jpg", "voice.ogg", "video-note.mp4"}) {
            when(ownerGuard.resolveUniqueOwner("3d543ab982d2f414aa9dc8b135805291/2026/08/24/" + filename))
                    .thenReturn("3d543ab982d2f414aa9dc8b135805291");
        }

        AttachmentService attachmentService = new AttachmentService(
                permissionService,
                objectStorage,
                legacyTicketIdJdbcGuard,
                ownerGuard,
                attachmentsRoot.toString(),
                knowledgeRoot.toString()
        , com.example.panel.support.AttachmentReadAccessStub.permitted());
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

    @Test
    void metadataOwnerServesRealRangeAndConflictingHistoryStopsStorageRead() throws Exception {
        String ticketId = " T/100?&%#+ Ю ";
        String key = "channel/attachments/video-note.mp4";
        Path attachmentsRoot = tempDir.resolve("owned-attachments");
        Path knowledgeRoot = tempDir.resolve("owned-knowledge");
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setMode("local_fs");
        AttachmentObjectStorageService storage = spy(new AttachmentObjectStorageService(properties,
                attachmentsRoot.toString(), knowledgeRoot.toString(),
                tempDir.resolve("owned-passport").toString(), tempDir.resolve("owned-avatars").toString()));
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("attachment_owner_mvc");
        jdbc.execute("CREATE TABLE tickets(is_general BOOLEAN NOT NULL DEFAULT FALSE,user_id BIGINT, ticket_id TEXT, PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("CREATE TABLE chat_history(id BIGINT PRIMARY KEY, ticket_id TEXT)");
        jdbc.execute("""
                CREATE TABLE chat_attachment_metadata(
                    chat_history_id BIGINT UNIQUE REFERENCES chat_history(id), ticket_id TEXT, storage_key TEXT,
                    storage_provider TEXT DEFAULT 'local_fs', storage_class TEXT DEFAULT 'dialog_attachment',
                    normalization_status TEXT DEFAULT 'normalized', deleted_at TIMESTAMPTZ)
                """);
        jdbc.update("INSERT INTO tickets(user_id,ticket_id) VALUES (1,?), (2,?), (3,'channel')", ticketId, ticketId.trim());
        jdbc.update("INSERT INTO chat_history VALUES (11,?)", ticketId);
        jdbc.update("INSERT INTO chat_attachment_metadata(chat_history_id,ticket_id,storage_key) VALUES (11,?,?)", ticketId, key);
        LegacyTicketIdJdbcGuard tickets = new LegacyTicketIdJdbcGuard(jdbc);
        TicketAttachmentOwnerJdbcGuard owner = new TicketAttachmentOwnerJdbcGuard(jdbc, tickets);
        PermissionService permissions = mock(PermissionService.class);
        Authentication authentication = mock(Authentication.class);
        when(permissions.hasAuthority(authentication, "PAGE_DIALOGS")).thenReturn(true);
        AttachmentService service = new AttachmentService(permissions, storage, tickets, owner,
                attachmentsRoot.toString(), knowledgeRoot.toString(), com.example.panel.support.AttachmentReadAccessStub.permitted());
        MockMvc mvc = createMockMvc(new AttachmentController(service), authentication);
        byte[] bytes = {0, 1, 2, 3, 4, 5};
        writeAttachment(attachmentsRoot, key, bytes);

        mvc.perform(get("/api/attachments/tickets/by-storage-key").principal(authentication)
                        .queryParam("key", key).header(HttpHeaders.RANGE, "bytes=1-3"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 1-3/6"))
                .andExpect(content().contentType(MediaType.parseMediaType("video/mp4")))
                .andExpect(content().bytes(new byte[] {1, 2, 3}));
        mvc.perform(get("/api/attachments/tickets/by-path").principal(authentication)
                        .queryParam("path", "C:\\legacy\\attachments\\" + key.replace('/', '\\')))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes));

        jdbc.update("UPDATE chat_attachment_metadata SET ticket_id=?", ticketId.trim());
        clearInvocations(storage);
        mvc.perform(get("/api/attachments/tickets/by-storage-key").principal(authentication).queryParam("key", key))
                .andExpect(status().isNotFound()).andExpect(content().string(""));
        mvc.perform(get("/api/attachments/tickets/by-path").principal(authentication)
                        .queryParam("path", "C:\\legacy\\attachments\\" + key.replace('/', '\\')))
                .andExpect(status().isNotFound()).andExpect(content().string(""));
        verifyNoInteractions(storage);
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
