package com.example.panel.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkforceManagementUiSourceContractTest {

    @Test
    void settingsLoadsBoundedWorkforceRuntimeAndExposesDedicatedPositionsWorkspace() throws Exception {
        String settings = read("src/main/resources/templates/settings/index.html");
        String users = read("src/main/resources/templates/settings/fragments/users-access.html");
        String runtime = read("src/main/resources/static/js/workforce-management.js");

        assertThat(settings)
                .contains("<script src=\"/js/auth-management.js\"></script>")
                .contains("<script src=\"/js/workforce-management.js\"></script>");
        assertThat(settings.indexOf("/js/workforce-management.js"))
                .isGreaterThan(settings.indexOf("/js/auth-management.js"));

        assertThat(users)
                .contains("id=\"auth-positions-tab\"")
                .contains("data-bs-target=\"#settingsAuthPositions\"")
                .contains("data-workforce-positions-section")
                .contains("data-workforce-position-form")
                .contains("data-workforce-position-notify")
                .contains("Отдельно от ролей доступа")
                .contains("data-workforce-user-section")
                .contains("data-workforce-user-enabled")
                .contains("data-workforce-user-position")
                .contains("data-workforce-user-checkin-override")
                .contains("data-workforce-user-notify-override")
                .contains("data-workforce-schedule-rows")
                .contains("data-workforce-schedule-add")
                .contains("data-workforce-schedule-save")
                .contains("data-workforce-sessions-rows");

        assertThat(runtime)
                .contains("window.__iguanaWorkforceManagementBound")
                .contains("/api/workforce/state")
                .contains("/api/workforce/positions/")
                .contains("/api/workforce/users/")
                .contains("/settings")
                .contains("/schedule")
                .contains("/sessions?limit=12")
                .contains("data.notification_channels")
                .contains("notification_channel_id_override")
                .contains("notification_target_override")
                .contains("check_in_required_override")
                .contains("notify_on_check_in_override")
                .contains("check_in_open_minutes")
                .contains("late_after_minutes")
                .contains("authManagement:userModalOpen")
                .contains("meta[name=\"_csrf_header\"]")
                .contains("X-XSRF-TOKEN");
    }

    @Test
    void authRuntimePublishesStableUserModalBridgeForWorkforceEditor() throws Exception {
        String auth = read("src/main/resources/static/js/auth-management.js");

        assertThat(auth)
                .contains("this.elements.userForm.dataset.userId")
                .contains("new CustomEvent('authManagement:userModalOpen'")
                .contains("detail: { mode, userId: this.modalState.userId }")
                .contains("delete this.elements.userForm.dataset.userId");
    }

    @Test
    void workforceStateOwnsSafeNotificationChannelCatalogWithoutTokens() throws Exception {
        String controller = read("src/main/java/com/example/panel/controller/WorkforceAdminApiController.java");
        String service = read("src/main/java/com/example/panel/service/WorkforceService.java");
        String serviceTest = read("src/test/java/com/example/panel/service/WorkforceServiceTest.java");

        assertThat(controller).contains("\"notification_channels\", workforceService.listNotificationChannels()");
        assertThat(service)
                .contains("public List<Map<String, Object>> listNotificationChannels()")
                .contains("FROM channels")
                .contains("lower(platform) = 'telegram'")
                .doesNotContain("SELECT id, token, channel_name");
        assertThat(serviceTest)
                .contains("listNotificationChannelsReturnsOnlyActiveTelegramMetadata")
                .contains("doesNotContainKey(\"token\")");
    }

    @Test
    void workforceStylesExistInScssAndCompiledSettingsCss() throws Exception {
        String scss = read("src/main/resources/scss/settings/calm/_users-access.scss");
        String css = read("src/main/resources/static/css/settings.css");

        assertThat(scss)
                .contains("/* ---------- workforce management ---------- */")
                .contains(".workforce-positions-layout")
                .contains(".workforce-schedule-table");
        assertThat(css)
                .contains("/* ---------- workforce management ---------- */")
                .contains("#usersModal .workforce-positions-layout")
                .contains("#authUserDetailsModal .workforce-schedule-table");
    }

    private String read(String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }
}
