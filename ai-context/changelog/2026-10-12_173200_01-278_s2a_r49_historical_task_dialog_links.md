# 01-278 / S2A / R49 — исторические ссылки задач на диалоги

Время: 2026-10-12 17:32 MSK. Baseline: `633571b58d5f9ede6248aa1a1d92df28604e0b6f`.

Промпт пользователя: «давай дальше но небольшим пакетом».

Изменённые области и файлы:

- `spring-panel/src/main/java/com/example/panel/service/DialogTaskDisplayService.java` —
  перевод известных локальных path-href в query-href на чтении HTML задачи,
  без изменения сохранённого тела или lookup.
- `spring-panel/src/test/java/com/example/panel/service/DialogTaskDisplayServiceTest.java` —
  регрессии для padded, slash, reserved, Unicode, буквального плюса и
  нетронутых внешних, неоднозначных или невалидных ссылок.
- `ai-context/tasks/task-details/01-278.md` — checkpoint R49;
  этот append-only changelog-файл.

Baseline: 7 display tests, 3 expected failures. После fix: 26/26 GREEN в
display, auto-close follow-up и notification классах. Unit-level HTML contract, не
browser/Tomcat E2E. API `/api/dialogs/{id}` для slash-ID не менялся.
Generated Sass CSS имеет только Windows EOL status, не входит в patch.
Рабочая БД, migration/backfill, flags, deploy, staging, commit и push
не выполнялись. S2B/R12 PENDING/PROPOSED_NOT_APPROVED, 01-278 — 🟡.
