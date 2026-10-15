# 01-278 R96: read-only preview привязок канала

- Время: 2026-10-15 12:08:00 MSK.
- Промт пользователя: «дальше».
- Файлы: `BusinessAdministrationService`, `BusinessAdministrationApiController`,
  PostgreSQL и WebMvc tests, `ai-context/tasks/task-details/01-278.md`.

Добавлен защищённый admin API для просмотра всех business bindings конкретного
канала и их классификации. Одиночная активная `SINGLE_BUSINESS`-привязка к
активному бизнесу отличается от пустого, выключенного и неоднозначного канала.
Метод использует один read-only SELECT; не создаёт binding и не присваивает
`tickets.business_id`. Это инвентаризация для решения S2B, а не разрешение
controlled write.

Чистый targeted Maven `clean test`: 11/11 GREEN (4 PostgreSQL, 7 WebMvc),
`git diff --check` GREEN. Реализация controlled write не оставлена: она требует
отдельного согласования по условиям задачи. Рабочая БД, миграции/backfill,
feature flags, deployment, staging, commit/push не выполнялись. Задача остаётся
🟡, решения S2B/product PENDING.
