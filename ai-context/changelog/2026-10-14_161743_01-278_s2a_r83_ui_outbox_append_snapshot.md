# 01-278 / S2A / R83 — snapshot-safe UI outbox append

Время: 2026-10-14 16:17:43 MSK.
Baseline: 81686fc22fc191da6e1a1e6a83e817c993d6ee31.

Промт пользователя: «продолжай».

Fresh fetch подтвердил HEAD=origin/main, branch main, clean root/empty index.
R82 проверен: parent 1330ab5664fda8ba4c6ff71289bd73e10a0b0e50,
commit message и четыре файла (649 additions / 0 deletions).
User-removed описание R52 не восстановлено.

Изменённые файлы (4):

- spring-panel/src/main/java/com/example/panel/service/UiEventOutboxAppendService.java
- spring-panel/src/test/java/com/example/panel/service/UiEventOutboxAppendSnapshotPostgresqlTest.java
- ai-context/tasks/task-details/01-278.md
- ai-context/changelog/2026-10-14_161743_01-278_s2a_r83_ui_outbox_append_snapshot.md

Один functional scope: eligibility самой записи UI outbox.

- Preliminary LegacyGuard сохранён; INSERT SELECT повторяет exact raw count=1
  внутри SQL snapshot. Missing/duplicate перед SQL дают 0 affected rows,
  existing outbox не меняется. NULL-channel duplicate учитывается независимо
  от event channel; unrelated duplicate не мешает unique target.
- Первые девять bindings остаются прежними; десятый — raw ticketId без trim.
  Explicit BIGINT/TEXT/INTEGER/TIMESTAMP WITH TIME ZONE casts сохраняют nullable
  numeric/text payloads при default JDBC string type. Ticket CAST AS TEXT
  оставляет VARCHAR target assignment вместо silent CAST AS VARCHAR truncation.
- Все public methods/event types, payload/event-type trim, nullable fields,
  ID clock/node/counter и time generation, void return и propagated INSERT
  exceptions сохранены. Late denied attempt вычисляет ID/time до SQL как раньше.
  Watcher cursor/dispatch, realtime/provider delivery, guard и schema не меняются.

Detached validation:

- Baseline: 96 tests / 26 failures / 0 errors/skipped, native exit=1.
  RED: 12 direct exact raw invalidations и 14 publisher variants;
  остальные 70 controls GREEN. Только functional snapshot assertions RED.
- Final: 2530/2530 GREEN в 60 Java classes, 0 failures/errors/skipped,
  actual native exit=0; все 2421 R82, 96 новых и 13 existing append/watcher.
  60 fresh XML names/counts/testcases/UTC timestamps сверены после completion.
  Полные 643 main / 462 test sources compiled; fresh compiler manifests checked.
  Maven actual 16:34 min; это evidence запуска, не performance claim.
- 10 unchanged JS scripts R27/R29/R33/R48/R50/R52/R53/R54/R55/R56 GREEN.
  Targeted task matrix, не все project tests или business E2E.
- Новый suite использует real PostgreSQL 16 Testcontainers/default JDBC string
  type, real JdbcTemplate/service/LegacyGuard и observer spy только для capture/
  committed callbacks. Нет provider/storage/network delivery I/O.
- Minimal V1/V7-compatible types/composite/unique/NOT NULL keys; channel FK/
  full Flyway chain не выполняются. Duplicate fixtures допустимы при разных
  composite owners и NULL channel. Existing assertions не ослаблены.
- Coverage включает все publish methods/оба close variants и append,
  padded/slash/reserved/Unicode/percent/null-literal IDs/trimmed sibling,
  initial/late missing/duplicate и NULL-channel ambiguity, unrelated mutation,
  payload trim/nulls, numeric types/rating, time/ID shape, parent/outbox snapshots,
  preliminary schema failure и propagated INSERT/outbox schema errors.
- Sequential committed callbacks before/after real INSERT, не concurrency/
  locking/serializable proof. После committed INSERT parent может измениться:
  event не откатывается. Unique replacement с тем же raw ID не распознаётся.
  Event channel не становится owner authority; это не composite/business guard.
- Два unique source anchors, reverse обоих spans восстанавливает весь class.
  12 fresh baseline sources pinned. До Maven исправлены sandbox patch/self-test
  defects: line-deletion/newline comparator и '?' count, ошибочно включавший
  Java ternaries вне SQL. Final test V3/source transform V2 emitted verified
  до matrix; source во время Maven не менялся. Root оставался clean.
- Generated Sass CSS — EOL-only, не patch. Existing tests, guard, watcher,
  delivery/runtime, migrations и flags остаются baseline.

Sandbox GREEN отделён от root apply: fresh HEAD/origin/main/branch/status/blob
guards, exact four-file scope, empty index, normalized content manifest/parity
и diff check. Own temporary worktree убирается только после exact path/HEAD/
content verification; остальные worktrees сверяются по structured records.
Working DB, migration/backfill, flags, deploy, staging, commit/push не выполняются.

Граница: raw legacy eligibility, не canonical owner proof/business authorization.
S2B/R12 PROPOSED_NOT_APPROVED; задача 01-278 остаётся 🟡.
