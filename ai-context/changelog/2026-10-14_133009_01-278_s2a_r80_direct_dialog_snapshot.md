# 01-278 / S2A / R80 — snapshot-safe direct dialog lookup

Время: 2026-10-14 13:30:09 MSK.
Baseline: `4863f56f934db1a9966920c3a5d1ba6cab71b632`.

Промт пользователя: «дальше».

Fresh fetch подтвердил HEAD=origin/main, clean root/empty index. R79 проверен:
parent `956dd21afed26b6a55532e74ea04f749a9c224f6`, commit message и четыре
файла (550 additions / 0 deletions). User-removed описание R52 не восстановлено.

Изменённые файлы (4):

- `spring-panel/src/main/java/com/example/panel/service/DialogLookupReadService.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogFindSnapshotPostgresqlTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-14_133009_01-278_s2a_r80_direct_dialog_snapshot.md`

Один functional scope: count=1 внутри основного findDialog SELECT.

- Две строки correlated SQL перепроверяют exact raw parent eligibility после
  positive preliminary guard. Поздний duplicate не раскрывает DialogListItem
  или resolveRequestNumber и не запускает profile/photo/daily-number followups.
  Missing parent уже исключался FROM tickets; его regression controls GREEN.
- Bindings operator/raw ticketId (2), предварительный guard, feedback schema
  inspection, LEFT JOIN/COALESCE и все 27 DTO arguments сохранены. Message/
  history timestamp NULLS LAST/group/id tie breakers, unread boundaries, category
  aggregation, latest rating, identity normalization и catches не переписываются.
  loadDialogs/loadSummary, profiles и daily-number SQL остаются прежними.
- Count=1 не фиксирует composite/user/channel/business owner. Unique replacement
  после proof сохраняет legacy lookup; committed removal после successful
  primary не делает enrichment/number атомарными и не отменяет legacy result.
  Message-first user/channel и timestamp offset-normalization helper сохраняются.

Detached validation:

- Test generation V1: 85 tests / 40 failures / 1 error / 0 skipped, native exit=1.
  Помимо 24 targeted late-duplicate failures было 16 неверных control
  expectations (JDBC timezone/unread boundary) и один legacy metadata fixture
  error из-за same-name tables других schemas.
- V2 исправляет тестовые ожидания/fixtures, не production logic: independent
  JDBC-date binding assertion, explicit last_read_at и изолированный legacy
  feedback metadata branch. Baseline: 85 / 24 failures / 0 errors/skipped,
  native exit=1. RED только duplicates: 6 raw IDs × 2 callback boundaries ×
  2 entry points. Остальные 61 control GREEN; missing-parent cases тоже GREEN.
- Final targeted matrix: 2189/2189 GREEN в 55 Java classes, 0 failures/errors/
  skipped, native exit=0. Все 2095 R79, 9 existing Lookup и 85 новых.
  Полные 643 main / 459 test sources компилируются. Не все project tests/E2E.
- Emitted-content comparison выявил шесть leading-indent differences:
  V3 меняет только эти отступы, assertions/token content сохранены. После
  матрицы full 459-source test compilation и повторный Lookup/new suite:
  94/94 GREEN (9 + 85), native exit=0. Все 55 XML names/counts/testcases/UTC
  timestamps сверены после actual completion; два V3 XML новее отдельного run.
- 10 unchanged JS scripts R27/R29/R33/R48/R50/R52/R53/R54/R55/R56 GREEN.
  Generated Sass CSS имеют только EOL changes и не входят в patch.
- Cases проверяют exact padded/slash/reserved/Unicode/percent/null-literal IDs,
  raw bindings и untouched trimmed sibling, before-SQL/after-proof invalidation,
  denied followups/no user-photo interactions, read-only parent/child snapshots,
  projection/defaults, history/message ordering and ties, rating/categories/AI,
  unread case/no-trim, nullable child values, legacy multiple messages,
  feedback-without-ticket metadata branch и JDBC error fallbacks.
- Real PostgreSQL 16 Testcontainers/default JDBC string type, JdbcTemplate/
  service/LegacyGuard и real mappers. Users/photo mocks не выполняют provider/
  storage I/O. Normal minimal V1/V3/V5/V14/V17-compatible types, composite/unique
  keys, NOT NULL identity fields и generated history/feedback IDs. Отдельный
  legacy multiple-message fixture снимает только messages ticket UNIQUE;
  metadata stub изолирует только legacy feedback branch, не SQL COUNT/query.
  Полный Flyway/channel FK chain не запускается; V17 backfill не выполняется.
- Callbacks — sequential committed mutations, не concurrency stress/locking/
  serializable proof. Followup queries не становятся атомарными.
- Reverse одного SQL span восстанавливает весь Lookup class. 14 fresh baseline
  sources pinned; emitted final code проверен. Existing tests, DTO, Guard,
  TimestampSqlSupport/SchemaInspector, R79 ClientContext и Runtime не меняются;
  existing assertions не ослаблены.

Sandbox GREEN отделён от root apply: fresh HEAD/origin/main/branch/status/blob
guards, exact four-file scope, empty index, normalized content manifest/parity
и diff check. Own temporary worktree убирается только после path/HEAD/content
verification; другие worktrees сверяются по path/HEAD/branch/detached records.
Working DB, migration/backfill, flags, deploy, staging, commit/push не выполняются.

Граница: raw legacy eligibility, не composite/business authorization.
S2B/R12 PROPOSED_NOT_APPROVED; задача 01-278 остаётся 🟡.
