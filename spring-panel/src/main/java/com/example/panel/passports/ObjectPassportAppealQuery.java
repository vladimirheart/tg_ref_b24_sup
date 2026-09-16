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
                SELECT ticket_id, business, city, problem, created_at
                FROM messages
                WHERE trim(COALESCE(ticket_id, '')) <> ''
                """);
        List<Object> params = new ArrayList<>();
        appendNormalizedMatch(sql, params, "business", businessKey);
        appendNormalizedMatch(sql, params, "city", cityKey);
        appendNormalizedMatch(sql, params, "location_name", departmentKey);
        sql.append(" ORDER BY COALESCE(created_at, '') DESC, ticket_id DESC");

        List<Map<String, Object>> rows = jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> {
                    LinkedHashMap<String, Object> item = new LinkedHashMap<>();
                    item.put("ticket_id", rs.getString("ticket_id"));
                    item.put("business", rs.getString("business"));
                    item.put("city", rs.getString("city"));
                    item.put("problem", rs.getString("problem"));
                    item.put("created_at", rs.getString("created_at"));
                    return item;
                },
                params.toArray()
        );
        LinkedHashMap<String, Map<String, Object>> byTicket = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String ticketId = stringValue(row.get("ticket_id"));
            if (StringUtils.hasText(ticketId)) {
                byTicket.putIfAbsent(ticketId, row);
            }
        }
        return new ArrayList<>(byTicket.values());
    }

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
