package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogListPage;
import com.example.panel.model.dialog.DialogListQuery;
import com.example.panel.support.PanelTimestampSqlSupport;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Real PostgreSQL, isolated schema; never connects to the working panel database. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DialogListPaginationPostgresqlTest {
    private JdbcTemplate jdbc;
    private DialogLookupReadService lookup;
    private final long now = Instant.parse("2026-10-26T12:00:00Z").toEpochMilli();
    private final DialogListPageSql.Policy policy = new DialogListPageSql.Policy(180, 60, 30, 24, true, false, false, now);

    @BeforeAll void schema() {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("dialogs_pagination");
        Flyway.configure().dataSource(jdbc.getDataSource()).locations("classpath:db/migration/postgresql").load().migrate();
        lookup = new DialogLookupReadService(jdbc, jdbc, mock(PanelUserPhotoService.class),
                new PanelTimestampSqlSupport(), new LegacyTicketIdJdbcGuard(jdbc));
    }

    @BeforeEach void reset() {
        jdbc.execute("TRUNCATE tickets, messages, channels, chat_history, ticket_responsibles, ticket_categories, "
                + "ticket_ai_agent_state, client_statuses, feedbacks CASCADE");
        jdbc.update("INSERT INTO channels(id,token,channel_name) OVERRIDING SYSTEM VALUE VALUES (1,'test1','Канал поддержки'), (2,'test2','Другой канал')");
    }

    private void seed(int count) {
        jdbc.update("""
            INSERT INTO tickets(user_id,ticket_id,status,channel_id,created_at)
            SELECT 100000+n, 'T-' || lpad(n::text,5,'0'), 'open', 1,
                   timestamptz '2026-10-26 09:00:00+00' + n * interval '1 second'
              FROM generate_series(1,?) n
            """, count);
        jdbc.update("""
            INSERT INTO messages(group_msg_id,user_id,ticket_id,client_name,username,business,channel_id,
                                 city,location_name,problem,created_at,created_date,created_time)
            SELECT user_id,user_id,ticket_id,'Клиент '||ticket_id,'user_'||ticket_id,'Бизнес',channel_id,
                   'Город','Ресторан','Проблема '||ticket_id,created_at,'2026-10-26',to_char(created_at,'HH24:MI:SS')
              FROM tickets
            """);
    }

    private DialogListQuery query(int page, int size, String search, String status, String view, Integer window, String sort, List<String> snoozed) {
        return new DialogListQuery(page, size, search, status, view, window, sort, snoozed);
    }
    private DialogListPage page(DialogListQuery q) { return lookup.loadDialogsPage("operator", q, policy); }

    @Test void tenThousandRecordsReturnOnlyTheRequestedPageAndBoundedMyPreview() {
        seed(10000);
        long started = System.nanoTime();
        var first = page(DialogListQuery.defaults());
        var last = page(query(Integer.MAX_VALUE, 100, "", "", "all", null, "default", List.of()));
        var preview = lookup.loadMyDialogsPreview("operator", policy);
        assertThat(first.dialogs()).hasSize(20);
        assertThat(first.totalItems()).isEqualTo(10000);
        assertThat(first.totalPages()).isEqualTo(500);
        assertThat(first.dialogs().get(0).ticketId()).isEqualTo("T-10000");
        assertThat(first.dialogs().get(0).requestNumber()).isEqualTo("20261026-10000");
        assertThat(last.page()).isEqualTo(100);
        assertThat(last.dialogs()).hasSize(100);
        assertThat(preview.dialogs().newUnassigned()).hasSize(20);
        assertThat(preview.counts().get("new")).isEqualTo(10000);
        System.out.printf("PAGINATION_EVIDENCE roots=10000 first_rows=20 last_rows=100 my_rows=20 elapsed_ms=%d%n",
                (System.nanoTime() - started) / 1_000_000);
    }

    @Test void numbersDoNotRestartWhenFilteringOrChangingPageSize() {
        seed(45);
        var all = lookup.loadDialogs("operator");
        var second = page(query(2, 10, "", "", "all", null, "default", List.of()));
        assertThat(second.dialogs()).extracting(DialogListItem::ticketId).containsExactlyElementsOf(
                all.subList(10, 20).stream().map(DialogListItem::ticketId).toList());
        assertThat(second.dialogs()).extracting(DialogListItem::requestNumber).containsExactlyElementsOf(
                all.subList(10, 20).stream().map(DialogListItem::requestNumber).toList());
        var found = page(query(1, 10, all.get(30).requestNumber(), "", "all", null, "default", List.of()));
        assertThat(found.totalItems()).isEqualTo(1);
        assertThat(found.dialogs().get(0).requestNumber()).isEqualTo(all.get(30).requestNumber());
    }

    @ParameterizedTest @ValueSource(strings = {" T/100?&%# Ю ", "null", "ticket,comma", "тип_%_\\", "x');DROP TABLE tickets;--"})
    void literalSearchAndSnoozePreserveOpaqueTicketIds(String raw) {
        seed(25);
        jdbc.update("UPDATE tickets SET ticket_id=? WHERE ticket_id='T-00001'", raw);
        jdbc.update("UPDATE messages SET ticket_id=? WHERE ticket_id='T-00001'", raw);
        var found = page(query(1, 20, raw.trim(), "", "all", null, "default", List.of()));
        assertThat(found.dialogs()).extracting(DialogListItem::ticketId).containsExactly(raw);
        var snoozed = page(query(1, 100, "", "", "active", null, "default", List.of(raw)));
        assertThat(snoozed.totalItems()).isEqualTo(24);
        assertThat(snoozed.dialogs()).extracting(DialogListItem::ticketId).doesNotContain(raw);
        assertThat(page(query(1, 100, "", "", "all", null, "default", List.of(raw))).totalItems()).isEqualTo(25);
    }

    @Test void generalAndMixedScopeCollisionsStayOutOfPageSearchAndNumbering() {
        seed(3);
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,channel_id,is_general,general_selection_source) VALUES (90001,'T-00001',2,TRUE,'GENERAL_CHANNEL'), (90002,'GENERAL',2,TRUE,'GENERAL_CHANNEL')");
        var result = page(DialogListQuery.defaults());
        assertThat(result.totalItems()).isEqualTo(2);
        assertThat(result.dialogs()).extracting(DialogListItem::ticketId).containsExactly("T-00003", "T-00002");
        assertThat(result.dialogs()).extracting(DialogListItem::requestNumber).containsExactly("20261026-002", "20261026-001");
        assertThat(page(query(1, 20, "T-00001", "", "all", null, "default", List.of())).dialogs()).isEmpty();
        assertThat(lookup.loadMyDialogsPreview("operator", policy).counts().get("new")).isEqualTo(2);
    }

    @Test void filtersAndSlaOrderApplyBeforeLimit() {
        seed(50);
        jdbc.update("UPDATE tickets SET status='resolved',resolved_by='auto' WHERE ticket_id='T-00050'");
        jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible) VALUES ('T-00001','operator'),('T-00002','someone_else')");
        jdbc.update("INSERT INTO chat_history(ticket_id,sender,message,timestamp) VALUES ('T-00001','user','Вопрос',timestamptz '2026-10-26 11:59:00+00')");
        assertThat(page(query(1, 10, "", "Закрыт автоматически", "all", null, "default", List.of())).dialogs())
                .extracting(DialogListItem::ticketId).containsExactly("T-00050");
        assertThat(page(query(1, 10, "", "", "active", null, "default", List.of())).totalItems()).isEqualTo(49);
        // Auto-closed tickets show Авто-система as their responsible, matching the existing list.
        assertThat(page(query(1, 10, "", "", "unassigned", null, "default", List.of())).totalItems()).isEqualTo(47);
        assertThat(page(query(1, 10, "", "", "all", 30, "sla_priority", List.of())).dialogs().get(0).ticketId()).isEqualTo("T-00001");
        assertThat(page(query(1, 10, "", "", "escalation_required", null, "default", List.of())).totalItems()).isEqualTo(47);
        var mine = lookup.loadMyDialogsPreview("operator", policy);
        assertThat(mine.counts()).containsEntry("new", 47L).containsEntry("unanswered", 1L).containsEntry("in_work", 0L);
    }

    @Test void emptyInvalidAndExtremeParametersAreBounded() {
        assertThat(new DialogListQuery(0, Integer.MAX_VALUE, null, null, "bad", -1, "bad", null))
                .isEqualTo(query(1, 100, "", "", "all", null, "default", List.of()));
        assertThat(new DialogListQuery(0, 0, null, null, null, null, null, null).pageSize()).isEqualTo(20);
        var result = page(query(Integer.MAX_VALUE, 100, "missing", "", "all", null, "default", List.of()));
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalItems()).isZero();
        assertThat(result.dialogs()).isEmpty();
        assertThatThrownBy(() -> query(1, 20, "x".repeat(501), "", "all", null, "default", List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void mixedLegacyDateFormatsAndSecondPrecisionKeepTheSameNumbers() {
        seed(4);
        jdbc.update("UPDATE messages SET created_date='26.10.2026' WHERE ticket_id='T-00002'");
        jdbc.update("UPDATE messages SET created_date='20261026' WHERE ticket_id='T-00003'");
        jdbc.update("UPDATE messages SET created_date=NULL WHERE ticket_id='T-00004'");
        var legacy = lookup.loadDialogs("operator");
        assertThat(page(DialogListQuery.defaults()).dialogs()).extracting(DialogListItem::requestNumber)
                .containsExactlyElementsOf(legacy.stream().map(DialogListItem::requestNumber).toList());
    }

    @Test void myPreviewCountsAllThreeGroupsAndKeepsAnExplicitPinOutsideTheLatestTwenty() {
        seed(70);
        jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible) SELECT ticket_id,'operator' FROM tickets WHERE user_id <= 100050");
        jdbc.update("""
            INSERT INTO chat_history(ticket_id,sender,message,timestamp)
            SELECT ticket_id,'user','Вопрос',timestamptz '2026-10-26 11:59:00+00' FROM tickets WHERE user_id <= 100025
            """);
        var preview = lookup.loadMyDialogsPreview("operator", policy);
        assertThat(preview.dialogs().newUnassigned()).hasSize(20);
        assertThat(preview.dialogs().unanswered()).hasSize(20);
        assertThat(preview.dialogs().inWork()).hasSize(20);
        assertThat(preview.counts()).containsEntry("new", 20L).containsEntry("unanswered", 25L).containsEntry("in_work", 25L);
        var q = new DialogListQuery(1,20,"","","all",null,"default",List.of(),List.of("T-00001"));
        var pinned = lookup.loadMyDialogsPreview("operator", policy, q);
        assertThat(pinned.dialogs().unanswered()).hasSize(21).extracting(DialogListItem::ticketId).contains("T-00001");
        assertThat(pinned.counts()).isEqualTo(preview.counts());
    }

    @Test void realReadServiceAndSqlRoundTripThroughGetAndJsonQueryWithoutWrites() throws Exception {
        seed(45);
        var settings = mock(SharedConfigService.class);
        org.mockito.Mockito.when(settings.loadSettings()).thenReturn(Map.of());
        var service = new DialogListReadService(lookup, settings, new DialogSlaRuntimeService());
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new com.example.panel.controller.DialogListController(service)).build();
        var auth = new org.springframework.security.authentication.TestingAuthenticationToken("operator", "n/a", "PAGE_DIALOGS");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dialogs").principal(auth))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.dialogs.length()").value(20))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.pagination.total_items").value(45))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.my_dialogs.counts.new").value(45));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/dialogs/query").principal(auth)
                        .contentType("application/json").content("""
                            {"query":{"page":3,"pageSize":20,"view":"all"},"includeOverview":false}
                            """))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.dialogs.length()").value(5))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.dialogs[0].ticketId").value("T-00005"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.dialogs[0].requestNumber").value("20261026-005"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.summary").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tickets", Long.class)).isEqualTo(45);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles", Long.class)).isZero();
    }
}
