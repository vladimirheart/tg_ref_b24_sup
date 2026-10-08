package com.example.panel.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkforceFoundationSourceContractTest {

    @Test
    void migrationDefinesPositionsSchedulesAndIdempotentShiftSessions() throws IOException {
        String migration = read("src/main/resources/db/migration/postgresql/V48__workforce_schedule_foundation.sql");

        assertThat(migration)
                .contains("CREATE TABLE IF NOT EXISTS workforce_positions")
                .contains("CREATE TABLE IF NOT EXISTS workforce_user_settings")
                .contains("CREATE TABLE IF NOT EXISTS workforce_schedule_rules")
                .contains("CREATE TABLE IF NOT EXISTS workforce_shift_sessions")
                .contains("shift_key VARCHAR(255) NOT NULL UNIQUE")
                .contains("check_in_status IN ('on_time', 'late', 'unscheduled')")
                .contains("notification_target IN ('support_chat', 'broadcast_channel', 'custom_chat')")
                .contains("enabled BOOLEAN NOT NULL DEFAULT FALSE");
    }

    @Test
    void loginFlowRequiresExplicitCheckInOnlyForEnabledWorkforceUsers() throws IOException {
        String redirect = read("src/main/java/com/example/panel/controller/LoginRedirectController.java");
        String checkInController = read("src/main/java/com/example/panel/controller/WorkforceCheckInController.java");
        String template = read("src/main/resources/templates/workforce/check-in.html");

        assertThat(redirect)
                .contains("workforceService.requiresCheckIn(authentication.getName())")
                .contains("return \"redirect:/shift-check-in\"");
        assertThat(checkInController)
                .contains("@GetMapping(\"/shift-check-in\")")
                .contains("@PostMapping(\"/shift-check-in\")")
                .contains("workforceService.confirmCheckIn(authentication.getName())");
        assertThat(template)
                .contains("Подтвердите начало смены")
                .contains("Подтвердить начало смены")
                .contains("th:if=\"${checkIn.unscheduled}\"");
    }

    @Test
    void serviceSeparatesSecurityRolesFromPositionsAndUsesIdempotentDelivery() throws IOException {
        String service = read("src/main/java/com/example/panel/service/WorkforceService.java");
        String transport = read("src/main/java/com/example/panel/service/ChannelTransportService.java");

        assertThat(service)
                .contains("LEFT JOIN workforce_positions p ON p.id = s.position_id")
                .contains("ON CONFLICT (shift_key) DO NOTHING")
                .contains("user override")
                .contains("notification_channel_id_override")
                .contains("scheduled:")
                .contains("unscheduled:")
                .contains("🟢 Пришёл на смену")
                .doesNotContain("role_id AS position_id");
        assertThat(transport)
                .contains("ConfiguredMessageDeliveryResult sendConfiguredMessage")
                .contains("broadcast_channel_id")
                .contains("channel.getSupportChatId()")
                .contains("unsupported_platform");
    }

    @Test
    void adminApiExposesPositionUserScheduleAndAttendanceEndpoints() throws IOException {
        String controller = read("src/main/java/com/example/panel/controller/WorkforceAdminApiController.java");

        assertThat(controller)
                .contains("@RequestMapping(\"/api/workforce\")")
                .contains("@PostMapping(\"/positions\")")
                .contains("@PutMapping(\"/users/{userId}/settings\")")
                .contains("@PutMapping(\"/users/{userId}/schedule\")")
                .contains("@GetMapping(\"/users/{userId}/sessions\")");
    }

    private String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
