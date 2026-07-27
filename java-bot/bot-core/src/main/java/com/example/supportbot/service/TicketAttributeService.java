package com.example.supportbot.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TicketAttributeService {

    private final JdbcTemplate jdbcTemplate;

    public TicketAttributeService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureSchema();
    }

    public void replaceAttributes(String ticketId, List<TicketService.TicketAttributeInput> attributes) {
        if (!StringUtils.hasText(ticketId)) {
            return;
        }
        jdbcTemplate.update("DELETE FROM ticket_attributes WHERE ticket_id = ?", ticketId);
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<Object[]> batch = new ArrayList<>();
        for (TicketService.TicketAttributeInput attribute : attributes) {
            if (attribute == null || !StringUtils.hasText(attribute.questionId())) {
                continue;
            }
            String attributeKey = StringUtils.hasText(attribute.attributeKey())
                    ? attribute.attributeKey().trim()
                    : attribute.questionId().trim();
            String valueText = trimToNull(attribute.valueText());
            String valueLabel = trimToNull(attribute.valueLabel());
            String valueId = trimToNull(attribute.valueId());
            if (valueText == null && valueLabel == null && valueId == null) {
                continue;
            }
            batch.add(new Object[]{
                    ticketId,
                    attribute.questionId().trim(),
                    attributeKey,
                    trimToNull(attribute.questionText()),
                    StringUtils.hasText(attribute.inputType()) ? attribute.inputType().trim() : "custom",
                    valueId,
                    valueLabel,
                    valueText,
                    attribute.includeInDashboard(),
                    now.toString(),
                    now.toString()
            });
        }
        if (batch.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO ticket_attributes (
                    ticket_id,
                    question_id,
                    attribute_key,
                    question_text,
                    input_type,
                    value_id,
                    value_label,
                    value_text,
                    include_in_dashboard,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                batch
        );
    }

    private void ensureSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ticket_attributes (
                    ticket_id TEXT NOT NULL,
                    question_id TEXT NOT NULL,
                    attribute_key TEXT NOT NULL,
                    question_text TEXT,
                    input_type TEXT NOT NULL,
                    value_id TEXT,
                    value_label TEXT,
                    value_text TEXT,
                    include_in_dashboard BOOLEAN NOT NULL DEFAULT FALSE,
                    created_at TEXT,
                    updated_at TEXT,
                    PRIMARY KEY (ticket_id, question_id)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ticket_attributes_ticket ON ticket_attributes(ticket_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ticket_attributes_key_dashboard ON ticket_attributes(attribute_key, include_in_dashboard)");
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
