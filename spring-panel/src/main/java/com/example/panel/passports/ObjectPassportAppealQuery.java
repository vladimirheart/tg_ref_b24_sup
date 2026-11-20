package com.example.panel.passports;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

final class ObjectPassportAppealQuery {

    private final JdbcTemplate jdbcTemplate;

    ObjectPassportAppealQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Map<String, Long> loadAppealCountsByLocation() {
        return jdbcTemplate.query(
                """
                        SELECT DISTINCT ticket_id,
                               lower(trim(replace(replace(COALESCE(business, ''), 'Ё', 'Е'), 'ё', 'е'))) AS business_key,
                               lower(trim(replace(replace(COALESCE(city, ''), 'Ё', 'Е'), 'ё', 'е'))) AS city_key,
                               lower(trim(replace(replace(COALESCE(location_name, ''), 'Ё', 'Е'), 'ё', 'е'))) AS department_key
                        FROM messages
                        WHERE trim(COALESCE(ticket_id, '')) <> ''
                        """,
                rs -> {
                    Map<String, Set<String>> ticketsByKey = new LinkedHashMap<>();
                    while (rs.next()) {
                        String ticketId = stringValue(rs.getString("ticket_id"));
                        String business = normalizeLookupValue(rs.getString("business_key"));
                        String city = normalizeLookupValue(rs.getString("city_key"));
                        String department = normalizeLookupValue(rs.getString("department_key"));
                        if (!StringUtils.hasText(ticketId)) {
                            continue;
                        }
                        if (StringUtils.hasText(department)) {
                            addAppealTicket(ticketsByKey, appealDepartmentKey(department), ticketId);
                            if (StringUtils.hasText(business)) {
                                addAppealTicket(ticketsByKey, appealDepartmentBusinessKey(business, department), ticketId);
                            }
                        } else if (StringUtils.hasText(city)) {
                            addAppealTicket(ticketsByKey, appealCityBusinessKey(business, city), ticketId);
                        }
                    }
                    Map<String, Long> result = new LinkedHashMap<>();
                    ticketsByKey.forEach((key, ids) -> result.put(key, (long) ids.size()));
                    return result;
                }
        );
    }

    private void addAppealTicket(Map<String, Set<String>> ticketsByKey, String key, String ticketId) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        ticketsByKey.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(ticketId);
    }

    long resolveAppealCount(Map<String, Long> index, Map<String, Object> payload) {
        String business = normalizeLookupValue(payload.get("business"));
        String city = normalizeLookupValue(payload.get("city"));
        String department = normalizeLookupValue(payload.get("department"));
        if (StringUtils.hasText(department)) {
            if (StringUtils.hasText(business)) {
                Long strict = index.get(appealDepartmentBusinessKey(business, department));
                if (strict != null) {
                    return strict;
                }
            }
            return index.getOrDefault(appealDepartmentKey(department), 0L);
        }
        if (StringUtils.hasText(city)) {
            return index.getOrDefault(appealCityBusinessKey(business, city), 0L);
        }
        return 0L;
    }

    private String appealDepartmentKey(String department) {
        return "department::" + normalizeLookupValue(department);
    }

    private String appealDepartmentBusinessKey(String business, String department) {
        return "department-business::" + normalizeLookupValue(business) + "::" + normalizeLookupValue(department);
    }

    private String appealCityBusinessKey(String business, String city) {
        return "city-business::" + normalizeLookupValue(business) + "::" + normalizeLookupValue(city);
    }

    List<Map<String, Object>> loadCases(Map<String, Object> payload) {
        String businessKey = normalizeLookupValue(payload.get("business"));
        String cityKey = normalizeLookupValue(payload.get("city"));
        String departmentKey = normalizeLookupValue(payload.get("department"));
        if (!StringUtils.hasText(businessKey) && !StringUtils.hasText(cityKey) && !StringUtils.hasText(departmentKey)) {
            return List.of();
        }
        if (StringUtils.hasText(departmentKey)) {
            List<Map<String, Object>> strict = queryCases(businessKey, "", departmentKey);
            if (!strict.isEmpty() || !StringUtils.hasText(businessKey)) {
                return strict;
            }
            return queryCases("", "", departmentKey);
        }
        return queryCases(businessKey, cityKey, "");
    }

    private List<Map<String, Object>> queryCases(String businessKey, String cityKey, String departmentKey) {
        StringBuilder sql = new StringBuilder("""
                WITH roots AS (
                    SELECT t.user_id, t.ticket_id, t.channel_id,
                           COALESCE(first_message.created_at, t.created_at) AS created_at,
                           first_message.created_date, first_message.group_msg_id,
                           count(*) OVER (PARTITION BY t.ticket_id) AS identity_count,
                           t.is_general
                      FROM tickets t
                      LEFT JOIN LATERAL (
                          SELECT m0.created_at, m0.created_date, m0.group_msg_id
                            FROM messages m0 WHERE m0.ticket_id = t.ticket_id
                           ORDER BY m0.created_at ASC NULLS LAST, m0.group_msg_id ASC LIMIT 1
                      ) first_message ON TRUE
                ), dated AS (
                    SELECT r.*,
                           CASE WHEN r.created_date ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' THEN replace(r.created_date, '-', '')
                                WHEN r.created_date ~ '^[0-9]{2}[.][0-9]{2}[.][0-9]{4}$' THEN
                                    substring(r.created_date, 7, 4) || substring(r.created_date, 4, 2) || substring(r.created_date, 1, 2)
                                WHEN r.created_date ~ '^[0-9]{8}$' THEN r.created_date
                                ELSE to_char(r.created_at AT TIME ZONE 'UTC', 'YYYYMMDD') END AS number_day
                      FROM roots r WHERE r.identity_count = 1 AND r.is_general = FALSE
                ), numbered AS (
                    SELECT d.*,
                           row_number() OVER (PARTITION BY number_day
                               ORDER BY date_trunc('second', created_at AT TIME ZONE 'UTC'), ticket_id COLLATE "C") AS day_sequence
                      FROM dated d
                )
                SELECT m.ticket_id, m.user_id, m.channel_id, m.business, m.city, m.problem, m.created_at,
                       t.ticket_public_id, t.is_general, t.status, roots.identity_count,
                       CASE WHEN numbered.created_at IS NULL THEN numbered.group_msg_id::text ELSE
                           numbered.number_day || '-' || lpad(numbered.day_sequence::text,
                               greatest(3, length(numbered.day_sequence::text)), '0') END AS request_number
                  FROM messages m
                  LEFT JOIN tickets t ON t.ticket_id = m.ticket_id AND t.user_id = m.user_id
                       AND t.channel_id IS NOT DISTINCT FROM m.channel_id
                  LEFT JOIN roots ON roots.user_id = t.user_id AND roots.ticket_id = t.ticket_id
                  LEFT JOIN numbered ON numbered.user_id = t.user_id AND numbered.ticket_id = t.ticket_id
                 WHERE trim(COALESCE(m.ticket_id, '')) <> ''
                """);
        List<Object> params = new ArrayList<>();
        appendNormalizedMatch(sql, params, "m.business", businessKey);
        appendNormalizedMatch(sql, params, "m.city", cityKey);
        appendNormalizedMatch(sql, params, "m.location_name", departmentKey);
        sql.append(" ORDER BY m.created_at DESC NULLS LAST, m.ticket_id DESC, m.group_msg_id DESC");

        List<Map<String, Object>> rows = jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> {
                    LinkedHashMap<String, Object> item = new LinkedHashMap<>();
                    item.put("ticket_id", rs.getString("ticket_id"));
                    item.put("user_id", rs.getObject("user_id", Long.class));
                    item.put("channel_id", rs.getObject("channel_id", Long.class));
                    item.put("request_number", rs.getString("request_number"));
                    item.put("status", rs.getString("status"));
                    String publicId = rs.getString("ticket_public_id");
                    String dialogUrl = null;
                    if (Boolean.TRUE.equals(rs.getObject("is_general", Boolean.class))) {
                        if (publicId != null && publicId.matches("[0-9a-f]{32}")) {
                            dialogUrl = "/general-dialog-work/" + publicId;
                        }
                    } else if (rs.getLong("identity_count") == 1) {
                        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/dialogs")
                                .queryParam("ticketId", rs.getString("ticket_id"));
                        Long channelId = rs.getObject("channel_id", Long.class);
                        if (channelId != null) builder.queryParam("channelId", channelId);
                        dialogUrl = builder.build().encode().toUriString();
                    }
                    item.put("dialog_url", dialogUrl);
                    item.put("business", rs.getString("business"));
                    item.put("city", rs.getString("city"));
                    item.put("problem", rs.getString("problem"));
                    item.put("created_at", rs.getString("created_at"));
                    return item;
                },
                params.toArray()
        );
        // A legacy ID alone can belong to different users/channels.
        LinkedHashMap<CaseIdentity, Map<String, Object>> byTicket = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String ticketId = stringValue(row.get("ticket_id"));
            if (StringUtils.hasText(ticketId)) {
                byTicket.putIfAbsent(new CaseIdentity(row.get("user_id"), row.get("channel_id"), ticketId), row);
            }
        }
        return new ArrayList<>(byTicket.values());
    }

    private record CaseIdentity(Object userId, Object channelId, String ticketId) {}

    private void appendNormalizedMatch(StringBuilder sql, List<Object> params, String column, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        sql.append(" AND lower(trim(replace(replace(COALESCE(")
                .append(column)
                .append(", ''), 'Ё', 'Е'), 'ё', 'е'))) = ?");
        params.add(value);
    }

    private String normalizeLookupValue(Object raw) {
        String value = stringValue(raw);
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value
                .replace('Ё', 'Е')
                .replace('ё', 'е')
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
