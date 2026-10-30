package com.example.panel.service;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
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
    private final BusinessAdministratorSql administrator;

    public BusinessAdministrationService(JdbcTemplate jdbc) {
        this.administrator = new BusinessAdministratorSql(jdbc);
    }

    public Overview overview(Authentication authentication) {
        List<Business> businesses = administrator.read(authentication, """
                SELECT id, code, display_name, status FROM businesses ORDER BY id
                """, (rs, row) -> new Business(rs.getLong("id"), rs.getString("code"),
                rs.getString("display_name"), rs.getString("status")), Map.of());
        List<PanelUser> users = administrator.read(authentication, """
                SELECT id, username FROM users ORDER BY lower(username), id
                """, (rs, row) -> new PanelUser(rs.getLong("id"), rs.getString("username")), Map.of());
        List<Membership> memberships = administrator.read(authentication, """
                SELECT membership.user_id, membership.business_id, membership.role_code
                  FROM business_memberships membership
                 WHERE membership.is_active = TRUE
                 ORDER BY membership.business_id, membership.user_id
                """, (rs, row) -> new Membership(rs.getLong("user_id"),
                rs.getLong("business_id"), rs.getString("role_code")), Map.of());
        List<AllGrant> allGrants = administrator.read(authentication, """
                SELECT user_id, role_code FROM business_access_grants
                 WHERE grant_scope = 'ALL_BUSINESSES' AND is_active = TRUE
                 ORDER BY user_id
                """, (rs, row) -> new AllGrant(rs.getLong("user_id"), rs.getString("role_code")), Map.of());
        List<Location> locations = administrator.read(authentication, """
                SELECT id, business_id, location_name, location_type, city, status
                  FROM business_locations ORDER BY business_id, id
                """, (rs, row) -> mapLocation(rs), Map.of());
        return new Overview(businesses, users, memberships, allGrants, locations);
    }

    /** Admin inventory only; channel metadata does not determine a ticket's business. */
    public List<ChannelSummary> listChannels(Authentication authentication) {
        return administrator.read(authentication, """
                SELECT id, channel_name, platform, is_active
                  FROM channels ORDER BY id
                """, (rs, row) -> new ChannelSummary(rs.getLong("id"),
                rs.getString("channel_name"), rs.getString("platform"),
                rs.getBoolean("is_active")), Map.of());
    }

    /** Read-only channel-binding inventory; classification is not permission to assign a ticket owner. */
    public Optional<ChannelBindingPreview> previewChannelBinding(Authentication authentication, long channelId) {
        requirePositiveIds(channelId);
        List<ChannelBindingRow> rows = administrator.read(authentication, """
                SELECT channel.is_active AS channel_active,
                       binding.business_id, binding.resolution_mode,
                       binding.is_active AS binding_active,
                       business.code AS business_code, business.status AS business_status
                  FROM channels channel
                  LEFT JOIN business_channel_bindings binding ON binding.channel_id = channel.id
                  LEFT JOIN businesses business ON business.id = binding.business_id
                 WHERE channel.id = :channelId
                 ORDER BY binding.business_id
                """, (rs, row) -> new ChannelBindingRow(
                rs.getBoolean("channel_active"),
                rs.getObject("business_id", Long.class), rs.getString("business_code"),
                rs.getString("business_status"), rs.getString("resolution_mode"),
                rs.getBoolean("binding_active")), Map.of("channelId", channelId));
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
    public boolean saveChannelBinding(Authentication authentication, long channelId, long businessId, String resolutionMode) {
        requirePositiveIds(channelId, businessId);
        if (!"SINGLE_BUSINESS".equals(resolutionMode) && !"EXPLICIT_SELECTION".equals(resolutionMode)) {
            throw new IllegalArgumentException("Unsupported channel binding mode");
        }
        if (administrator.read(authentication, "SELECT id FROM channels WHERE id = :channelId FOR UPDATE", (rs, row) -> rs.getLong("id"), Map.of("channelId", channelId)).isEmpty()
                || administrator.read(authentication, "SELECT id FROM businesses WHERE id = :businessId AND status = 'active'",
                        (rs, row) -> rs.getLong("id"), Map.of("businessId", businessId)).isEmpty()) {
            return false;
        }
        List<String> otherModes = administrator.read(authentication, """
                SELECT resolution_mode FROM business_channel_bindings
                 WHERE channel_id = :channelId AND business_id <> :businessId AND is_active = TRUE
                """, (rs, row) -> rs.getString("resolution_mode"), Map.of("channelId", channelId, "businessId", businessId));
        if (("SINGLE_BUSINESS".equals(resolutionMode) && !otherModes.isEmpty())
                || ("EXPLICIT_SELECTION".equals(resolutionMode)
                    && otherModes.stream().anyMatch(mode -> !"EXPLICIT_SELECTION".equals(mode)))) {
            throw new IllegalStateException("Channel has incompatible active business bindings");
        }
        return administrator.change(authentication, """
                INSERT INTO business_channel_bindings(channel_id, business_id, resolution_mode, is_active)
                SELECT :channelId, :businessId, :resolutionMode, TRUE FROM administrator WHERE TRUE
                ON CONFLICT (channel_id, business_id) DO UPDATE
                    SET resolution_mode = EXCLUDED.resolution_mode,
                        is_active = TRUE, updated_at = CURRENT_TIMESTAMP
                """, Map.of("channelId", channelId, "businessId", businessId, "resolutionMode", resolutionMode));
    }

    /** Disabling a binding preserves its identity and never rewrites existing tickets. */
    public boolean deactivateChannelBinding(Authentication authentication, long channelId, long businessId) {
        requirePositiveIds(channelId, businessId);
        return administrator.change(authentication, """
                UPDATE business_channel_bindings SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP
                 FROM administrator WHERE channel_id = :channelId AND business_id = :businessId AND is_active = TRUE
                """, Map.of("channelId", channelId, "businessId", businessId));
    }

    public Business createBusiness(Authentication authentication, String code, String displayName) {
        if (code == null || code.length() > 100 || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Invalid business code");
        }
        String name = requiredText(displayName, 200, "Invalid business name");
        return administrator.change(authentication, """
                INSERT INTO businesses(code, display_name) SELECT :code, :name FROM administrator
                RETURNING TRUE AS __present, id, code, display_name, status
                """, (rs, row) -> new Business(rs.getLong("id"), rs.getString("code"),
                rs.getString("display_name"), rs.getString("status")), Map.of("code", code, "name", name)).get(0);
    }

    /** Business code and existing links are preserved when its display name changes. */
    public Optional<Business> renameBusiness(Authentication authentication, long businessId, String displayName) {
        requirePositiveIds(businessId);
        String name = requiredText(displayName, 200, "Invalid business name");
        return administrator.change(authentication, """
                UPDATE businesses SET display_name = :name, updated_at = CURRENT_TIMESTAMP
                 FROM administrator WHERE businesses.id = :businessId
                RETURNING TRUE AS __present, businesses.id, code, display_name, status
                """, (rs, row) -> new Business(rs.getLong("id"), rs.getString("code"),
                rs.getString("display_name"), rs.getString("status")), Map.of("name", name, "businessId", businessId))
                .stream().findFirst();
    }

    /** Archiving hides the business from scoped reads; restoring reuses the same identity and grants. */
    public boolean setBusinessStatus(Authentication authentication, long businessId, String status) {
        requirePositiveIds(businessId);
        if (!"active".equals(status) && !"archived".equals(status)) {
            throw new IllegalArgumentException("Invalid business status");
        }
        return administrator.change(authentication, """
                UPDATE businesses SET status = :status, updated_at = CURRENT_TIMESTAMP FROM administrator WHERE businesses.id = :businessId
                """, Map.of("status", status, "businessId", businessId));
    }

    public boolean grantMembership(Authentication authentication, long businessId, long userId, String roleCode) {
        requirePositiveIds(businessId, userId);
        String role = requireRole(roleCode);
        return administrator.change(authentication, """
                INSERT INTO business_memberships(user_id, business_id, role_code, is_active)
                SELECT account.id, business.id, :role, TRUE
                  FROM administrator CROSS JOIN users account CROSS JOIN businesses business
                 WHERE account.id = :userId AND business.id = :businessId AND business.status = 'active'
                ON CONFLICT (user_id, business_id) DO UPDATE
                  SET role_code = EXCLUDED.role_code, is_active = TRUE, updated_at = CURRENT_TIMESTAMP
                """, Map.of("role", role, "userId", userId, "businessId", businessId));
    }

    public void revokeMembership(Authentication authentication, long businessId, long userId) {
        requirePositiveIds(businessId, userId);
        administrator.change(authentication, """
                UPDATE business_memberships SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP
                 FROM administrator WHERE business_id = :businessId AND user_id = :userId AND is_active = TRUE
                """, Map.of("businessId", businessId, "userId", userId));
    }

    public boolean grantAllBusinesses(Authentication authentication, long userId, String roleCode) {
        requirePositiveIds(userId);
        String role = requireRole(roleCode);
        return administrator.change(authentication, """
                INSERT INTO business_access_grants(user_id, grant_scope, role_code, is_active)
                SELECT users.id, 'ALL_BUSINESSES', :role, TRUE FROM users CROSS JOIN administrator WHERE users.id = :userId
                ON CONFLICT (user_id, grant_scope) DO UPDATE
                  SET role_code = EXCLUDED.role_code, is_active = TRUE, updated_at = CURRENT_TIMESTAMP
                """, Map.of("role", role, "userId", userId));
    }

    public void revokeAllBusinesses(Authentication authentication, long userId) {
        requirePositiveIds(userId);
        administrator.change(authentication, """
                UPDATE business_access_grants SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP
                 FROM administrator WHERE user_id = :userId AND grant_scope = 'ALL_BUSINESSES' AND is_active = TRUE
                """, Map.of("userId", userId));
    }

    /** Creates a location inside one active business; no external mapping is implied. */
    public Optional<Location> createLocation(Authentication authentication, long businessId, String locationName,
                                             String locationType, String city) {
        requirePositiveIds(businessId);
        String name = requiredText(locationName, 200, "Invalid location name");
        String type = optionalText(locationType, 80, "Invalid location type");
        String cityName = optionalText(city, 120, "Invalid city");
        return administrator.change(authentication, """
                INSERT INTO business_locations(
                    business_id, location_name, normalized_location_name, location_type, city)
                SELECT business.id, :name, :normalizedName, :type, :city
                  FROM administrator CROSS JOIN businesses business
                 WHERE business.id = :businessId AND business.status = 'active'
                RETURNING TRUE AS __present, id, business_id, location_name, location_type, city, status
                """, (rs, row) -> mapLocation(rs),
                Map.of("name", name, "normalizedName", name.toLowerCase(Locale.ROOT), "type", type, "city", cityName, "businessId", businessId))
                .stream().findFirst();
    }

    /** Archive/restore preserves location identity and external mapping references. */
    public boolean setLocationStatus(Authentication authentication, long businessId, long locationId, String status) {
        requirePositiveIds(businessId, locationId);
        if (!"active".equals(status) && !"archived".equals(status)) {
            throw new IllegalArgumentException("Invalid location status");
        }
        return administrator.change(authentication, """
                UPDATE business_locations location
                   SET status = :status, updated_at = CURRENT_TIMESTAMP
                 FROM administrator WHERE location.id = :locationId AND location.business_id = :businessId
                   AND EXISTS (
                       SELECT 1 FROM businesses business
                        WHERE business.id = location.business_id
                          AND business.status = 'active')
                """, Map.of("status", status, "locationId", locationId, "businessId", businessId));
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
