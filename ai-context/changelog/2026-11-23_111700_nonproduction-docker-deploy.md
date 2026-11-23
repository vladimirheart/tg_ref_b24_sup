# Непроизводственное обновление Docker-контура

Время: 2026-11-23 11:17:00 Europe/Moscow.

## Промпт пользователя

```text
так, с паролями чуть позже - поставь на это отдельную задачу чтобы не забыть.
обнови докер - можно делать, т.к. проект ещё не вышел в прод
```

## Выполнено

- Использована существующая активная задача `01-236` для контролируемой ротации persistent-паролей PostgreSQL, RabbitMQ, Redis и MinIO; в неё добавлен свежий checkpoint о временно отложенной ротации.
- Перед изменением контура выполнен read-only `docker-production-credential-migration-status.ps1`: четыре persistent-компонента имеют статус `migration_required`, Grafana — `ready`.
- Штатный preflight успешно пройден с явным `-AllowInsecureDefaults`, допустимым пользователем для непроизводственного контура.
- Контур обновлён официальной командой `scripts/docker-production-up.ps1 -Observability -AllowInsecureDefaults -Build`; launcher собрал `iguana-panel:local`, выполнил `db-migrate` и пересоздал backend-роли.
- Flyway успешно применил миграцию `V99__it_equipment_manual_hierarchy.sql`.

## Проверки после обновления

- `panel-web`, `bot-runner` и `ops-worker` — `healthy` на образе `iguana-panel:local`.
- PostgreSQL, Redis и RabbitMQ сохранили статус `healthy`.
- `http://127.0.0.1:8080/analytics/rms-control` вернул `302` на штатную страницу входа.
- Лог `db-migrate`: `Successfully applied 1 migration ... version v99`.

## Ограничение

`-AllowInsecureDefaults` передал documented local defaults только в процесс launcher и не является завершением ротации. До выхода в production задача `01-236` должна быть выполнена: секреты нужно заменить согласованно внутри persistent-сервисов и их клиентов.
