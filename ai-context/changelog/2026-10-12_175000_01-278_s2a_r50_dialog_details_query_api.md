# 01-278 / S2A / R50 — query API для деталей диалога

Время: 2026-10-12 17:50 MSK. Baseline: `c3da1118e29750fb8d9ce6560cdbf9a11f629544`.

Промпт пользователя: «давай ещё один небольшой шаг».

Изменённые области и файлы:

- `spring-panel/src/main/java/com/example/panel/controller/DialogTicketQueryReadController.java`,
  `spring-panel/src/test/java/com/example/panel/controller/DialogTicketQueryReadControllerWebMvcTest.java` —
  отдельный GET query route с делегированием в прежний read service и
  регрессиями exact opaque ID, channel/operator, 404 и missing parameter.
- `spring-panel/src/main/resources/static/js/dialogs-details-runtime.js`,
  `spring-panel/src/test/js/dialog-details-query-api-r50.test.js` —
  первичный запрос деталей через query API и контракт URL для slash,
  reserved symbols, Unicode, padded ID и channel.
- `spring-panel/src/main/resources/templates/dialogs/index.html` — новый
  cache key клиентского runtime.
- `ai-context/tasks/task-details/01-278.md` — checkpoint R50;
  этот append-only changelog-файл.

Проверка в detached sandbox: 12/12 WebMvc tests двух контроллеров, два Node
source-contract теста, JS syntax и `git diff --check` GREEN. MockMvc и Node
не являются browser/Tomcat E2E. Другие API карточки пока path-based.
Generated Sass CSS — только Windows EOL status, не patch. Рабочая БД,
migration/backfill, flags, deploy, staging, commit и push не выполнялись.
S2B/R12 PENDING/PROPOSED_NOT_APPROVED, 01-278 — 🟡.
