package com.example.panel.service;

import com.example.panel.model.dialog.DialogOperatorOption;
import com.example.panel.model.dialog.DialogParticipantDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DialogParticipantService {

    private static final Logger log = LoggerFactory.getLogger(DialogParticipantService.class);
    private static final String AI_AGENT_USERNAME = "ai_agent";

    private final JdbcTemplate jdbcTemplate;
    private final JdbcTemplate usersJdbcTemplate;

    public DialogParticipantService(JdbcTemplate jdbcTemplate,
                                    @Qualifier("usersJdbcTemplate") JdbcTemplate usersJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.usersJdbcTemplate = usersJdbcTemplate;
        ensureSchema();
    }

    public boolean ticketExists(String ticketId) {
        String normalizedTicketId = trimToNull(ticketId);
        if (normalizedTicketId == null) {
            return false;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tickets WHERE ticket_id = ?",
                    Integer.class,
                    normalizedTicketId
            );
            return count != null && count > 0;
        } catch (DataAccessException ex) {
            log.warn("Unable to check ticket existence for {}: {}", normalizedTicketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
            return false;
        }
    }

    public List<DialogParticipantDto> loadParticipants(String ticketId) {
        String normalizedTicketId = trimToNull(ticketId);
        if (normalizedTicketId == null) {
            return List.of();
        }
        try {
            List<StoredParticipant> storedParticipants = jdbcTemplate.query(
                    """
                    SELECT username, added_at, added_by
                      FROM ticket_participants
                     WHERE ticket_id = ?
                     ORDER BY COALESCE(added_at, '') ASC, lower(username) ASC
                    """,
                    (rs, rowNum) -> new StoredParticipant(
                            rs.getString("username"),
                            rs.getString("added_at"),
                            rs.getString("added_by")
                    ),
                    normalizedTicketId
            );
            if (storedParticipants.isEmpty()) {
                return List.of();
            }
            Set<String> usernames = storedParticipants.stream()
                    .map(StoredParticipant::username)
                    .map(this::normalizeIdentity)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Map<String, DialogOperatorOption> directory = loadOperatorDirectory(usernames);
            List<DialogParticipantDto> participants = new ArrayList<>();
            for (StoredParticipant storedParticipant : storedParticipants) {
                String username = normalizeIdentity(storedParticipant.username());
                if (!StringUtils.hasText(username)) {
                    continue;
                }
                DialogOperatorOption profile = directory.get(username);
                participants.add(new DialogParticipantDto(
                        profile != null ? profile.username() : storedParticipant.username(),
                        profile != null ? profile.displayLabel() : storedParticipant.username(),
                        profile != null ? profile.avatarUrl() : null,
                        profile != null ? profile.department() : null,
                        profile != null ? profile.role() : null,
                        storedParticipant.addedAt(),
                        storedParticipant.addedBy()
                ));
            }
            return participants;
        } catch (DataAccessException ex) {
            log.warn("Unable to load ticket participants for {}: {}", normalizedTicketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
            return List.of();
        }
    }

    public List<DialogOperatorOption> loadAssignableOperators() {
        return queryOperators(Set.of());
    }

    public Optional<DialogOperatorOption> findOperator(String username) {
        String normalizedUsername = normalizeIdentity(username);
        if (normalizedUsername == null) {
            return Optional.empty();
        }
        return queryOperators(Set.of(normalizedUsername)).stream().findFirst();
    }

    public boolean addParticipant(String ticketId, String username, String addedBy) {
        String normalizedTicketId = trimToNull(ticketId);
        String normalizedUsername = normalizeIdentity(username);
        String actor = normalizeIdentity(addedBy);
        if (normalizedTicketId == null || normalizedUsername == null) {
            return false;
        }
        try {
            return jdbcTemplate.update(
                    """
                    INSERT OR IGNORE INTO ticket_participants(ticket_id, username, added_at, added_by)
                    VALUES(?, ?, CURRENT_TIMESTAMP, ?)
                    """,
                    normalizedTicketId,
                    normalizedUsername,
                    actor
            ) > 0;
        } catch (DataAccessException ex) {
            log.warn("Unable to add participant {} to ticket {}: {}", normalizedUsername, normalizedTicketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
            return false;
        }
    }

    public boolean removeParticipant(String ticketId, String username) {
        String normalizedTicketId = trimToNull(ticketId);
        String normalizedUsername = normalizeIdentity(username);
        if (normalizedTicketId == null || normalizedUsername == null) {
            return false;
        }
        try {
            return jdbcTemplate.update(
                    "DELETE FROM ticket_participants WHERE ticket_id = ? AND username = ?",
                    normalizedTicketId,
                    normalizedUsername
            ) > 0;
        } catch (DataAccessException ex) {
            log.warn("Unable to remove participant {} from ticket {}: {}", normalizedUsername, normalizedTicketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
            return false;
        }
    }

    private Map<String, DialogOperatorOption> loadOperatorDirectory(Set<String> usernames) {
        return queryOperators(usernames).stream().collect(Collectors.toMap(
                option -> normalizeIdentity(option.username()),
                option -> option,
                (left, right) -> left,
                LinkedHashMap::new
        ));
    }

    private List<DialogOperatorOption> queryOperators(Set<String> usernames) {
        Set<String> userColumns = loadUserColumns();
        if (userColumns.isEmpty() || !userColumns.contains("username")) {
            return List.of();
        }

        boolean hasRoleId = userColumns.contains("role_id");
        String fullNameExpr = selectColumn(userColumns, "u", "full_name");
        String photoExpr = selectColumn(userColumns, "u", "photo");
        String departmentExpr = selectColumn(userColumns, "u", "department");
        String roleExpr = hasRoleId
                ? "COALESCE(r.name, " + selectColumn(userColumns, "u", "role") + ")"
                : selectColumn(userColumns, "u", "role");
        String orderNameExpr = userColumns.contains("full_name")
                ? "COALESCE(u.full_name, u.username)"
                : "u.username";

        StringBuilder sql = new StringBuilder("""
                SELECT u.username AS username,
                       %s AS full_name,
                       %s AS photo,
                       %s AS department,
                       %s AS role_name
                  FROM users u
                """.formatted(fullNameExpr, photoExpr, departmentExpr, roleExpr));
        if (hasRoleId) {
            sql.append(" LEFT JOIN roles r ON r.id = u.role_id ");
        }
        sql.append(" WHERE 1 = 1 ");
        if (userColumns.contains("enabled")) {
            sql.append(" AND COALESCE(u.enabled, 1) = 1 ");
        }
        if (userColumns.contains("is_blocked")) {
            sql.append(" AND COALESCE(u.is_blocked, 0) = 0 ");
        }

        List<Object> params = new ArrayList<>();
        Set<String> normalizedUsernames = usernames.stream()
                .map(this::normalizeIdentity)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!normalizedUsernames.isEmpty()) {
            sql.append(" AND lower(u.username) IN (");
            sql.append(String.join(", ", normalizedUsernames.stream().map(value -> "?").toList()));
            sql.append(") ");
            params.addAll(normalizedUsernames);
        }

        sql.append(" ORDER BY lower(").append(orderNameExpr).append("), lower(u.username)");

        try {
            return usersJdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
                String username = trimToNull(rs.getString("username"));
                if (username == null) {
                    return null;
                }
                String normalizedUsername = normalizeIdentity(username);
                if (AI_AGENT_USERNAME.equalsIgnoreCase(normalizedUsername)) {
                    return null;
                }
                return new DialogOperatorOption(
                        username,
                        trimToNull(rs.getString("full_name")),
                        trimToNull(rs.getString("photo")),
                        trimToNull(rs.getString("department")),
                        trimToNull(rs.getString("role_name"))
                );
            }, params.toArray()).stream().filter(item -> item != null).toList();
        } catch (DataAccessException ex) {
            log.warn("Unable to load assignable operators: {}", DialogDataAccessSupport.summarizeDataAccessException(ex));
            return List.of();
        }
    }

    private Set<String> loadUserColumns() {
        try {
            return usersJdbcTemplate.query(
                    "PRAGMA table_info(users)",
                    (rs, rowNum) -> rs.getString("name")
            ).stream().collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (DataAccessException ex) {
            log.warn("Unable to inspect users schema: {}", DialogDataAccessSupport.summarizeDataAccessException(ex));
            return Set.of();
        }
    }

    private void ensureSchema() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ticket_participants (
                        ticket_id TEXT NOT NULL,
                        username TEXT NOT NULL,
                        added_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        added_by TEXT,
                        PRIMARY KEY (ticket_id, username)
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_ticket_participants_username
                        ON ticket_participants(username)
                    """);
        } catch (DataAccessException ex) {
            log.warn("Unable to ensure ticket_participants schema: {}", DialogDataAccessSupport.summarizeDataAccessException(ex));
        }
    }

    private String selectColumn(Set<String> columns, String alias, String column) {
        return columns.contains(column) ? alias + "." + column : "NULL";
    }

    private String normalizeIdentity(String value) {
        String trimmed = trimToNull(value);
        return trimmed != null ? trimmed.toLowerCase(Locale.ROOT) : null;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record StoredParticipant(String username, String addedAt, String addedBy) {
    }
}
