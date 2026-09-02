# SQLite production perimeter isolation

## Пользовательский запрос

`давай выпиливать legacy по задаче 01-229`.

## Значимые уточнения

- `01-212` оставить в текущем статусе.
- 24 старых медиа больше не нужны.

## Фактические изменения

- Обычный `docker-compose.production-contour.yml` больше не монтирует staged SQLite-файлы, local bot shard directory и не передаёт SQLite import paths или auto-import flag в `db-migrate`.
- Сохранён отдельный `docker-compose.legacy-sqlite-import.yml`: он подключает immutable staged snapshot только при явной аварийной/архивной операции.
- First-run bootstrap и `.env.example` больше не создают SQLite runtime settings; verifier и runbook используют explicit archive override.
- Обновлены source-contract tests и документация, включая безопасный archive-only compaction path.
- `01-229` переведена в работу: production runtime perimeter удалён, дальнейшая очистка исходного compatibility-кода остаётся отдельным шагом.
- `01-236` переформулирована: это контролируемая ротация уже сохранённых паролей PostgreSQL/RabbitMQ/Redis/MinIO, а не задача о bot token.
- По решению владельца `01-245` закрыта без восстановления: metadata остаётся `missing`, удаления данных не выполнялись.

## Проверка

- `docker compose -f docker-compose.production-contour.yml config -q`
- `docker compose -f docker-compose.production-contour.yml -f docker-compose.legacy-sqlite-import.yml config -q`
- `spring-panel\\mvnw.cmd -q "-Dtest=DockerProductionRoleTopologySourceContractTest,LegacySqliteImportOperationsSourceContractTest" test`
