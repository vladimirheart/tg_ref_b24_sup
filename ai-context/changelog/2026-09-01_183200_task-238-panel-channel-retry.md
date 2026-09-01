# 2026-09-01 18:32:00 - Panel channel retry

## Пользовательский запрос

> делай

## Изменения

- `PanelChannelClient` выполняет ограниченные retry transport errors и HTTP 5xx по существующим retry-настройкам.
- Повторные write-запросы используют исходный idempotency key.
- MAX cleanup scheduler не выбрасывает ERROR при временно недоступном internal channel API.
- Добавлена запись выполнения задачи `01-238`.
