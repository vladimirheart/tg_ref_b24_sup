package com.example.panel.service;

import com.example.panel.entity.Notification;
import com.example.panel.model.notification.NotificationDto;
import com.example.panel.model.notification.NotificationSummary;
import com.example.panel.repository.NotificationRepository;
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

    public NotificationService(NotificationRepository notificationRepository,
                               JdbcTemplate jdbcTemplate,
                               @Qualifier("usersJdbcTemplate") JdbcTemplate usersJdbcTemplate) {
        this.notificationRepository = notificationRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.usersJdbcTemplate = usersJdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> findForUser(String userIdentity) {
        String identity = normalizeIdentity(userIdentity);
        return notificationRepository.findByUserIdentityOrderByCreatedAtDesc(identity).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationSummary summary(String userIdentity) {
        String identity = normalizeIdentity(userIdentity);
        long unread = notificationRepository.countByUserIdentityAndIsReadFalse(identity);
        return new NotificationSummary(unread);
    }

    public void markAsRead(String userIdentity, Long id) {
        String identity = normalizeIdentity(userIdentity);
        notificationRepository.findByIdAndUserIdentity(id, identity).ifPresent(notification -> {
            notification.setIsRead(Boolean.TRUE);
            notificationRepository.save(notification);
        });
    }

    public long markAllAsRead(String userIdentity) {
        String identity = normalizeIdentity(userIdentity);
        List<Notification> unread = notificationRepository.findByUserIdentityAndIsReadFalseOrderByCreatedAtDesc(identity);
        if (unread.isEmpty()) {
            return 0;
        }
        unread.forEach(notification -> notification.setIsRead(Boolean.TRUE));
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    public void notifyUser(String userIdentity, String text, String url) {
        String identity = normalizeRecipient(userIdentity);
        if (!StringUtils.hasText(identity) || !StringUtils.hasText(text)) {
            return;
        }
        saveNotification(identity, text.trim(), normalizeUrl(url));
    }

    public void notifyUsers(Set<String> userIdentities, String text, String url) {
        notifyUsersExcluding(userIdentities, null, text, url);
    }

    public void notifyUsersExcluding(Set<String> userIdentities, String excludedIdentity, String text, String url) {
        if (!StringUtils.hasText(text) || userIdentities == null || userIdentities.isEmpty()) {
            return;
        }
        String excluded = normalizeRecipient(excludedIdentity);
        String safeText = text.trim();
        String safeUrl = normalizeUrl(url);
        Set<String> recipients = normalizeRecipients(userIdentities);
        for (String identity : recipients) {
            if (StringUtils.hasText(excluded) && excluded.equals(identity)) {
                continue;
            }
            saveNotification(identity, safeText, safeUrl);
        }
    }

    @Transactional(readOnly = true)
    public Set<String> findDialogRecipients(String ticketId) {
        if (!StringUtils.hasText(ticketId)) {
            return Set.of();
        }
        Set<String> recipients = new LinkedHashSet<>();
        String normalizedTicketId = ticketId.trim();
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
        return recipients;
    }

    public void notifyDialogParticipants(String ticketId, String text, String url, String excludedIdentity) {
        Set<String> recipients = findDialogRecipients(ticketId);
        if (recipients.isEmpty()) {
            notifyAllOperators(text, url, excludedIdentity);
            return;
        }
        notifyUsersExcluding(recipients, excludedIdentity, text, url);
    }

    public void notifyAllOperators(String text, String url, String excludedIdentity) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        notifyUsersExcluding(findAllOperatorRecipients(), excludedIdentity, text, url);
    }

    public String buildDialogUrl(String ticketId) {
        String normalizedTicketId = trimToNull(ticketId);
        if (normalizedTicketId == null) {
            return "/dialogs";
        }
        return "/dialogs/" + URLEncoder.encode(normalizedTicketId, StandardCharsets.UTF_8);
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
        StringBuilder sql = new StringBuilder("""
                SELECT username
                  FROM users
                 WHERE 1 = 1
                """);
        if (userColumns.contains("enabled")) {
            sql.append(" AND COALESCE(enabled, 1) = 1");
        }
        if (userColumns.contains("is_blocked")) {
            sql.append(" AND COALESCE(is_blocked, 0) = 0");
        }
        Set<String> recipients = new LinkedHashSet<>();
        try {
            source.query(sql.toString(), (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
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

    private Set<String> loadUsersTableColumns(JdbcTemplate source) {
        try {
            return new HashSet<>(source.query(
                    "PRAGMA table_info(users)",
                    (rs, rowNum) -> rs.getString("name")
            ));
        } catch (Exception ex) {
            return Set.of();
        }
    }

    private NotificationDto toDto(Notification entity) {
        return new NotificationDto(
                entity.getId(),
                normalizeNotificationText(entity.getText()),
                normalizeUrl(entity.getUrl()),
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
                    sql,
                    (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                            recipients.addAll(splitIdentities(rs.getString(column))),
                    ticketId
            );
        } catch (Exception ex) {
            // ignore missing optional tables for legacy databases
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
            return trimToNull(trimmed.substring("#open=ticket:".length()));
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
            return trimToNull(URLDecoder.decode(raw, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return trimToNull(raw);
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
