# Persisted credential rotation apply runbook

Этот runbook продолжает `01-223` после диагностического helper из
`docs/runbooks/persisted-credential-migration-status.md`.

На текущем шаге автоматизирован только Windows-first apply workflow для:

- `postgresql`;
- `rabbitmq`.

По умолчанию workflow не делает изменений. Реальные действия происходят только с
явным `-Apply`.

## 1. Когда использовать

Используйте этот apply-path, когда:

- `docker-production-credential-migration-status.ps1` показывает
  `migration_required`;
- контейнер соответствующего сервиса запущен;
- перед вами существующий volume-backed contour, а не fresh install;
- нужно убрать drift между live persisted credential и repository `.env`.

## 2. Что делает script

Скрипт `scripts/docker-production-credential-migration-apply.ps1`:

1. находит живой контейнер компонента;
2. подбирает текущий рабочий credential-кандидат из:
   - canonical `IGUANA_*`;
   - compatibility `SPRING_*`;
   - documented default (`iguana`) как fallback только если live auth реально проходит;
3. генерирует новый secret или принимает явный `-TargetPassword`;
4. делает checkpoint `.env.credential-migration-<component>-<timestamp>.bak`;
5. меняет credential в live service;
6. проверяет новый логин на живом сервисе;
7. только после этого обновляет `.env`;
8. пересоздаёт зависимые сервисы, чтобы они взяли новый env;
9. повторно проверяет readiness и auth;
10. при ошибке пытается выполнить best-effort rollback live credential и `.env`.

## 3. Dry-run

### PostgreSQL

```powershell
.\scripts\docker-production-credential-migration-apply.ps1 -Component postgresql
```

### RabbitMQ

```powershell
.\scripts\docker-production-credential-migration-apply.ps1 -Component rabbitmq
```

Dry-run не раскрывает новый secret и не меняет ни `.env`, ни live runtime.

## 4. Real apply

### PostgreSQL

```powershell
.\scripts\docker-production-credential-migration-apply.ps1 -Component postgresql -Apply
```

### RabbitMQ

```powershell
.\scripts\docker-production-credential-migration-apply.ps1 -Component rabbitmq -Apply
```

При необходимости можно передать свой целевой secret:

```powershell
.\scripts\docker-production-credential-migration-apply.ps1 -Component postgresql -Apply -TargetPassword "<secret>"
```

## 5. Какие env-ключи обновляются

### PostgreSQL

- всегда `IGUANA_POSTGRES_PASSWORD`;
- дополнительно `SPRING_DATASOURCE_PASSWORD`, если локальный datasource в `.env`
  указывает на `localhost` / `127.0.0.1` или ключ ещё пустой.

### RabbitMQ

- всегда `IGUANA_RABBITMQ_PASSWORD`;
- дополнительно `SPRING_RABBITMQ_PASSWORD`, если локальный host в `.env`
  указывает на `localhost` / `127.0.0.1` или ключ ещё пустой.

## 6. Какие сервисы пересоздаются

### После PostgreSQL rotation

- `ops-worker`;
- `panel-web`;
- `postgres-exporter`, если observability overlay уже запущен.

### После RabbitMQ rotation

- `ops-worker`;
- `panel-web`;
- `bot-telegram`, `bot-vk`, `bot-max`, если они реально запущены в текущем
  compose project.

## 7. Чего этот apply-path пока не делает

- не ротирует `redis`;
- не ротирует `minio`;
- не меняет `grafana`;
- не делает cross-component bulk rotation за один прогон;
- не запускает full backup сам по себе, а только делает rollback checkpoint `.env`.

Именно этот remaining contour вынесен в `01-224`.
