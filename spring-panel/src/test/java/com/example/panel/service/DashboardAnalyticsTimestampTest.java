package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.repository.ChannelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DashboardAnalyticsTimestampTest {
    private final DashboardAnalyticsService service = new DashboardAnalyticsService(
            mock(SharedConfigService.class), mock(ChannelRepository.class),
            new ObjectMapper(), mock(JdbcTemplate.class));

    @ParameterizedTest
    @CsvSource({
            "2026-09-28 08:58:13.354904+00, 2026-09-28 11:50:00.031156+00, 171",
            "2026-09-28 08:00:00+03, 2026-09-28 06:30:00+00, 90",
            "2026-09-28 08:00:00.123456+03:00, 2026-09-28 09:00:00.123456+03:00, 60",
            "2026-09-28 08:00:00+0330, 2026-09-28 09:00:00+0330, 60",
            "2026-09-28T08:00:00.123456Z, 2026-09-28T09:00:00.123456Z, 60",
            "2026-09-28T08:00:00+03:00, 2026-09-28T09:00:00+03:00, 60",
            "2026-09-28T08:00:00, 2026-09-28T09:00:00, 60",
            "2026-09-28 08:00:00, 2026-09-28 09:00:00, 60",
            "28.09.2026 08:00:00, 28.09.2026 09:00:00, 60",
            "2026-09-28 08:00:00.123456+00, 2026-09-28 08:00:30.123456+00, 0"
    })
    void staffAndTimeStatsReadDatabaseAndLegacyTimestamps(String created, String resolved, int minutes) {
        Map<String, Object> payload = service.buildDashboardPayload(List.of(dialog(created, resolved)), null);
        assertThat((List<?>) payload.get("staff_time_stats")).singleElement().satisfies(row -> {
            assertThat(((Map<?, ?>) row).get("name")).isEqualTo("operator");
            assertThat(((Map<?, ?>) row).get("total_minutes")).isEqualTo(minutes);
            assertThat(((Map<?, ?>) row).get("resolved_count")).isEqualTo(1);
        });
        assertThat(((Map<?, ?>) payload.get("time_stats")).get("resolved_count")).isEqualTo(1);
    }

    @Test
    void dateFilterUsesTheExistingSystemZoneForOffsetTimestamps() {
        DialogListItem dialog = dialog("2026-09-28 23:30:00.123456-03", null);
        LocalDate expected = OffsetDateTime.parse("2026-09-28T23:30:00.123456-03:00")
                .atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
        assertThat(service.dialogDate(dialog)).isEqualTo(expected);
        assertThat(service.filterDialogs(List.of(dialog),
                new DashboardAnalyticsService.DashboardFilters(expected, expected, List.of())))
                .containsExactly(dialog);
        assertThat(service.filterDialogs(List.of(dialog),
                new DashboardAnalyticsService.DashboardFilters(expected.plusDays(1), expected.plusDays(1), List.of())))
                .isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "2026-09-28 08:00:00+00, invalid",
            "invalid, 2026-09-28 09:00:00+00",
            "2026-09-28 09:00:00+00, 2026-09-28 08:00:00+00"
    })
    void invalidOrNegativeDurationStillDoesNotProduceStaffData(String created, String resolved) {
        Map<String, Object> payload = service.buildDashboardPayload(List.of(dialog(created, resolved)), null);
        assertThat((List<?>) payload.get("staff_time_stats")).isEmpty();
        assertThat(((Map<?, ?>) payload.get("time_stats")).get("resolved_count")).isEqualTo(0);
    }

    @Test
    void separateLegacyDateAndTimeStillWorkAsFallback() {
        DialogListItem dialog = dialog("invalid", "28.09.2026 09:00:00", "28.09.2026", "08:00:00");
        Map<String, Object> payload = service.buildDashboardPayload(List.of(dialog), null);
        assertThat((List<?>) payload.get("staff_time_stats")).singleElement().satisfies(row ->
                assertThat(((Map<?, ?>) row).get("total_minutes")).isEqualTo(60));
    }

    private DialogListItem dialog(String created, String resolved) {
        return dialog(created, resolved, null, null);
    }

    private DialogListItem dialog(String created, String resolved, String date, String time) {
        return new DialogListItem(null, "1", null, null, null, null, null,
                null, null, null, null, created, null, false, null, resolved,
                "operator", date, time, null, null, null, 0, null, null, null, null);
    }
}
