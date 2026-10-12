# 01-278 / S2A / R55 — query API для quick actions и записи участников

Время: 2026-10-12 22:49:02 MSK.
Baseline: `a51c18d4a1a1f9f626b1c92e8e8f18a32999550d`.

Промт пользователя: «продолжай».

Подтверждён пользовательский R54 commit: HEAD=origin/main, parent/message/
12 changed files проверены, checkout чистый. Удалённое описание R52 не
восстанавливается.

Изменённые файлы и области (15 project files):

- `spring-panel/src/main/java/com/example/panel/controller/DialogQuickActionsController.java` —
  12 query POST/DELETE wrappers для reply/edit/delete, resolve/reopen/spam,
  categories/take/snooze, participants add/remove и reassign вызывают прежние
  методы. Required ticketId и DELETE username идут через query без trim.
  DTO/defaults, permissions/action names, operator, timing, audit, errors и
  все 13 legacy mappings сохранены, включая неизменённый multipart media.
- `spring-panel/src/main/resources/static/js/dialogs-actions-runtime.js`,
  `spring-panel/src/main/resources/static/js/dialogs-details-history-runtime.js`,
  `spring-panel/src/main/resources/static/js/dialogs-workspace-runtime.js`,
  `spring-panel/src/main/resources/static/js/dialogs-participants-runtime.js`,
  `spring-panel/src/main/resources/static/js/dialogs-templates-runtime.js` —
  16 mutation fetch call sites передают encoded exact ticketId через query;
  DELETE участников также передаёт encoded username. Payload, UI guards,
  default credentials и существующая CSRF-обвязка сохранены.
- `spring-panel/src/main/resources/templates/dialogs/index.html` —
  cache keys пяти изменённых runtime `20261012-01-278-r55`.
- `spring-panel/src/test/java/com/example/panel/controller/DialogQuickActionsQueryControllerWebMvcTest.java` —
  63 новых проверки required ID/body/username, permissions, CSRF, точных args,
  defaults, operator, response, validation/errors и audit всех 12 операций.
- `spring-panel/src/test/java/com/example/panel/controller/DialogTicketQueryHttpIntegrationTest.java` —
  сохранены R53/R54 scenarios; добавлены реальный HTTP POST reply и DELETE
  participant через authenticated session с 403 без CSRF и 200 с токеном.
- `spring-panel/src/test/js/dialog-quick-actions-query-requests-r55.test.js` —
  URL-контракт всех 16 mutation call sites для четырёх вариантов opaque ID;
  11 реальных fetch из извлечённых production functions/callbacks с doubles,
  body/default credentials, blank-ID и 403 checks, пять cache keys.
- `spring-panel/src/test/js/dialogs-secondary-exact-legacy-ticket-id-r29.test.js`,
  `spring-panel/src/test/js/dialog-previous-history-query-api-r52.test.js`,
  `spring-panel/src/test/js/dialog-read-query-requests-r53.test.js` —
  актуализированы только точные URL/cache expectations, остальные assertions
  сохранены; cache key неизменённого details runtime остаётся R53.
- `ai-context/tasks/task-details/01-278.md` — checkpoint R55;
  этот append-only changelog-файл.

Detached validation: новый WebMvc baseline 63 tests / 51 expected failures,
новый Node-контракт RED на path URL. Итоговый Maven run: 185/185 GREEN в шести
классах — query quick controller (63), legacy quick controller (40), quick-action
service (32), participant legacy guard (3), HTTP integration (4), query AI R54
(43). Participant guard использует изолированную PostgreSQL Testcontainers
схему и подтверждает отсутствие изменения trimmed sibling. Девять JS test
files GREEN (R27/R29/R33/R48/R50/R52/R53/R54/R55), шесть syntax checks и diff
check GREEN. Обратная нормализация controller source подтвердила побайтное
после EOL normalization совпадение всех прежних method bodies; 13 legacy
и 12 query mappings.

HTTP проверяет padded/slash/reserved/plus/Unicode ticketId и username, точные
service args, body, operator и audit POST/DELETE. Это transport/controller
evidence с mock services, не browser/production business-scope E2E и не
реальная отправка внешнему клиенту. Source apply отдельно защищён fresh
HEAD/origin_main/blob/status guard и parity check. Generated Sass CSS имеет
только Windows EOL status и не входит в пакет. Multipart upload, media
download и location-business остаются отдельными следующими scopes.
Рабочая БД, migration/backfill, feature flags, deploy, staging, commit и push
не выполнялись. S2B/R12 PROPOSED_NOT_APPROVED, 01-278 — 🟡.
