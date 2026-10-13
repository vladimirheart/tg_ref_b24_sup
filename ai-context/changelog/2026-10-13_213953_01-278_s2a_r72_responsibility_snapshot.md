# 01-278 / S2A / R72 — snapshot guards для ответственных

Время: 2026-10-13 21:39:53 MSK.
Baseline: `5cc0f4f3c29fc306ee317b2c8d96a2b1dd6f0d95`.

Промт пользователя: «продолжай».

Fresh fetch подтвердил HEAD=origin/main. Пользовательский R71 проверен:
parent `536ced7c843d8eae1dc9a8a0b2bfd51f20280f81`, message и пять файлов;
исходное дерево чистое. Удалённое описание R52 не восстанавливается.

Изменённые файлы (4):

- `spring-panel/src/main/java/com/example/panel/service/DialogResponsibilityService.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogResponsibilitySnapshotPostgresqlTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-13_213953_01-278_s2a_r72_responsibility_snapshot.md`

В ResponsibilityService изменены пять SQL spans одного функционального scope:

- Missing assignment INSERT и redirect INSERT используют typed VALUES row
  (text/text/text), прежний NOT EXISTS и unique raw parent count=1.
  Четыре placeholders/Java arguments каждого INSERT сохранены.
- Redirect fallback UPDATE и read-mark UPDATE проверяют parent count=1
  внутри SQL, сохраняя по три args. Parent invalidation после zero-row INSERT,
  до UPDATE, не разрешает переназначение.
- Load SELECT проверяет unique parent в том же snapshot, сохраняя один exact
  ticket argument, mapper/trim и LIMIT 1.
- API, preliminary guard, normalizers/actor fallback, first-owner и legacy
  raw redirect policy неизменны. AI-agent исключается только из missing
  assignment, не из redirect. Read-mark требует прежний exact responsible,
  сохраняет MAX по всей history raw ticket, исторические user/channel mismatch
  rows и CURRENT_TIMESTAMP fallback. DataAccessException catch/log/return
  behavior не переписывается. Это legacy identity integrity, не business scope.

Detached validation:

- Baseline: 139 tests / 24 failures / 0 errors/skipped. RED: 16 late mutation
  cases, четыре stale responsible reads и четыре invalidations между zero-row
  INSERT и UPDATE. Остальные 115 cases GREEN; baseline RED ожидаемый.
- Final Maven: 1227/1227 GREEN в 37 classes, 0 failures/errors/skipped.
  Все 37 fresh XML прочитаны после native exit=0. Включены 1071 regressions R71,
  17 existing responsibility/realtime/lifecycle cases и 139 новых. Компилируются
  полные 643 main / 451 test sources. Все project tests не запускались.
- New cases: plain/padded × missing/duplicate × assign/redirect/read ×
  absent/existing rows; load deny и real JDBC INSERT=0 перед fallback UPDATE.
  Полный child row snapshot сохраняется при deny. Положительные exact ID cases
  проверяют actor trim/fallback, first-owner, raw redirect values, nullable/blank
  inputs, AI-agent policy, assigned_at default, last_read_at и MAX domain.
  Trimmed sibling не переписывается, schema failure сохраняет прежний fallback.
- Real isolated PostgreSQL/JdbcTemplate/guard/responsibility service, default
  JDBC string type. Minimal child schema использует V1-compatible VARCHAR(255)
  PK, NOT NULL responsible, assigned_at default и timestamptz. Полный Flyway/FK
  chain этим не доказывается. Parent changes committed sequentially до SQL,
  не concurrent stress/locking/serializable proof, live provider/browser/
  production или business-scope E2E. Binary/object storage I/O не выполнялся.
- Обратная замена пяти SQL spans восстанавливает весь ResponsibilityService
  baseline: Java bindings, normalizers, exception behavior и signatures сохранены.
  TargetService, ReplyService, Runtime и existing fixtures не менялись;
  negative assertions не ослаблены.
- Source reader v1 сообщил task mismatch до apply. Независимые git blob SHA и
  text comparison подтвердили, что task не менялся; причина mismatch v1 не
  воспроизведена. Пофайловый reader v2 подтвердил равенство всех восьми sources.
  Root до guarded apply оставался чистым, baseline сохранён.
- 10 unchanged JS scripts R27/R29/R33/R48/R50/R52/R53/R54/R55/R56 GREEN.
  Generated Sass CSS — EOL-only, не patch. API/DTO/UI/cache/schema/permissions/
  flags/receipts, history/activity writers и attachment I/O не переписывались.

Sandbox GREEN и source apply разделены. Перед apply проверяются fresh
HEAD/origin/status/blob guards, затем exact four-file scope, normalized
sandbox/root parity и diff check. Working DB, migration/backfill, deploy,
staging, commit/push не выполнялись.

Граница: count=1 действует в statement snapshot. User/channel/business owner
не замораживается после SQL; missing/redirect/read и provider/history/side effects
не становятся атомарными. Concurrent INSERT race/duplicate-key behavior остаётся;
canonical public/composite linkage, locking/outbox/reconciliation, storage
namespaces и business acceptance требуют отдельных scopes.
S2B/R12 PROPOSED_NOT_APPROVED; задача 01-278 остаётся 🟡.
