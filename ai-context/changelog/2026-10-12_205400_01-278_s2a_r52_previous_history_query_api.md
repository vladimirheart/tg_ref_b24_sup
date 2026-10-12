# 01-278 / S2A / R52 — предыдущая история через query API

Время: 2026-10-12 20:54 MSK. Baseline: `66c848e6b4ad23fe74da1d9b27d3e43ff88f0656`.

Промпты пользователя:

- «репо изменён в рамках зади. проанализируй и продолжай»;
- «продолжи».

Разобраны новые R50/R51 коммиты; R51 уже перевёл polling истории на query API.

Изменённые области и файлы:

- `spring-panel/src/main/java/com/example/panel/controller/DialogTicketQueryReadController.java`,
  `spring-panel/src/test/java/com/example/panel/controller/DialogTicketQueryReadControllerWebMvcTest.java` —
  query endpoint предыдущей истории, exact ID, offset/default и missing parameter.
- `spring-panel/src/main/java/com/example/panel/service/DialogReadService.java`,
  `spring-panel/src/test/java/com/example/panel/service/DialogReadServicePreviousHistoryTest.java` —
  устранён NPE при пустой предыдущей странице, сохранён nullable batch envelope.
- `spring-panel/src/main/resources/static/js/dialogs-details-history-runtime.js`,
  `spring-panel/src/test/js/dialog-previous-history-query-api-r52.test.js` —
  догрузка истории передаёт exact ID в query и сохраняет offset.
- `spring-panel/src/main/resources/templates/dialogs/index.html` — cache key
  JS истории обновлён для доставки изменений R51/R52.
- `ai-context/tasks/task-details/01-278.md` — checkpoint R52;
  этот append-only changelog-файл.

Detached validation: baseline 11 Java tests, 3 failures и 1 NPE. После fix
34/34 Java tests в четырёх классах, включая PostgreSQL-регрессии истории,
три Node source-contract теста, JS syntax и `git diff --check` GREEN.
Проверка не включает browser/Tomcat E2E. Другие API карточки остаются
path-based. Generated Sass CSS — только Windows EOL status, не patch.
Рабочая БД, migration/backfill, flags, deploy, staging, commit и push
не выполнялись. S2B/R12 PROPOSED_NOT_APPROVED, 01-278 — 🟡.
