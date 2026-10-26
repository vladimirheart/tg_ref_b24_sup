package com.example.panel.service;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Existing service + real Hibernate/Spring Data transactions; isolated PostgreSQL only. */
class IncidentMultilinePostgresqlTest {
    @Test void descriptionAndCommentsRoundTripWithParagraphsIndentationXmlAndWindowsPath() {
        try (var f = new IncidentPriorityPostgresqlTest.Fixture()) {
            // This Hibernate-only fixture defaults Strings to varchar(255). Match canonical V22 TEXT
            // for long description/event/payload fields, without changing any production schema.
            f.jdbc.execute("ALTER TABLE incidents ALTER COLUMN description TYPE TEXT");
            f.jdbc.execute("ALTER TABLE incident_events ALTER COLUMN event_text TYPE TEXT");
            f.jdbc.execute("ALTER TABLE incident_events ALTER COLUMN payload_json TYPE TEXT");
            long id = f.signal("multiline-r20", "low");
            for (String newline : List.of("\n", "\r\n")) {
                String text = String.join(newline,
                    "0. скачать актуальную версию", "https://example.test/Setup.iikoCard5.POS.exe", "",
                    "1. удалить старую версию.", " если запросит, то ребутнуться.", "",
                    "4. в файле C:\\Program Files\\iiko\\iikoCard5POS\\iikoCard5.Pos.Host.exe.config",
                    "<add key=\"ForceHttpSync\" value=\"true\" />", "<!--Force HTTP regardless of the server UseHttpSync setting, without RabbitMQ fallback.-->", "",
                    "5. запустить службу.");
                f.service().updateIncident(id, Map.of("description", text), "operator");
                f.service().addIncidentEvent(id, Map.of("event_type", "comment", "event_text", text), "operator");
                assertThat(f.jdbc.queryForObject("SELECT description FROM incidents WHERE id=?", String.class, id)).isEqualTo(text);
                assertThat(f.jdbc.queryForObject("SELECT event_text FROM incident_events WHERE incident_id=? AND event_type='comment' ORDER BY id DESC LIMIT 1", String.class, id)).isEqualTo(text);
                Map<?, ?> reloaded = f.details(id); // A fresh service instance reads the persisted values.
                assertThat(reloaded.get("description")).isEqualTo(text);
                assertThat(((List<?>) reloaded.get("events")).stream().map(value -> (Map<?, ?>) value)
                        .filter(event -> "comment".equals(event.get("event_type"))).map(event -> (String) event.get("event_text")))
                        .contains(text);
            }
        }
    }
}
