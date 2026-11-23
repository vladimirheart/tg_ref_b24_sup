package com.example.panel.controller;

import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.service.KnowledgeAccessService;
import com.example.panel.service.KnowledgeNotePhotoService;
import com.example.panel.service.KnowledgeNoteService;
import com.example.panel.service.PermissionService;
import com.example.panel.service.LegacyTicketIdJdbcGuard;
import com.example.panel.storage.*;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.example.panel.support.AttachmentReadAccessStub;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises the real HTTP file guard, note persistence and storage together, without a mocked ACL. */
class KnowledgeNotePhotoHttpPostgresTest {
    static final String PREFIX = "/api/attachments/knowledge-base/";
    static final byte[] PHOTO = {(byte) 0xff, (byte) 0xd8, 1, 2, (byte) 0xff, (byte) 0xd9};
    @TempDir Path root;
    JdbcTemplate jdbc;
    Authentication auth;
    AttachmentService attachments;
    KnowledgeNoteService notes;
    MockMvc mvc;

    @BeforeEach void setup() throws Exception {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("note_photo_http");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY, username TEXT, role_id BIGINT, enabled BOOLEAN, is_blocked BOOLEAN)");
        jdbc.update("INSERT INTO users VALUES(1,'reader',10,true,false),(2,'other',20,true,false)");
        jdbc.execute("CREATE TABLE knowledge_articles(id BIGINT PRIMARY KEY, section_id BIGINT)");
        jdbc.execute("CREATE TABLE knowledge_sections(id BIGINT PRIMARY KEY, parent_id BIGINT)");
        jdbc.execute("CREATE TABLE knowledge_resource_acl(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,resource_type TEXT, resource_id BIGINT,subject_type TEXT,subject_id BIGINT,access_level TEXT)");
        jdbc.execute("""
            CREATE TABLE knowledge_article_files(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            article_id BIGINT REFERENCES knowledge_articles(id) ON DELETE CASCADE,draft_token VARCHAR(255),
            stored_path TEXT NOT NULL,original_name TEXT,mime_type TEXT,file_size BIGINT,
            uploaded_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP)
            """);
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V45__knowledge_notes.sql")));
        notes = new KnowledgeNoteService(jdbc, new ObjectMapper());
        auth = authentication(1,"reader","PAGE_KNOWLEDGE_BASE");
        recreateServices();
    }

    void recreateServices() throws Exception {
        ObjectStorageProperties properties = new ObjectStorageProperties(); properties.setMode("local_fs");
        var storage = new AttachmentObjectStorageService(properties, root.resolve("attachments").toString(),
            root.resolve("knowledge").toString(), root.resolve("passports").toString(), root.resolve("avatars").toString());
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.hasAuthority(any(),eq("PAGE_KNOWLEDGE_BASE"))).thenAnswer(call -> {
            Authentication actor = call.getArgument(0);
            return actor != null && actor.getAuthorities().stream().anyMatch(a -> "PAGE_KNOWLEDGE_BASE".equals(a.getAuthority()));
        });
        attachments = new AttachmentService(permissions,storage,mock(LegacyTicketIdJdbcGuard.class),
            mock(TicketAttachmentOwnerJdbcGuard.class),root.resolve("attachments").toString(),root.resolve("knowledge").toString(),
            AttachmentReadAccessStub.permitted());
        var access = new KnowledgeAccessService(jdbc);
        var controller = new AttachmentController(attachments);
        controller.setKnowledgeAccessService(access);
        controller.setKnowledgeNotePhotoService(new KnowledgeNotePhotoService(jdbc,attachments,access));
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .defaultRequest(get("/").with(request -> { request.setUserPrincipal(auth); return request; }))
            .setControllerAdvice(new com.example.panel.config.RestExceptionHandler())
            .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
            public boolean supportsParameter(MethodParameter p) {return Authentication.class.isAssignableFrom(p.getParameterType());}
            public Object resolveArgument(MethodParameter p,ModelAndViewContainer c,NativeWebRequest r,WebDataBinderFactory b) {return auth;}
        }).build();
    }

    @Test void uploadPreviewSaveAndServiceRestartAllServeTheSamePhoto() throws Exception {
        String json = mvc.perform(multipart(PREFIX + "note-photos").file(new MockMultipartFile("file","photo.jpg","image/jpeg",PHOTO)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String stored = new ObjectMapper().readTree(json).get("storedName").asText();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM knowledge_article_files WHERE stored_path=? AND article_id IS NULL AND draft_token IS NOT NULL",Integer.class,stored)).isEqualTo(1);
        readPhoto(stored);
        long id = save("![Фото](" + PREFIX + stored + ")");
        assertThat(notes.findNote(id).orElseThrow().body()).contains(stored);
        recreateServices();
        readPhoto(stored);
    }

    @Test void existingSavedPhotoNeedsNoReuploadWhileUnreferencedBlobRemainsHidden() throws Exception {
        String old = "c8a9ddc5-831c-4ce7-a0b8-cca080989956_photo_2026-07-22_11-11-39.jpg";
        store(old); store("unreferenced.jpg");
        mvc.perform(get(PREFIX+old)).andExpect(status().isNotFound());
        save("![Фото]("+PREFIX+old+")");
        readPhoto(old);
        mvc.perform(get(PREFIX+"unreferenced.jpg")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM knowledge_article_files",Integer.class)).isZero();
    }

    @Test void articleAclWinsEvenIfPrivateFileUrlIsPastedIntoANote() throws Exception {
        store("private.jpg");
        jdbc.update("INSERT INTO knowledge_articles VALUES(100,NULL),(101,NULL)");
        jdbc.update("INSERT INTO knowledge_article_files(article_id,stored_path) VALUES(100,'private.jpg')");
        jdbc.update("INSERT INTO knowledge_resource_acl(resource_type,resource_id,subject_type,subject_id,access_level) VALUES('ARTICLE',100,'USER',2,'MANAGE')");
        save("![Фото]("+PREFIX+"private.jpg)");
        mvc.perform(get(PREFIX+"private.jpg")).andExpect(status().isNotFound());
        jdbc.update("INSERT INTO knowledge_resource_acl(resource_type,resource_id,subject_type,subject_id,access_level) VALUES('ARTICLE',100,'USER',1,'VIEW')");
        readPhoto("private.jpg");
        jdbc.update("INSERT INTO knowledge_article_files(article_id,stored_path) VALUES(101,'private.jpg')");
        mvc.perform(get(PREFIX+"private.jpg")).andExpect(status().isNotFound());
    }

    @Test void missingPageUnboundRenamedDisabledAndBlockedActorsCannotReadNotePhotos() throws Exception {
        store("photo.jpg"); save("![Фото]("+PREFIX+"photo.jpg)");
        readPhoto("photo.jpg");
        auth = authentication(1,"reader","PAGE_DIALOGS");
        mvc.perform(get(PREFIX+"photo.jpg")).andExpect(status().isNotFound());
        auth = authentication(1,"renamed","PAGE_KNOWLEDGE_BASE");
        mvc.perform(get(PREFIX+"photo.jpg")).andExpect(status().isNotFound());
        auth = UsernamePasswordAuthenticationToken.authenticated("reader","",List.of(new SimpleGrantedAuthority("PAGE_KNOWLEDGE_BASE")));
        mvc.perform(get(PREFIX+"photo.jpg")).andExpect(status().isNotFound());
        auth = authentication(1,"reader","PAGE_KNOWLEDGE_BASE");
        jdbc.update("UPDATE users SET is_blocked=true WHERE id=1");
        mvc.perform(get(PREFIX+"photo.jpg")).andExpect(status().isNotFound());
        jdbc.update("UPDATE users SET is_blocked=false,enabled=false WHERE id=1");
        mvc.perform(get(PREFIX+"photo.jpg")).andExpect(status().isNotFound());
        auth=null;
        mvc.perform(get(PREFIX+"photo.jpg")).andExpect(status().isNotFound());
    }

    @Test void ordinaryArticleUploadIsNotRegisteredAsPublicNotePhoto() throws Exception {
        String json = mvc.perform(multipart("/api/attachments/knowledge-base").file(new MockMultipartFile("file","article.jpg","image/jpeg",PHOTO)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String stored = new ObjectMapper().readTree(json).get("storedName").asText();
        mvc.perform(get(PREFIX+stored)).andExpect(status().isNotFound());
    }

    @Test void uploadRequiresBoundLiveKnowledgeAccountAndRasterFilename() throws Exception {
        var photo = new MockMultipartFile("file","photo.jpg","image/jpeg",PHOTO);
        auth = authentication(1,"reader","PAGE_DIALOGS");
        mvc.perform(multipart(PREFIX+"note-photos").file(photo)).andExpect(status().isForbidden());
        auth = authentication(1,"reader","PAGE_KNOWLEDGE_BASE");
        jdbc.update("UPDATE users SET is_blocked=true WHERE id=1");
        mvc.perform(multipart(PREFIX+"note-photos").file(photo)).andExpect(status().isForbidden());
        jdbc.update("UPDATE users SET is_blocked=false WHERE id=1");
        mvc.perform(multipart(PREFIX+"note-photos").file(new MockMultipartFile("file","photo.svg","image/svg+xml",PHOTO)))
            .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM knowledge_article_files",Integer.class)).isZero();
    }

    long save(String body) {return notes.save(null,"Заметка",body,List.of(),List.of(),List.of(),List.of(),"reader");}
    void store(String name) throws Exception {attachments.storeImportedKnowledgeBaseFile(name,name,"image/jpeg",new ByteArrayInputStream(PHOTO));}
    void readPhoto(String name) throws Exception {
        mvc.perform(get(PREFIX+name)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG)).andExpect(content().bytes(PHOTO));
    }
    static Authentication authentication(long id,String username,String authority) {
        var principal=new PanelUserPrincipal(id,username,"",true,List.of(new SimpleGrantedAuthority(authority)));
        return UsernamePasswordAuthenticationToken.authenticated(principal,"",principal.getAuthorities());
    }
}
