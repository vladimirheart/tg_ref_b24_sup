# 01-278 / S2A / R76 — snapshot guards для close/reopen status SQL

Время: 2026-10-14 09:26:03 MSK.
Baseline: `561261451b19bc3114ffcf7c97d51fe2bed81525`.

Промт пользователя: «дальше».

Fresh fetch подтвердил HEAD=origin/main и чистое дерево. R75 проверен:
parent `811aae45c326c889a2d1dfa460f6272650c33ca0`, message и четыре файла
(460 additions / 2 deletions). Удалённое описание R52 не восстанавливается.

Изменённые файлы (4):

- `spring-panel/src/main/java/com/example/panel/service/DialogTicketLifecycleService.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogLifecycleStatusSnapshotPostgresqlTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-14_092603_01-278_s2a_r76_lifecycle_status_snapshot.md`

Один функциональный scope: четыре SQL spans для status read/transition.

- Resolve status SELECT и reopen status SELECT проверяют unique raw parent
  count=1 внутри statement snapshot, до передачи status в mapper/Java decisions.
  Один ticket binding каждого SELECT и mapper trimToNull сохранены.
- Close UPDATE и reopen UPDATE также проверяют count=1 внутри своих snapshots.
  Прежние status predicates, close args resolvedBy/ticket (2), reopen ticket (1),
  COALESCE counter increments, resolved_at/by reset и timestamps сохранены.
- Preliminary guard, raw ID без trim, actor fallback/whitespace, result DTO,
  сообщения already_closed/not_closed/empty categories, DataAccessException
  catch/log/return и updated>0 followup branches не переписываются.
  exists остаётся результатом прежнего preliminary eligibility path, не новой
  гарантией свежего наличия или canonical owner после SELECT/UPDATE.

Detached validation:

- Baseline: 125 tests / 8 failures / 0 errors/skipped, native exit=1.
  Четыре plain/padded duplicate status reads после real positive guard и
  четыре duplicate status UPDATE после допустимого SELECT RED. Missing-parent
  controls и остальные positives GREEN: 117 cases.
- Final Maven: 1768/1768 GREEN в 49 classes, 0 failures/errors/skipped,
  native exit=0. Все 49 fresh XML прочитаны после завершения; exact имена,
  testcase counts и timestamps сверены. Полные 643 main / 455 test sources
  компилируются; все project tests не запускались.
- Matrix сохраняет все 1643 cases / 48 classes R75, включая actual legacy
  LifecycleService, R75 category, QuickActionService и legacy/query controllers;
  добавляет 125 новых PostgreSQL cases.
- Реальный status ResultSetExtractor проверяется отдельно от DTO; deny UPDATE
  возвращает real JDBC count=0. Parent/child snapshots и no-responsibility-I/O
  assertions подтверждают отсутствие followups после отклонённого transition.
  Статус, сменённый на противоположный между SELECT/UPDATE, по-прежнему даёт
  zero-row update через прежний conditional status predicate.
- Positives покрывают exact padded/slash/reserved/Unicode/percent-looking/
  null-literal IDs и untouched trimmed siblings; nullable counters, raw actor
  и Оператор fallback, resolved/reopen dates, category/feedback/activity effects,
  raw responsibility argument и отсутствие этого call для nullable/blank actor.
- No-op cases сохраняют already_closed precedence до пустых категорий, русское
  empty-category сообщение, not_closed и Java trim versus SQL lower без trim:
  padded Closed распознаётся как закрытый на read, но reopen UPDATE=0, как раньше.
  Auto-close source/actor, blank-only activity identity fill, invalid IDs,
  missing schema и synthetic JDBC read/update failures сохраняют fallback.
- Compatibility control меняет единственный user_id после status SELECT:
  count=1 разрешает прежний legacy transition. Composite/business owner не
  заморожен; это ограничение gate, не business authorization.
- Testcontainers PostgreSQL/default JDBC string type, реальные JdbcTemplate/
  guard/lifecycle service, mock responsibility с exact call/no-interactions checks.
  Minimal schema сохраняет composite tickets key, status/counter/time columns,
  V3-compatible category DDL и необходимые auxiliary columns. Полный Flyway/FK
  chain не запускается. Callback invalidations/status changes committed
  sequentially до следующего SQL, не multithread stress/locking/serializable proof.
  New cases не выполняют provider/attachment/object-storage I/O.
- Обратная замена четырёх SQL spans восстанавливает весь LifecycleService
  baseline, включая R75 category INSERT/DELETE и неизменные feedback/activity
  helpers. Existing tests, migration V3, DTO, AuditService, ParticipantService,
  ResponsibilityService и Runtime неизменны; assertions не ослаблены.
  Девять baseline sources сверены пофайлово.
- 10 unchanged JS scripts R27/R29/R33/R48/R50/R52/R53/R54/R55/R56 GREEN.
  Generated Sass CSS — EOL-only, не patch. API/UI/cache/schema/permissions/flags
  не переписывались; provider/browser/production/business E2E не заявляется.

Sandbox GREEN и root source apply разделены. Перед apply проверяются fresh
HEAD/origin/status/blob guards и отсутствие новых paths; затем exact four-file
scope, normalized sandbox/root parity и diff check. Working DB, migration/
backfill, deploy, staging, commit/push не выполнялись.

Граница: count=1 действует в statement snapshot, не фиксирует composite/user/
channel/business owner после SQL и не предоставляет business grant. Status
transition не становится атомарным с feedback/activity/provider/history/audit.
Followup helper SQL guards, canonical public/composite linkage, locking/outbox/
reconciliation и business acceptance остаются отдельными scopes.
S2B/R12 PROPOSED_NOT_APPROVED; задача 01-278 остаётся 🟡.
