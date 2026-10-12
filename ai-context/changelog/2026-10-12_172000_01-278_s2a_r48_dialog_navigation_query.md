# 01-278 / S2A / R48 — query-навигация к диалогам

Время: 2026-10-12 17:20 MSK. Baseline: `8aaf1b621a5fec0bc6c05056ec7f3ae62cacee69`.

Промпт пользователя: «дальше более широким пакетом».

Изменённые области и файлы:

- `spring-panel/src/main/java/com/example/panel/service/NotificationService.java`,
  `spring-panel/src/test/java/com/example/panel/service/NotificationServiceTest.java`,
  `spring-panel/src/test/java/com/example/panel/service/NotificationLegacyTicketGuardTest.java` —
  новые уведомления содержат точный query-ID; форматирование номера
  обращения работает с ним, старый path остаётся читаемым.
- `spring-panel/src/main/resources/templates/dialogs/index.html`,
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-workspace-runtime.js`,
  `spring-panel/src/main/resources/static/js/dialogs-flow-runtime.js`,
  `spring-panel/src/main/resources/static/js/dialogs-location-business-runtime.js`,
  `spring-panel/src/test/java/com/example/panel/controller/DialogsControllerWebMvcTest.java`,
  `spring-panel/src/test/js/dialog-query-navigation-r48.test.js` —
  список, workspace, modal, browser-history и business/location surface
  принимают и формируют query-маршрут без изменения opaque ID.
- `spring-panel/src/main/resources/static/js/ai-ops.js`,
  `spring-panel/src/test/js/ai-ops-exact-legacy-ticket-id-r33.test.js`,
  `spring-panel/src/main/resources/templates/clients/profile.html`,
  `spring-panel/src/main/resources/static/js/sidebar.js`,
  `spring-panel/src/test/js/sidebar-dialog-query-navigation-r47.test.js` —
  переходы из AI Ops, профиля клиента и hash-уведомления больше не
  возвращаются к path с `%2F`.
- `ai-context/tasks/task-details/01-278.md` — checkpoint R48;
  этот append-only changelog-файл.

Baseline: 25 panel tests, 2 expected failures и RED в JS-навигации.
После fix: 42/42 GREEN в шести panel-классах (включая PostgreSQL
Testcontainers), 7/7 JS test files и syntax checks GREEN. Это не
browser/Tomcat E2E. API `/api/dialogs/{id}` и старые href в сохранённом
HTML задач не менялись. Sass CSS — только Windows EOL status, не patch.
Рабочая БД, migration/backfill, flags, deploy, commit и push не выполнялись.
S2B/R12 PENDING/PROPOSED_NOT_APPROVED, 01-278 — 🟡.
