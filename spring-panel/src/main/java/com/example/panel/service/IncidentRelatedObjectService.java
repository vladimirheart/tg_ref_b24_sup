package com.example.panel.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Search is not an authorization token: selection is checked again on every incident write. */
@Service
public class IncidentRelatedObjectService {
    private static final Map<String, String> PAGES = Map.of(
            "ticket", "PAGE_DIALOGS", "task", "PAGE_TASKS", "object_passport", "PAGE_OBJECT_PASSPORTS");
    private static final List<String> TYPES = List.of("ticket", "task", "object_passport", "external_object");
    private final JdbcTemplate jdbc;
    private final BusinessAccessService businessAccess;

    public IncidentRelatedObjectService(JdbcTemplate jdbc, BusinessAccessService businessAccess) {
        this.jdbc = jdbc;
        this.businessAccess = businessAccess;
    }

    public Map<String, Object> capabilities() {
        requireIncidentPage();
        return Map.of("types", TYPES.stream().filter(this::canRead).toList(),
                "can_create_external", hasAuthority("ROLE_ADMIN"));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> search(String type, String query, Integer limit) {
        requireType(type);
        String search = query == null ? "" : query.strip();
        if (search.length() > 200) throw badRequest("Поисковый запрос: не более 200 символов.");
        int count = limit == null ? 20 : Math.max(1, Math.min(limit, 30));
        List<Object> args = new ArrayList<>();
        String rows = rows(type, args);
        args.add(search);
        args.add(search);
        args.add(count);
        List<Map<String, Object>> items = jdbc.query("SELECT value, label, detail FROM (" + rows + ") candidates "
                        + "WHERE (? = '' OR strpos(lower(search_text), lower(?)) > 0) ORDER BY sort_time DESC NULLS LAST, value LIMIT ?",
                (rs, n) -> Map.of("value", rs.getString("value"), "label", rs.getString("label"),
                        "detail", rs.getString("detail")), args.toArray());
        return Map.of("items", items, "type", type);
    }

    @Transactional(readOnly = true)
    public void validateSelection(String type, String key) {
        selection(type, key);
    }

    @Transactional(readOnly = true)
    public String selectionLabel(String type, String key) {
        return selection(type, key);
    }

    private String selection(String type, String key) {
        requireType(type);
        if (key == null || key.isEmpty() || key.length() > 512) throw notFound();
        List<Object> args = new ArrayList<>();
        String rows = rows(type, args);
        args.add(key); // Never trim/case-fold a ticket identity or coerce a browser ID to a Number.
        List<String> labels = jdbc.query("SELECT label FROM (" + rows + ") candidates WHERE value = ?",
                (rs, n) -> rs.getString("label"), args.toArray());
        if (labels.size() != 1) throw notFound();
        return labels.get(0);
    }

    @Transactional
    public Map<String, Object> createExternal(Map<String, Object> payload) {
        requireIncidentPage();
        if (!hasAuthority("ROLE_ADMIN")) throw new AccessDeniedException("Создавать внешние объекты может только администратор портала.");
        String name = text(payload, "name", 200, true);
        String number = text(payload, "external_number", 200, false);
        String url = text(payload, "external_url", 2048, false);
        String description = text(payload, "description", 5000, false);
        if (url != null) {
            try {
                URI uri = new URI(url);
                if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null || uri.getRawUserInfo() != null) throw new IllegalArgumentException();
            } catch (Exception e) {
                throw badRequest("Укажите корректную http(s)-ссылку без логина и пароля.");
            }
        }
        String id = jdbc.queryForObject("""
                INSERT INTO incident_external_objects(name, external_number, external_url, description, created_by)
                VALUES (?, ?, ?, ?, ?) RETURNING id::text
                """, String.class, name, number, url, description, authentication().getName());
        return Map.of("item", Map.of("value", id, "label", externalLabel(name, number),
                "detail", description == null ? "Внешний объект портала" : description));
    }

    private String rows(String type, List<Object> args) {
        return switch (type) {
            case "ticket" -> ticketRows(args);
            case "task" -> """
                    SELECT id::text AS value, 'DL_' || COALESCE(seq, id)::text || ' · ' || COALESCE(title, '') AS label,
                           COALESCE(status, '') AS detail,
                           concat_ws(' ', id, 'DL_' || COALESCE(seq, id)::text, title) AS search_text,
                           created_at AS sort_time FROM tasks
                    """;
            case "object_passport" -> """
                    SELECT p.id::text AS value,
                           COALESCE(NULLIF(p.passport_number, ''), '#' || p.id::text) || ' · ' || COALESCE(o.name, 'Объект') AS label,
                           COALESCE(o.address, '') AS detail,
                           concat_ws(' ', p.id, p.passport_number, o.name, o.address) AS search_text,
                           p.created_at AS sort_time
                      FROM object_passports p LEFT JOIN objects o ON o.id = p.object_id
                    """;
            case "external_object" -> """
                    SELECT id::text AS value, name || CASE WHEN COALESCE(external_number, '') = '' THEN '' ELSE ' · ' || external_number END AS label,
                           COALESCE(description, external_url, 'Внешний объект портала') AS detail,
                           concat_ws(' ', id, name, external_number, external_url, description) AS search_text,
                           created_at AS sort_time FROM incident_external_objects
                    """;
            default -> throw badRequest("Неизвестный тип связанного объекта.");
        };
    }

    private String ticketRows(List<Object> args) {
        // Count ALL raw roots before excluding GENERAL, so a colliding opaque ID never selects an arbitrary business.
        // Numbering is portal-wide (as in DialogLookupReadService); the visibility filter is applied afterwards.
        Set<Long> businesses = businessAccess.resolveAccessibleBusinessIds(canonicalUserId());
        String businessFilter = "business_id IS NULL";
        if (!businesses.isEmpty()) {
            businessFilter += " OR business_id IN (" + String.join(",", businesses.stream().map(id -> "?").toList()) + ")";
            args.addAll(businesses);
        }
        return """
                WITH unique_roots AS (
                    SELECT t.ticket_id, t.business_id, t.status, m.problem, m.client_name,
                           COALESCE(m.created_at, t.created_at) AS stamp
                      FROM tickets t LEFT JOIN messages m ON m.ticket_id = t.ticket_id
                     WHERE t.is_general = FALSE
                       AND (SELECT count(*) FROM tickets roots WHERE roots.ticket_id = t.ticket_id) = 1
                ), numbered AS (
                    SELECT *, to_char(stamp, 'YYYYMMDD') || '-' ||
                           lpad((row_number() OVER (PARTITION BY to_char(stamp, 'YYYY-MM-DD') ORDER BY stamp, ticket_id))::text,
                                greatest(3, length((row_number() OVER (PARTITION BY to_char(stamp, 'YYYY-MM-DD') ORDER BY stamp, ticket_id))::text)), '0') AS display_number
                      FROM unique_roots
                )
                SELECT ticket_id AS value,
                       COALESCE(display_number, ticket_id) || ' · ' || COALESCE(NULLIF(problem, ''), 'Диалог') AS label,
                       concat_ws(' · ', client_name, status) AS detail,
                       concat_ws(' ', ticket_id, display_number, problem, client_name) AS search_text,
                       stamp AS sort_time FROM numbered WHERE (
                """ + businessFilter + ")";
    }

    private Long canonicalUserId() {
        // Exact authenticated username only; global ROLE_ADMIN is not ALL_BUSINESSES.
        List<Long> ids = jdbc.query("SELECT id FROM users WHERE username = ?", (rs, n) -> rs.getLong(1), authentication().getName());
        return ids.size() == 1 ? ids.get(0) : null;
    }

    private void requireType(String type) {
        requireIncidentPage();
        if (type == null || !TYPES.contains(type)) throw badRequest("Неизвестный тип связанного объекта.");
        if (!canRead(type)) throw new AccessDeniedException("Нет доступа к этому типу объектов.");
    }

    private boolean canRead(String type) {
        return "external_object".equals(type) || hasAuthority(PAGES.get(type));
    }

    private void requireIncidentPage() {
        if (PAGES.values().stream().noneMatch(this::hasAuthority)) throw new AccessDeniedException("Нет доступа к инцидентам.");
    }

    private boolean hasAuthority(String authority) {
        Authentication auth = authentication();
        return auth != null && auth.isAuthenticated() && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(authority));
    }

    private Authentication authentication() { return SecurityContextHolder.getContext().getAuthentication(); }

    private static String text(Map<String, Object> payload, String field, int max, boolean required) {
        String label = Map.of("name", "Название", "external_number", "Внешний номер", "external_url", "Ссылка", "description", "Описание").get(field);
        Object raw = payload.get(field);
        if (raw != null && !(raw instanceof String)) throw badRequest("Поле «" + label + "» должно быть текстом.");
        String value = raw instanceof String s ? s.strip() : "";
        boolean empty = value.codePoints().noneMatch(cp -> !Character.isWhitespace(cp) && !Character.isSpaceChar(cp));
        if (value.length() > max || (required && empty)) throw badRequest("Проверьте поле «" + label + "» (до " + max + " символов).");
        return empty ? null : value;
    }

    private static String externalLabel(String name, String number) { return number == null ? name : name + " · " + number; }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Объект не найден или недоступен. Выберите его заново из поиска."); }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
