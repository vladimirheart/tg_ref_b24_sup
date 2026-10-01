# Перенос Iguana на другую Windows-машину

## 1. Назначение

Этот guide описывает перенос текущего PostgreSQL-first контура Iguana на другой Windows-хост. Normal runtime не восстанавливается из root SQLite-файлов: business source of truth находится в PostgreSQL.

## 2. Что переносится

Минимальный пакет приложения:

- исходники или release artifact;
- `config/shared/`;
- production configuration/secrets через безопасный deployment mechanism;
- `attachments/` только если конкретное окружение ещё использует local attachment source для migration/recovery;
- PostgreSQL backup/restore artifact либо доступ к существующему canonical PostgreSQL;
- object-storage backup/restore data, если используется отдельный MinIO/S3 contour;
- документация и runbooks.

Legacy root `*.db` и `bot_databases/`, если сохраняются, относятся только к historical/audit evidence. Они не являются входом normal startup и не имеют first-party live import/recovery path.

## 3. Что не нужно переносить как runtime state

Обычно не копируются:

- `.git/`;
- `.venv/`;
- `node_modules/`;
- `target/`;
- старые `logs/`;
- `run/`;
- transient `*.db-wal`, `*.db-shm`, `*.pid`, `*.tmp`.

## 4. Подготовка новой машины

1. Установить JDK 17.
2. Установить/запустить Docker Desktop для штатного local PostgreSQL-first bootstrap либо обеспечить доступ к внешнему PostgreSQL/RabbitMQ/Redis/object-storage contour.
3. Разместить code/release artifact и `config/shared/`.
4. Восстановить PostgreSQL и object storage по утверждённому backup/recovery runbook либо подключить уже существующие production services.
5. Задать безопасные secrets и env.

Подробный production recovery contract: [runbooks/production-backup-recovery.md](runbooks/production-backup-recovery.md).

## 5. Запуск

Для локального Windows bootstrap:

```powershell
cd spring-panel
.\run-windows.bat
```

Fresh bootstrap обязан завершиться в PostgreSQL/RabbitMQ contour. Если Docker/required infrastructure недоступны, startup должен завершиться ошибкой, а не создавать новую business SQLite БД.

## 6. Проверка после переноса

Проверьте:

- `APP_DB_MODE=postgresql`;
- доступность canonical PostgreSQL и ожидаемых business данных;
- shared config;
- attachments/object storage;
- каналы и bot supervisor;
- dialogs, dashboard, knowledge/client/object data;
- реальное входящее сообщение и operator reply;
- Settings -> Production readiness для production-like contour.

## 7. Если система выглядит пустой

Проверяйте не наличие root `*.db`, а:

1. правильный `SPRING_DATASOURCE_URL`;
2. восстановлен ли нужный PostgreSQL backup;
3. совпадает ли environment/secret configuration;
4. на месте ли `config/shared/`;
5. доступен ли attachment/object-storage contour.

Legacy SQLite не подключается к current runtime. Если старые files сохраняются для audit/history, они остаются offline evidence и никогда не становятся live fallback.

## 8. Historical legacy evidence

Если требуется сохранить историю для audit/investigation, отдельно от runtime package можно архивировать:

- legacy root `*.db`;
- `bot_databases/`;
- старые manifests/ledgers/logs, если они существуют;
- rollback/audit evidence.

Это offline historical package, а не production runtime или supported import package.
