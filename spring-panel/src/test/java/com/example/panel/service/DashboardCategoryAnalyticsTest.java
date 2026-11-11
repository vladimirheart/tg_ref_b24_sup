package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.repository.ChannelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardCategoryAnalyticsTest {
    private final SharedConfigService config = mock(SharedConfigService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DashboardAnalyticsService service = new DashboardAnalyticsService(
            config, mock(ChannelRepository.class), new ObjectMapper(), jdbc);

    @Test
    void labelsEveryCategoryAndCountsEachTicketOnceAtItsHighestPriority() {
        configure(Map.of("Касса", "P1", "Сеть", "P2", "Вопрос", "P5"));
        Map<String, Object> charts = charts(List.of(
                dialog("1", " Сеть, Касса, Касса ", "2026-11-01T10:00:00", "Москва"),
                dialog("2", "Вопрос", "2026-11-01T11:00:00", "Москва"),
                dialog("3", "Старая категория", "2026-11-01T12:00:00", "Москва"),
                dialog("4", null, "2026-11-01T13:00:00", "Москва")), null);

        assertThat(charts.get("category")).isEqualTo(Map.of(
                "Сеть · P2", 1L, "Касса · P1", 1L, "Вопрос · P5", 1L, "Старая категория", 1L, "Без категории", 1L));
        assertThat(charts.get("priority")).isEqualTo(Map.of(
                "P1", 1L, "P2", 0L, "P3", 0L, "P4", 0L, "P5", 1L, "Без критичности", 2L));
        assertThat(List.copyOf(((Map<?, ?>) charts.get("priority")).keySet()))
                .isEqualTo(List.of("P1", "P2", "P3", "P4", "P5", "Без критичности"));
    }

    @Test
    void appliesDashboardDateAndRestaurantFiltersToBothCharts() {
        configure(Map.of("Касса", "P1", "Вопрос", "P5"));
        var filters = new DashboardAnalyticsService.DashboardFilters(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), List.of("Москва"));
        var charts = charts(List.of(
                dialog("1", "Касса", "2026-11-01T10:00:00", "Москва"),
                dialog("2", "Вопрос", "2026-11-01T10:00:00", "Казань"),
                dialog("3", "Вопрос", "2026-10-31T10:00:00", "Москва")), filters);
        assertThat(charts.get("category")).isEqualTo(Map.of("Касса · P1", 1L));
        assertThat(charts.get("priority")).isEqualTo(Map.of("P1", 1L, "P2", 0L, "P3", 0L, "P4", 0L, "P5", 0L));
    }

    @Test
    void readsChangedPrioritiesOnTheNextDashboardRequest() {
        var dialogs = List.of(dialog("1", "Касса", "2026-11-01T10:00:00", "Москва"));
        configure(Map.of("Касса", "P1"));
        assertThat(charts(dialogs, null).get("category")).isEqualTo(Map.of("Касса · P1", 1L));
        configure(Map.of("Касса", "P4"));
        assertThat(charts(dialogs, null).get("category")).isEqualTo(Map.of("Касса · P4", 1L));
    }

    @Test
    void preservesStructuredDirectionsSeparatelyFromCategoryFlags() throws Exception {
        configure(Map.of("Касса", "P1"));
        doAnswer(invocation -> {
            Object[] params = (Object[]) invocation.getRawArguments()[2];
            if (!"direction".equals(params[0])) return null;
            ResultSet rows = mock(ResultSet.class);
            when(rows.next()).thenReturn(true, false);
            when(rows.getString("label")).thenReturn("Техническая поддержка");
            when(rows.getLong("total")).thenReturn(1L);
            return ((ResultSetExtractor<?>) invocation.getArgument(1)).extractData(rows);
        }).when(jdbc).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));

        var charts = charts(List.of(dialog("1", "Касса", "2026-11-01T10:00:00", "Москва")), null);
        assertThat(charts.get("category")).isEqualTo(Map.of("Касса · P1", 1L));
        assertThat(charts.get("direction")).isEqualTo(Map.of("Техническая поддержка", 1L));
    }

    @Test
    void emptySelectionProducesEmptyChartsAndLegacyConfigDoesNotInventPriorities() {
        assertThat(charts(List.of(), null).get("category")).isEqualTo(Map.of());
        assertThat(charts(List.of(), null).get("priority")).isEqualTo(Map.of());
        var charts = charts(List.of(dialog("1", "Касса", "2026-11-01T10:00:00", "Москва")), null);
        assertThat(charts.get("category")).isEqualTo(Map.of("Касса", 1L));
        assertThat(((Map<?, ?>) charts.get("priority")).get("Без критичности")).isEqualTo(1L);
    }

    private void configure(Map<String, String> priorities) {
        when(config.loadSettings()).thenReturn(Map.of("dialog_config", Map.of("category_templates", List.of(
                Map.of("categories", List.copyOf(priorities.keySet()), "category_priorities", priorities)))));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> charts(List<DialogListItem> dialogs, DashboardAnalyticsService.DashboardFilters filters) {
        return (Map<String, Object>) service.buildDashboardPayload(dialogs, filters).get("charts");
    }

    private DialogListItem dialog(String id, String categories, String created, String restaurant) {
        return new DialogListItem(id, id, null, null, null, null, null,
                null, null, restaurant, null, created, null, false, null, null,
                null, null, null, null, null, null, 0, null, categories, null, null);
    }
}
