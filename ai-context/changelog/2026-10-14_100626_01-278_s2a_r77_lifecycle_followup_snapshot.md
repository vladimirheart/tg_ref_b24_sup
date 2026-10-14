# 01-278 / S2A / R77 — snapshot guards для feedback/activity followups

Время: 2026-10-14 10:06:26 MSK.
Baseline: `61649b3037d65df7593dd86eecd92afc420760fa`.

Промт пользователя: «дальше».

Fresh fetch подтвердил HEAD=origin/main, чистый root и пустой index. R76
проверен: parent `561261451b19bc3114ffcf7c97d51fe2bed81525`, message,
четыре файла (600 additions / 4 deletions). Удалённое описание R52 не
восстановлено; новые source pins прочитаны из текущего commit.

Изменённые файлы (4):

- `spring-panel/src/main/java/com/example/panel/service/DialogTicketLifecycleService.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogLifecycleFollowupSnapshotPostgresqlTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-14_100626_01-278_s2a_r77_lifecycle_followup_snapshot.md`

Один связанный functional scope: семь SQL expression spans в lifecycle helpers.

- Feedback UPDATE проверяет raw unique parent внутри statement snapshot,
  сохраняет три bindings и меняет только expires_at/source existing rows.
- Feedback owner SELECT проверяет count=1 до mapper; один raw ticket binding,
  прежние rs.getLong user/channel и owner-null early return сохранены.
- Feedback INSERT SELECT из typed VALUES сохраняет шесть bindings и типы
  bigint/bigint/text/text/timestamptz/timestamptz. Java UTC createdAt,
  expiresAt=createdAt.plusDays(1), actor/source policy не изменены.
- Activity DELETE и identity SELECT проверяют count=1 внутри SQL, каждый
  сохраняет один binding. Identity mapper/trimToNull не переписан.
- Activity UPDATE сохраняет два bindings, blank-only CASE и CURRENT_TIMESTAMP,
  добавляет snapshot count=1. Fallback INSERT SELECT использует typed text/text
  VALUES и прежние два bindings/CURRENT_TIMESTAMP.
- Preliminary eligibility, lifecycle/public DTO, Java branches, helper order,
  catches/log wording, responsibility path и R75/R76 category/status SQL
  сохранены. Нет новой транзакции, locks или изменения API/schema/flags.

Detached validation:

- Generation v1: 92 tests / 0 failures / 28 errors / 0 skipped, exit=1.
  Причина — новая проверка bindings использовала Mockito getArgument,
  возвращающий expanded varargs, вместо raw Object[]. Generation v2 меняет
  только доступ к массивам через getRawArguments[1]/[2]; те же assertions
  сохранены. Real checkout до GREEN не менялся.
- V2 baseline: 92 tests / 24 failures / 0 errors/skipped, native exit=1.
  Поздний missing/duplicate parent для write sites и duplicate для read sites
  воспроизводят нарушение. Четыре missing-parent read controls и остальные
  64 cases GREEN. Fresh XML прочитан после завершения.
- Final Maven: 1860/1860 GREEN в 50 classes, 0 failures/errors/skipped,
  native exit=0. Все 50 fresh XML names/counts/testcases/UTC timestamps
  сверены после actual завершения. Все 1768 R76 cases и 92 новых;
  полные 643 main / 456 test sources компилируются. Это targeted matrix,
  не все project tests либо production/business E2E.
- Все 28 stale-parent cases проверяют реальный public resolve/reopen:
  разрешённый status transition до invalidation, real JDBC update count=0
  либо mapper null и parent/child snapshots без late helper effects.
  Permitted status/activity/category effects остаются committed; публичный
  result не меняется из-за helper denial. Ответственный не вызывается при
  nullable actor; existing R76 cases сохраняют проверки raw nonblank actor.
- Positives покрывают exact raw padded/slash/reserved/Unicode/percent-looking/
  null-literal IDs и untouched trimmed siblings, activity insert и fill только
  nullable/blank identity, raw nonblank identity, timestamps, auto/operator
  feedback source и actor fallback, TTL в сутки и все existing feedback rows
  без переписывания id/user/channel/created_at/sent_at.
- Synthetic helper JDBC failures и missing auxiliary schema сохраняют catch
  и successful transition; clear failure по-прежнему не мешает последующим
  category/feedback writes. Assertion weakening отсутствует.
- Compatibility controls меняют единственный user/channel после helper read:
  INSERT по-прежнему использует уже считанные owner/identity. Count=1 не
  замораживает tuple/business owner и не является business permission.
- Real Testcontainers PostgreSQL 16/default JDBC string type, JdbcTemplate/
  guard/lifecycle, mock responsibility. Minimal V1/V3-compatible fixtures
  сохраняют composite/unique keys, feedback types/NOT NULL/tuple+source
  uniqueness и normal activity NOT NULL identity. Только nullable legacy
  activity case допускает NULL. Channels FK/full Flyway chain не проверяется.
  Invalidation committed sequentially перед SQL, не multithread stress,
  locks/serializable proof; новые cases без provider/storage I/O.
- Reverse seven SQL spans восстанавливает весь LifecycleService baseline.
  Одиннадцать fresh sources pinned; existing tests, DTO, migrations, соседние
  services и Runtime неизменны. 10 unchanged JS scripts R27/R29/R33/R48/R50/
  R52/R53/R54/R55/R56 GREEN. Generated Sass CSS — EOL-only, не patch.
- Read-only progress reader исправлен отдельно: UTC cutoff приводится через
  ToUniversalTime, Measure-Object получает pscustomobject, не ordered hashtable.
  Это diagnostic harness correction, не source/test behavior change.

Sandbox GREEN отделён от root apply: повторные fresh HEAD/origin/status/blob
guards и отсутствие new paths, затем те же validated four-file changes,
exact scope/empty index/diff check/normalized sandbox-root parity.
Working DB, migration/backfill, deploy, flags, staging, commit/push не менялись.

Граница: statement count=1 не делает status/category/feedback/activity/provider/
history атомарными и не фиксирует composite/user/channel/business owner после SQL.
Canonical linkage, locking/outbox/reconciliation и business acceptance остаются
отдельными scopes. S2B/R12 PROPOSED_NOT_APPROVED; задача 01-278 остаётся 🟡.
