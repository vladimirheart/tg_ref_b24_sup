package com.example.panel.service;

import com.example.panel.model.dialog.DialogListQuery;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** SQL-only selection; no full DialogListItem collection is materialized in the JVM. */
final class DialogListPageSql {
    record Policy(int targetMinutes, int warningMinutes, int criticalMinutes, int overdueHours,
                  boolean escalationEnabled, boolean pinUnassignedOnly, boolean viewUnassignedOnly, long nowMs) {}

    static final class Selection {
        final DialogListQuery query;
        final Policy policy;
        final boolean myPreview;
        long total;
        int page = 1;
        final Map<String, Long> counts = new LinkedHashMap<>(Map.of("new", 0L, "unanswered", 0L, "in_work", 0L));

        Selection(DialogListQuery query, Policy policy, boolean myPreview) {
            this.query = query;
            this.policy = policy;
            this.myPreview = myPreview;
        }
    }

    record Statement(String sql, Object[] bindings) {}

    static Statement build(String base, String operator, Selection selection) {
        var p = selection.policy;
        var q = selection.query;
        List<Object> args = new ArrayList<>();
        args.add(operator); // unread boundary in the shared legacy projection
        args.add(Timestamp.from(java.time.Instant.ofEpochMilli(p.nowMs)));
        args.add(p.targetMinutes);
        String prefix = """
                WITH source_rows AS NOT MATERIALIZED (%s),
                dated AS (
                    SELECT d.*,
                           CASE WHEN d.created_date ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' THEN replace(d.created_date, '-', '')
                                WHEN d.created_date ~ '^[0-9]{2}[.][0-9]{2}[.][0-9]{4}$' THEN
                                    substring(d.created_date, 7, 4) || substring(d.created_date, 4, 2) || substring(d.created_date, 1, 2)
                                WHEN d.created_date ~ '^[0-9]{8}$' THEN d.created_date
                                ELSE to_char(d.created_at AT TIME ZONE 'UTC', 'YYYYMMDD') END AS number_day
                      FROM source_rows d
                ),
                numbered AS (
                    SELECT d.*,
                           row_number() OVER (
                               PARTITION BY d.number_day
                               ORDER BY date_trunc('second', d.created_at AT TIME ZONE 'UTC'),
                                        d.ticket_id COLLATE "C") AS day_sequence,
                           lower(trim(COALESCE(d.status, ''))) IN ('closed', 'resolved') AS closed,
                           COALESCE(NULLIF(trim(d.responsible), ''),
                               CASE WHEN lower(COALESCE(d.status, '')) IN ('closed','resolved')
                                          AND lower(COALESCE(d.resolved_by, '')) ~ '(auto|авто)'
                                    THEN 'Авто-система' ELSE trim(d.resolved_by) END, '') IN ('', '—', '-') AS unassigned,
                           CASE WHEN d.last_sender IS NULL THEN FALSE ELSE
                               lower(d.last_sender) ~ '(support|operator|admin|system)' END AS operator_reply,
                           floor((extract(epoch FROM (d.created_at - CAST(? AS timestamptz))) / 60)
                                 + ? + 0.5)::bigint AS minutes_left
                      FROM dated d
                ), classified AS (
                    SELECT n.*,
                           CASE WHEN n.closed THEN
                               CASE WHEN lower(COALESCE(n.resolved_by, '')) ~ '(auto|авто)'
                                    THEN 'Закрыт автоматически' ELSE 'Закрыт' END
                           WHEN n.ai_processing > 0 OR lower(trim(COALESCE(n.responsible, ''))) = 'ai_agent'
                               THEN 'в автоматической обработке'
                           WHEN trim(COALESCE(n.responsible, '')) = '' AND NOT n.operator_reply THEN 'новый'
                           WHEN NOT n.operator_reply THEN 'ожидает ответа оператора'
                           ELSE 'ожидает ответа клиента' END AS status_label,
                           CASE WHEN n.created_at IS NULL THEN n.request_number::text ELSE
                               n.number_day || '-' || lpad(n.day_sequence::text,
                                   greatest(3, length(n.day_sequence::text)), '0') END AS display_number
                      FROM numbered n
                )
                """.formatted(base);

        if (selection.myPreview) {
            args.add(operator);
            args.add(operator);
            String pinnedPredicate = "";
            if (!q.pinnedTicketIds().isEmpty()) {
                pinnedPredicate = " OR ticket_id IN (" + String.join(",", java.util.Collections.nCopies(q.pinnedTicketIds().size(), "?")) + ")";
                args.addAll(q.pinnedTicketIds());
            }
            return new Statement(prefix + """
                    , buckets AS (
                        SELECT d.ticket_id, d.display_number, d.last_activity,
                               CASE WHEN trim(COALESCE(responsible, '')) = ''
                                          AND status_label IN ('новый', 'в автоматической обработке') THEN 'new'
                                    WHEN unread_count > 0 THEN 'unanswered' ELSE 'in_work' END AS my_bucket
                          FROM classified d
                         WHERE NOT closed AND CAST(? AS text) IS NOT NULL
                           AND (trim(COALESCE(responsible, '')) = ''
                                  AND status_label IN ('новый', 'в автоматической обработке')
                                OR lower(trim(responsible)) = lower(trim(CAST(? AS text))))
                    ), ranked AS (
                        SELECT b.*, count(*) OVER (PARTITION BY my_bucket) AS page_total,
                               row_number() OVER (PARTITION BY my_bucket
                                   ORDER BY last_activity DESC NULLS LAST, ticket_id DESC) AS bucket_position
                          FROM buckets b
                    )
                    SELECT d.*, r.display_number, r.my_bucket, r.page_total
                      FROM (SELECT * FROM ranked WHERE bucket_position <= 20 %s) r
                      JOIN source_rows d ON d.ticket_id = r.ticket_id
                     ORDER BY r.my_bucket, r.bucket_position
                    """.formatted(pinnedPredicate), args.toArray());
        }

        List<String> filters = new ArrayList<>();
        if (!q.search().isEmpty()) {
            // strpos is literal substring search: %, _ and backslashes aren't LIKE wildcards.
            filters.add("""
                    strpos(lower(concat_ws(' ', d.ticket_id, d.display_number,
                        COALESCE(NULLIF(d.client_name, ''), NULLIF(d.username, ''), 'Неизвестный клиент'),
                        COALESCE(NULLIF(d.client_status, ''), 'статус не указан'),
                        COALESCE(NULLIF(d.channel_name, ''), 'Без канала'),
                        COALESCE(NULLIF(d.business, ''), 'Без бизнеса'),
                        COALESCE(NULLIF(d.problem, ''), 'Проблема не указана'), d.status_label,
                        concat_ws(', ', NULLIF(d.city, ''), NULLIF(d.location_name, '')),
                        COALESCE(NULLIF(d.categories, ''), '—'), d.responsible,
                        (SELECT u.full_name FROM users u WHERE u.username = d.responsible LIMIT 1))), ?) > 0
                    """);
            args.add(q.search());
        }
        if (!q.status().isEmpty()) {
            filters.add("lower(d.status_label) = ?");
            args.add(q.status());
        }
        String critical = "NOT d.closed AND d.minutes_left <= " + p.criticalMinutes
                + (p.pinUnassignedOnly ? " AND d.unassigned" : "");
        filters.add(switch (q.view()) {
            case "active" -> "NOT d.closed";
            case "new" -> "d.status_label = 'новый' OR lower(d.status) = 'new' OR "
                    + "(d.status_label = 'в автоматической обработке' AND d.unassigned)";
            case "unassigned" -> "d.unassigned";
            case "overdue" -> {
                args.add(Timestamp.from(java.time.Instant.ofEpochMilli(p.nowMs - p.overdueHours * 3_600_000L)));
                yield "NOT d.closed AND d.created_at < CAST(? AS timestamptz)";
            }
            case "sla_critical" -> critical + (p.viewUnassignedOnly ? " AND d.unassigned" : "");
            case "escalation_required" -> p.escalationEnabled
                    ? "NOT d.closed AND d.minutes_left <= " + p.criticalMinutes
                        + " AND COALESCE(NULLIF(trim(d.responsible), ''), NULLIF(trim(d.resolved_by), ''), '') = ''"
                    : "FALSE";
            default -> "TRUE";
        });
        if (q.slaWindowMinutes() != null) {
            filters.add("NOT d.closed AND d.minutes_left <= ?");
            args.add(q.slaWindowMinutes());
        }
        if (!"all".equals(q.view()) && !q.snoozedTicketIds().isEmpty()) {
            filters.add("d.ticket_id NOT IN (" + String.join(",", java.util.Collections.nCopies(q.snoozedTicketIds().size(), "?")) + ")");
            args.addAll(q.snoozedTicketIds());
        }
        String where = filters.stream().map(f -> "(" + f + ")").collect(java.util.stream.Collectors.joining(" AND "));
        String order = "sla_priority".equals(q.sortMode())
                ? "CASE WHEN " + critical + " THEN 0 ELSE 1 END, "
                    + "CASE WHEN d.closed OR d.minutes_left IS NULL THEN 3 WHEN d.minutes_left <= 0 THEN 0 "
                    + "WHEN d.minutes_left <= " + p.warningMinutes + " THEN 1 ELSE 2 END, "
                    + "CASE WHEN NOT d.closed THEN d.minutes_left END ASC NULLS LAST, d.created_at ASC NULLS FIRST, d.ticket_id COLLATE \"C\""
                : "d.last_activity DESC NULLS LAST, d.ticket_id DESC";
        // Counts and the clamped page are from one SQL snapshot, including an empty result.
        args.add(q.pageSize());
        args.add(q.page());
        args.add(q.pageSize());
        // Bound values are never interpolated into SQL; order/filter fragments are fixed allowlists.
        String sql = prefix + ", filtered AS MATERIALIZED (SELECT d.ticket_id, d.display_number, d.last_activity, "
                + "d.closed, d.minutes_left, d.created_at, d.unassigned FROM classified d WHERE " + where + "), "
                + "totals AS (SELECT count(*) AS page_total FROM filtered), "
                + "position AS (SELECT page_total, ?::integer AS page_size, "
                + "least(?::bigint, greatest(1, ceil(page_total::numeric / ?)))::integer AS current_page FROM totals), "
                + "page_rows AS (SELECT d.* FROM filtered d ORDER BY " + order
                + " LIMIT (SELECT page_size FROM position) OFFSET (SELECT (current_page::bigint - 1) * page_size FROM position)) "
                + "SELECT d.*, selected.display_number, p.page_total, p.current_page FROM position p "
                + "LEFT JOIN page_rows selected ON TRUE LEFT JOIN source_rows d ON d.ticket_id = selected.ticket_id ORDER BY "
                + order.replace("d.", "selected.");
        return new Statement(sql, args.toArray());
    }
}
