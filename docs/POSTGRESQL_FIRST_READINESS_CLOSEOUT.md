# Close-out по PostgreSQL-first readiness для `01-181`

Документ фиксирует итоговый practical status по readiness-части задачи `01-181`.

Речь именно про свежий `PostgreSQL-first` запуск проекта без обязательной миграции уже существующих legacy SQLite-данных.

## 1. Что уже считается закрытым

- `spring-panel` и `java-bot` получили явный runtime contract по режиму БД через `APP_DB_MODE` / `app.datasource.mode` и соответствующие external datasource properties;
- `spring-panel` использует vendor-aware Flyway ownership для external БД, а не SQLite migrations по умолчанию;
- active read/runtime SQL path, который мешал fresh external PostgreSQL запуску, очищен от критичных SQLite-only timestamp/date assumptions;
- runtime DDL drift в external PostgreSQL path закрыт: bot/runtime больше не должен мутировать business schema на старте;
- SQLite bootstrap и trigger/schema init ограничены local/dev perimeter и не должны активироваться в external PostgreSQL path;
- first-run bootstrap умеет поднимать локальный PostgreSQL-контур автоматически, а при отсутствии Docker оставляет воспроизводимый `sqlite` fallback только для dev/onboarding;
- `/api/bots/{channelId}/runtime-contract` теперь явно различает `local/dev bootstrap perimeter` и production-ready external PostgreSQL path;
- targeted verification-path снова рабочий: ключевые тесты по runtime contract, bootstrap guards и external PostgreSQL protection проходят.

## 2. Практический acceptance check

Readiness-часть `01-181` можно считать практически закрытой, если одновременно выполняется следующее:

- новый клон проекта можно поднять через first-run bootstrap без ручной SQL-подготовки;
- при наличии Docker bootstrap переводит локальный старт в `APP_DB_MODE=postgresql`;
- при `APP_DB_MODE=postgresql` runtime не выполняет SQLite schema bootstrap, trigger bootstrap и runtime `ALTER TABLE`;
- Flyway остаётся единственным владельцем PostgreSQL schema;
- `runtime-contract` не помечает SQLite path как production-ready;
- `runtime-contract` помечает explicit jar + external PostgreSQL datasource contract как production-ready сценарий;
- SQLite остаётся только в роли local/dev bootstrap, fallback и legacy/test perimeter.

## 3. Что сознательно не входит в этот close-out

Этот readiness close-out не означает, что полностью завершена вся большая архитектурная задача из `01-181`.

Вне текущего practical scope остаются:

- полноценная data-migration utility `SQLite -> PostgreSQL` для уже накопленных production-like данных;
- вынос business ownership из bot/runtime JDBC path в целевой `provider -> worker -> queue/api -> backend -> PostgreSQL` boundary;
- Redis leases, RabbitMQ transport backbone, MinIO/S3 attachment storage и остальной target production infra слой;
- multi-worker orchestration, channel lease coordination и stateless horizontal runtime model;
- полный production deployment program для всех окружений, а не только readiness слоя fresh-start PostgreSQL path.

## 4. Решение по статусу

На текущем этапе разумно считать readiness-часть `01-181` практически завершённой.

Следующие большие шаги лучше открывать уже как отдельный scope:

- либо `SQLite -> PostgreSQL` migration/backfill;
- либо transport/backend ownership split;
- либо инфраструктурный production contour (`Redis` / `RabbitMQ` / `MinIO` / leases).

Для текущего проекта, который ещё не ушёл в production, fastest path остаётся таким:

1. считать fresh-start `PostgreSQL-first` readiness закрытым;
2. не тратить ближайшее время на migration utility;
3. возвращаться к ownership split или migration layer только когда это станет реальным blocker следующего запуска.
