package com.example.panel.controller;

import com.example.panel.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("sqlite")
@TestPropertySource(properties = {
        "spring.flyway.locations=classpath:db/migration/sqlite"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationApiIntegrationTest {

    private static Path dbFile;
    private static Path sharedConfigDir;

    @DynamicPropertySource
    static void sqlite(DynamicPropertyRegistry registry) throws IOException {
        dbFile = Files.createTempFile("panel-notification-api", ".db");
        sharedConfigDir = Files.createTempDirectory("panel-notification-shared-config");
        registry.add("app.datasource.sqlite.path", () -> dbFile.toString());
        registry.add("shared-config.dir", () -> sharedConfigDir.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NotificationService notificationService;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM ticket_active");
        jdbcTemplate.update("DELETE FROM ticket_responsibles");
    }

    @Test
    void notificationsApiReflectsUnreadLifecycleForAuthenticatedIdentity() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO notifications (user_identity, text, url, is_read, created_at) VALUES (?,?,?,?,?)",
                "operator.main", "Первое уведомление", "/dialogs?ticketId=T-100", 0, "2026-05-20 07:00:00.000"
        );
        jdbcTemplate.update(
                "INSERT INTO notifications (user_identity, text, url, is_read, created_at) VALUES (?,?,?,?,?)",
                "operator.main", "Уже прочитано", "/dialogs?ticketId=T-101", 1, "2026-05-20 07:01:00.000"
        );
        jdbcTemplate.update(
                "INSERT INTO notifications (user_identity, text, url, is_read, created_at) VALUES (?,?,?,?,?)",
                "other.user", "Чужое уведомление", "/dialogs?ticketId=T-999", 0, "2026-05-20 07:02:00.000"
        );
        Long unreadId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE user_identity = ? AND is_read = 0 ORDER BY id DESC LIMIT 1",
                Long.class,
                "operator.main"
        );

        mockMvc.perform(get("/api/notifications").principal(userDetailsAuthentication("Operator.Main")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].text").value("Уже прочитано"))
                .andExpect(jsonPath("$[0].read").value(true))
                .andExpect(jsonPath("$[1].text").value("Первое уведомление"))
                .andExpect(jsonPath("$[1].read").value(false));

        mockMvc.perform(get("/api/notifications/unread_count").principal(userDetailsAuthentication("Operator.Main")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.unread").value(1));

        mockMvc.perform(post("/api/notifications/" + unreadId + "/read").principal(userDetailsAuthentication("Operator.Main")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/notifications/unread_count").principal(userDetailsAuthentication("Operator.Main")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0));

        Integer readFlag = jdbcTemplate.queryForObject(
                "SELECT is_read FROM notifications WHERE id = ?",
                Integer.class,
                unreadId
        );
        assertThat(readFlag).isEqualTo(1);
    }

    @Test
    void markAsReadDoesNotTouchNotificationOfAnotherIdentity() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO notifications (user_identity, text, url, is_read, created_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP)",
                "watcher_peer", "Уведомление для peer", "/dialogs?ticketId=T-201", 0
        );
        Long peerNotificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE user_identity = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                "watcher_peer"
        );

        mockMvc.perform(post("/api/notifications/" + peerNotificationId + "/read")
                        .principal(namedAuthentication("watcher_owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/notifications/unread_count").principal(namedAuthentication("watcher_peer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1));

        Integer readFlag = jdbcTemplate.queryForObject(
                "SELECT is_read FROM notifications WHERE id = ?",
                Integer.class,
                peerNotificationId
        );
        assertThat(readFlag).isZero();
    }

    @Test
    void anonymousRequestsOperateOnAllIdentityOnly() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO notifications (user_identity, text, url, is_read, created_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP)",
                "all", "Общее уведомление", "/dialogs?ticketId=T-ALL", 0
        );
        jdbcTemplate.update(
                "INSERT INTO notifications (user_identity, text, url, is_read, created_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP)",
                "operator", "Личное уведомление", "/dialogs?ticketId=T-PRIVATE", 0
        );
        Long allNotificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE user_identity = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                "all"
        );

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("Общее уведомление"));

        mockMvc.perform(get("/api/notifications/unread_count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1));

        mockMvc.perform(post("/api/notifications/" + allNotificationId + "/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/notifications/unread_count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0));

        Integer privateUnread = jdbcTemplate.queryForObject(
                "SELECT is_read FROM notifications WHERE user_identity = ?",
                Integer.class,
                "operator"
        );
        assertThat(privateUnread).isZero();
    }

    @Test
    void notifyUsersExcludingBridgesRuntimeNotificationsToApiWithDedupAndExclusion() throws Exception {
        notificationService.notifyUsersExcluding(
                java.util.Set.of(" watcher_peer ", "watcher_active", "WATCHER_owner", "watcher_peer"),
                "WATCHER_owner",
                "  Новый ответ в обращении T-NOTIFY-500  ",
                " /dialogs?ticketId=T-NOTIFY-500 "
        );

        mockMvc.perform(get("/api/notifications").principal(namedAuthentication("watcher_peer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("Новый ответ в обращении T-NOTIFY-500"))
                .andExpect(jsonPath("$[0].url").value("/dialogs/T-NOTIFY-500"))
                .andExpect(jsonPath("$[0].read").value(false));

        mockMvc.perform(get("/api/notifications").principal(namedAuthentication("watcher_active")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("Новый ответ в обращении T-NOTIFY-500"));

        mockMvc.perform(get("/api/notifications").principal(namedAuthentication("watcher_owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    private Authentication userDetailsAuthentication(String username) {
        return new TestingAuthenticationToken(
                new User(username, "n/a", AuthorityUtils.NO_AUTHORITIES),
                "n/a",
                AuthorityUtils.NO_AUTHORITIES
        );
    }

    private Authentication namedAuthentication(String name) {
        return new TestingAuthenticationToken(name, "n/a", AuthorityUtils.NO_AUTHORITIES);
    }
}
