# 2026-08-24 08:55 - worker DB isolation v35

## Промпт пользователя

- `патч встал. репо обновил - проверяй и давай дальше`

## Что проверено

- v34 находится ровно одним commit поверх v33 и содержит production readiness surface;
- v34 probes изолированы и не маскируют compatibility runtime как production-ready;
- final audit `01-183` обнаружил, что `BotRuntimeContractService` всё ещё передаёт java-bot canonical `SPRING_DATASOURCE_*`;
- `BotProcessService` наследует parent environment через `ProcessBuilder`, поэтому простого удаления ключей из contract map недостаточно;
- `EngagementTasks.dispatchOperatorNotifications()` выполняет `NotificationRepository.count()` каждые две минуты без RabbitMQ guard;
- основные ticket/channel/feedback/blacklist paths уже используют queue/internal panel API или fail-closed в RabbitMQ режиме.

## Что изменено

- добавлен java-bot database mode `worker`;
- `worker` mode short-circuit-ит external datasource resolver до разбора inherited DB settings;
- `DataSourceConfig` создаёт isolated temp SQLite DataSource без business schema и legacy trigger layer; self-owned worker technical tables могут создаваться своими сервисами;
- `BotRuntimeContractService` явно передаёт transport mode и использует `APP_DB_MODE=worker` для RabbitMQ child;
- `BotProcessService` удаляет inherited PostgreSQL credentials и legacy business DB paths перед запуском worker;
- legacy operator-notification scheduler не обращается к repository в RabbitMQ;
- production bot runtime contract/docs переведены с "bot connects to PostgreSQL" на backend-owned queue/API boundary;
- добавлены targeted regression tests.

## Проверка после применения

- `git diff --check`;
- `spring-panel/.mvnw.cmd -q -DskipTests test-compile`;
- `spring-panel/.mvnw.cmd -q -Dtest=BotRuntimeContractServiceTest,BotProcessServiceTest test`;
- `java-bot/mvnw.cmd -q -pl bot-core -am -DskipTests test-compile`;
- `java-bot/mvnw.cmd -q -pl bot-core -am -Dtest=ExternalDatabaseSettingsResolverTest,BotDatabaseRuntimeModeTest,DataSourceConfigTest,EngagementTasksTest -Dsurefire.failIfNoSpecifiedTests=false test`;
- smoke: runtime-contract показывает RabbitMQ + worker boundary и реальный bot child стартует без canonical datasource env.

## Следующий шаг

После push/smoke — повторный criterion-by-criterion audit `01-183`. Если новых direct business DB paths не обнаружено, следующий пакет должен быть documentation/task closeout, а не новая feature-разработка.