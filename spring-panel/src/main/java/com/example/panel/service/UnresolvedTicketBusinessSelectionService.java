package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.service.integration.LegacyTicketIdConcurrencyGuard;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The only operator path from an unresolved ticket to a BUSINESS ticket.
 * It never derives a business from message text, channel or location data.
 */
@Service
public class UnresolvedTicketBusinessSelectionService {

    private static final String PUBLIC_ID_PATTERN = "[0-9a-f]{32}";
    private static final String OPERATE_TARGET = """
            (EXISTS (
                SELECT 1 FROM business_memberships membership
                 WHERE membership.user_id = actor.id
                   AND membership.business_id = target.id
                   AND membership.is_active = TRUE
                   AND membership.role_code IN ('OPERATOR','MANAGER','ADMIN')
            ) OR EXISTS (
                SELECT 1 FROM business_access_grants access_grant
                 WHERE access_grant.user_id = actor.id
                   AND access_grant.grant_scope = 'ALL_BUSINESSES'
                   AND access_grant.is_active = TRUE
                   AND access_grant.role_code IN ('OPERATOR','MANAGER','ADMIN')
            ))
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final MultibusinessFeatureFlags flags;
    private final UiEventOutboxAppendService outbox;
    private final LegacyTicketIdConcurrencyGuard guard;
    private final TransactionTemplate transaction;

    public UnresolvedTicketBusinessSelectionService(JdbcTemplate jdbc,
                                                    MultibusinessFeatureFlags flags,
                                                    UiEventOutboxAppendService outbox) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
        this.flags = flags;
        this.outbox = outbox;
        this.guard = new LegacyTicketIdConcurrencyGuard(jdbc);
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public List<BusinessOption> availableBusinesses(Authentication authentication) {
        PanelUserPrincipal actor = boundActor(authentication);
        if (actor == null || !enabled(actor.getUsername())) {
            return List.of();
        }
        try {
            return jdbc.query("""
                    SELECT target.id, target.display_name
                      FROM businesses target
                      JOIN users actor ON actor.id = :actorId
                                      AND actor.username = :actorName
                                     AND actor.enabled = TRUE
                                     AND actor.is_blocked = FALSE
                     WHERE target.status = 'active'
                       AND 1 = (SELECT COUNT(*) FROM users duplicate
                                  WHERE lower(duplicate.username) = lower(actor.username))
                       AND %s
                     ORDER BY lower(target.display_name), target.id
                    """.formatted(OPERATE_TARGET), actorArgs(actor),
                    (rs, rowNum) -> new BusinessOption(rs.getLong("id"), rs.getString("display_name")));
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    public Optional<Selection> select(String publicId, Long businessId, Authentication authentication) {
        PanelUserPrincipal actor = boundActor(authentication);
        if (actor == null || !enabled(actor.getUsername()) || !validPublicId(publicId)
                || businessId == null || businessId <= 0L) {
            return Optional.empty();
        }
        MapSqlParameterSource args = actorArgs(actor)
                .addValue("public", publicId)
                .addValue("business", businessId, Types.BIGINT);
        try {
            return Optional.ofNullable(transaction.execute(status -> selectInsideTransaction(args, actor)));
        } catch (DataAccessException | IllegalStateException ex) {
            return Optional.empty();
        }
    }

    private Selection selectInsideTransaction(MapSqlParameterSource args, PanelUserPrincipal account) {
        List<Root> roots = jdbc.query("""
                SELECT owner.user_id, owner.ticket_id, owner.ticket_public_id, owner.channel_id,
                       target.display_name, actor.id AS actor_id, actor.username AS actor
                  FROM tickets owner
                  JOIN businesses target ON target.id = :business AND target.status = 'active'
                  JOIN users actor ON actor.id = :actorId
                                  AND actor.username = :actorName
                                 AND actor.enabled = TRUE
                                 AND actor.is_blocked = FALSE
                 WHERE owner.ticket_public_id = :public
                   AND owner.business_id IS NULL
                   AND owner.is_general = FALSE
                   AND 1 = (SELECT COUNT(*) FROM tickets collision WHERE collision.ticket_id = owner.ticket_id)
                   AND 1 = (SELECT COUNT(*) FROM users duplicate
                              WHERE lower(duplicate.username) = lower(actor.username))
                   AND %s
                 FOR UPDATE OF owner FOR SHARE OF actor, target
                """.formatted(OPERATE_TARGET), args, (rs, rowNum) -> new Root(
                    rs.getLong("user_id"), rs.getString("ticket_id"), rs.getString("ticket_public_id"),
                    rs.getObject("channel_id", Long.class), rs.getString("display_name"),
                    rs.getLong("actor_id"), rs.getString("actor")));
        if (roots.size() != 1) {
            return null;
        }
        Root root = roots.get(0);
        guard.acquire(root.ticketId());
        args.addValue("owner", root.ownerId()).addValue("raw", root.ticketId())
                .addValue("auditActorId", root.actorId()).addValue("auditActor", root.actor());

        // Lock grants before the final statement; the final predicate repeats the
        // complete authorization condition and therefore remains race-safe.
        jdbc.query("""
                SELECT membership.user_id FROM business_memberships membership
                 WHERE membership.user_id = :actorId AND membership.business_id = :business
                   AND membership.is_active = TRUE
                 FOR SHARE
                """, args, (rs, rowNum) -> rs.getLong(1));
        jdbc.query("""
                SELECT access_grant.user_id FROM business_access_grants access_grant
                 WHERE access_grant.user_id = :actorId AND access_grant.grant_scope = 'ALL_BUSINESSES'
                   AND access_grant.is_active = TRUE
                 FOR SHARE
                """, args, (rs, rowNum) -> rs.getLong(1));
        if (!enabled(account.getUsername())) {
            return null;
        }

        int updated = jdbc.update("""
                UPDATE tickets owner
                   SET business_id = :business
                  FROM businesses target, users actor
                 WHERE owner.user_id = :owner AND owner.ticket_id = :raw
                   AND owner.ticket_public_id = :public
                   AND owner.business_id IS NULL AND owner.is_general = FALSE
                   AND target.id = :business AND target.status = 'active'
                   AND actor.id = :actorId AND actor.username = :actorName
                   AND actor.enabled = TRUE AND actor.is_blocked = FALSE
                   AND 1 = (SELECT COUNT(*) FROM tickets collision WHERE collision.ticket_id = owner.ticket_id)
                   AND 1 = (SELECT COUNT(*) FROM users duplicate
                              WHERE lower(duplicate.username) = lower(actor.username))
                   AND %s
                """.formatted(OPERATE_TARGET), args);
        if (updated != 1) {
            return null;
        }
        int audit = jdbc.update("""
                INSERT INTO business_ticket_selection_events(
                    ticket_user_id, ticket_id, ticket_public_id, business_id,
                    actor_user_id, actor, source
                ) VALUES (:owner, :raw, :public, :business, :auditActorId, :auditActor, 'OPERATOR_DIALOG')
                """, args);
        int event = jdbc.update("""
                INSERT INTO ui_event_outbox(
                    id, event_type, ticket_id, channel_id, created_at,
                    ticket_user_id, ticket_public_id, ticket_scope_kind, ticket_business_id
                ) VALUES (
                    :eventId, 'business_scope_selected', :raw, :channel, CURRENT_TIMESTAMP,
                    :owner, :public, 'BUSINESS', :business
                )
                """, args.addValue("eventId", outbox.nextEventId()).addValue("channel", root.channel(), Types.BIGINT));
        if (audit != 1 || event != 1) {
            throw new IllegalStateException("Unable to persist business selection audit");
        }
        return new Selection(root.publicId(), root.businessName());
    }

    private MapSqlParameterSource actorArgs(PanelUserPrincipal actor) {
        return new MapSqlParameterSource("actorId", actor.getUserId())
                .addValue("actorName", actor.getUsername())
                .addValue("identity", actor.getUsername().toLowerCase(Locale.ROOT));
    }

    private PanelUserPrincipal boundActor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof PanelUserPrincipal account)
                || account.getUserId() <= 0L || account.getUsername().isBlank()
                || !account.isEnabled() || !account.isAccountNonLocked()
                || !account.isAccountNonExpired() || !account.isCredentialsNonExpired()) {
            return null;
        }
        return account;
    }

    private boolean enabled(String identity) {
        return flags.isFoundationEnabled() && flags.isUiSelector() && identity != null && !identity.isBlank();
    }

    private boolean validPublicId(String publicId) {
        return publicId != null && publicId.matches(PUBLIC_ID_PATTERN);
    }

    public record BusinessOption(long id, String displayName) {
    }

    public record Selection(String ticketPublicId, String businessName) {
    }

    private record Root(long ownerId, String ticketId, String publicId, Long channel,
                        String businessName, long actorId, String actor) {
    }
}
