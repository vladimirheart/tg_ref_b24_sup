# SSE и JDBC-пулы child-ботов

Задача `01-312`, 25.11.2026.

## Настройки

| Область | Параметры |
| --- | --- |
| Panel service JVM | `APP_POSTGRES_POOL_MAX_SIZE=6` |
| Каждый child bot JVM | `APP_BOT_DB_MIN_IDLE=5`, `APP_BOT_DB_MAX_POOL_SIZE=20`, `APP_BOT_DB_IDLE_TIMEOUT_MS=120000` |
| PostgreSQL | `max_connections=550`; SQL quota managed child role = 20 после V103 |
| SSE | heartbeat 25 с, отдельный scheduler, read timeout 180 с, proxy buffering/cache off |

Число открытых диалогов не равно числу занятых соединений. Для текущих трёх
child JVM минимум 15 и максимум 60 соединений. При добавлении каналов необходимо
пересчитывать общий бюджет с panel/worker/runner и эксплуатационным резервом.
Рост maximumPoolSize выше 20 требует отдельного изменения SQL role quota.

## Передача параметров

Compose задаёт отдельные APP_BOT_DB_* для supervisor. BotRuntimeContractService
передаёт child-процессу APP_DB_MIN_IDLE, APP_DB_MAX_POOL_SIZE и APP_DB_IDLE_TIMEOUT_MS.
Это сохраняет фильтрацию унаследованного окружения и не передаёт control-plane
credentials. DataSourceConfig проверяет границы до открытия соединений.

## SSE

Адреса: `/api/events/stream`, `/api/general-tickets/events/stream`.
Nginx direct/HTTP/TLS configs содержат отдельный streaming location.
Основной endpoint также возвращает `Cache-Control: no-store` и
`X-Accel-Buffering: no`. Остальные HTTP endpoints сохраняют прежний timeout 60 с.
UI и GENERAL heartbeat используют два выделенных потока; медленный default
scheduler или один тип heartbeat не блокируют другой. Session revocation и
current access checks не обходятся.

## Порядок rollout

1. Зафиксировать фактические Compose labels, image IDs и rollback tags.
2. Собрать полный backend image с обновлёнными bot JAR и V103.
3. Одноразовым db-migrate того же image применить V103 и проверить exit=0.
   Меняется функция provisioning и исходные managed child role quotas, без
   изменений пользовательских таблиц/паролей/прав доступа.
4. Пересоздать только panel-web и bot-runner новым image через фактическую
   цепочку overrides, без rebuild/restart PostgreSQL, ops-worker и брокера.
5. После readiness проверить nginx -t и reload panel-direct.

## Проверки

- Startup child logs: `maximumPoolSize=20, minimumIdle=5, idleTimeoutMs=120000`.
- `pg_roles.rolconnlimit` = 20 для трёх исходных managed roles.
- В спокойном состоянии pg_stat_activity показывает около 5 соединений на child,
  не учитывая кратковременную нагрузку/уборку idle connections.
- Панель и runtime роли healthy; `/actuator/health/readiness` = UP.
- В авторизованном браузере поток остаётся открыт дольше 60 секунд, ping
  приходит примерно раз в 25 секунд; сообщения обновляют диалог без reload.
- Проверить доставку сообщений в обе стороны отдельно: SSE обновляет браузер,
  а подтверждение мессенджера относится к другому участку доставки.

## Откат

Runtime checkpoint задачи содержит release/rollback overrides в
`C:/1C/backup/deploy-feedback-4ca73f33b-20261119/panel-web-sse-pools-20261125-093500/`.
Последним override подключить `rollback.override.yml`, пересоздать panel-web,
bot-runner и panel-direct без build/dependency restart; проверить readiness.
Он возвращает прежние images и прежний Nginx config. Версия БД остаётся V103;
квота 20 совместима с прежними пулами 10. Старые migrations не переписывать,
расширения диагностики/права child не удалять. Полное снижение role quota до 16
выполняется отдельно только после проверки числа открытых соединений.
