# 01-278: точный ticket ID в transport debug/recovery (R34)

Дата и время: 2026-10-12 12:01:15, Europe/Moscow.

Промт пользователя: `дальше`

## Фактический пакет

- UI Inspect, targeted inbound replay и outbound requeue сохраняют исходный
  nonblank ticket ID до encodeURIComponent. Пустой/blank input отклоняется.
  Replay/requeue click handlers получают event и передают currentTarget в
  существующий pending/recovery guard; раньше использовалась global event.
- Server transport debug, targeted replay/requeue и operation log сохраняют
  точный ID до SQL, ответа и audit. Ticket target_id в audit сохраняется и
  при чтении. Остальная нормализация event/worker/actor/text не менялась.
- IncidentService read-only ticket relation lookup не подменяет padded ID
  plain sibling. Generic relation keys не менялись. Создание/поиск incident
  relations — отдельная оставшаяся граница, не закрытая этим пакетом.

## Baseline, тесты и перенос

- Fresh fetch: HEAD=origin/main=`2717e6b2955b3523b23a0d95f35ebaebbb68682f`,
  parent `a31280daf87c96d1de1a79289a50406c428b5d9a`.
  Проверены message и фактические 4 файла R33. Root исходно чистый;
  source и тестовые blobs pinned.
- Detached worktree, tests before production: Node 36 cases,
  14 pass/22 fail. Java v1 test-compile нашёл 3 generic wildcard errors;
  v2 оставил прежние size+exact-row assertions без ослабления. Baseline v2
  15 tests, 4 failures, 0 errors/skipped. Test count в Node harness
  исправлен 35→36 до source fix; остальные assertions сохранены.
- После source fix: Node 36/36; R27/R29/R33 ещё 50/50 GREEN.
  Full IIFE с DOM/fetch doubles проверяет request URL, CSRF, disabled/pending,
  duplicate click, failure cleanup, stale debug responses, blank/no request.
  Реальный браузер и transport network не вызывались.
- Maven wrapper: `.\mvnw.cmd '-Dtest=IntegrationTransportOpsServiceTest,IncidentServiceTest,AnalyticsIntegrationTransportControllerWebMvcTest,IncidentWorkbenchCredentialRotationUiContractTest,MultibusinessFeatureFlagsTest' test`.
  21/21 tests, 5 классов, 0 failures/errors/skipped. Новые Java tests: 5.
  Реальный PostgreSQL только временный Testcontainers. Ingestion/outbox
  delegates mocked; рабочая БД, delivery и production migrations не затронуты.
- Sass compile дал четыре status-only CSS EOL changes в sandbox; git diff
  не содержит CSS. JS syntax, diff --check, exact 8-file scope GREEN.
  Guarded transfer: fresh fetch и clean HEAD=origin/main, pinned blobs,
  отсутствие new-file конфликтов; те же patches в root, full normalized
  content equality и пустой staging.

## Границы

- S2A source checkpoint, 01-278 🟡. S2B/R12 остаются
  PENDING/PROPOSED_NOT_APPROVED; business owner не угадывается.
- Не является browser/manual E2E acceptance либо полным business scope.
  Следующий review: ticket relation create/search в IncidentService и UI,
  где пока отдельно нормализуется relation key.
- Рабочая/production БД, aliases/assignment, backfill, flags и runtime не
  менялись. Staging, commit, push и deployment агентом не выполнялись.

## Изменённые файлы

- `spring-panel/src/main/resources/static/js/incidents-workbench.js`
- `spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportOpsService.java`
- `spring-panel/src/main/java/com/example/panel/service/IncidentService.java`
- `spring-panel/src/test/js/incidents-transport-exact-ticket-id-r34.test.js`
- `spring-panel/src/test/java/com/example/panel/service/integration/IntegrationTransportOpsServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/IncidentServiceTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-12_120115_01-278_transport_exact_ticket_identity.md`
