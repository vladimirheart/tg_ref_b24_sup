package com.example.panel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.config.RestExceptionHandler;
import com.example.panel.service.DialogConversationReadService;
import com.example.panel.service.ClientBlacklistService;
import com.example.panel.service.DialogAiAssistantService;
import com.example.panel.service.DialogLookupReadService;
import com.example.panel.service.DialogNotificationService;
import com.example.panel.service.DialogParticipantService;
import com.example.panel.service.DialogQuickActionService;
import com.example.panel.service.DialogReplyService;
import com.example.panel.service.DialogResponsibilityService;
import com.example.panel.service.DialogTicketLifecycleService;
import com.example.panel.service.NotificationService;
import com.example.panel.service.UiEventStreamService;
import com.example.panel.service.LegacyTicketIdJdbcGuard;
import com.example.panel.service.PermissionService;
import com.example.panel.storage.AttachmentObjectStorageService;
import com.example.panel.storage.AttachmentService;
import com.example.panel.storage.ObjectStorageProperties;
import com.example.panel.storage.TicketAttachmentOwnerJdbcGuard;
import com.example.panel.support.PanelTimestampSqlSupport;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.web.util.UriUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@SpringBootTest(classes = AttachmentTicketQueryHttpIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.endpoint.health.group.readiness.include=ping")
class AttachmentTicketQueryHttpIntegrationTest {
    private static final String ROUTE = "/api/attachments/tickets/by-ticket";
    private static final String OWNER = " T/100+%2F Ю ";
    private static final String FILE = "12345678-1234-1234-1234-123456789abc_report+%2F Ю.mp4";
    private static final byte[] BYTES = {0, 1, 2, 3, 4, 5};
    @TempDir Path tempDir;
    @LocalServerPort int port;
    @MockBean AttachmentService attachments;

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
    @Import({AttachmentController.class, RestExceptionHandler.class})
    static class TestApplication {
        @Bean
        UserDetailsService testUsers() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("operator").password("{noop}test-password").authorities("PAGE_DIALOGS").build(),
                    User.withUsername("viewer").password("{noop}test-password").authorities("PAGE_KNOWLEDGE_BASE").build());
        }
    }

    private JdbcTemplate jdbc;
    private AttachmentService realService;
    private AttachmentObjectStorageService storage;
    private DialogConversationReadService reader;

    @Test
    void legacyQueryDownloadDoesNotExposeGeneralEvenWithValidOwnerMetadataAndRange() throws Exception {
        jdbc.update("UPDATE tickets SET is_general=TRUE WHERE ticket_id=?", OWNER);
        clearInvocations(storage);
        var response = get(query(OWNER, FILE), "operator", "bytes=0-1");
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).isEmpty();
        verifyNoInteractions(storage);
    }

    @BeforeEach
    void setupOwnedLocalFileAndRealServiceDelegate() throws Exception {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("attachment_ticket_query_http");
        jdbc.execute("CREATE TABLE tickets(is_general BOOLEAN NOT NULL DEFAULT FALSE,user_id BIGINT,ticket_id TEXT,PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("""
                CREATE TABLE chat_history(
                    id BIGINT PRIMARY KEY,ticket_id TEXT,sender TEXT DEFAULT 'client',
                    message TEXT DEFAULT 'own message',timestamp TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                    message_type TEXT DEFAULT 'video',attachment TEXT,file_name TEXT,tg_message_id BIGINT,
                    reply_to_tg_id BIGINT,channel_id BIGINT DEFAULT 2)
                """);
        jdbc.execute("""
                CREATE TABLE chat_attachment_metadata(
                    chat_history_id BIGINT UNIQUE REFERENCES chat_history(id),ticket_id TEXT,storage_key TEXT,
                    storage_provider TEXT DEFAULT 'local_fs',storage_class TEXT DEFAULT 'dialog_attachment',
                    normalization_status TEXT DEFAULT 'normalized',deleted_at TIMESTAMPTZ,
                    original_name TEXT,size BIGINT,availability_status TEXT DEFAULT 'available',legacy_attachment_ref TEXT)
                """);
        jdbc.update("INSERT INTO tickets(user_id,ticket_id) VALUES (1,?),(2,?)", OWNER, OWNER.trim());
        jdbc.update("INSERT INTO chat_history(id,ticket_id,channel_id) VALUES (11,?,1)", OWNER);
        jdbc.update("INSERT INTO chat_history(id,ticket_id,attachment,channel_id) VALUES (12,?,?,2)", OWNER, FILE);
        String key = OWNER.trim() + "/" + FILE;
        jdbc.update("INSERT INTO chat_attachment_metadata(chat_history_id,ticket_id,storage_key) VALUES (11,?,?)", OWNER, key);
        Path root = tempDir.resolve("attachments");
        Path file = root.resolve(key);
        Files.createDirectories(file.getParent());
        Files.write(file, BYTES);
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setMode("local_fs");
        storage = spy(new AttachmentObjectStorageService(properties, root.toString(),
                tempDir.resolve("knowledge").toString(), tempDir.resolve("passport").toString(),
                tempDir.resolve("avatars").toString()));
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.hasAuthority(any(Authentication.class), eq("PAGE_DIALOGS"))).thenAnswer(call -> {
            Authentication authentication = call.getArgument(0);
            return authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(authority -> "PAGE_DIALOGS".equals(authority.getAuthority()));
        });
        var tickets = new LegacyTicketIdJdbcGuard(jdbc);
        realService = new AttachmentService(permissions, storage, tickets,
                new TicketAttachmentOwnerJdbcGuard(jdbc, tickets), root.toString(),
                tempDir.resolve("knowledge").toString(), com.example.panel.support.AttachmentReadAccessStub.permitted());
        reader = new DialogConversationReadService(jdbc, realService, new PanelTimestampSqlSupport(), tickets);
        // Only the test wiring is mocked: controller calls delegate to real owner/JDBC/local_fs logic.
        when(attachments.downloadTicketAttachment(any(Authentication.class), anyString(), anyString(), nullable(String.class)))
                .thenAnswer(call -> realService.downloadTicketAttachment(
                        call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"T-PLAIN", OWNER})
    void immediateQuickMediaUrlDownloadsRetainedUploadAndRejectsLaterForeignClaim(String owner) throws Exception {
        if ("T-PLAIN".equals(owner)) jdbc.update("INSERT INTO tickets(user_id,ticket_id) VALUES (3,?)", owner);
        byte[] payload = {7, 6, 5, 4};
        var reply = mock(DialogReplyService.class);
        var ai = mock(DialogAiAssistantService.class);
        var notifications = mock(NotificationService.class);
        var quick = new DialogQuickActionService(mock(DialogTicketLifecycleService.class),
                mock(DialogLookupReadService.class), mock(DialogResponsibilityService.class),
                mock(DialogParticipantService.class), reply, mock(DialogNotificationService.class), ai,
                notifications, mock(UiEventStreamService.class), realService, mock(ClientBlacklistService.class));
        var storedName = new java.util.concurrent.atomic.AtomicReference<String>();
        when(reply.sendMediaReply(eq(owner), any(MultipartFile.class), eq("caption"), eq(null), eq("operator"),
                anyString(), eq("fresh+%2F Ю.png"), eq("image/png"), eq(4L))).thenAnswer(call -> {
            String filename = call.getArgument(5);
            storedName.set(filename);
            assertThat(call.getArgument(1, MultipartFile.class).getBytes()).containsExactly(payload);
            // Controlled transport substitute persists the same owner/metadata claim as a successful send.
            jdbc.update("INSERT INTO chat_history(id,ticket_id,attachment,channel_id) VALUES (21,?,?,2)", owner, filename);
            jdbc.update("""
                    INSERT INTO chat_attachment_metadata(chat_history_id,ticket_id,storage_key,original_name,size)
                    VALUES (21,?,?,?,4)
                    """, owner, owner.trim() + "/" + filename, "fresh+%2F Ю.png");
            return new DialogReplyService.DialogMediaReplyResult(true, null, "2026-10-13T13:00:01Z",
                    202L, filename, "image", "caption", "operator");
        });
        var authentication = UsernamePasswordAuthenticationToken.authenticated("operator", "test-password",
                AuthorityUtils.createAuthorityList("PAGE_DIALOGS"));

        var response = quick.sendMediaReply(owner,
                new MockMultipartFile("file", "fresh+%2F Ю.png", "image/png", payload),
                "caption", null, "operator", authentication);

        assertThat(response.get("success")).isEqualTo(true);
        String url = (String) response.get("attachment");
        assertThat(url).startsWith(ROUTE + "?ticketId=");
        var locator = com.example.panel.storage.AttachmentStorageKeyResolver.parseLegacyDialogReference(url);
        assertThat(locator.ticketId()).isEqualTo(owner);
        assertThat(locator.reference()).isEqualTo(storedName.get());
        Path uploaded = tempDir.resolve("attachments").resolve(owner.trim() + "/" + storedName.get());
        assertThat(Files.readAllBytes(uploaded)).containsExactly(payload);
        clearInvocations(storage);

        var whole = get(url, "operator", null);
        assertThat(whole.statusCode()).isEqualTo(200);
        assertThat(whole.body()).containsExactly(payload);
        var range = get(url, "operator", "bytes=1-2");
        assertThat(range.statusCode()).isEqualTo(206);
        assertThat(range.body()).containsExactly(6, 5);
        assertThat(range.headers().firstValue(HttpHeaders.CONTENT_RANGE)).contains("bytes 1-2/4");
        verify(storage, org.mockito.Mockito.times(2)).openDialogAttachment(owner, storedName.get());
        verify(ai).registerOperatorReply(owner, "caption", "operator");

        if (OWNER.equals(owner)) {
            jdbc.update("UPDATE chat_attachment_metadata SET ticket_id=? WHERE chat_history_id=21", owner.trim());
            clearInvocations(storage);
            assertThat(get(url, "operator", null).statusCode()).isEqualTo(404);
            verifyNoInteractions(storage);
            assertThat(Files.readAllBytes(uploaded)).containsExactly(payload);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"T", " T ", " T/100?&%#+ Ю ", "T%2F+", " null ", "T\\opaque"})
    void queryBindingKeepsOpaqueIdAndFilenameExactlyOnceOverRealHttp(String ticketId) throws Exception {
        String filename = "folder/photo+%2F Ю.bin";
        when(attachments.downloadTicketAttachment(any(Authentication.class), eq(ticketId), eq(filename), eq("bytes=1-3")))
                .thenReturn(ResponseEntity.status(206).contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes 1-3/6")
                        .body(new ByteArrayResource(new byte[] {1, 2, 3})));

        var response = get(query(ticketId, filename), "operator", "bytes=1-3");

        assertThat(response.statusCode()).isEqualTo(206);
        assertThat(response.body()).containsExactly(1, 2, 3);
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_RANGE)).contains("bytes 1-3/6");
        verify(attachments).downloadTicketAttachment(
                org.mockito.ArgumentMatchers.argThat(auth -> "operator".equals(auth.getName())),
                eq(ticketId), eq(filename), eq("bytes=1-3"));
        verifyNoInteractions(storage);
    }

    @Test
    void actualHistoryUrlDownloadsExactOwnedFileAndRangeWithoutEncodedSlashPath() throws Exception {
        var messages = reader.loadHistory(OWNER, 2L);
        assertThat(messages).hasSize(1);
        String url = messages.get(0).attachment();
        assertThat(url).startsWith(ROUTE + "?ticketId=");
        assertThat(url).contains("%2B", "%252F");
        assertThat(URI.create(url).getRawPath()).isEqualTo(ROUTE);
        assertThat(messages.get(0).attachmentSize()).isEqualTo(6L);
        jdbc.update("UPDATE chat_history SET attachment=? WHERE id=12", url);
        var fromQueryReference = reader.loadHistory(OWNER, 2L);
        assertThat(fromQueryReference).hasSize(1);
        assertThat(fromQueryReference.get(0).attachment()).isEqualTo(url);
        assertThat(fromQueryReference.get(0).attachmentSize()).isEqualTo(6L);
        clearInvocations(storage);

        var whole = get(url, "operator", null);
        assertThat(whole.statusCode()).isEqualTo(200);
        assertThat(whole.body()).containsExactly(BYTES);
        assertThat(whole.headers().firstValue(HttpHeaders.ACCEPT_RANGES)).contains("bytes");
        assertThat(whole.headers().firstValue(HttpHeaders.CONTENT_LENGTH)).contains("6");
        assertThat(MediaType.parseMediaType(whole.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElseThrow())
                .isCompatibleWith(MediaType.parseMediaType("video/mp4"))).isTrue();
        assertThat(whole.headers().firstValue(HttpHeaders.CONTENT_DISPOSITION)).isPresent();

        var range = get(url, "operator", "bytes=1-3");
        assertThat(range.statusCode()).isEqualTo(206);
        assertThat(range.body()).containsExactly(1, 2, 3);
        assertThat(range.headers().firstValue(HttpHeaders.CONTENT_RANGE)).contains("bytes 1-3/6");
        assertThat(range.headers().firstValue(HttpHeaders.CONTENT_LENGTH)).contains("3");
        verify(storage, org.mockito.Mockito.times(2)).openDialogAttachment(OWNER, FILE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"trimmed-sibling", "conflicting-metadata", "duplicate-ticket", "missing-schema", "unsafe-filename"})
    void deniedOwnerContextStopsBinaryIoOverHttp(String condition) throws Exception {
        String requested = OWNER;
        String filename = FILE;
        switch (condition) {
            case "trimmed-sibling" -> requested = OWNER.trim();
            case "conflicting-metadata" -> jdbc.update("UPDATE chat_attachment_metadata SET ticket_id=?", OWNER.trim());
            case "duplicate-ticket" -> jdbc.update("INSERT INTO tickets(user_id,ticket_id) VALUES (3,?)", OWNER);
            case "missing-schema" -> jdbc.execute("DROP TABLE chat_attachment_metadata");
            case "unsafe-filename" -> filename = "../" + FILE;
            default -> throw new AssertionError(condition);
        }

        var response = get(query(requested, filename), "operator", null);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).isEmpty();
        verifyNoInteractions(storage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?ticketId=&filename=file.pdf", "?ticketId=%20&filename=file.pdf",
            "?ticketId=UNKNOWN&filename=file.pdf", "?ticketId=T&filename="})
    void blankOrUnknownContextDoesNotReachBinaryStorage(String query) throws Exception {
        var response = get(ROUTE + query, "operator", null);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).isEmpty();
        verifyNoInteractions(storage);
    }

    @Test
    void nestedFilenameIsDecodedOnceAndUsesTheExactOwnedPhysicalKey() throws Exception {
        String filename = "nested/" + FILE;
        String key = OWNER.trim() + "/" + filename;
        jdbc.update("UPDATE chat_attachment_metadata SET storage_key=?", key);
        Path path = tempDir.resolve("attachments").resolve(key);
        Files.createDirectories(path.getParent());
        byte[] nestedBytes = {6, 7, 8};
        Files.write(path, nestedBytes);

        var response = get(query(OWNER, filename), "operator", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).containsExactly(nestedBytes);
        verify(storage).openDialogAttachment(OWNER, filename);
    }

    @Test
    void defaultFirewallRejectsEncodedPercentInPathNotInQuery() {
        var firewall = new org.springframework.security.web.firewall.StrictHttpFirewall();
        var pathRequest = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/attachments/tickets/T/photo%252F.png");
        pathRequest.setServletPath("/api/attachments/tickets/T/photo%2F.png");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> firewall.getFirewalledRequest(pathRequest))
                .isInstanceOf(org.springframework.security.web.firewall.RequestRejectedException.class)
                .hasMessageContaining("%25");
        var queryRequest = new org.springframework.mock.web.MockHttpServletRequest("GET", ROUTE);
        queryRequest.setServletPath(ROUTE);
        queryRequest.setQueryString("ticketId=T&filename=photo%252F.png");
        org.assertj.core.api.Assertions.assertThatCode(() -> firewall.getFirewalledRequest(queryRequest)).doesNotThrowAnyException();
    }

    @Test
    void encodedSlashLegacyPathIsRejectedByTomcatBeforeService() throws Exception {
        String legacy = "/api/attachments/tickets/" + UriUtils.encodePathSegment(OWNER, StandardCharsets.UTF_8)
                + "/" + UriUtils.encodePathSegment(FILE, StandardCharsets.UTF_8);

        assertThat(get(legacy, "operator", null).statusCode()).isEqualTo(400);
        verifyNoInteractions(attachments, storage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?filename=file.pdf", "?ticketId=T", "?ticketId=T&ticketId=U&filename=file.pdf",
            "?ticketId=T&filename=a&filename=b", "?ticketId=T&filename=file.pdf&unexpected=1"})
    void missingDuplicateOrUnknownParametersAreRejectedBeforeService(String query) throws Exception {
        var response = get(ROUTE + query, "operator", null);

        assertThat(response.statusCode()).isEqualTo(400);
        verifyNoInteractions(attachments, storage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"viewer", "anonymous"})
    void authenticationAndPageAuthorityRemainRequired(String user) throws Exception {
        var response = get(query(OWNER, FILE), "anonymous".equals(user) ? null : user, null);

        assertThat(response.statusCode()).isEqualTo("anonymous".equals(user) ? 401 : 403);
        verifyNoInteractions(storage);
        if ("anonymous".equals(user)) verifyNoInteractions(attachments);
    }

    @Test
    void legacyPathRouteStillServesAnOrdinaryUniqueTicket() throws Exception {
        jdbc.update("INSERT INTO tickets(user_id,ticket_id) VALUES (3,'T')");
        String filename = "legacy.bin";
        Path path = tempDir.resolve("attachments/T/" + filename);
        Files.createDirectories(path.getParent());
        Files.write(path, BYTES);

        var response = get("/api/attachments/tickets/T/" + filename, "operator", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).containsExactly(BYTES);
    }

    private static String query(String owner, String filename) {
        return ROUTE + "?ticketId=" + UriUtils.encode(owner, StandardCharsets.UTF_8)
                + "&filename=" + UriUtils.encode(filename, StandardCharsets.UTF_8);
    }

    private HttpResponse<byte[]> get(String path, String user, String range) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10));
        if (user != null) {
            String credentials = Base64.getEncoder().encodeToString((user + ":test-password").getBytes(StandardCharsets.UTF_8));
            request.header("Authorization", "Basic " + credentials);
        }
        if (range != null) request.header(HttpHeaders.RANGE, range);
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                .send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
