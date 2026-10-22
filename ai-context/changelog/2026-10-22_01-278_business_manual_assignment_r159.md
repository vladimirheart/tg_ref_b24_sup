# 01-278 — ручное BUSINESS-назначение с CAS и scoped аудитом (R159)

Дата: 2026-10-22, 07:15:51 Europe/Moscow.

Промт пользователя:

> продолжай

## Выполнено

- Fresh baseline HEAD/origin/main/actual remote main:
  7974ee520ff6e660de14a4e821ec7520c1433c07, parent
  c99db15c48eb0090116f2e521d4c557aadb6a643. Проверены subject и реальные
  14 файлов R158; main/index исходно чистые. Detached sandbox
  .tmp/01-278-r159-manual-business-assignment. Предыдущий пакет не повторялся.
- Новый /business-dialog-assignment/{publicId}: GET формы/current/history,
  POST take/assign, ссылка из scoped карточки и cache-buster R159.
  PAGE_DIALOGS, current READ/OPERATE, CSRF и no-store; форма привязана к полному
  user/raw/public/business и общей responsibility_revision V77. Opaque raw ID
  сохраняется без trim. Take выбирает actor, а не submitted target.
- Actor и assignee: unique enabled/unblocked canonical accounts, active business,
  OPERATOR/MANAGER/ADMIN membership либо ALL_BUSINESSES OPERATE. ROLE_ADMIN сам
  по себе не даёт бизнес-доступ. VIEWER/foreign/disabled/blocked/duplicate/ai_agent
  не допускаются; candidate pool недоступен READ-only, limit 200 указан в UI.
  Назначение не создаёт page authority, membership, READ или новый grant.
- Отдельная REQUIRES_NEW транзакция: legacy-ID advisory guard, root FOR UPDATE,
  account/business/grant FOR SHARE; финальный SQL повторяет current actor/target
  и frozen owner/revision. Shared V77 revision исключает stale/ABA, delete/insert,
  manual/SLA overwrite и повторную success запись. No-op/closed/ambiguous/
  GENERAL/UNRESOLVED/missing scope дают отказ без raw fallback.
- Source V78 добавляет только immutable business_ticket_assignment_events:
  полный owner/business/revision, previous responsible, actor/assignee IDs и
  снимки имён. Projection, audit и frozen business_assignment_changed outbox
  commit атомарно. Audit/outbox failure откатывает назначение и revision.
- Новый outbox callback даёт только metadata invalidation через frozen BUSINESS
  owner/current READ SSE/Redis path. Нет позднего raw capture, bell, queue,
  AI/participant cleanup или provider/support-chat отправки. Канал может быть
  NULL: ручная операция локальная. Смена owner/generation не retag'ит событие.
- Чтение последних 10 новых ручных событий требует exact owner и current READ
  до limit; не наследует чужой бизнес/generation. Старая проекция показана с явной
  оговоркой, не выдаётся за historical attribution. Скрываются/очищаются stale
  ссылки при owner mismatch/смене карточки. SQL readiness требует V78.
- S0-контракт и актуальная карта выполненного/оставшегося обновлены.
  GENERAL, legacy raw take/reassign/reply/bot APIs, участники и AI не расширялись.

## Проверки и источник GREEN

- 701 Java tests / 12 свежих Surefire XML, failures/errors/skips=0 по совокупности
  broad run и исправленного targeted rerun. Новые manual HTTP/PostgreSQL cases 37,
  frozen outbox cases 84 (7 новых), readiness 24; scoped SLA 26+19,
  scoped assignment 108, candidate/routing 43+23, GENERAL HTTP 74,
  AI HTTP 17, selected creation 238 и notification scopes 8.
- Broad log: spring-panel/target/r159-regression-final.log. Девять не затронутых
  исправлением fixture классов GREEN; новые callback cases первоначально RED.
  Финальный corrected cohort: spring-panel/target/r159-corrected-v3.log,
  145 tests в manual HTTP/readiness/frozen outbox, exit 0; fresh XML проверены
  после исправления. Не заявляется, что broad log целиком GREEN.
- Проверены настоящий CSRF из rendered формы/cookie, assign/take/reload/read-only,
  current actor/target permissions, ALL grant, TOCTOU после controller read,
  stale/ABA/opaque tuple, competing workers, общий CAS со SLA, atomic rollback
  audit/outbox, immutable history и V77→V78 без изменения старых полей/истории.
  Full latest migrations V1→V78 и прежние upgrade assertions также выполнены.
- 4 Node checks GREEN: R102 (включая public-ID assignment link), R104 (очистка
  AI/assignment links при mismatch/stale), R117/R134. node --check нового JS GREEN.
  В R104 baseline main воспроизводимо падала из-за отсутствующего removeAttribute
  в DOM double; добавлен API mock и новые assertions, прежние не ослаблены.
- RED HTTP v1: HTML CSRF denial по текущему SecurityConfig даёт redirect /error/403,
  не JSON status 403. Ожидание приведено к реальному contract с проверкой URL и
  отсутствия записей. Callback fixture проверяла реальную queue как mock —
  заменена на spy с сохранением реальных side effects/negative assertions.
  В новых owner-change negatives событие ошибочно создавалось после изменения
  owner; теперь INSERT события предшествует mutation/watch. Проверки no delivery
  сохранены, production guards не ослаблены. RED/interrupted logs сохранены.
- Прогон corrected-final остановлен после выявления второго дефекта callback
  fixture: завершено только проверенное дерево изолированного Maven, не runtime.
  Исправление перепроверено в corrected-v3. Testcontainers — не рабочая приёмка.
- Exact manifest 17 файлов, pinned blobs/new targets, fresh baseline/remote/
  main/index guards; deterministic apply_patch replay после sandbox GREEN.
  UTF-8/LF SHA-256 и postapply file set/diff/index проверены. Четыре Sass CSS
  исключены только по совпадению Git blobs с baseline. Нет прежних SQL rewrites.
- Рабочий экземпляр на этой машине. Docker Server 29.7.2 заново проверен только
  для isolated tests. Health рабочего stack — история R155; рабочие revision/
  DB/flags/live acceptance не подтверждены. Java-bot/fleet/browser/provider/
  live Redis/proxy checks не повторялись.
- Нет staging/commit/push/manual commit commands, restart/rebuild/deploy,
  рабочих migrations/backfill/config/flags, provider sends, prune/удаления пользовательских данных.

## Осталось

- Старые manual/raw assignment, reply/bot/lifecycle и прочие escalation writers:
  новый scoped экран не переводит их автоматически. Producer context до INSERT,
  child historical attribution и общая внешняя recipient/destination policy.
- Участники/AI control при manual handoff, снятие ответственного, retention/
  reconciliation новых audit/outbox, отображение SLA audit, live/fleet acceptance.
- Остальные GENERAL/AI/global-system-task policy/S2B/S3–S6 gates из task details.
  V78 рабочая migration и rollout требуют отдельного разрешения. 01-278 остаётся 🟡.

## Точный manifest (17 файлов)

- `ai-context/changelog/2026-10-22_01-278_business_manual_assignment_r159.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `spring-panel/src/main/java/com/example/panel/config/PostgresRuntimeReadinessVerifier.java`
- `spring-panel/src/main/java/com/example/panel/controller/BusinessTicketAssignmentController.java`
- `spring-panel/src/main/java/com/example/panel/service/BusinessTicketAssignmentService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogRealtimeEventService.java`
- `spring-panel/src/main/java/com/example/panel/service/UiEventOutboxWatcher.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V78__business_manual_assignment_events.sql`
- `spring-panel/src/main/resources/static/js/business-dialogs.js`
- `spring-panel/src/main/resources/templates/businesses/dialog-assignment.html`
- `spring-panel/src/main/resources/templates/businesses/dialogs.html`
- `spring-panel/src/test/java/com/example/panel/config/PostgresRuntimeReadinessVerifierTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/BusinessTicketAssignmentHttpPostgresTest.java`
- `spring-panel/src/test/java/com/example/panel/service/UiEventOutboxFrozenDeliveryPostgresTest.java`
- `spring-panel/src/test/js/business-ticket-root-preview-ui-r102.test.js`
- `spring-panel/src/test/js/business-ticket-scope-reset-ui-r104.test.js`

