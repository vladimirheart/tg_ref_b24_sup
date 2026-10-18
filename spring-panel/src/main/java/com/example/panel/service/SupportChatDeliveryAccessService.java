package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.entity.Channel;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Statement-time owner/destination proof, not authorization of external chat members. */
@Service
public class SupportChatDeliveryAccessService {
    private static final String OWNER_AND_CHANNEL_SQL = """
        FROM tickets owner JOIN channels destination ON destination.id = owner.channel_id
        WHERE owner.user_id = ? AND owner.ticket_id = ? AND owner.ticket_public_id = ?
          AND owner.business_id IS NOT DISTINCT FROM CAST(? AS BIGINT)
          AND CASE WHEN owner.business_id IS NULL THEN 'UNRESOLVED' ELSE 'BUSINESS' END = ?
          AND owner.is_general = FALSE
          AND 1 = (SELECT COUNT(*) FROM tickets candidate WHERE candidate.ticket_id = owner.ticket_id)
          AND (owner.business_id IS NOT NULL OR ? = FALSE)
          AND (owner.business_id IS NULL OR EXISTS (
              SELECT 1 FROM businesses business WHERE business.id = owner.business_id AND business.status = 'active'
          ))
          AND destination.id = ? AND destination.is_active = TRUE
        """;
    private final JdbcTemplate jdbc;
    private final MultibusinessFeatureFlags flags;

    public SupportChatDeliveryAccessService(JdbcTemplate jdbc, MultibusinessFeatureFlags flags) {
        this.jdbc = jdbc;
        this.flags = flags;
    }

    public Optional<Target> captureTarget(UiEventTicketScope scope, Long channelId) {
        if (scope == null || !scope.isValid() || channelId == null) {
            return Optional.empty();
        }
        try {
            var targets = jdbc.query("""
                SELECT destination.id, destination.token, destination.support_chat_id, destination.platform,
                       destination.platform_config, destination.delivery_settings, destination.credential_id
                """ + OWNER_AND_CHANNEL_SQL,
                (rs, row) -> new Target(rs.getObject("id", Long.class), rs.getString("token"),
                    rs.getString("support_chat_id"), rs.getString("platform"), rs.getString("platform_config"),
                    rs.getString("delivery_settings"), rs.getObject("credential_id", Long.class)),
                scope.ticketUserId(), scope.ticketId(), scope.ticketPublicId(), scope.businessId(),
                scope.scopeKind(), flags.isStrictReadScope(), channelId);
            return targets.size() == 1 && targets.get(0).isValid() ? Optional.of(targets.get(0)) : Optional.empty();
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
    }

    /** Called after request serialization and client setup, immediately before HttpClient.send. */
    public boolean isCurrentTarget(UiEventTicketScope scope, Target target) {
        if (scope == null || !scope.isValid() || target == null || !target.isValid()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 " + OWNER_AND_CHANNEL_SQL + """
                  AND destination.token IS NOT DISTINCT FROM CAST(? AS TEXT)
                  AND destination.support_chat_id IS NOT DISTINCT FROM CAST(? AS TEXT)
                  AND destination.platform IS NOT DISTINCT FROM CAST(? AS TEXT)
                  AND destination.platform_config IS NOT DISTINCT FROM CAST(? AS TEXT)
                  AND destination.delivery_settings IS NOT DISTINCT FROM CAST(? AS TEXT)
                  AND destination.credential_id IS NOT DISTINCT FROM CAST(? AS BIGINT)
                )
                """, Boolean.class, scope.ticketUserId(), scope.ticketId(), scope.ticketPublicId(),
                scope.businessId(), scope.scopeKind(), flags.isStrictReadScope(), target.channelId(),
                target.token(), target.supportChatId(), target.platform(), target.platformConfig(),
                target.deliverySettings(), target.credentialId()));
        } catch (DataAccessException ex) {
            return false;
        }
    }

    public record Target(Long channelId, String token, String supportChatId, String platform,
                         String platformConfig, String deliverySettings, Long credentialId) {
        boolean isValid() {
            String normalized = platform == null || platform.isBlank() ? "telegram" : platform.trim().toLowerCase(Locale.ROOT);
            return channelId != null && StringUtils.hasText(token) && StringUtils.hasText(supportChatId)
                && ("telegram".equals(normalized) || "vk".equals(normalized) || "max".equals(normalized));
        }

        // Never share the mutable JPA channel or the copy exposed to network setup with request construction.
        Channel toChannel() {
            Channel channel = new Channel();
            channel.setId(channelId);
            channel.setToken(token);
            channel.setSupportChatId(supportChatId);
            channel.setPlatform(platform == null || platform.isBlank() ? "telegram" : platform);
            channel.setPlatformConfig(platformConfig);
            channel.setDeliverySettings(deliverySettings);
            channel.setCredentialId(credentialId);
            channel.setActive(true);
            return channel;
        }

        @Override
        public String toString() {
            return "SupportChatTarget[channelId=" + channelId + "]";
        }
    }
}
