# 01-278 R105: рабочие данные scoped-обращения

- Время: 2026-10-15 16:47:00 MSK.
- Промт пользователя: «продолжай».
- Файлы: `BusinessScopedTicketReadService`, `BusinessScopeReadController`,
  `business-dialogs.js`, `businesses/dialogs.html`, PostgreSQL/WebMvc/Node tests,
  `ai-context/tasks/task-details/01-278.md`.

Добавлено чтение ответственного, категорий и участников canonical обращения
через `GET /api/business-scope/tickets/{publicId}/workspace` и показ в scoped
карточке. Ответ требует `PAGE_DIALOGS` и foundation flag, запрещает кэширование,
повторно проверяет активный бизнес и текущий direct/ALL grant внутри SQL.
Legacy рабочие таблицы привязаны лишь к raw `ticket_id`, поэтому данные
возвращаются только при глобальной однозначности этого ID. Коллизия даёт
`relationVerified=false` без данных, чужое обращение — 404, превышение лимита
100 категорий или участников — 413. Клиент очищает DOM при смене контекста,
отбрасывает запоздалые ответы и выводит строки только через `textContent`.

Чистый targeted Maven `clean test`: 21/21 GREEN (11 PostgreSQL, 10 WebMvc).
Полный набор 20 Node UI-регрессий, `node --check` и `git diff --check` GREEN.
Промежуточный не-чистый запуск Maven был RED из-за устаревших test class-файлов
с `Unresolved compilation problems`; после `clean` тесты прошли. Рабочая БД,
bindings, feature flags, migration/backfill, deployment, staging/commit/push не
менялись. S2B/product decisions, изоляция вопросников S4 и полная S3–S6
остаются PENDING; статус задачи 🟡.
