# 2026-07-06 00:00:00 — Monitoring history retention plan

## Связанные задачи

- `01-140` — Вынести `monitoring_check_history` в `monitoring.db` и ограничить хранение логов 30 днями

## Пользовательский промпт

> 1 и 3.  
> плюс нужен план чтобы логи жили не в логах общей БД и хранились не более 1 месяца

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-140.md`

## Что сделано

- Проведён локальный анализ `spring-panel/bot_database.db` и подтверждено, что основной рост размера даёт таблица `monitoring_check_history` с историей RMS/iiko-мониторинга.
- Проверено, что `MonitoringCheckHistoryRepository` уже работает через `monitoringJdbcTemplate`, а `MonitoringDatabaseBootstrapService` создаёт `monitoring_check_history` в monitoring datasource, но не переносит legacy-историю из общей БД.
- Зафиксирован отдельный follow-up task на перенос monitoring history в `monitoring.db`, очистку legacy-копии и введение retention 30 дней.

## Примечания

- В рамках этого шага код приложения не менялся; оформлен технический план и постановка следующей задачи.
