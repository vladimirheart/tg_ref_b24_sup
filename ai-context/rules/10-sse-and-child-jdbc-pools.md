# SSE и отдельные JDBC-пулы дочерних ботов

Действует с 25.11.2026 по решению владельца проекта, задача 01-312.

- `APP_POSTGRES_POOL_MAX_SIZE` относится к panel service JVM.
- Child JVM получает отдельные `APP_BOT_DB_MIN_IDLE=5`,
  `APP_BOT_DB_MAX_POOL_SIZE=20`, `APP_BOT_DB_IDLE_TIMEOUT_MS=120000` через
  явный runtime contract как APP_DB_MIN_IDLE/MAX_POOL_SIZE/IDLE_TIMEOUT_MS.
- Размер JDBC-пула не ограничивает число открытых диалогов. При росте числа
  каналов пересчитывается сумма максимумов всех JVM с резервом ниже server limit.
- SQL role CONNECTION LIMIT также должен покрывать максимум child-пула:
  V103 задаёт 20 для новых и исходных автоматически управляемых ролей.
  Увеличение maximumPoolSize выше 20 требует согласовать SQL quota отдельно.
- SSE endpoints: `/api/events/stream` и `/api/general-tickets/events/stream`.
  Они не буферизуются/кэшируются proxy; read timeout 180 секунд.
- UI и GENERAL heartbeat выполняются независимо от общего scheduler и друг друга.
  Интервал 25 секунд; текущие session revocation и права проверяются как прежде.
- Увеличение SSE timeout не распространяется на обычные HTTP endpoints.
- SSE обновляет браузер оператора и не заменяет подтверждение доставки сообщения
  провайдером мессенджера. Рост throughput требует измерения SQL/очередей/API.
