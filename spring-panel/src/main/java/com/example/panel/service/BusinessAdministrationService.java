package com.example.panel.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit business catalog and access grants. No integration is required to create a business. */
@Service
public class BusinessAdministrationService {

    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");
    private final JdbcTemplate jdbc;

    public BusinessAdministrationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Overview overview() {
        List<Business> businesses = jdbc.query("""
                SELECT id, code, display_name, status FROM businesses ORDER BY id
                """, (rs, row) -> new Business(rs.getLong("id"), rs.getString("code"),
                rs.getString("display_name"), rs.getString("status")));
        List<PanelUser> users = jdbc.query("""
                SELECT id, username FROM users ORDER BY lower(username), id
                """, (rs, row) -> new PanelUser(rs.getLong("id"), rs.getString("username")));
        List<Membership> memberships = jdbc.query("""
                SELECT membership.user_id, membership.business_id, membership.role_code
                  FROM business_memberships membership
                 WHERE membership.is_active = TRUE
                 ORDER BY membership.business_id, membership.user_id
                """, (rs, row) -> new Membership(rs.getLong("user_id"),
                rs.getLong("business_id"), rs.getString("role_code")));
        List<AllGrant> allGrants = jdbc.query("""
                SELECT user_id, role_code FROM business_access_grants
                 WHERE grant_scope = 'ALL_BUSINESSES' AND is_active = TRUE
                 ORDER BY user_id
                """, (rs, row) -> new AllGrant(rs.getLong("user_id"), rs.getString("role_code")));
        List<Location> locations = jdbc.query("""
                SELECT id, business_id, location_name, location_type, city, status
                  FROM business_locations ORDER BY business_id, id
                """, (rs, row) -> mapLocation(rs));
        return new Overview(businesses, users, memberships, allGrants, locations);
    }

    /** Admin inventory only; channel metadata does not determine a ticket's business. */
    public List<ChannelSummary> listChannels() {
        return jdbc.query("""
                SELECT id, channel_name, platform, is_active
                  FROM channels ORDER BY id
                """, (rs, row) -> new ChannelSummary(rs.getLong("id"),
                rs.getString("channel_name"), rs.getString("platform"),
                rs.getBoolean("is_active")));
    }

    /** Read-only channel-binding inventory; classification is not permission to assign a ticket owner. */
    public Optional<ChannelBindingPreview> previewChannelBinding(long channelId) {
        requirePositiveIds(channelId);
        List<ChannelBindingRow> rows = jdbc.query("""
                SELECT channel.is_active AS channel_active,
                       binding.business_id, binding.resolution_mode,
                       binding.is_active AS binding_active,
                       business.code AS business_code, business.status AS business_status
                  FROM channels channel
                  LEFT JOIN business_channel_bindings binding ON binding.channel_id = channel.id
                  LEFT JOIN businesses business ON business.id = binding.business_id
                 WHERE channel.id = ?
                 ORDER BY binding.business_id
                """, (rs, row) -> new ChannelBindingRow(
                rs.getBoolean("channel_active"),
                rs.getObject("business_id", Long.class), rs.getString("business_code"),
                rs.getString("business_status"), rs.getString("resolution_mode"),
                rs.getBoolean("binding_active")), channelId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        List<ChannelBinding> bindings = rows.stream()
                .filter(row -> row.businessId() != null)
                .map(row -> new ChannelBinding(row.businessId(), row.businessCode(),
                        row.businessStatus(), row.resolutionMode(), row.bindingActive()))
                .toList();
        List<ChannelBinding> active = bindings.stream().filter(ChannelBinding::active).toList();
        String classification;
        if (!rows.get(0).channelActive()) {
            classification = "CHANNEL_INACTIVE";
        } else if (active.isEmpty()) {
            classification = "NO_ACTIVE_BINDING";
        } else if (active.size() == 1 && "active".equals(active.get(0).businessStatus())
                && "SINGLE_BUSINESS".equals(active.get(0).resolutionMode())) {
            classification = "SINGLE_BUSINESS";
        } else if (active.stream().allMatch(binding -> "active".equals(binding.businessStatus())
                && "EXPLICIT_SELECTION".equals(binding.resolutionMode()))) {
            classification = "EXPLICIT_SELECTION";
        } else {
            classification = "NEEDS_EXPLICIT_DECISION";
        }
        return Optional.of(new ChannelBindingPreview(channelId, rows.get(0).channelActive(),
                classification, bindings));
    }

    /** Configure only modes that have an explicit S4 path; channel lock serializes competing edits. */
    @Transactional
    public boolean saveChannelBinding(long channelId, long businessId, String resolutionMode) {
        requirePositiveIds(channelId, businessId);
        if (!"SINGLE_BUSINESS".equals(resolutionMode) && !"EXPLICIT_SELECTION".equals(resolutionMode)) {
            throw new IllegalArgumentException("Unsupported channel binding mode");
        }
        if (jdbc.queryForList("SELECT id FROM channels WHERE id = ? FOR UPDATE", Long.class, channelId).isEmpty()
                || jdbc.queryForList("SELECT id FROM businesses WHERE id = ? AND status = 'active'",
                        Long.class, businessId).isEmpty()) {
            return false;
        }
        List<String> otherModes = jdbc.queryForList("""
                SELECT resolution_mode FROM business_channel_bindings
                 WHERE channel_id = ? AND business_id <> ? AND is_active = TRUE
                """, String.class, channelId, businessId);
        if (("SINGLE_BUSINESS".equals(resolutionMode) && !otherModes.isEmpty())
                || ("EXPLICIT_SELECTION".equals(resolutionMode)
                    && otherModes.stream().anyMatch(mode -> !"EXPLICIT_SELECTION".equals(mode)))) {
            throw new IllegalStateException("Channel has incompatible active business bindings");
        }
        return jdbc.update("""
                INSERT INTO business_channel_bindings(channel_id, business_id, resolution_mode, is_active)
                VALUES (?, ?, ?, TRUE)
                ON CONFLICT (channel_id, business_id) DO UPDATE
                    SET resolution_mode = EXCLUDED.resolution_mode,
                        is_active = TRUE, updated_at = CURRENT_TIMESTAMP
                """, channelId, businessId, resolutionMode) == 1;
    }

    /** Disabling a binding preserves its identity and never rewrites existing tickets. */
    public boolean deactivateChannelBinding(long channelId, long businessId) {
        requirePositiveIds(channelId, businessId);
        return jdbc.update("""
                UPDATE business_channel_bindings SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP
                 WHERE channel_id = ? AND business_id = ? AND is_active = TRUE
                """, channelId, businessId) == 1;
    }

    public Business createBusiness(String code, String displayName) {
        if (code == null || code.length() > 100 || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Invalid business code");
        }
        String name = requiredText(displayName, 200, "Invalid business name");
        return jdbc.queryForObject("""
                INSERT INTO businesses(code, display_name) VALUES (?, ?)
                RETURNING id, code, display_name, status
                """, (rs, row) -> new Business(rs.getLong("id"), rs.getString("code"),
                rs.getString("display_name"), rs.getString("status")), code, name);
    }

    /** Business code and existing links are preserved when its display name changes. */
    public Optional<Business> renameBusiness(long businessId, String displayName) {
        requirePositiveIds(businessId);
        String name = requiredText(displayName, 200, "Invalid business name");
        return jdbc.query("""
                UPDATE businesses SET display_name = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                RETURNING id, code, display_name, status
                """, (rs, row) -> new Business(rs.getLong("id"), rs.getString("code"),
                rs.getString("display_name"), rs.getString("status")), name, businessId)
                .stream().findFirst();
    }

    /** Archiving hides the business from scoped reads; restoring reuses the same identity and grants. */
    public boolean setBusinessStatus(long businessId, String status) {
        requirePositiveIds(businessId);
        if (!"active".equals(status) && !"archived".equals(status)) {
            throw new IllegalArgumentException("Invalid business status");
        }
        return jdbc.update("""
                UPDATE businesses SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, status, businessId) == 1;
    }

    public boolean grantMembership(long businessId, long userId, String roleCode) {
        requirePositiveIds(businessId, userId);
        String role = requireRole(roleCode);
        return jdbc.update("""
                INSERT INTO business_memberships(user_id, business_id, role_code, is_active)
                SELECT account.id, business.id, ?, TRUE
                  FROM users account CROSS JOIN businesses business
                 WHERE account.id = ? AND business.id = ? AND business.status = 'active'
                ON CONFLICT (user_id, business_id) DO UPDATE
                  SET role_code = EXCLUDED.role_code, is_active = TRUE, updated_at = CURRENT_TIMESTAMP
                """, role, userId, businessId) == 1;
    }

    public void revokeMembership(long businessId, long userId) {
        requirePositiveIds(businessId, userId);
        jdbc.update("""
                UPDATE business_memberships SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP
                 WHERE business_id = ? AND user_id = ? AND is_active = TRUE
                """, businessId, userId);
    }

    public boolean grantAllBusinesses(long userId, String roleCode) {
        requirePositiveIds(userId);
        String role = requireRole(roleCode);
        return jdbc.update("""
                INSERT INTO business_access_grants(user_id, grant_scope, role_code, is_active)
                SELECT id, 'ALL_BUSINESSES', ?, TRUE FROM users WHERE id = ?
                ON CONFLICT (user_id, grant_scope) DO UPDATE
                  SET role_code = EXCLUDED.role_code, is_active = TRUE, updated_at = CURRENT_TIMESTAMP
                """, role, userId) == 1;
    }

    public void revokeAllBusinesses(long userId) {
        requirePositiveIds(userId);
        jdbc.update("""
                UPDATE business_access_grants SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP
                 WHERE user_id = ? AND grant_scope = 'ALL_BUSINESSES' AND is_active = TRUE
                """, userId);
    }

    /** Creates a location inside one active business; no external mapping is implied. */
    public Optional<Location> createLocation(long businessId, String locationName,
                                             String locationType, String city) {
        requirePositiveIds(businessId);
        String name = requiredText(locationName, 200, "Invalid location name");
        String type = optionalText(locationType, 80, "Invalid location type");
        String cityName = optionalText(city, 120, "Invalid city");
        return jdbc.query("""
                INSERT INTO business_locations(
                    business_id, location_name, normalized_location_name, location_type, city)
                SELECT business.id, ?, ?, ?, ?
                  FROM businesses business
                 WHERE business.id = ? AND business.status = 'active'
                RETURNING id, business_id, location_name, location_type, city, status
                """, (rs, row) -> mapLocation(rs),
                name, name.toLowerCase(Locale.ROOT), type, cityName, businessId)
                .stream().findFirst();
    }

    /** Archive/restore preserves location identity and external mapping references. */
    public boolean setLocationStatus(long businessId, long locationId, String status) {
        requirePositiveIds(businessId, locationId);
        if (!"active".equals(status) && !"archived".equals(status)) {
            throw new IllegalArgumentException("Invalid location status");
        }
        return jdbc.update("""
                UPDATE business_locations location
                   SET status = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE location.id = ? AND location.business_id = ?
                   AND EXISTS (
                       SELECT 1 FROM businesses business
                        WHERE business.id = location.business_id
                          AND business.status = 'active')
                """, status, locationId, businessId) == 1;
    }

    private Location mapLocation(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Location(rs.getLong("id"), rs.getLong("business_id"),
                rs.getString("location_name"), rs.getString("location_type"),
                rs.getString("city"), rs.getString("status"));
    }

    private String requiredText(String value, int maxLength, String error) {
        String result = optionalText(value, maxLength, error);
        if (result.isEmpty()) {
            throw new IllegalArgumentException(error);
        }
        return result;
    }

    private String optionalText(String value, int maxLength, String error) {
        String result = value == null ? "" : value.strip();
        if (result.length() > maxLength || result.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(error);
        }
        return result;
    }

    private String requireRole(String roleCode) {
        if (roleCode == null) {
            throw new IllegalArgumentException("Invalid business role");
        }
        try {
            return BusinessAccessService.BusinessRole.valueOf(roleCode).name();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid business role", ex);
        }
    }

    private void requirePositiveIds(long... ids) {
        for (long id : ids) {
            if (id <= 0) {
                throw new IllegalArgumentException("Invalid identifier");
            }
        }
    }

    public record Business(long id, String code, String displayName, String status) {
    }

    public record PanelUser(long id, String username) {
    }

    public record Membership(long userId, long businessId, String roleCode) {
    }

    public record AllGrant(long userId, String roleCode) {
    }

    public record Location(long id, long businessId, String locationName,
                           String locationType, String city, String status) {
    }

    private record ChannelBindingRow(boolean channelActive, Long businessId,
                                     String businessCode, String businessStatus,
                                     String resolutionMode, boolean bindingActive) {
    }

    public record ChannelBinding(long businessId, String businessCode, String businessStatus,
                                 String resolutionMode, boolean active) {
    }

    public record ChannelBindingPreview(long channelId, boolean channelActive,
                                        String classification, List<ChannelBinding> bindings) {
    }

    public record ChannelSummary(long id, String channelName, String platform, boolean active) {
    }

    public record Overview(List<Business> businesses, List<PanelUser> users,
                           List<Membership> memberships, List<AllGrant> allGrants,
                           List<Location> locations) {
    }
}
