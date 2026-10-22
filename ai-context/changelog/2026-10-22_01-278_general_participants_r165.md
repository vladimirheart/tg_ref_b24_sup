# 01-278 — отдельные участники GENERAL, R165

Время: 2026-10-22 12:08:01 +03:00 (Europe/Moscow).

## Пользовательский контекст

Промт пользователя:

> продолжай

Действует уточнение текущей сессии:

> репо обновлён. проверь и погнали дальше. рабочий экземпляр проекта на этой машине, поэтому docker и его контейнеры можно перезапускать.

Ручные команды фиксации не предоставляются. Restart не означает разрешения
выполнить рабочие migration/backfill/flags/rollout. Git grant R160 из другой
сессии не наследуется.

## Baseline и среда

HEAD/origin/main/actual remote main:
628230a6e4d896fcba42a603f5c8e8f6b44c2e3e; parent
40e2ff6277f252e85681d94068198f85f35d4c12.
Проверены subject и 19 фактических файлов R164; main/index перед работой чистые.
Detached sandbox: .tmp/01-278-r165-general-participants.
Docker Server 29.7.2; рабочие web/bot/worker/direct/PostgreSQL/RabbitMQ/Redis
healthy при read-only проверке, без restart/rebuild. Рабочие revision/DB/flags
и functional acceptance не подтверждены. Тестовые PostgreSQL — изолированные,
не рабочая БД. Migration/backfill/config/flags/deploy/staging/commit/push
не выполнялись; пользовательские файлы/данные и rollback paths не удалялись.

## Реализовано

- V80: новые general_ticket_participants и append-only participant events.
  Exact GENERAL owner (user/raw/public), numeric member/actor IDs со snapshots,
  отдельная ревизия списка и owner revision события. Roster UPDATE и event
  UPDATE/DELETE запрещены. Нет импорта legacy/BUSINESS rows или выдачи grants;
  история не следует за удалённым/пересозданным root либо аккаунтом.
- На general-dialog-work — список, ADD/REMOVE, последние 10 событий и отметка
  недоступного участника. Actor требует PAGE_DIALOGS/current GENERAL READ+OPERATE
  и pending/open; member достаточно отдельного GENERAL READ. Участие не даёт
  доступа, ROLE_ADMIN/ALL_BUSINESSES не заменяют GENERAL grants. Non-AI,
  unique enabled/unblocked accounts. Padded principal не trim-ится к чужому имени.
- Формы с настоящим CSRF передают public locator/work revision/list revision/
  member ID, без raw owner в HTML. Сервер разрешает owner заново. Shared raw-ID
  advisory → root/account/grant locks → final INSERT SELECT/DELETE с повтором
  tuple/namespace/unique ID/current rights/двух revisions. ADD также проверяет
  member READ и 200-bound; REMOVE допускается после revoke/disable/delete member.
- Успех атомарно сохраняет roster, immutable event и frozen GENERAL outbox
  participants_changed. Stale/ABA/no-op/конкурентная форма не дают success,
  ошибки event/outbox откатывают обе mutation paths. Статус, work intervals,
  assignee, AI и client/provider delivery не меняются.
- Отдельный GENERAL SSE/Redis принимает participants_changed, повторно проверяет
  frozen owner/current READ и отдаёт только public ID/reason/time. Watcher не
  вызывает legacy bell/assignment/AI/support callbacks. Прежняя аудитория всех
  current GENERAL READ connections сохранена: roster не является grant,
  персональной подпиской или новой bell/client recipient policy.
- Readiness требует обе V80 таблицы. Task summary/семь блоков/ближайшая очередь
  и S0-контракт актуализированы; source minimum V80, общий статус 01-278 — 🟡.

## Проверки

Финальный r165-regression-final-v2.log: 526 Java tests / 8 свежих XML GREEN,
failures/errors/skips=0, exit 0. Compiled/source и XML freshness сверены.

- GENERAL HTTP — 132: прежние 74 и новые 58. Rendered CSRF ADD READ-only member/
  reload/REMOVE, отсутствие raw owner в HTML, отдельные grants/ROLE_ADMIN/ALL
  bounds, late controller и семь final JDBC injections, CAS/concurrent/ABA/no-op,
  пять настоящих ожиданий grant/account/root locks с проверкой committed changes,
  rollback ADD/REMOVE, cap 200, revoked/disabled/deleted member, numeric rename/
  reuse, generation/history immutability, missing schema, независимые revisions.
- V79→V80: полное сравнение старых roots, legacy participants, BUSINESS roster/
  events и GENERAL grants; новые GENERAL таблицы пусты, никаких новых прав.
- GENERAL realtime — 50: whitelisted positive reason, отдельный Redis round-trip,
  metadata-only browser payload, real watcher dispatch без raw callbacks и
  девять frozen owner/READ/account/flag negatives с продвижением cursor.
- Readiness — 28 с двумя missing-V80 checks; BUSINESS HTTP R164 — 114,
  GENERAL assignment — 48, raw participant boundary — 51, legacy GENERAL — 5,
  frozen delivery — 98. В пакете 72 новых Java cases.
- node --check general-dialogs.js и 2 GENERAL Node tests GREEN:
  r165-general-ui-node.log. JS не менялся; реальный server-side template
  проверен HTTP. Live browser/Redis/proxy/providers не проверялись.
- Первый broad run r165-regression-v1.log: 521 tests, один stale upgrade assertion
  RED. Старый R139 test сравнивал SELECT * V68 с latest, хотя V73/V76/V77 добавили
  новые столбцы. Исправление сохраняет точное golden V68→V69, сравнивает каждый
  старый столбец и новые defaults на V79, затем все V79 rows на V80. Отдельный
  assert подтверждает различие формы строк уже на V79, до новой migration.
  Root values, legacy assignments/grants и no-invented-history assertions
  не ослаблены. В окончательном run все 48 assignment cases GREEN.
- Guarded apply: exact manifest 13, baseline blob/absent-target guards,
  fresh HEAD/origin/remote и clean main/index до записи, replay тех же apply_patch
  transformations после GREEN, затем UTF-8/LF SHA-256/file set/diff/index.
  Четыре generated Sass CSS исключаются только при равенстве baseline blobs.
  Логи и sandbox сохранены; source apply не является рабочим rollout.

## Осталось

GENERAL inline/media/VK/MAX reply/change/feedback, отдельная bell/служебная
доставка и recipient policy, historical activity/span/responsible/participant
attribution. Остальные producer/legacy write/read/lifecycle paths, внешние
destinations, system/task/project policy, AI и S2B решения владельца остаются.
V80 и новый reason требуют совместимых consumers и отдельного согласованного
рабочего выпуска/migration/flags/rollback/functional acceptance.

## Точный manifest (13 файлов)

- `ai-context/changelog/2026-10-22_01-278_general_participants_r165.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `spring-panel/src/main/java/com/example/panel/config/PostgresRuntimeReadinessVerifier.java`
- `spring-panel/src/main/java/com/example/panel/controller/GeneralTicketWorkController.java`
- `spring-panel/src/main/java/com/example/panel/service/GeneralTicketParticipantService.java`
- `spring-panel/src/main/java/com/example/panel/service/GeneralTicketRealtimeService.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V80__general_ticket_participants.sql`
- `spring-panel/src/main/resources/templates/general/work.html`
- `spring-panel/src/test/java/com/example/panel/config/PostgresRuntimeReadinessVerifierTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/GeneralTicketWorkHttpPostgresTest.java`
- `spring-panel/src/test/java/com/example/panel/service/GeneralTicketAssignmentPostgresTest.java`
- `spring-panel/src/test/java/com/example/panel/service/GeneralTicketRealtimePostgresTest.java`

