package com.example.panel.storage;

import com.example.panel.service.LegacyTicketIdJdbcGuard;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Read-only attachment identity guard. A storage namespace is not a ticket ID.
 * Metadata/history must agree on one exact unique legacy owner. Without metadata,
 * only an unambiguous legacy ticket-directory layout is accepted.
 */
@Component
public class TicketAttachmentOwnerJdbcGuard {
    private final JdbcTemplate jdbcTemplate;
    private final LegacyTicketIdJdbcGuard ticketGuard;

    public TicketAttachmentOwnerJdbcGuard(JdbcTemplate jdbcTemplate, LegacyTicketIdJdbcGuard ticketGuard) {
        this.jdbcTemplate = jdbcTemplate;
        this.ticketGuard = ticketGuard;
    }

    public String resolveUniqueOwner(String rawStorageKey) {
        String storageKey = normalizeStorageKey(rawStorageKey);
        if (storageKey == null || storageKey.indexOf('/') < 1) {
            return null;
        }
        try {
            List<MetadataOwner> metadata = jdbcTemplate.query("""
                    SELECT m.ticket_id, h.ticket_id AS history_ticket_id,
                           BOOL_OR(m.deleted_at IS NOT NULL
                               OR m.storage_class IS DISTINCT FROM 'dialog_attachment'
                               OR m.normalization_status IS DISTINCT FROM 'normalized'
                               OR m.storage_provider IS NULL
                               OR m.storage_provider NOT IN ('local_fs', 's3')) AS invalid
                      FROM chat_attachment_metadata m
                      LEFT JOIN chat_history h ON h.id = m.chat_history_id
                     WHERE m.storage_key = ?
                     GROUP BY m.ticket_id, h.ticket_id
                     LIMIT 2
                    """, (rs, rowNum) -> new MetadataOwner(rs.getString("ticket_id"),
                            rs.getString("history_ticket_id"), rs.getBoolean("invalid")), storageKey);
            if (!metadata.isEmpty()) {
                if (metadata.size() != 1) return null;
                MetadataOwner owner = metadata.get(0);
                if (owner.invalid() || !StringUtils.hasText(owner.ticketId())
                        || !owner.ticketId().equals(owner.historyTicketId())) {
                    return null;
                }
                return ticketGuard.hasUniqueTicket(owner.ticketId()) ? owner.ticketId() : null;
            }

            List<String> prefixes = new ArrayList<>();
            for (int separator = storageKey.indexOf('/'); separator >= 0;
                    separator = storageKey.indexOf('/', separator + 1)) {
                prefixes.add(storageKey.substring(0, separator));
            }
            String placeholders = String.join(",", Collections.nCopies(prefixes.size(), "?"));
            List<String> candidates = jdbcTemplate.query(
                    "SELECT ticket_id FROM tickets WHERE ticket_id IN (" + placeholders + ") LIMIT 2",
                    (rs, rowNum) -> rs.getString("ticket_id"), prefixes.toArray());
            if (candidates.size() != 1 || !prefixes.get(0).equals(candidates.get(0))) {
                return null;
            }
            String owner = candidates.get(0);
            return ticketGuard.hasUniqueTicket(owner) ? owner : null;
        } catch (DataAccessException ex) {
            // A missing schema or failed lookup is not permission to guess an owner.
            return null;
        }
    }

    public boolean hasDirectOwner(String ticketId, String filename) {
        String directory = normalizeStorageKey(ticketId);
        String storedName = normalizeStorageKey(filename);
        if (directory == null || storedName == null) return false;
        return ticketId.equals(resolveUniqueOwner(directory + "/" + storedName));
    }

    private String normalizeStorageKey(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String key = raw.trim().replace('\\', '/');
        while (key.startsWith("/")) key = key.substring(1);
        if (key.length() > 4096 || key.chars().anyMatch(Character::isISOControl)) return null;
        String[] parts = key.split("/", -1);
        if (parts.length > 64) return null;
        for (String part : parts) {
            if (part.isBlank() || ".".equals(part) || "..".equals(part)
                    || !part.equals(part.trim()) || part.endsWith(".")) {
                return null;
            }
        }
        return key;
    }

    private record MetadataOwner(String ticketId, String historyTicketId, boolean invalid) { }
}
