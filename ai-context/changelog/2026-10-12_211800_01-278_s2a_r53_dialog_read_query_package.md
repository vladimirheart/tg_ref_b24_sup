# 01-278 / S2A / R53 — query-чтение workspace, заметок и участников

Время: 2026-10-12 21:18 MSK. Baseline: `fb4e5084d59798fb0e610239bbef47afc09121c1`.

Промпт пользователя: «продолжи более широким пакетом».

Подтверждён код R52; последующее пользовательское удаление его описания из
task-details сохранено.

Изменённые области и файлы:

- `spring-panel/src/main/java/com/example/panel/controller/DialogWorkspaceController.java`,
  `spring-panel/src/main/java/com/example/panel/controller/DialogKnowledgeNotesApiController.java`,
  `spring-panel/src/main/java/com/example/panel/controller/DialogTicketQueryReadController.java` —
  три query GET endpoints с прежними сервисами и совместимыми legacy routes.
- `spring-panel/src/main/java/com/example/panel/service/KnowledgeNoteLocationService.java` —
  lookup заметок сохраняет исходный nonblank ticket ID.
- `spring-panel/src/main/java/com/example/panel/config/RestExceptionHandler.java` —
  отказ method security возвращает 403 FORBIDDEN вместо generic 500.
- `spring-panel/src/main/resources/static/js/dialogs-workspace-runtime.js`,
  `spring-panel/src/main/resources/static/js/dialogs-details-runtime.js`,
  `spring-panel/src/main/resources/static/js/dialogs-participants-runtime.js`,
  `spring-panel/src/main/resources/templates/dialogs/index.html` —
  query requests и обновлённые cache keys трёх runtime.
- `spring-panel/src/test/java/com/example/panel/controller/DialogWorkspaceControllerWebMvcTest.java`,
  `spring-panel/src/test/java/com/example/panel/controller/DialogTicketQueryReadControllerWebMvcTest.java`,
  `spring-panel/src/test/java/com/example/panel/controller/DialogKnowledgeNotesApiControllerWebMvcTest.java` —
  точные ID, envelope/defaults, required params, legacy routes и notes permissions.
- `spring-panel/src/test/java/com/example/panel/controller/DialogTicketQueryHttpIntegrationTest.java` —
  настоящий embedded Tomcat и HTTP query round-trip с mock services, без БД.
- `spring-panel/src/test/java/com/example/panel/service/KnowledgeNoteLocationServiceExactTicketIdTest.java`,
  `spring-panel/src/test/java/com/example/panel/config/RestExceptionHandlerTest.java` —
  exact lookup без sibling fallback и безопасный 403 envelope.
- `spring-panel/src/test/java/com/example/panel/runtime/DialogKnowledgeNotesSourceContractTest.java`,
  `spring-panel/src/test/js/dialog-details-query-api-r50.test.js` —
  актуализированы route/cache expectations, остальные assertions сохранены.
- `spring-panel/src/test/js/dialog-read-query-requests-r53.test.js` —
  фактические клиентские fetch URLs, workspace params, notes 403 и blank participants.
- `ai-context/tasks/task-details/01-278.md` — checkpoint R53;
  этот append-only changelog-файл.

Detached validation: baseline 28 Java tests, 14 failures; итоговые отчёты
42/42 GREEN в восьми классах. Шесть JS test files, JS syntax и diff checks GREEN.
HTTP-тест подтвердил encoded slash/reserved/Unicode query-ID на embedded
Tomcat для трёх routes. Это не browser или production business-scope E2E.
Generated Sass CSS — только Windows EOL status, не patch. AI, actions и
media продолжают использовать path endpoints. Рабочая БД, migration/backfill,
flags, deploy, staging, commit и push не выполнялись.
S2B/R12 PROPOSED_NOT_APPROVED, 01-278 — 🟡.
