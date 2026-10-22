package com.example.panel.service;

import com.example.panel.entity.Notification;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.model.notification.NotificationDto;
import com.example.panel.model.notification.NotificationPage;
import com.example.panel.model.notification.NotificationSummary;
import com.example.panel.repository.NotificationRepository;
import com.example.panel.repository.NotificationBusinessAccessSql;
import com.example.panel.repository.ScopedAiSql;
import com.example.panel.support.JdbcSchemaInspector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class NotificationService {

    private static final String EXPECTED_OWNER_SQL = """
        owner.user_id = ? AND owner.ticket_id = ? AND owner.ticket_public_id = ?
        AND owner.business_id IS NOT DISTINCT FROM CAST(? AS BIGINT)
        AND CASE WHEN owner.business_id IS NULL THEN 'UNRESOLVED' ELSE 'BUSINESS' END = ?
        AND owner.is_general = FALSE
        AND (SELECT COUNT(*) FROM tickets candidate WHERE candidate.ticket_id = owner.ticket_id) = 1
        AND (owner.business_id IS NOT NULL OR ? = TRUE)
        """;

    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    private static final Pattern CP1251_UTF8_MOJIBAKE = Pattern.compile("(?:Р.|С.){2,}");
    private static final Pattern LATIN1_UTF8_MOJIBAKE = Pattern.compile("(?:Ð.|Ñ.){2,}");
    private static final Pattern ASCII_P_MOJIBAKE = Pattern.compile("(?:P[A-Za-z%0-9]{1,3}){4,}");
    private static final Map<String, String> LEGACY_NOTIFICATION_REPLACEMENTS = Map.ofEntries(
            Map.entry("РќРѕРІРѕРµ РѕР±СЂР°С‰РµРЅРёРµ", "Новое обращение"),
            Map.entry("РќРѕРІРѕРµ СЃРѕРѕР±С‰РµРЅРёРµ РІ РѕР±СЂР°С‰РµРЅРёРё", "Новое сообщение в обращении"),
            Map.entry("РќРѕРІР°СЏ РѕС†РµРЅРєР° РїРѕ РѕР±СЂР°С‰РµРЅРёСЋ", "Новая оценка по обращению"),
            Map.entry("Р”РёР°Р»РѕРі", "Диалог"),
            Map.entry("РџРµСЂРІР°СЏ СЂРµР°РєС†РёСЏ РїСЂРѕСЃСЂРѕС‡РµРЅР°", "Первая реакция просрочена"),
            Map.entry("РџСЂРѕСЃСЂРѕС‡РєР°", "Просрочка"),
            Map.entry("РљР°РЅР°Р»", "Канал"),
            Map.entry("Р±РµР· С‚РµРєСЃС‚Р°", "без текста"),
            Map.entry("AI-Р°РіРµРЅС‚ СЌСЃРєР°Р»РёСЂРѕРІР°Р» РѕР±СЂР°С‰РµРЅРёРµ", "AI-агент эскалировал обращение"),
            Map.entry("Р’РѕРїСЂРѕСЃ РєР»РёРµРЅС‚Р°", "Вопрос клиента"),
            Map.entry("РћР±СЂР°С‰РµРЅРёРµ", "Обращение"),
            Map.entry("РІР·СЏС‚Рѕ РІ СЂР°Р±РѕС‚Сѓ РѕРїРµСЂР°С‚РѕСЂРѕРј", "взято в работу оператором"),
            Map.entry("Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРё Р·Р°РєСЂС‹С‚ РёР·-Р·Р° РѕС‚СЃСѓС‚СЃС‚РІРёСЏ Р°РєС‚РёРІРЅРѕСЃС‚Рё", "автоматически закрыт из-за отсутствия активности"),
            Map.entry("РјРёРЅ.", "мин.")
    );

    private final NotificationRepository notificationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final JdbcTemplate usersJdbcTemplate;
    private final UiEventStreamService uiEventStreamService;
    private final LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard;

    @Autowired
    private DialogLookupReadService dialogLookupReadService;

    @Autowired
    private DialogTaskDisplayService dialogTaskDisplayService;

    @Autowired
    private MultibusinessFeatureFlags multibusinessFeatureFlags;

    private boolean allowLegacyNotifications() {
        return multibusinessFeatureFlags == null || !multibusinessFeatureFlags.isStrictReadScope();
    }

    public NotificationService(NotificationRepository notificationRepository,
                               JdbcTemplate jdbcTemplate,
                               @Qualifier("usersJdbcTemplate") JdbcTemplate usersJdbcTemplate,
                               UiEventStreamService uiEventStreamService,
                               LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard) {
        this.notificationRepository = notificationRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.usersJdbcTemplate = usersJdbcTemplate;
        this.uiEventStreamService = uiEventStreamService;
        this.legacyTicketIdJdbcGuard = legacyTicketIdJdbcGuard;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> findForUser(String userIdentity) {
        String identity = normalizeIdentity(userIdentity);
        return notificationRepository.findVisibleForUser(identity, allowLegacyNotifications()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationPage findPageForUser(String userIdentity, String cursor, int requestedLimit) {
        String identity = normalizeIdentity(userIdentity);
        int limit = normalizeNotificationPageLimit(requestedLimit);
        NotificationCursor decodedCursor = decodeNotificationCursor(cursor);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, limit + 1);
        List<Notification> rows = decodedCursor == null
                ? notificationRepository.findVisiblePage(identity, allowLegacyNotifications(), pageable)
                : notificationRepository.findPageBefore(
                        identity,
                        allowLegacyNotifications(),
                        decodedCursor.createdAt(),
                        decodedCursor.id(),
                        pageable
                );
        boolean hasMore = rows.size() > limit;
        List<Notification> visibleRows = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore && !visibleRows.isEmpty()
                ? encodeNotificationCursor(visibleRows.get(visibleRows.size() - 1))
                : null;
        return new NotificationPage(visibleRows.stream().map(this::toDto).toList(), nextCursor, hasMore);
    }

    private int normalizeNotificationPageLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return 20;
        }
        return Math.min(requestedLimit, 20);
    }

    private String encodeNotificationCursor(Notification notification) {
        if (notification == null || notification.getCreatedAt() == null || notification.getId() == null) {
            throw new IllegalStateException("Notification cursor requires createdAt and id");
        }
        String raw = notification.getCreatedAt() + "|" + notification.getId();
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private NotificationCursor decodeNotificationCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }
        try {
            String raw = new String(
                    java.util.Base64.getUrlDecoder().decode(cursor.trim()),
                    StandardCharsets.UTF_8
            );
            int separator = raw.lastIndexOf('|');
            if (separator <= 0 || separator >= raw.length() - 1) {
                throw new IllegalArgumentException("Malformed notification cursor");
            }
            OffsetDateTime createdAt = OffsetDateTime.parse(raw.substring(0, separator));
            Long id = Long.valueOf(raw.substring(separator + 1));
            return new NotificationCursor(createdAt, id);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid notification cursor", ex);
        }
    }

    private record NotificationCursor(OffsetDateTime createdAt, Long id) {
    }

    @Transactional(readOnly = true)
    public NotificationSummary summary(String userIdentity) {
        String identity = normalizeIdentity(userIdentity);
        long unread = notificationRepository.countVisibleUnread(identity, allowLegacyNotifications());
        return new NotificationSummary(unread);
    }

    public void markAsRead(String userIdentity, Long id) {
        String identity = normalizeIdentity(userIdentity);
        if (notificationRepository.markVisibleAsRead(id, identity, allowLegacyNotifications()) > 0) {
            uiEventStreamService.publishNotificationsChanged(identity, "notification_marked_read");
        }
    }

    public long markAllAsRead(String userIdentity) {
        String identity = normalizeIdentity(userIdentity);
        int updated = notificationRepository.markAllVisibleAsRead(identity, allowLegacyNotifications());
        if (updated == 0) {
            return 0;
        }
        uiEventStreamService.publishNotificationsChanged(identity, "notifications_marked_read");
        return updated;
    }

    public void notifyUser(String userIdentity, String text, String url) {
        String identity = normalizeRecipient(userIdentity);
        if (!StringUtils.hasText(identity) || !StringUtils.hasText(text)) {
            return;
        }
        String safeUrl = normalizeUrl(url);
        saveNotification(identity, normalizeNotificationText(text.trim(), safeUrl), safeUrl);
    }

    public void notifyUsers(Set<String> userIdentities, String text, String url) {
        notifyUsersExcluding(userIdentities, null, text, url);
    }

    public void notifyUsersExcluding(Set<String> userIdentities, String excludedIdentity, String text, String url) {
        if (!StringUtils.hasText(text) || userIdentities == null || userIdentities.isEmpty()) {
            return;
        }
        String excluded = normalizeRecipient(excludedIdentity);
        String safeUrl = normalizeUrl(url);
        String safeText = normalizeNotificationText(text.trim(), safeUrl);
        Set<String> recipients = normalizeRecipients(userIdentities);
        for (String identity : recipients) {
            if (StringUtils.hasText(excluded) && excluded.equals(identity)) {
                continue;
            }
            saveNotification(identity, safeText, safeUrl);
        }
    }

    public String formatNewAppealText(String ticketId) {
        return formatDialogText(ticketId, "Новое обращение №", "Новое обращение");
    }

    public String formatIncomingClientMessageText(String ticketId) {
        return formatDialogText(ticketId, "Новое сообщение в обращении №", "Новое сообщение в обращении");
    }

    public String formatAutoCloseText(String ticketId) {
        String requestNumber = resolveDialogRequestNumber(ticketId);
        return StringUtils.hasText(requestNumber)
                ? "Обращение №" + requestNumber + " автоматически закрыто из-за отсутствия активности."
                : "Обращение автоматически закрыто из-за отсутствия активности.";
    }

    private String formatDialogText(String ticketId, String numberedPrefix, String fallback) {
        String requestNumber = resolveDialogRequestNumber(ticketId);
        return StringUtils.hasText(requestNumber) ? numberedPrefix + requestNumber : fallback;
    }

    private String resolveDialogRequestNumber(String ticketId) {
        if (!StringUtils.hasText(ticketId) || dialogLookupReadService == null) {
            return null;
        }
        try {
            String requestNumber = dialogLookupReadService.resolveRequestNumber(ticketId);
            return StringUtils.hasText(requestNumber) ? requestNumber.trim() : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String normalizeNotificationText(String text, String url) {
        String current = normalizeNotificationText(text);
        if (dialogTaskDisplayService != null) {
            current = dialogTaskDisplayService.normalizeNotificationText(current);
        }
        if (StringUtils.hasText(current) && current.contains("№")) {
            return current;
        }
        String ticketId = extractNotificationDialogTicketId(url);
        if (!StringUtils.hasText(current) || !StringUtils.hasText(ticketId)) {
            return current;
        }
        String normalized = current.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("новое обращение")) {
            return formatNewAppealText(ticketId);
        }
        if (normalized.startsWith("новое сообщение в обращении")) {
            return formatIncomingClientMessageText(ticketId);
        }
        if (normalized.contains("автоматически закрыт")
                && normalized.contains("отсутств")
                && normalized.contains("активнос")) {
            return formatAutoCloseText(ticketId);
        }
        return current;
    }

    private String extractNotificationDialogTicketId(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        String value = url.trim();
        String queryTicketId = extractDialogTicketId(value);
        if (queryTicketId != null) {
            return queryTicketId;
        }
        int marker = value.indexOf("/dialogs/");
        if (marker < 0) {
            return null;
        }
        String candidate = value.substring(marker + "/dialogs/".length());
        int end = candidate.length();
        for (char delimiter : new char[]{'?', '#', '/'}) {
            int index = candidate.indexOf(delimiter);
            if (index >= 0 && index < end) {
                end = index;
            }
        }
        candidate = candidate.substring(0, end);
        if (candidate.isEmpty()) {
            return null;
        }
        try {
            return URLDecoder.decode(candidate, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return candidate;
        }
    }

    @Transactional(readOnly = true)
    public Set<String> findDialogRecipients(String ticketId) {
        if (!legacyTicketIdJdbcGuard.hasUniqueNonGeneralTicket(ticketId)) {
            return Set.of();
        }
        Set<String> recipients = new LinkedHashSet<>();
        String normalizedTicketId = ticketId;
        collectDialogRecipients(
                "SELECT responsible FROM ticket_responsibles WHERE ticket_id = ?",
                "responsible",
                normalizedTicketId,
                recipients
        );
        collectDialogRecipients(
                "SELECT user_identity FROM ticket_active WHERE ticket_id = ?",
                "user_identity",
                normalizedTicketId,
                recipients
        );
        collectDialogRecipients(
                "SELECT username FROM ticket_participants WHERE ticket_id = ?",
                "username",
                normalizedTicketId,
                recipients
        );
        return legacyTicketIdJdbcGuard.hasUniqueNonGeneralTicket(ticketId) ? recipients : Set.of();
    }

    public void notifyDialogParticipants(String ticketId, String text, String url, String excludedIdentity) {
        if (!legacyTicketIdJdbcGuard.hasUniqueNonGeneralTicket(ticketId)) {
            return;
        }
        Set<String> recipients = findDialogRecipients(ticketId);
        if (!legacyTicketIdJdbcGuard.hasUniqueNonGeneralTicket(ticketId)) {
            return;
        }
        if (recipients.isEmpty()) {
            recipients = findAllOperatorRecipients();
        }
        notifyDialogUsersExcluding(ticketId, recipients, excludedIdentity, text, url);
    }

    @Transactional(readOnly = true)
    public Set<String> findScopedDialogRecipients(UiEventTicketScope scope) {
        if (!isCurrentNotificationScope(scope)) {
            return Set.of();
        }
        Set<String> recipients = scopedAssignedAndActiveRecipients(scope);
        if ("BUSINESS".equals(scope.scopeKind())) {
            collectBusinessParticipants(scope, recipients);
        } else {
            collectScopedDialogRecipients("SELECT username FROM ticket_participants WHERE ticket_id = ?",
                "username", scope, recipients);
        }
        return isCurrentNotificationScope(scope) ? recipients : Set.of();
    }

    private Set<String> scopedAssignedAndActiveRecipients(UiEventTicketScope scope) {
        Set<String> recipients = new LinkedHashSet<>();
        collectScopedDialogRecipients("SELECT responsible FROM ticket_responsibles WHERE ticket_id = ?",
            "responsible", scope, recipients);
        collectScopedDialogRecipients("SELECT user_identity FROM ticket_active WHERE ticket_id = ?",
            "user_identity", scope, recipients);
        return recipients;
    }

    private void collectBusinessParticipants(UiEventTicketScope scope, Set<String> recipients) {
        // Numeric identity and the entire frozen owner, never the historical username or raw participant table.
        try {
            jdbcTemplate.query("SELECT candidate.username FROM tickets owner"
                +" JOIN business_ticket_participants entry ON ROW(entry.ticket_user_id,entry.ticket_id,entry.ticket_public_id,entry.business_id)"
                +"=ROW(owner.user_id,owner.ticket_id,owner.ticket_public_id,owner.business_id)"
                +" JOIN users candidate ON candidate.id=entry.member_user_id WHERE "+EXPECTED_OWNER_SQL
                +" AND lower(btrim(candidate.username))<>'ai_agent' AND ("
                +com.example.panel.repository.NotificationBusinessAccessSql.CAN_READ_OWNER_BUSINESS
                    .replace("n.user_identity","lower(candidate.username)")+")",
                rs->{
                    String identity=normalizeRecipient(rs.getString("username"));
                    if(StringUtils.hasText(identity))recipients.add(identity);
                },scopeArguments(scope).toArray());
        } catch (org.springframework.dao.DataAccessException ex) {
            // Missing new schema is not permission to fall back to raw legacy participants.
        }
    }

    public void notifyScopedDialogParticipants(UiEventTicketScope scope, String text, String url, String excludedIdentity) {
        if (!isCurrentNotificationScope(scope)) {
            return;
        }
        if ("BUSINESS".equals(scope.scopeKind())) {
            Set<String> assignedAndActive = scopedAssignedAndActiveRecipients(scope);
            Set<String> members = new LinkedHashSet<>();
            collectBusinessParticipants(scope, members);
            if (!isCurrentNotificationScope(scope)) return;
            notifyDialogUsersExcluding(scope.ticketId(), assignedAndActive, excludedIdentity, text, url, scope);
            members.removeAll(assignedAndActive);
            // A removed/renamed/reused account must not inherit an in-flight roster delivery.
            notifyDialogUsersExcluding(scope.ticketId(), members, excludedIdentity, text, url, scope, false, true);
            return;
        }
        Set<String> recipients = findScopedDialogRecipients(scope);
        if (!isCurrentNotificationScope(scope)) {
            return;
        }
        if (recipients.isEmpty() && !"BUSINESS".equals(scope.scopeKind())) {
            recipients = findAllOperatorRecipients();
        }
        notifyDialogUsersExcluding(scope.ticketId(), recipients, excludedIdentity, text, url, scope);
    }

    public Set<String> notifyScopedDialogUsers(UiEventTicketScope scope, Set<String> userIdentities, String text, String url) {
        if (scope == null || !scope.isValid()) {
            return Set.of();
        }
        return notifyDialogUsersExcluding(scope.ticketId(), userIdentities, null, text, url, scope);
    }

    public Set<String> notifyScopedAiDialogUsers(UiEventTicketScope scope, Set<String> userIdentities, String text, String url) {
        if (scope == null || !scope.isValid()) {
            return Set.of();
        }
        return notifyDialogUsersExcluding(scope.ticketId(), userIdentities, null, text, url, scope, true);
    }

    /** Explicit ticket context stays attached until the notification INSERT snapshot. */
    public Set<String> notifyDialogUsers(String ticketId, Set<String> userIdentities, String text, String url) {
        return notifyDialogUsersExcluding(ticketId, userIdentities, null, text, url);
    }

    private Set<String> notifyDialogUsersExcluding(String ticketId, Set<String> userIdentities,
                                                  String excludedIdentity, String text, String url) {
        return notifyDialogUsersExcluding(ticketId, userIdentities, excludedIdentity, text, url, null);
    }

    private Set<String> notifyDialogUsersExcluding(String ticketId, Set<String> userIdentities,
                                                  String excludedIdentity, String text, String url,
                                                  UiEventTicketScope expectedScope) {
        return notifyDialogUsersExcluding(ticketId, userIdentities, excludedIdentity, text, url, expectedScope, false);
    }

    private Set<String> notifyDialogUsersExcluding(String ticketId, Set<String> userIdentities,
                                                  String excludedIdentity, String text, String url,
                                                  UiEventTicketScope expectedScope, boolean scopedAi) {
        return notifyDialogUsersExcluding(ticketId,userIdentities,excludedIdentity,text,url,expectedScope,scopedAi,false);
    }

    private Set<String> notifyDialogUsersExcluding(String ticketId, Set<String> userIdentities,
                                                  String excludedIdentity, String text, String url,
                                                  UiEventTicketScope expectedScope, boolean scopedAi, boolean rosterDelivery) {
        if (!StringUtils.hasText(text) || userIdentities == null || userIdentities.isEmpty()
                || !legacyTicketIdJdbcGuard.hasUniqueNonGeneralTicket(ticketId)) {
            return Set.of();
        }
        String excluded = normalizeRecipient(excludedIdentity);
        String safeUrl = normalizeUrl(url);
        String safeText = normalizeNotificationText(text.trim(), safeUrl);
        Set<String> delivered = new LinkedHashSet<>();
        for (String identity : normalizeRecipients(userIdentities)) {
            if (identity.equals(excluded)) {
                continue;
            }
            String sql = """
                INSERT INTO notifications(user_identity, text, url, is_read, created_at,
                    ticket_user_id, ticket_id, ticket_public_id, ticket_scope_kind, ticket_business_id)
                SELECT n.user_identity, n.text, n.url, FALSE,
                       n.created_at, owner.user_id, owner.ticket_id, owner.ticket_public_id,
                       CASE WHEN owner.business_id IS NULL THEN 'UNRESOLVED' ELSE 'BUSINESS' END, owner.business_id
                  FROM (VALUES (CAST(? AS TEXT), CAST(? AS TEXT), CAST(? AS TEXT), CAST(? AS TIMESTAMP WITH TIME ZONE)))
                    AS n(user_identity, text, url, created_at)
                  JOIN tickets owner ON owner.ticket_id = ?
                 WHERE owner.is_general = FALSE
                   AND (SELECT COUNT(*) FROM tickets candidate WHERE candidate.ticket_id = ?) = 1
                   AND (owner.business_id IS NULL OR
                """ + NotificationBusinessAccessSql.CAN_READ_OWNER_BUSINESS + ")";
            List<Object> arguments = new ArrayList<>(Arrays.asList(
                identity, safeText, safeUrl, OffsetDateTime.now(ZoneOffset.UTC), ticketId, ticketId));
            if (expectedScope != null) {
                sql += " AND (" + EXPECTED_OWNER_SQL + ")";
                arguments.addAll(scopeArguments(expectedScope));
            }
            if (scopedAi) {
                sql += " AND (" + ScopedAiSql.CAN_ESCALATE + ")";
            }
            if (rosterDelivery) {
                sql += " AND EXISTS(SELECT 1 FROM business_ticket_participants entry JOIN users member ON member.id=entry.member_user_id"
                    +" WHERE ROW(entry.ticket_user_id,entry.ticket_id,entry.ticket_public_id,entry.business_id)"
                    +"=ROW(owner.user_id,owner.ticket_id,owner.ticket_public_id,owner.business_id)"
                    +" AND lower(member.username)=n.user_identity AND lower(btrim(member.username))<>'ai_agent')";
            }
            int inserted = jdbcTemplate.update(sql, arguments.toArray());
            if (inserted == 1) {
                delivered.add(identity);
                uiEventStreamService.publishNotificationsChanged(identity, "notification_created");
            }
        }
        return delivered;
    }

    public void notifyAllOperators(String text, String url, String excludedIdentity) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        notifyUsersExcluding(findAllOperatorRecipients(), excludedIdentity, text, url);
    }

    public String buildDialogUrl(String ticketId) {
        String normalizedTicketId = exactTicketId(ticketId);
        if (normalizedTicketId == null) {
            return "/dialogs";
        }
        return "/dialogs?ticketId="
                + URLEncoder.encode(normalizedTicketId, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Transactional(readOnly = true)
    public Set<String> findAllOperatorRecipients() {
        Set<String> recipients = new LinkedHashSet<>();
        recipients.addAll(loadOperatorRecipients(usersJdbcTemplate));
        recipients.addAll(loadOperatorRecipients(jdbcTemplate));
        return recipients;
    }

    private Set<String> loadOperatorRecipients(JdbcTemplate source) {
        if (source == null) {
            return Set.of();
        }
        Set<String> userColumns = loadUsersTableColumns(source);
        if (userColumns.isEmpty() || !userColumns.contains("username")) {
            return Set.of();
        }
        boolean hasEnabled = userColumns.contains("enabled");
        boolean hasBlocked = userColumns.contains("is_blocked");
        StringBuilder sql = new StringBuilder("SELECT username");
        if (hasEnabled) {
            sql.append(", enabled");
        }
        if (hasBlocked) {
            sql.append(", is_blocked");
        }
        sql.append(" FROM users");

        Set<String> recipients = new LinkedHashSet<>();
        try {
            source.query(sql.toString(), (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                if (hasEnabled && !coerceBooleanFlag(rs.getObject("enabled"), true)) {
                    return;
                }
                if (hasBlocked && coerceBooleanFlag(rs.getObject("is_blocked"), false)) {
                    return;
                }
                String username = normalizeRecipient(rs.getString("username"));
                if (StringUtils.hasText(username)) {
                    recipients.add(username);
                }
            });
        } catch (Exception ex) {
            return Set.of();
        }
        return recipients;
    }

    static boolean coerceBooleanFlag(Object raw, boolean defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof Number value) {
            return value.longValue() != 0L;
        }
        String text = raw.toString().trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return defaultValue;
        }
        return switch (text) {
            case "1", "true", "t", "yes", "y", "on" -> true;
            case "0", "false", "f", "no", "n", "off" -> false;
            default -> defaultValue;
        };
    }

    private Set<String> loadUsersTableColumns(JdbcTemplate source) {
        try {
            return new HashSet<>(JdbcSchemaInspector.loadColumnNames(source, "users"));
        } catch (Exception ex) {
            return Set.of();
        }
    }

    private NotificationDto toDto(Notification entity) {
        String safeUrl = normalizeUrl(entity.getUrl());
        return new NotificationDto(
                entity.getId(),
                normalizeNotificationText(entity.getText(), safeUrl),
                safeUrl,
                Boolean.TRUE.equals(entity.getIsRead()),
                entity.getCreatedAt()
        );
    }

    private String normalizeIdentity(String userIdentity) {
        return StringUtils.hasText(userIdentity)
                ? userIdentity.trim().toLowerCase(Locale.ROOT)
                : "all";
    }

    private String normalizeRecipient(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private Set<String> splitIdentities(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Set.of();
        }
        Set<String> identities = new LinkedHashSet<>();
        String[] chunks = raw.split("[,;\\s]+");
        for (String chunk : chunks) {
            String normalized = normalizeRecipient(chunk);
            if (StringUtils.hasText(normalized)) {
                identities.add(normalized);
            }
        }
        return identities;
    }

    private Set<String> normalizeRecipients(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String identity : raw) {
            String value = normalizeRecipient(identity);
            if (StringUtils.hasText(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private void collectDialogRecipients(String sql,
                                         String column,
                                         String ticketId,
                                         Set<String> recipients) {
        try {
            jdbcTemplate.query(
                    sql + " AND (SELECT COUNT(*) FROM tickets owner WHERE owner.ticket_id = ?) = 1"
                        + " AND EXISTS (SELECT 1 FROM tickets owner WHERE owner.ticket_id = ? AND owner.is_general = FALSE)",
                    (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                            recipients.addAll(splitIdentities(rs.getString(column))),
                    ticketId, ticketId, ticketId
            );
        } catch (Exception ex) {
            // ignore missing optional tables for legacy databases
        }
    }

    private List<Object> scopeArguments(UiEventTicketScope scope) {
        return Arrays.asList(scope.ticketUserId(), scope.ticketId(), scope.ticketPublicId(),
            scope.businessId(), scope.scopeKind(), allowLegacyNotifications());
    }

    private boolean isCurrentNotificationScope(UiEventTicketScope scope) {
        if (scope == null || !scope.isValid()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM tickets owner WHERE " + EXPECTED_OWNER_SQL + ")",
                Boolean.class, scopeArguments(scope).toArray()));
        } catch (org.springframework.dao.DataAccessException ex) {
            return false;
        }
    }

    private void collectScopedDialogRecipients(String sql, String column, UiEventTicketScope scope, Set<String> recipients) {
        List<Object> arguments = new ArrayList<>();
        arguments.add(scope.ticketId());
        arguments.addAll(scopeArguments(scope));
        try {
            jdbcTemplate.query(sql + " AND EXISTS (SELECT 1 FROM tickets owner WHERE " + EXPECTED_OWNER_SQL + ")",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> recipients.addAll(splitIdentities(rs.getString(column))),
                arguments.toArray());
        } catch (org.springframework.dao.DataAccessException ex) {
            // Optional legacy recipient tables may be absent; owner proof is still required before fallback.
        }
    }

    private String normalizeUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        String trimmed = url.trim();
        String dialogTicketId = extractDialogTicketId(trimmed);
        if (dialogTicketId != null) {
            return buildDialogUrl(dialogTicketId);
        }
        return trimmed;
    }

    private void saveNotification(String identity, String text, String url) {
        Notification notification = new Notification();
        notification.setUserIdentity(identity);
        notification.setText(normalizeNotificationText(text));
        notification.setUrl(normalizeUrl(url));
        notification.setIsRead(Boolean.FALSE);
        notification.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        notificationRepository.save(notification);
        uiEventStreamService.publishNotificationsChanged(identity, "notification_created");
    }

    private String normalizeNotificationText(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String current = text.trim();
        for (Map.Entry<String, String> entry : LEGACY_NOTIFICATION_REPLACEMENTS.entrySet()) {
            current = current.replace(entry.getKey(), entry.getValue());
        }
        current = repairMojibakeSegments(current, CP1251_UTF8_MOJIBAKE, WINDOWS_1251);
        current = repairMojibakeSegments(current, LATIN1_UTF8_MOJIBAKE, StandardCharsets.ISO_8859_1);
        if (!looksLikeMojibake(current)) {
            return current;
        }
        String repaired = selectBestDecoding(current,
                decodeWithCharset(current, WINDOWS_1251),
                decodeWithCharset(current, StandardCharsets.ISO_8859_1));
        if (looksLikeMojibake(repaired)) {
            String secondPass = selectBestDecoding(repaired, decodeWithCharset(repaired, WINDOWS_1251));
            if (qualityScore(secondPass) > qualityScore(repaired)) {
                repaired = secondPass;
            }
        }
        return repaired;
    }

    private String repairMojibakeSegments(String value, Pattern pattern, Charset sourceCharset) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        Matcher matcher = pattern.matcher(value);
        StringBuffer buffer = new StringBuffer();
        boolean replaced = false;
        while (matcher.find()) {
            String source = matcher.group();
            String decoded = decodeWithCharset(source, sourceCharset);
            if (qualityScore(decoded) > qualityScore(source)) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(decoded));
                replaced = true;
            }
        }
        if (!replaced) {
            return value;
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String decodeWithCharset(String value, Charset sourceCharset) {
        try {
            return new String(value.getBytes(sourceCharset), StandardCharsets.UTF_8).trim();
        } catch (Exception ex) {
            return value;
        }
    }

    private String selectBestDecoding(String original, String... candidates) {
        String best = original;
        int bestScore = qualityScore(original);
        for (String candidate : candidates) {
            int candidateScore = qualityScore(candidate);
            if (candidateScore > bestScore) {
                best = candidate;
                bestScore = candidateScore;
            }
        }
        return best;
    }

    private int qualityScore(String value) {
        if (!StringUtils.hasText(value)) {
            return Integer.MIN_VALUE / 4;
        }
        int score = 0;
        for (char ch : value.toCharArray()) {
            if (Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CYRILLIC) {
                score += 3;
            } else if (Character.isLetterOrDigit(ch)) {
                score += 1;
            } else if (Character.isWhitespace(ch) || ",.:;!?()[]{}-_/\\#".indexOf(ch) >= 0) {
                score += 1;
            }
        }
        if (CP1251_UTF8_MOJIBAKE.matcher(value).find()) {
            score -= 30;
        }
        if (LATIN1_UTF8_MOJIBAKE.matcher(value).find()) {
            score -= 30;
        }
        if (ASCII_P_MOJIBAKE.matcher(value).find()) {
            score -= 30;
        }
        return score;
    }

    private boolean looksLikeMojibake(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return CP1251_UTF8_MOJIBAKE.matcher(value).find()
                || LATIN1_UTF8_MOJIBAKE.matcher(value).find()
                || ASCII_P_MOJIBAKE.matcher(value).find();
    }

    private String extractDialogTicketId(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("#open=ticket:")) {
            return exactTicketId(trimmed.substring("#open=ticket:".length()));
        }
        int ticketIdIndex = trimmed.indexOf("ticketId=");
        boolean dialogsLink = trimmed.startsWith("/dialogs?")
                || trimmed.startsWith("dialogs?")
                || trimmed.contains("/dialogs?");
        if (!dialogsLink || ticketIdIndex < 0) {
            return null;
        }
        String raw = trimmed.substring(ticketIdIndex + "ticketId=".length());
        int ampIndex = raw.indexOf('&');
        if (ampIndex >= 0) {
            raw = raw.substring(0, ampIndex);
        }
        int hashIndex = raw.indexOf('#');
        if (hashIndex >= 0) {
            raw = raw.substring(0, hashIndex);
        }
        try {
            return exactTicketId(URLDecoder.decode(raw, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return exactTicketId(raw);
        }
    }

    private String exactTicketId(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
