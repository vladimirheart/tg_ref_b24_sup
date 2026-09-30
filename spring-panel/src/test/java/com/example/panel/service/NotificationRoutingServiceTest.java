package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationRoutingServiceTest {

    private static final DateTimeFormatter LOCAL_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private JdbcTemplate jdbcTemplate;
    private JdbcTemplate usersJdbcTemplate;
    private SharedConfigService sharedConfigService;
    private NotificationService notificationService;
    private NotificationRoutingService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = PostgresqlJdbcTestSupport.freshJdbcTemplate("notification_routing_panel");
        usersJdbcTemplate = PostgresqlJdbcTestSupport.freshJdbcTemplate("notification_routing_users");
        sharedConfigService = mock(SharedConfigService.class);
        notificationService = mock(NotificationService.class);
        service = new NotificationRoutingService(sharedConfigService, notificationService, jdbcTemplate, usersJdbcTemplate);
        createUsersSchema(jdbcTemplate);
        createUsersSchema(usersJdbcTemplate);
    }

    @Test
    void notifyUsesEmployeesOnlyRecipientsAcrossMainAndUsersDatabases() {
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                NotificationRoutingService.SETTINGS_KEY, Map.of(
                        "passports", Map.of(
                                "passport_saved", Map.of(
                                        "enabled", true,
                                        "audienceStrategy", "route_only",
                                        "department", "Support",
                                        "targetMode", "employees_only",
                                        "deliveryMode", "all",
                                        "employeeUsernames", List.of(" Alice ", "BOB", "alice", ""),
                                        "excludeUsernames", List.of()
                                )
                        )
                )
        ));

        insertUser(usersJdbcTemplate, "alice", true, false, "Support", null);
        insertUser(jdbcTemplate, "bob", true, false, "Support", null);
        insertUser(usersJdbcTemplate, "charlie", true, false, "Support", null);
        insertUser(jdbcTemplate, "disabled", false, false, "Support", null);
        insertUser(usersJdbcTemplate, "other-team", true, false, "Sales", null);

        service.notify("passports", "passport_saved", Set.of("ignored"), "Паспорт обновлён", "/passports/1", "bob");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> recipientsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(notificationService).notifyUsersExcluding(
                recipientsCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("bob"),
                org.mockito.ArgumentMatchers.eq("Паспорт обновлён"),
                org.mockito.ArgumentMatchers.eq("/passports/1")
        );
        assertThat(recipientsCaptor.getValue()).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void notifyAppliesOnlineOnlyRoutingForAllOperatorsUsingLocalTimestamps() {
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                NotificationRoutingService.SETTINGS_KEY, Map.of(
                        "tasks", Map.of(
                                "task_saved", Map.of(
                                        "enabled", true,
                                        "audienceStrategy", "route_only",
                                        "department", "",
                                        "targetMode", "all_operators",
                                        "deliveryMode", "online_only_fallback_all",
                                        "employeeUsernames", List.of(),
                                        "excludeUsernames", List.of()
                                )
                        )
                )
        ));
        when(notificationService.findAllOperatorRecipients()).thenReturn(Set.of("alice", "bob", "carol"));

        insertUser(usersJdbcTemplate,
                "alice",
                true,
                false,
                "Support",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(4).format(LOCAL_TS));
        insertUser(jdbcTemplate,
                "bob",
                true,
                false,
                "Support",
                LocalDateTime.now(ZoneOffset.UTC).minusHours(2).format(LOCAL_TS));
        insertUser(usersJdbcTemplate,
                "carol",
                true,
                true,
                "Support",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1).format(LOCAL_TS));

        service.notify("tasks", "task_saved", Set.of(), "Задача обновлена", "/tasks#task=1", null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> recipientsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(notificationService).notifyUsersExcluding(
                recipientsCaptor.capture(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("Задача обновлена"),
                org.mockito.ArgumentMatchers.eq("/tasks#task=1")
        );
        assertThat(recipientsCaptor.getValue()).containsExactly("alice");
    }

    private void createUsersSchema(JdbcTemplate template) {
        template.execute("""
                CREATE TABLE users (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    username TEXT NOT NULL UNIQUE,
                    password TEXT NOT NULL DEFAULT '{noop}test',
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    department TEXT,
                    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
                    last_portal_activity_at TEXT,
                    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private void insertUser(JdbcTemplate template,
                            String username,
                            boolean enabled,
                            boolean blocked,
                            String department,
                            String lastPortalActivityAt) {
        template.update(
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
