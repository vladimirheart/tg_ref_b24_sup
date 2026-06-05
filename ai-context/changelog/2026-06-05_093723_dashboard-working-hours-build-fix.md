# 2026-06-05 09:37:23 — dashboard working hours build fix

## Промты пользователя

- `приложен лог запуска с ошибкой компиляции после изменений dashboard`

## Что изменено

- в `DashboardAnalyticsService` исправлен `ActivityStats.toMap()`: вместо `Map.of(...)` с превышением лимита по числу пар используется обычный `HashMap`;
- в `ai-context/tasks` добавлена и зафиксирована отдельная задача `01-122` для регрессионного фикса сборки после изменений рабочих часов канала.

## Проверка

- `./mvnw.cmd -q -DskipTests compile`
- `git diff --check -- ai-context/tasks/task-list.md ai-context/tasks/task-details/01-122.md spring-panel/src/main/java/com/example/panel/service/DashboardAnalyticsService.java`
