# 01-278 / S2A / R84 — SQL snapshot выбора получателя уведомления

Время: 2026-10-14 17:06:27 MSK.
Baseline: 7ae54c4ca542c8ddf26e8180c37781822a73feb2.

Промт пользователя: «продолжи».

Fresh fetch подтвердил HEAD=origin/main, branch main, clean root/empty index.
R83 проверен: parent 81686fc22fc191da6e1a1e6a83e817c993d6ee31,
commit message и четыре файла (505 additions / 2 deletions).
User-removed описание R52 не восстановлено.

Изменённые файлы (4):

- spring-panel/src/main/java/com/example/panel/service/DialogNotificationService.java
- spring-panel/src/test/java/com/example/panel/service/DialogNotificationTargetSnapshotPostgresqlTest.java
- ai-context/tasks/task-details/01-278.md
- ai-context/changelog/2026-10-14_170627_01-278_s2a_r84_notification_target_snapshot.md

Один functional scope: eligibility ticket-scoped recipient SELECT в loadTarget.

- Exact raw parent count=1 перепроверяется внутри SQL snapshot. Missing/duplicate
  после preliminary guard не возвращают recipient: notifyResolved/notifyReopened
  останавливаются до channel/config/template/session/history/provider calls.
- Correlated predicate охватывает полный parent set, независимо от channel,
  и не размножает projection rows. Сохранены raw binding, user/channel projection,
  getLong mapper, date DESC/LIMIT 1 и все другие SQL/Java paths.
- Payload/rating templates, NULL→0 mapper quirk, NULL-first descending dates,
  historical message user/channel mismatch не исправляются в этом scope.
  User-wide latest target, direct/support methods, web-session/history SQL
  и HTTP/provider helpers остаются baseline.

Detached validation:

- Baseline: 106 tests / 24 failures / 0 errors/skipped, actual native exit=1.
  Только late missing/duplicate functional assertions RED для шести raw ID
  и двух actions. Остальные 82 controls GREEN.
- Final: 2640/2640 GREEN в 62 Java classes, 0 failures/errors/skipped,
  actual native exit=0: все 2530 R83, 106 новых и четыре existing notification
  template/guard tests. Все 62 fresh XML names/counts/testcases/UTC timestamps
  сверены после completion. Полные 643 main / 463 test sources compiled;
  fresh compiler manifests подтверждены. Maven actual 19:10 min, не perf claim.
- 10 unchanged JS scripts R27/R29/R33/R48/R50/R52/R53/R54/R55/R56 GREEN.
  Это targeted task matrix, не все project tests или business E2E.
- Coverage: exact padded/slash/reserved/apostrophe/Unicode/percent/null-literal
  IDs/trimmed sibling, initial/late missing/duplicate с NULL channel, unrelated
  mutation, unchanged inputs/existing history, WEB/blank-token fallback,
  missing message/channel, nullable mapper, legacy latest/NULL-first ordering,
  preliminary fail-closed JDBC и propagated recipient SELECT errors.
- Два positive Telegram controls используют mock HttpClient/HttpResponse.
  Реальной provider/storage/network delivery нет. Real PostgreSQL 16
  Testcontainers/default string type, JdbcTemplate/service/LegacyGuard/mapper/
  ObjectMapper; repository/config/normalizer/network service — mocks.
- Minimal V1-compatible types/composite/unique/NOT NULL constraints и generated
  history/session IDs; полный channel FK/Flyway chain не запускается.
  messages unique key ослаблен только в explicit multiple-message fixtures.
- Sequential committed callbacks до/после SELECT, не concurrency/locking/
  serializable proof. Четыре after-selection controls сохраняют delivery/history
  для ранее admitted recipient после parent mutation. Unique replacement с тем
  же raw ID не распознаётся. Это не atomic delivery и не owner/business proof.
- Один unique source anchor, reverse SQL span восстанавливает весь class.
  13 fresh sources pinned. Final emitted Java verified до matrix; source
  во время Maven не менялся. Existing tests/assertions не изменены,
  operator/generation defects не выявлены. Sass CSS — EOL-only, не patch.

Sandbox GREEN отделён от root apply: fresh HEAD/origin/main/branch/status/blob
guards, exact four-file scope, empty index, normalized content manifest/parity
и diff check. Own temporary worktree убирается только после exact path/HEAD/
content verification; остальные worktrees сверяются по structured records.
Working DB, migration/backfill, flags, deploy, staging, commit/push не выполняются.

Граница: raw legacy eligibility, не canonical owner proof/business authorization.
S2B/R12 PROPOSED_NOT_APPROVED; задача 01-278 остаётся 🟡.
