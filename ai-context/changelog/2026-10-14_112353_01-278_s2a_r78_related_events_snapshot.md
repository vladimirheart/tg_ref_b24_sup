# 01-278 / S2A / R78 — snapshot-safe related events primary/fallback

Время: 2026-10-14 11:23:53 MSK.
Baseline: `1f441a1d64d57d918b8bf22ac9e7d6bfd0a17664`.

Промт пользователя: «дальше».

Fresh fetch подтвердил HEAD=origin/main, clean root/empty index. R77 проверен:
parent `61649b3037d65df7593dd86eecd92afc420760fa`, message, четыре файла
(567 additions / 8 deletions). Рабочая БД/deploy не являются scope продолжения.
Удалённое описание R52 сохраняется удалённым.

Изменённые файлы (5):

- `spring-panel/src/main/java/com/example/panel/service/DialogClientContextReadService.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogClientContextReadServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogRelatedEventsSnapshotPostgresqlTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-14_112353_01-278_s2a_r78_related_events_snapshot.md`

Один связанный functional scope — loadRelatedEvents primary/fallback SQL.

- Пять correlated parent count=1 predicates добавлены в chat_history/
  task_links/dialog_action_audit branches основного UNION ALL и chat_history/
  task_links branches legacy fallback. Каждый SQL использует собственный snapshot.
- Existing primary/fallback JDBC args 4/3 сохранены, дополнительных placeholders
  нет. Raw ticket ID не trim-ится; прежние limit и ordering сохраняются.
- Java preliminary guard, catch/log/fallback branches, sender/type lower без trim,
  COALESCE actor/time, workflow blank-history filter, UNION ALL, mapper map fields
  actor/timestamp/type/detail и audit formatting/defaults неизменны.
- Client-wide dialog history/profile enrichment и settings matching paths
  не переписываются; task origin/business visibility policy не вводится.
- Existing positive T-55 fixture получает одну parent row. Ранее mock guard
  разрешал orphan children. Assertions, schema и остальные cases сохранены:
  fixture соответствует прежнему намерению unique-parent positive path,
  а не обходит добавленный SQL gate.

Detached validation:

- Baseline: 114 tests / 34 failures / 0 errors/skipped, actual native exit=1.
  24 stale-proof failures изолируют event sources и primary/legacy SQL;
  10 failures воспроизводят invalidation между primary/fallback после
  разрешённого primary read с synthetic DataAccessException. Остальные 80 GREEN,
  включая шесть missing workflow controls с реальным FK ON DELETE CASCADE.
- Final: 1978/1978 GREEN в 52 Java classes, 0 failures/errors/skipped,
  actual native exit=0. Все 52 fresh XML names/counts/testcases/UTC timestamps
  сверены после завершения. Matrix сохраняет все 1860 cases / 50 classes R77,
  добавляет четыре existing ClientContext cases и 114 новых.
  Полные 643 main / 457 test sources компилируются. Не все project tests/E2E.
- Real primary/fallback queries проходят через service/JdbcTemplate/guard;
  default JDBC string type, Testcontainers PostgreSQL 16. Binding assertions
  используют raw invocation Object[] и exact raw ticket/limit порядок.
  Read-only snapshots подтверждают отсутствие дополнительных parent/child writes
  после committed callback invalidation и в positives.
- Exact padded/slash/reserved/Unicode/percent/null-literal IDs и trimmed siblings
  не смешиваются. Проверены timestamp-first/sort_id-desc order и limit,
  nullable chat detail/time, sender/type case и отсутствие trim, raw actors,
  audit trim/defaults/case, workflow actor/time COALESCE и исключение blank text,
  invalid IDs/limit, отсутствующая схема и synthetic primary/double failure.
- Normal fixtures сохраняют relevant V1/V34 constraints/types/defaults:
  composite/unique tickets key, identity/seq tasks, task_links composite PK/FK
  ON DELETE CASCADE и task_history FK, chat TIMESTAMPTZ и audit NOT NULL/defaults.
  Только legacy audit cases снимают NOT NULL для nullable actor/action/result
  либо created_at. Полный Flyway/канальный FK chain не запускается.
- Sequential committed invalidation, не multithread/concurrency/locking/
  serializable proof; новые cases без provider/browser/production/storage I/O.
- Replacement control меняет единственный user/channel после positive proof:
  count=1 по-прежнему разрешает legacy raw-ID read; composite/business owner
  не заморожен. Собственная видимость linked tasks этим gate не доказывается.
- Reverse пяти SQL spans восстанавливает весь ClientContext service; reverse
  one-row fixture span восстанавливает existing test. Десять fresh baseline
  sources pinned; TimestampSqlSupport, LegacyGuard, Lifecycle R77, Audit,
  Participant и Runtime неизменны; assertions не ослаблены.
- 10 unchanged JS scripts R27/R29/R33/R48/R50/R52/R53/R54/R55/R56 GREEN.
  Generated Sass CSS — EOL-only, не patch. UI/API/DTO/schema/permissions/flags
  не изменены.

Sandbox GREEN отделён от root apply: fresh HEAD/origin/status/blob guards,
точный five-file scope, empty index, diff check и normalized sandbox/root parity.
Временный собственный worktree удаляется после content/target verification;
остальные worktrees сверяются по path/HEAD/branch/detached, не text block EOF.
Рабочая БД, migration/backfill, deploy, staging, commit/push не выполняются.

Граница: statement count=1 — legacy eligibility, не business authorization,
не frozen canonical owner и не linked task visibility proof. Canonical linkage,
client-wide reads, task grants и business acceptance — отдельные scopes.
S2B/R12 PROPOSED_NOT_APPROVED; задача 01-278 остаётся 🟡.
