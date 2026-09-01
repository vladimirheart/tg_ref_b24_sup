# 2026-09-01 19:00:00 - Production documentation refresh

## Пользовательский запрос

> пока я проверяю, пересобери readme файл и к нему относящиеся под актуальное состояние проекта

## Изменения

- README описывает PostgreSQL production source of truth, MinIO/S3, RabbitMQ, Redis и единственный dynamic `bot-runner`.
- Обновлены current architecture snapshot, Docker contour, runtime roles runbook и bot runtime contract.
- Зафиксированы правила одной реплики supervisor, запрет параллельного static bot profile для того же token и команды проверки/restart.
