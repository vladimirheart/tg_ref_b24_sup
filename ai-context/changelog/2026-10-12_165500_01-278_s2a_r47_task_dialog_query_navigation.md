# 01-278 / S2A / R47 — точный переход из задачи к диалогу

Время: 2026-10-12 16:55 MSK. Baseline: `cd7f7ef5ae9c3b89752797b28bd965966cbf6cf0`.

Промпт пользователя: «дальше более широким пакетом».

Изменённые области и файлы:

- `spring-panel/src/main/java/com/example/panel/controller/DialogsController.java`,
  `spring-panel/src/test/java/com/example/panel/controller/DialogsControllerWebMvcTest.java` —
  exact query-ID в начальной модели `/dialogs`, path-маршрут сохранён.
- `spring-panel/src/main/java/com/example/panel/service/DialogAutoCloseFollowUpTaskService.java`,
  `spring-panel/src/test/java/com/example/panel/service/DialogAutoCloseFollowUpTaskServiceTest.java`,
  `java-bot/bot-core/src/main/java/com/example/supportbot/service/AutoCloseFollowUpTaskService.java`,
  `java-bot/bot-core/src/test/java/com/example/supportbot/service/AutoCloseFollowUpTaskServiceTest.java`,
  `java-bot/bot-core/src/test/java/com/example/supportbot/service/AutoCloseFollowUpExactTicketIdTest.java` —
  ссылки auto-close задач переведены на percent-encoded query-ID, payload links
  и точные lookups проверены без изменений.
- `spring-panel/src/main/java/com/example/panel/service/DialogTaskDisplayService.java`,
  `spring-panel/src/test/java/com/example/panel/service/DialogTaskDisplayServiceTest.java` —
  исторические фразы HTML-задач с padded ID используют exact lookup;
  сохранённый href не переписывается.
- `spring-panel/src/main/resources/static/js/sidebar.js`,
  `spring-panel/src/test/js/sidebar-dialog-query-navigation-r47.test.js` —
  query-ID уведомления не превращается в path, exact ID остаётся в SPA-переходе.
- `spring-panel/src/test/java/com/example/panel/service/NotificationServiceTest.java` —
  регрессия существующего форматирования по query-ID.
- `ai-context/tasks/task-details/01-278.md` — checkpoint R47;
  этот append-only changelog-файл.

Baseline panel tests: 30, 3 expected failures. После source fix: 30/30 GREEN.
Bot-core auto-close tests: 8/8 GREEN. Node: 3/3 нового sidebar-теста
и 18/18 существующих R27/R29, `node --check` GREEN. Это не browser/Tomcat
E2E и не доказательство всех внутренних UI path-ссылок. Generated CSS
имеет только Windows EOL status и не входит в patch. Рабочая БД,
migration/backfill, flags, deploy, commit и push не выполнялись.
S2B/R12 остаются PENDING/PROPOSED_NOT_APPROVED, 01-278 — 🟡.
