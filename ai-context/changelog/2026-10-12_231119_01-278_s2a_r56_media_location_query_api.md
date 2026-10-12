# 01-278 / S2A / R56 — query multipart upload и реквизитов локации

Время: 2026-10-12 23:11:19 MSK.
Baseline: `c4fbe9615e2ba793eb69288054920699d04aff19`.

Промт пользователя: «продолжи».

Подтверждён пользовательский R55 commit: HEAD=origin/main, parent/message/
15 changed files проверены, checkout чистый. Удалённое описание R52 не
восстанавливается.

Изменённые файлы и области (16 project files):

- `spring-panel/src/main/java/com/example/panel/controller/DialogQuickActionsController.java` —
  query multipart POST media делегирует прежнему replyWithMedia; required
  ticketId/file, optional message/replyToTelegramId, operator, permissions,
  timing и audit сохранены.
- `spring-panel/src/main/java/com/example/panel/controller/DialogLocationBusinessApiController.java` —
  query PATCH делегирует прежнему update. Legacy catalog/PATCH URL,
  PAGE_DIALOGS, can_assign, metadata validation/normalization, SQL и audit
  сохранены; canonical business assignment не добавляется.
- `spring-panel/src/main/java/com/example/panel/config/RestExceptionHandler.java` —
  MissingServletRequestPartException получает 400 MISSING_PART вместо
  воспроизведённого generic 500. Это намеренное изменение error contract для
  отсутствующего multipart part, в том числе на legacy route; остальные
  handlers не меняются.
- `spring-panel/src/main/resources/static/js/dialogs-details-history-runtime.js`,
  `spring-panel/src/main/resources/static/js/dialogs-location-business-runtime.js` —
  два query call sites, сохранены multipart body и PATCH payload/CSRF;
  task-link fallback редактора сохраняет nonblank ID без trim.
- `spring-panel/src/main/resources/templates/dialogs/index.html` —
  cache keys двух runtime `20261012-01-278-r56`.
- `spring-panel/src/test/java/com/example/panel/controller/DialogMediaLocationQueryControllerWebMvcTest.java` —
  34 проверки exact ID/file bytes/name/MIME/caption/reply ID/operator/audit,
  required fields, CSRF, permissions/ambiguity, PAGE_DIALOGS, metadata
  normalization/defaults/hierarchy, not-found/conflict и legacy routes.
- `spring-panel/src/test/java/com/example/panel/controller/DialogTicketQueryHttpIntegrationTest.java` —
  сохранены R53/R54/R55 scenarios; добавлены реальный multipart POST и PATCH
  в authenticated session, 403 без CSRF и 200 с токеном. Байты читаются в
  mocked service во время request, до удаления servlet temp file Tomcat.
- `spring-panel/src/test/java/com/example/panel/config/RestExceptionHandlerTest.java` —
  строгая проверка 400 MISSING_PART envelope.
- `spring-panel/src/test/java/com/example/panel/runtime/DialogLocationBusinessEditSourceContractTest.java`,
  `spring-panel/src/test/js/dialogs-secondary-exact-legacy-ticket-id-r29.test.js`,
  `spring-panel/src/test/js/dialog-previous-history-query-api-r52.test.js`,
  `spring-panel/src/test/js/dialog-quick-actions-query-requests-r55.test.js` —
  актуальные legacy/query mappings и URL/cache expectations; остальные
  assertions сохранены, неизменённые runtime остаются на прежних cache keys.
- `spring-panel/src/test/js/dialog-media-location-query-requests-r56.test.js` —
  production sendMediaFiles и editor callbacks с doubles, FormData identity,
  filenames/caption/reply ID, common CSRF wrapper без принудительного multipart
  Content-Type, cleanup/error handling, route/query/task-link ID и PATCH CSRF.
- `ai-context/tasks/task-details/01-278.md` — checkpoint R56;
  этот append-only changelog-файл.

Detached validation: новый WebMvc baseline 33 tests / 28 expected failures;
новый Node-контракт RED на path URL. Первый run после query wrappers выявил
реальный generic 500 при missing file-part и ошибку HTTP test fixture, читавшей
удалённый Tomcat temp file. Missing-part expectation 400 не ослаблена: handler
исправлен, добавлены legacy check и unit test. HTTP byte snapshot перенесён
в обработку request. Итоговый Maven run 158/158 GREEN в десяти классах:
new query MVC (34), legacy quick MVC (40), R55 quick query MVC (63), HTTP (5),
location source contract (1), PostgreSQL uniqueness (2), metadata exact ID (3),
attachment media MVC (1), media MIME/Range (4), error handler (5).
Десять JS files GREEN: R27/R29/R33/R48/R50/R52/R53/R54/R55/R56; шесть syntax
checks и diff check GREEN. Обратная нормализация обоих controller sources
подтвердила неизменность прежних method bodies.

HTTP проверяет точный padded/slash/reserved/plus/Unicode ticketId, binary
multipart bytes, filename/MIME, caption/reply ID, SQL args/operator/audit и
CSRF. Это transport/controller evidence с mock services, не browser или
production business-scope E2E и не отправка внешнему клиенту. Source apply
отдельно защищён fresh HEAD/origin_main/blob/status guard и parity check.
Generated Sass CSS — только Windows EOL status, не patch.

Download by-storage-key/by-path и storage layout не изменялись. Их owner
extraction использует первый path segment с trim; текущие обычные MIME/Range
regressions не доказывают opaque/padded/slash identity на download. Эта
граница оставлена следующим отдельным source scope. Рабочая БД, migration/
backfill, feature flags, deploy, staging, commit и push не выполнялись.
S2B/R12 PROPOSED_NOT_APPROVED, 01-278 — 🟡.
