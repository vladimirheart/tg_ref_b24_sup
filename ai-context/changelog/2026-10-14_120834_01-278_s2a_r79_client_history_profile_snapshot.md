# 01-278 / S2A / R79 — snapshot-safe client history/profile cohorts

Время: 2026-10-14 12:08:34 MSK.
Baseline: `956dd21afed26b6a55532e74ea04f749a9c224f6`.

Промт пользователя: «дальше».

Fresh fetch подтвердил HEAD=origin/main, clean root/empty index. R78 проверен:
parent `1f441a1d64d57d918b8bf22ac9e7d6bfd0a17664`, message и пять файлов
(606 additions / 0 deletions). Удалённое пользователем описание R52 не восстановлено.

Изменённые файлы (4):

- `spring-panel/src/main/java/com/example/panel/service/DialogClientContextReadService.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogClientHistorySnapshotPostgresqlTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-14_120834_01-278_s2a_r79_client_history_profile_snapshot.md`

Один связанный functional scope: два WHERE eligibility predicates client reads.

- loadClientDialogHistory проверяет correlated unique raw parent count=1 для
  каждой messages row, до выдачи history/limit. Existing LEFT JOIN, m.user_id,
  nullable/raw current-ticket exclusion и четыре JDBC bindings сохранены.
- loadClientProfileEnrichment проверяет тот же count=1 до COUNT/MIN/MAX
  aggregation. Default bindings timestamp/userId (2), dynamic thirty-day
  predicate и прежний SELECT/list construction остаются.
- Orphan/duplicate/unlinked message не раскрывается в list и не влияет на
  counters/first_seen_at/last_ticket_activity_at. Данные не удаляются и не
  назначаются бизнесу.
- Мапперы, list/map shape, DISTINCT/CASE/COALESCE, status lower без trim,
  timestamp order/limit, null user/nonpositive limit и DataAccessException
  catches не переписываются. Eligible empty aggregate возвращает zero counts/
  null dates, schema/JDBC failure — прежний empty map/list.
- Current ticket остаётся nullable exclusion, не grant. Client-wide methods
  не получают новый preliminary bare-ticket guard. R78 events, settings
  matching, другие services/API/permissions/feature flags не меняются.

Detached validation:

- Baseline: 117 tests / 36 failures / 0 errors/skipped, actual native exit=1.
  Восемь direct invalid parent, восемь mixed-cohort limit/date/counter,
  восемь unlinked NULL/blank/orphan message IDs, восемь before-SQL invalidations,
  четыре between-history/profile cases RED. Остальные 81 GREEN.
- Final: 2095/2095 GREEN в 53 Java classes, 0 failures/errors/skipped,
  actual native exit=0. Все 53 fresh XML names/counts/testcases/UTC timestamps
  проверены после завершения. Все 1978 R78 cases / 52 classes и 117 новых;
  полные 643 main / 458 test sources компилируются. Не все project tests/E2E.
- Real Testcontainers PostgreSQL 16/default JDBC string type, JdbcTemplate/
  service/TimestampSqlSupport и real history/aggregate mapping. Constructor
  LegacyGuard mock проверяется на no-interactions, не подменяет SQL COUNT.
  Binding assertions читают raw invocation Object[]; profile cutoff — actual
  Timestamp перед userId, с проверкой thirty-day timing.
- Cases покрывают exact padded/slash/reserved/Unicode/percent/null-literal IDs,
  raw-versus-trimmed current exclusion/siblings, limit/timestamp order,
  nullable problem/date/status, raw status/case/no-trim, date COALESCE и
  thirty-day counters, independent profile при history exclusion/limit,
  zero aggregate shape против schema/JDBC empty collection fallback.
- NULL message ticket с total DISTINCT count=0 по-прежнему не вправе раскрывать
  MIN/MAX дат. Missing/duplicate rows не занимают limit перед eligible neighbor
  и не добавляют profile counters/dates.
- Permitted history сохраняется при позднем parent denial следующего profile
  SQL; two reads не становятся атомарными. Zero/negative non-null client IDs
  сохраняют legacy policy; other/NULL messages.user_id не меняют выбранный client.
- Normal minimal V1/V17-compatible fixtures: tickets composite/unique key,
  created_at NOT NULL/default; messages group_msg_id PK, UNIQUE ticket ID,
  nullable user/created/problem и TIMESTAMPTZ types. Только legacy date cases
  снимают NOT NULL с ticket created_at. Full Flyway/channel FK chain не запускается.
  Migration V17 читалась как schema reference, её UPDATE/backfill не выполнялся.
- Sequential committed callback changes до SQL/между reads, не multithread
  stress/concurrency/locking/serializable proof. Parent/message snapshots
  подтверждают отсутствие дополнительных writes. Provider/storage I/O нет.
- Reverse двух SQL spans восстанавливает весь ClientContext service, включая
  R78 events и остальные methods. Одиннадцать fresh baseline sources pinned;
  existing tests, TimestampSqlSupport, LegacyGuard, Lifecycle/Audit/Participant/
  Runtime не изменены; assertions не ослаблены.
- 10 unchanged JS scripts R27/R29/R33/R48/R50/R52/R53/R54/R55/R56 GREEN.
  Generated Sass CSS — EOL-only, не patch.

Sandbox GREEN отделён от root apply: fresh HEAD/origin/status/blob guards,
exact four-file scope, empty index, normalized sandbox/root parity и diff check.
Собственный temporary worktree удаляется только после path/content verification;
остальные worktrees сверяются по path/HEAD/branch/detached.
Working DB, migration/backfill, deploy, staging, commit/push не выполняются.

Граница: count=1 — raw legacy eligibility, не composite/business authorization.
Owner-mismatch control сохраняет lookup через messages.user_id и bare ticket:
canonical linkage и business grants ещё отдельные scopes. S2B/R12
PROPOSED_NOT_APPROVED; задача 01-278 остаётся 🟡.
