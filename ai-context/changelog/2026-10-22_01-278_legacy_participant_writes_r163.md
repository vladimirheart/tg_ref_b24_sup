# 01-278 — граница legacy INSERT/DELETE участников (R163)

Дата: 2026-10-22, 10:24:35 Europe/Moscow (финальный GREEN HTTP run).

Промт пользователя:

> репо обновлён. проверь и погнали дальше. рабочий экземпляр проекта на этой машине, поэтому docker и его контейнеры можно перезапускать.

## Baseline, среда и полномочия

- HEAD/origin/main/actual remote main: 86bf3e41cbf0acbed5630df47eb2253d044cc5bf,
  parent 039b0acccefc20ff3fd4623c1b9ff9d0bd5a9209, subject
  «fix(multibusiness): изоляция legacy-аудита участников #ai».
  Проверены реальные 5 файлов R162, main/index исходно чистые. Изучены R160–R162;
  прежняя работа не повторялась. Detached sandbox .tmp/01-278-r163-legacy-participant-writes.
- Новое уточнение: рабочий экземпляр здесь, Docker/контейнеры можно перезапускать.
  История другой runtime-сессии R160–R162 не определяет текущую машину.
  Заново подтверждены Docker Server 29.7.2 и healthy panel-web/bot-runner/
  ops-worker/panel-direct/PostgreSQL/RabbitMQ/Redis; это health, не функциональная
  приёмка. Рабочие revision/DB migrations/flags не подтверждены.
- Перезапуск не потребовался. Нет restart/rebuild/deploy/рабочих migrations/
  backfill/config/flags/provider sends. Разрешение restart не подменяет scope
  рабочего migration, в том числе Flyway при startup.
- Grant commit/push из документов R160 относится к той сессии и не перенесён сюда.
  В R163 нет staging/commit/push и ручных команд фиксации.

## Изменение

- DialogParticipantService.addParticipant/removeParticipant: конечные INSERT/
  DELETE допускают только один UNRESOLVED/non-GENERAL root до strictReadScope.
  Обе SQL операции повторяют business_id IS NULL, is_general=FALSE и count всех
  raw-ID roots=1. BUSINESS/GENERAL не допускаются даже при foundation=false.
- Managed service получает flags через явный injected constructor; прежний
  трёхаргументный constructor сохраняет compatibility semantics до strict.
  Strict проверяется до guard/JDBC и снова при формировании SQL arguments.
  Поздние namespace/collision/missing markers/schema не изменяют участников.
  Нет fallback к старому небезопасному SQL при отсутствующих namespace/child полях.
- Сохранены opaque raw IDs без trim, username/added_by normalization, timestamps,
  duplicate/no-op/catch semantics, отсутствие directory требования для legacy
  mutation, неизвестные/AI legacy usernames и соседние строки. Assignment fields
  не меняются. Scoped candidate directory/reads не переписывались.
- Это namespace boundary, не generation lock, не historical child attribution,
  не новое scoped BUSINESS/GENERAL participant API и не recipient/current READ
  policy. Strict после binding не сериализуется до commit: sampling contract
  тот же, что у R161/R162. Старые reply/lifecycle/read workflows остаются gates.
- S0-контракт и текущая карта выполненного/оставшегося обновлены. Source minimum
  V78; новой SQL migration нет.

## Валидация

- Совокупный итог: 410 Java tests / 10 свежих Surefire XML GREEN,
  failures/errors/skips=0. 347 tests в девяти классах из r163-regression-v1.log;
  BusinessTicketAssignmentHttpPostgresTest повторён после добавления 12 participant
  HTTP cases в r163-http-final.log: 63 tests, exit 0. Первоначальный broad run
  также GREEN: 398 tests, до добавления этих 12 cases.
- LegacyParticipantWriteBoundaryPostgresTest — 51: обе операции, BUSINESS/GENERAL/
  strict/collision/missing/null marker/неизвестные namespace поля, поздние изменения
  после unique proof и после binding, no JDBC при strict, opaque trim-neighbour,
  fields/history/no-op, неизменность ответственного и отсутствие directory I/O.
- 12 новых HTTP cases: add/remove × path/query × BUSINESS/GENERAL/strict,
  PAGE_DIALOGS+ROLE_ADMIN не обходят границу. Проверены сохранность participant
  rows, отсутствие raw audit, новых assignment events/outbox и revision changes.
- Включены прежние participant snapshots 73, exact-ID guard 3, GENERAL boundary 5,
  R162 audit boundary 50, quick-action service 35, bot runtime 50,
  authorization 6 и GENERAL HTTP 74. XML freshness и compiled/source timestamps
  сверены. Java-bot/fleet/browser/live provider/Redis/proxy checks не повторялись.
- До source edits отдельно проверен exact baseline: r163-baseline-participants.log,
  81 tests, один stale GENERAL assertion RED. Он ожидал bare-ID assignment GENERAL,
  уже запрещённый R160. Новый GENERAL test имеет canonical root fields, проверяет
  denial assignment и обеих participant mutations с сохранением старых child rows.
  UNRESOLVED golden assertions не ослаблены; в двух minimal fixtures добавлены
  только известные nullable business_id/explicit non-GENERAL namespace columns.
- Guarded source apply: точный manifest 9 файлов, pinned baseline blobs/absent new
  targets, fresh HEAD/origin/actual remote и clean main/index до записи; deterministic
  apply_patch replay после GREEN. UTF-8/LF SHA-256, postapply file set/diff/index
  проверены. Четыре Sass CSS исключены только по равенству baseline Git blobs.
  Logs/worktrees сохранены; пользовательские данные и rollback paths не удалялись.

## Осталось

Scoped participant READ/OPERATE/UI, recipient authority, historical business/
generation attribution, остальные legacy reply/lifecycle/read APIs и producers,
внешние destinations/webhook policy, S2B согласование, AI и сквозная приёмка/rollout.
Этот пакет закрывает две legacy mutation SQL, не всю задачу 01-278; статус 🟡.

## Точный manifest (9 файлов)

- `ai-context/changelog/2026-10-22_01-278_legacy_participant_writes_r163.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `spring-panel/src/main/java/com/example/panel/service/DialogParticipantService.java`
- `spring-panel/src/test/java/com/example/panel/controller/BusinessTicketAssignmentHttpPostgresTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogParticipantLegacyTicketGuardTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogParticipantSnapshotPostgresqlTest.java`
- `spring-panel/src/test/java/com/example/panel/service/LegacyGeneralTicketBoundaryPostgresIntegrationTest.java`
- `spring-panel/src/test/java/com/example/panel/service/LegacyParticipantWriteBoundaryPostgresTest.java`

