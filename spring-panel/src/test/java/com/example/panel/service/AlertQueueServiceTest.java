package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertQueueServiceTest {

    private static final DateTimeFormatter SQLITE_LOCAL_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private JdbcTemplate usersJdbcTemplate;
    private NotificationService notificationService;
    private AlertQueueService service;

    @BeforeEach
    void setUp() throws Exception {
        Path usersDbFile = Files.createTempFile("alert-queue-users-", ".db");
        DataSource usersDataSource = new DriverManagerDataSource("jdbc:sqlite:" + usersDbFile.toAbsolutePath());
        usersJdbcTemplate = new JdbcTemplate(usersDataSource);
        notificationService = mock(NotificationService.class);
        when(notificationService.buildDialogUrl(any())).thenAnswer(invocation -> {
            Object raw = invocation.getArgument(0);
            return raw == null ? "/dialogs" : "/dialogs/" + String.valueOf(raw).trim();
        });
        service = new AlertQueueService(usersJdbcTemplate, notificationService, new ObjectMapper());
        usersJdbcTemplate.execute("""
                CREATE TABLE users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    password TEXT NOT NULL DEFAULT '{noop}test',
                    enabled BOOLEAN NOT NULL DEFAULT 1,
                    department TEXT,
                    is_blocked BOOLEAN NOT NULL DEFAULT 0,
                    last_portal_activity_at TEXT,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    @Test
    void notifyIncomingClientMessageUsesReadableTextAndOnlineDepartmentRouting() {
        insertUser("alice", true, false, "Support", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5).format(SQLITE_LOCAL_TS));
        insertUser("bob", true, false, "Support", LocalDateTime.now(ZoneOffset.UTC).minusHours(2).format(SQLITE_LOCAL_TS));
        insertUser("carol", true, false, "Sales", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(3).format(SQLITE_LOCAL_TS));

        boolean notified = service.notifyIncomingClientMessage(
                channel("Support Desk", """
                        {
                          "panelNotifications": {
                            "routing": {
                              "department": "Support",
                              "targetMode": "department_all",
                              "deliveryMode": "online_only_fallback_all"
                            },
                            "events": {
                              "incomingClientMessage": true
                            }
                          }
                        }
                        """),
                "T-88",
                "  Клиент прислал лог ошибки  "
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> recipientsCaptor = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

        assertThat(notified).isTrue();
        verify(notificationService).notifyUsers(recipientsCaptor.capture(), textCaptor.capture(), urlCaptor.capture());
        assertThat(recipientsCaptor.getValue()).containsExactly("alice");
        assertThat(textCaptor.getValue()).isEqualTo("Новое сообщение в обращении T-88: Клиент прислал лог ошибки");
        assertThat(urlCaptor.getValue()).isEqualTo("/dialogs/T-88");
    }

    @Test
    void notifyFirstResponseOverdueFallsBackToAllSelectedRecipientsWhenNobodyIsOnline() {
        insertUser("alice", true, false, "Support", LocalDateTime.now(ZoneOffset.UTC).minusHours(3).format(SQLITE_LOCAL_TS));
        insertUser("bob", true, false, "Support", null);
        insertUser("carol", true, false, "Support", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2).format(SQLITE_LOCAL_TS));

        boolean notified = service.notifyFirstResponseOverdue(
                channel("Support Desk", """
                        {
                          "panelNotifications": {
                            "routing": {
                              "department": "Support",
                              "targetMode": "department_except",
                              "deliveryMode": "online_only_fallback_all",
                              "excludeUsernames": ["carol"]
                            },
                            "events": {
                              "firstResponseOverdue": true
                            }
                          }
                        }
                        """),
                "T-99",
                42
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> recipientsCaptor = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);

        assertThat(notified).isTrue();
        verify(notificationService).notifyUsers(recipientsCaptor.capture(), textCaptor.capture(), org.mockito.ArgumentMatchers.eq("/dialogs/T-99"));
        assertThat(recipientsCaptor.getValue()).containsExactlyInAnyOrder("alice", "bob");
        assertThat(textCaptor.getValue()).isEqualTo("Первая реакция просрочена (Support Desk) в обращении T-99. Просрочка: 42 мин.");
    }

    @Test
    void notifyQueueForNewPublicAppealSupportsLegacyAlertQueueEmployeesOnlyRouting() {
        insertUser("mila", true, false, "Support", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1).toString());
        insertUser("sasha", true, false, "Support", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(30).toString());
        insertUser("dima", true, false, "Support", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1).toString());

        String preview = "x".repeat(150);
        service.notifyQueueForNewPublicAppeal(
                channel("Веб-форма", """
                        {
                          "alertQueue": {
                            "enabled": true,
                            "department": "Support",
                            "targetMode": "employees_only",
                            "deliveryMode": "all",
                            "employeeUsernames": ["mila", " Sasha ", "mila"]
                          }
                        }
                        """),
                "T-11",
                preview
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> recipientsCaptor = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);

        verify(notificationService).notifyUsers(recipientsCaptor.capture(), textCaptor.capture(), org.mockito.ArgumentMatchers.eq("/dialogs/T-11"));
        assertThat(recipientsCaptor.getValue()).containsExactlyInAnyOrder("mila", "sasha");
        assertThat(textCaptor.getValue())
                .startsWith("Новое обращение (Веб-форма): ")
                .hasSize("Новое обращение (Веб-форма): ".length() + 143);
        assertThat(textCaptor.getValue()).endsWith("...");
    }

    private Channel channel(String channelName, String questionsCfg) {
        Channel channel = new Channel();
        channel.setId(7L);
        channel.setChannelName(channelName);
        channel.setQuestionsCfg(questionsCfg);
        return channel;
    }

    private void insertUser(String username,
                            boolean enabled,
                            boolean blocked,
                            String department,
                            String lastPortalActivityAt) {
        usersJdbcTemplate.update(
                """
                INSERT INTO users(username, password, enabled, department, is_blocked, last_portal_activity_at, created_at)
                VALUES (?, '{noop}test', ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                username,
                enabled,
                department,
                blocked,
                lastPortalActivityAt
        );
    }
}
