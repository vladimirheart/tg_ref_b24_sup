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

## Освобождение JDBC и готовое состояние — 01-326

- Web имеет отдельный APP_PANEL_WEB_DB_MAX_POOL_SIZE, default 15; common service
  pool и child budget от этого не изменяются. Pool не является лимитом операторов.
- Hibernate HOLD сохраняется для Spring custom isolation; ReleasingHibernateJpaDialect
  возвращает physical lease после штатного cleanupTransaction и восстановления
  isolation/readOnly. Не подменять это глобальным AFTER_TRANSACTION без проверки
  READ COMMITTED/REPEATABLE READ и request-bound EM + Spring Session save.
- Оповещения читаются короткой транзакцией с SET LOCAL jit=off и прежними
  statement-time access predicates. API выдаёт максимум 10, SQL limit+1 нужен
  только для hasMore. Старые страницы — cursor + scroll, без unbounded read.
- notifications_snapshot содержит готовый scoped unread и первую страницу;
  чтение по numeric stream identity/current account/session/role/grants
  заканчивается до сетевой записи. События mutations публикуются после commit.
- Push coalesces по recipient, новые события во время чтения дают один follow-up.
  Heartbeat сверяет состояние для восстановления после пропуска Redis pub/sub.
  Это current-state resync, а не обещание replay каждой доставки.
- Клиент проверяет instance/sequence/generation, не допускает overlapping list
  requests и не запрашивает счётчик после каждого успешного push. Sound module
  использует уже полученные данные и не читает список ради определения мелодии.
- Rating patch ограничен current READ/full owner/public ID/business ID/version;
  не создаёт bell alert и не требует полного list reload.
- HTTP/Nginx correlation не журналирует query/body/cookies. Длительность SSE —
  время жизни соединения. Sustained pending dump имеет 15 s threshold/5 min cooldown.
