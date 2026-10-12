# 01-278 / S2A / R45 — точный ticket ID в исторических названиях задач

Время: 2026-10-12 16:32 MSK. Baseline: `0ac619863e1a0787438734640a81045ad4f7167f`.

Промт пользователя: «дальше».

Изменённые файлы:

- `spring-panel/src/main/java/com/example/panel/service/DialogTaskDisplayService.java` — разбор legacy title сохраняет краевые пробелы opaque `ticket_id` до lookup.
- `spring-panel/src/test/java/com/example/panel/service/DialogTaskDisplayServiceTest.java` — direct regressions для двух префиксов, literal ` null ` и заголовка без суффикса.
- `spring-panel/src/test/java/com/example/panel/service/NotificationServiceTest.java` — сохранённое task-уведомление не берёт номер у plain sibling.
- `ai-context/tasks/task-details/01-278.md` — checkpoint R45.
- Этот append-only changelog-файл.

До fix: 11 targeted tests, 2 failures. После fix: 20/20 GREEN в трёх
целевых классах. Sass CSS имеет только Windows EOL status и не включён.
HTML body parser и storage key не изменены; рабочая БД, migration/backfill,
feature flags, runtime и deploy не затронуты. S2B/R12 остаются
PENDING/PROPOSED_NOT_APPROVED, 01-278 — 🟡.
