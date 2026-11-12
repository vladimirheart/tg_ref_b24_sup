package com.example.panel.service;

import com.example.panel.model.dialog.DialogListQuery;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DialogTriageInitialQueryTest {
    private final DialogTriagePreferenceService service = new DialogTriagePreferenceService(null);

    @Test
    void restoresPersistedServerFiltersWithBoundedPage() {
        var query = service.initialListQuery(Map.of("view", " ACTIVE ", "page_size", "50",
                "sort_mode", "sla_priority", "sla_window_minutes", 30));
        assertThat(query).isEqualTo(new DialogListQuery(1, 50, "", "", "active", 30, "sla_priority", java.util.List.of()));
    }

    @Test
    void legacyAndInvalidValuesNeverEnableUnboundedInitialRendering() {
        assertThat(service.initialListQuery(null)).isEqualTo(DialogListQuery.defaults());
        for (String pageSize : new String[] {"all", "", "-1", "broken", "99999999999999999"}) {
            assertThat(service.initialListQuery(Map.of("page_size", pageSize)).pageSize()).isEqualTo(20);
        }
        assertThat(service.initialListQuery(Map.of("page_size", "10000")).pageSize()).isEqualTo(100);
        assertThat(service.initialListQuery(Map.of("view", "invalid", "sort_mode", "invalid", "sla_window_minutes", 999)))
                .isEqualTo(DialogListQuery.defaults());
    }
}
