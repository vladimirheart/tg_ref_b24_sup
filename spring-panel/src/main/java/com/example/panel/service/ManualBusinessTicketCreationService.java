package com.example.panel.service;

import com.example.panel.service.BusinessAccessService.BusinessCapability;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates an independently owned ticket without a bot channel or external client. */
@Service
public class ManualBusinessTicketCreationService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final JdbcTemplate jdbc;
    private final BusinessAccessService access;

    public ManualBusinessTicketCreationService(JdbcTemplate jdbc, BusinessAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @Transactional
    public CreatedTicket create(long actorUserId, long businessId, String clientName,
                                String problem, Long businessLocationId) {
        if (actorUserId <= 0 || businessId <= 0) {
            throw new IllegalArgumentException("Business and operator are required");
        }
        String name = normalized(clientName, 200, "Client name", false);
        String description = normalized(problem, 10000, "Problem", true);
        access.requireCapability(actorUserId, businessId, BusinessCapability.OPERATE);
        Location location = resolveLocation(businessId, businessLocationId);

        long clientUserId = allocateClientUserId();
        // Recheck the active role and business in the write statement itself:
        // a membership may have been revoked after the initial validation.
        List<Long> createdClients = jdbc.query("""
                INSERT INTO manual_business_clients(user_id, business_id, display_name, created_by_user_id)
                SELECT ?, business.id, ?, ?
                  FROM businesses business
                 WHERE business.id = ? AND business.status = 'active'
                   AND (EXISTS (
                       SELECT 1 FROM business_memberships membership
                        WHERE membership.business_id = business.id
                          AND membership.user_id = ? AND membership.is_active = TRUE
                          AND membership.role_code IN ('OPERATOR', 'MANAGER', 'ADMIN')
                   ) OR EXISTS (
                       SELECT 1 FROM business_access_grants access_grant
                        WHERE access_grant.user_id = ?
                          AND access_grant.grant_scope = 'ALL_BUSINESSES'
                          AND access_grant.is_active = TRUE
                          AND access_grant.role_code IN ('OPERATOR', 'MANAGER', 'ADMIN')
                   ))
                RETURNING user_id
                """, (rs, row) -> rs.getLong("user_id"),
                clientUserId, name, actorUserId, businessId, actorUserId, actorUserId);
        if (createdClients.size() != 1) {
            throw new AccessDeniedException("Нет доступа к выбранному бизнесу");
        }

        String publicId = UUID.randomUUID().toString().replace("-", "");
        String ticketId = "manual-" + publicId;
        OffsetDateTime createdAt = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO messages(group_msg_id, user_id, ticket_id, client_name, problem,
                                     location_type, city, location_name, created_at,
                                     created_date, created_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, clientUserId, clientUserId, ticketId, name, description,
                location.locationType(), location.city(), location.locationName(), createdAt,
                createdAt.toLocalDate().toString(), TIME.format(createdAt));
        jdbc.update("""
                INSERT INTO tickets(user_id, ticket_id, group_msg_id, ticket_public_id,
                                    business_id, status, created_at, reopen_count,
                                    closed_count, work_time_total_sec, last_reopen_at)
                VALUES (?, ?, ?, ?, ?, 'open', ?, 0, 0, 0, ?)
                """, clientUserId, ticketId, clientUserId, publicId, businessId, createdAt, createdAt);
        jdbc.update("INSERT INTO ticket_spans(ticket_id, span_no, started_at) VALUES (?, 1, ?)",
                ticketId, createdAt);
        return new CreatedTicket(publicId, ticketId, clientUserId, businessId);
    }

    private long allocateClientUserId() {
        for (int attempt = 0; attempt < 32; attempt++) {
            Long candidate = jdbc.queryForObject("SELECT nextval('manual_client_user_id_seq')", Long.class);
            if (candidate == null || candidate >= 0) {
                throw new IllegalStateException("Manual client ID namespace exhausted");
            }
            Boolean occupied = jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM tickets WHERE user_id = ?)
                        OR EXISTS (SELECT 1 FROM messages WHERE user_id = ? OR group_msg_id = ?)
                        OR EXISTS (SELECT 1 FROM bot_users WHERE user_id = ?)
                        OR EXISTS (SELECT 1 FROM manual_business_clients WHERE user_id = ?)
                    """, Boolean.class, candidate, candidate, candidate, candidate, candidate);
            if (!Boolean.TRUE.equals(occupied)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to allocate a local client ID");
    }

    private Location resolveLocation(long businessId, Long locationId) {
        if (locationId == null) {
            return new Location(null, null, null);
        }
        if (locationId <= 0) {
            throw new IllegalArgumentException("Invalid business location");
        }
        List<Location> locations = jdbc.query("""
                SELECT location_type, city, location_name
                  FROM business_locations
                 WHERE id = ? AND business_id = ? AND status = 'active'
                """, (rs, row) -> new Location(rs.getString("location_type"),
                rs.getString("city"), rs.getString("location_name")), locationId, businessId);
        if (locations.size() != 1) {
            throw new IllegalArgumentException("Business location is not available");
        }
        return locations.get(0);
    }

    private String normalized(String value, int maxLength, String field, boolean allowLines) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String trimmed = value.strip();
        if (trimmed.length() > maxLength || trimmed.chars().anyMatch(character ->
                Character.isISOControl(character)
                        && !(allowLines && (character == '\n' || character == '\r' || character == '\t')))) {
            throw new IllegalArgumentException("Invalid " + field.toLowerCase());
        }
        return trimmed;
    }

    private record Location(String locationType, String city, String locationName) {
    }

    public record CreatedTicket(String ticketPublicId, String ticketId,
                                long clientUserId, long businessId) {
    }
}
