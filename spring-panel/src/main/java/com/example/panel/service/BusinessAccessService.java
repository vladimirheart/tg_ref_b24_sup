package com.example.panel.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Canonical read-side resolver for the business data scope of a panel user.
 *
 * <p>This service deliberately knows nothing about page permissions. Callers
 * must first validate the required global page authority and then use this
 * service to validate the business data scope and business capability.</p>
 */
@Service
public class BusinessAccessService {

    private final JdbcTemplate jdbcTemplate;

    public BusinessAccessService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Set<Long> resolveAccessibleBusinessIds(Long userId) {
        if (userId == null || userId <= 0L) {
            return Set.of();
        }

        LinkedHashSet<Long> result = new LinkedHashSet<>(jdbcTemplate.query(
                """
                SELECT membership.business_id
                  FROM business_memberships membership
                  JOIN businesses business ON business.id = membership.business_id
                 WHERE membership.user_id = ?
                   AND membership.is_active = TRUE
                   AND business.status = 'active'
                 ORDER BY membership.business_id
                """,
                (rs, rowNum) -> rs.getLong("business_id"),
                userId
        ));

        if (hasActiveAllBusinessesGrant(userId)) {
            result.addAll(jdbcTemplate.query(
                    "SELECT id FROM businesses WHERE status = 'active' ORDER BY id",
                    (rs, rowNum) -> rs.getLong("id")
            ));
        }
        return Set.copyOf(result);
    }

    public Optional<BusinessRole> resolveEffectiveRole(Long userId, Long businessId) {
        if (userId == null || userId <= 0L || businessId == null || businessId <= 0L) {
            return Optional.empty();
        }

        List<String> roleCodes = jdbcTemplate.query(
                """
                SELECT membership.role_code
                  FROM business_memberships membership
                  JOIN businesses business ON business.id = membership.business_id
                 WHERE membership.user_id = ?
                   AND membership.business_id = ?
                   AND membership.is_active = TRUE
                   AND business.status = 'active'
                UNION ALL
                SELECT access_grant.role_code
                  FROM business_access_grants access_grant
                  JOIN businesses business ON business.id = ?
                 WHERE access_grant.user_id = ?
                   AND access_grant.grant_scope = 'ALL_BUSINESSES'
                   AND access_grant.is_active = TRUE
                   AND business.status = 'active'
                """,
                (rs, rowNum) -> rs.getString("role_code"),
                userId,
                businessId,
                businessId,
                userId
        );

        return roleCodes.stream()
                .map(BusinessRole::fromStorageValue)
                .flatMap(Optional::stream)
                .max(Comparator.comparingInt(BusinessRole::priority));
    }

    public boolean hasCapability(Long userId, Long businessId, BusinessCapability capability) {
        if (capability == null) {
            return false;
        }
        return resolveEffectiveRole(userId, businessId)
                .map(role -> role.capabilities().contains(capability))
                .orElse(false);
    }

    public void requireCapability(Long userId, Long businessId, BusinessCapability capability) {
        if (!hasCapability(userId, businessId, capability)) {
            throw new AccessDeniedException("Нет доступа к выбранному бизнесу");
        }
    }

    /**
     * Resolves a presentation context without granting any additional access.
     * A concrete context must be one of the caller's accessible businesses;
     * the all-businesses context is only their resolved union.
     */
    public SelectedBusinessScope requireSelectedBusiness(Long userId,
                                                         RequestedBusinessContext requestedContext) {
        if (requestedContext == null) {
            throw new AccessDeniedException("Business context is required");
        }

        Set<Long> accessibleBusinessIds = resolveAccessibleBusinessIds(userId);
        if (requestedContext instanceof AllAccessibleBusinesses) {
            return new SelectedBusinessScope(accessibleBusinessIds, null);
        }
        if (requestedContext instanceof OneBusiness oneBusiness
                && oneBusiness.businessId() != null
                && accessibleBusinessIds.contains(oneBusiness.businessId())) {
            return new SelectedBusinessScope(Set.of(oneBusiness.businessId()), oneBusiness.businessId());
        }
        throw new AccessDeniedException("Business context is not accessible");
    }

    private boolean hasActiveAllBusinessesGrant(Long userId) {
        Boolean granted = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                      FROM business_access_grants
                     WHERE user_id = ?
                       AND grant_scope = 'ALL_BUSINESSES'
                       AND is_active = TRUE
                )
                """,
                Boolean.class,
                userId
        );
        return Boolean.TRUE.equals(granted);
    }

    public enum BusinessCapability {
        READ,
        OPERATE,
        MANAGE,
        CONFIGURE
    }

    public sealed interface RequestedBusinessContext permits OneBusiness, AllAccessibleBusinesses {
    }

    public record OneBusiness(Long businessId) implements RequestedBusinessContext {
    }

    public enum AllAccessibleBusinesses implements RequestedBusinessContext {
        INSTANCE
    }

    public record SelectedBusinessScope(Set<Long> businessIds, Long selectedBusinessId) {

        public SelectedBusinessScope {
            businessIds = businessIds == null ? Set.of() : Set.copyOf(businessIds);
            if (selectedBusinessId != null && !businessIds.contains(selectedBusinessId)) {
                throw new IllegalArgumentException("Selected business must be inside the resolved scope");
            }
        }

        public boolean isAllBusinessesView() {
            return selectedBusinessId == null;
        }
    }

    public enum BusinessRole {
        VIEWER(10, EnumSet.of(BusinessCapability.READ)),
        OPERATOR(20, EnumSet.of(BusinessCapability.READ, BusinessCapability.OPERATE)),
        MANAGER(30, EnumSet.of(
                BusinessCapability.READ,
                BusinessCapability.OPERATE,
                BusinessCapability.MANAGE
        )),
        ADMIN(40, EnumSet.allOf(BusinessCapability.class));

        private final int priority;
        private final Set<BusinessCapability> capabilities;

        BusinessRole(int priority, Collection<BusinessCapability> capabilities) {
            this.priority = priority;
            this.capabilities = Set.copyOf(capabilities);
        }

        int priority() {
            return priority;
        }

        public Set<BusinessCapability> capabilities() {
            return capabilities;
        }

        static Optional<BusinessRole> fromStorageValue(String value) {
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(BusinessRole.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
    }
}
