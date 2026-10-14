# 01-278 / S2A / R82 — snapshot-safe daily request-number COUNT

Время: 2026-10-14 15:41:09 MSK.
Baseline: `1330ab5664fda8ba4c6ff71289bd73e10a0b0e50`.

Промт пользователя: «дальше».

Fresh fetch подтвердил HEAD=origin/main, branch main, clean root/empty index.
R81 проверен: parent `887f2b75b8abf3cf1e5da149d178dd6261c1f578`,
commit message и четыре файла (701 additions / 0 deletions).
User-removed описание R52 не восстановлено.

Изменённые файлы (4):

- `spring-panel/src/main/java/com/example/panel/service/DialogLookupReadService.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogRequestNumberSnapshotPostgresqlTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-14_154109_01-278_s2a_r82_request_number_snapshot.md`

Один functional scope: eligibility каждой tickets row дневного COUNT cohort.

- assignDailyRequestNumber перепроверяет exact raw parent count=1 внутри SQL
  snapshot. Ambiguous predecessor/late duplicate не увеличивает sequence.
  Missing parent уже исключался FROM tickets; children не считаются без parent.
- Две строки AND сохраняют четыре bindings: ISO day, Timestamp, тот же
  Timestamp, raw ticketId. Date bucket, < / = + ticket_id <=, MIN message time/
  parent COALESCE и весь остальной COUNT SQL сохранены.
- Seed/date/time parsing, legacy offset-dropping normalization, count null/
  nonpositive fallback=1, three-or-more digit formatting и whole-find
  DataAccessException catch не переписываются. DTO/profile enrichment,
  R80 primary, R81 list/My groups, summary и in-memory numbering неизменны.
- Rank фильтруется по полному parent set, а не только selected day/channel.
  Второй parent вне selected day или с NULL channel всё равно делает raw ID
  неоднозначным. Raw IDs не trim-ятся, не нормализуются и не переписываются.
- Count=1 не отменяет уже разрешённый primary DTO и не замораживает owner:
  late duplicate target перед COUNT исключается из rank, но прежний fallback/
  count других eligible rows возвращает live number. Mutation после COUNT
  не меняет уже считанное значение.
- Number — legacy metadata, не immutable identity или business authority.
  Normal mixed fixture согласует list/direct number без duplicate pollution;
  full equivalence не заявляется: helper MIN message date против latest
  primary, explicit mismatched day и timezone quirks остаются. Дополнительная
  blank-ID policy, canonical linkage и business access checks не вводятся.

Detached validation:

- Baseline: 115 tests / 53 failures / 0 errors/skipped, native exit=1.
  RED только targeted ambiguous cases: 36 direct predecessor (same-day/
  outside-day/NULL-channel), 12 committed late predecessor duplicate,
  4 late target duplicate и 1 normal mixed list/direct agreement.
  Остальные 62 controls GREEN, включая missing-parent cases.
- Final: 2421/2421 GREEN в 57 Java classes, 0 failures/errors/skipped,
  actual native exit=0. Все 2306 R81 и 115 новых; полные 643 main / 461 test
  sources компилируются, fresh compiler manifests подтверждают оба counts.
  Все 57 fresh XML names/counts/testcases/UTC timestamps сверены после actual
  completion. Это targeted task matrix, не все project tests/business E2E.
- 10 unchanged JS scripts R27/R29/R33/R48/R50/R52/R53/R54/R55/R56 GREEN.
  Generated Sass CSS — только EOL, не patch.
- Cases покрывают real COUNT values, four typed/raw bindings и read-only
  parent/child snapshots, exact padded/slash/reserved/Unicode/percent/null-literal
  IDs/trimmed sibling, NULL channels и split-day collision, chronology/cutoff/
  ticket tie/day boundaries, nullable date exclusion, ISO/legacy/basic/invalid
  date parsing, mismatched explicit date, number 1000, JDBC/schema failure,
  synthetic null/nonpositive count, primary denial и missing seed/raw number.
- Real PostgreSQL 16 Testcontainers/default JDBC string type, JdbcTemplate/
  service/LegacyGuard/mappers; users/photo mocks проверяются на no-interactions,
  provider/storage I/O отсутствует. Normal minimal V1/V3/V5/V14/V17-compatible
  types/composite/unique/NOT NULL keys и generated history/feedback IDs.
  Nullable ticket created_at и multiple messages — только legacy fixtures;
  999 synthetic predecessors в two formatting controls — только test schema.
  Полный Flyway/channel FK chain не запускается; V17 backfill не выполняется.
- Sequential committed callbacks перед/после COUNT, не concurrency stress/
  locking/serializable proof. Primary и count reads не становятся атомарными.
- Reverse одного COUNT span восстанавливает весь Lookup class, включая R80/R81.
  16 fresh baseline sources pinned; final emitted Java verified до matrix.
  Existing tests, DTO, guard, timestamp/schema support и Runtime не меняются;
  existing assertions не ослаблены. Generation/operator defects не выявлены.

Sandbox GREEN отделён от root apply: fresh HEAD/origin/main/branch/status/blob
guards, exact four-file scope, empty index, normalized content manifest/parity
и diff check. Own temporary worktree убирается только после exact path/HEAD/
content verification; остальные worktrees сверяются по structured records.
Working DB, migration/backfill, flags, deploy, staging, commit/push не выполняются.

Граница: raw legacy eligibility, не composite/business authorization.
S2B/R12 PROPOSED_NOT_APPROVED; задача 01-278 остаётся 🟡.
