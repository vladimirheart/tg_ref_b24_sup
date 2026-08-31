# Bot Runtime Contract

Документ фиксирует текущий runtime contract между `spring-panel` и `java-bot`
после `Phase 5` рефакторинга.

## Launcher Contract

- основной источник правды: `app.bots.*` в `spring-panel/src/main/resources/application.yml`;
- `app.bots.launch-mode` поддерживает `auto`, `jar`, `maven`;
- `auto` сначала пытается запустить явный или найденный executable `jar`, затем
  откатывается на `spring-boot:run` как dev fallback;
- `app.bots.executable-jars` задаёт явный contract `module -> jar path`;
- `app.bots.preferred-production-launcher` фиксирует рекомендуемый production launcher;
- `app.bots.recommended-artifact-directory` задаёт рекомендуемую директорию для runtime jar;
- для production предпочтителен explicit `jar` contract, а не `target` scan.

## Runtime Inputs

Обязательные cross-platform env keys для production worker, который запускается panel в `rabbitmq` contour:

- `APP_DB_MODE=worker`
- `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq`
- `APP_PANEL_INTERNAL_API_BASE_URL`
- `APP_PANEL_INTERNAL_API_TOKEN`
- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_BOT_USERNAME`
- `GROUP_CHAT_ID`
- `APP_BOT_LOG_PATH`
- `SPRING_PROFILES_ACTIVE`
- `JAVA_TOOL_OPTIONS`

В этом режиме `spring-panel` намеренно не передаёт и перед стартом удаляет из inherited process environment:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `DATABASE_URL`
- legacy SQLite business paths (`APP_DB_BOT*`, `SUPPORT_BOT_DATABASE_PATH`).

`APP_DB_MODE=worker` стартует с пустым временным per-process SQLite DataSource из системной temp-директории. Full business schema туда намеренно не инициализируется: допускаются только self-owned technical tables, которые worker-сервисы создают для своей технической координации/dedup (например, `integration_outbound_event_deliveries`). Случайный repository/JDBC путь к `tickets/messages/channels/...` должен fail-closed, а не тихо читать или писать локальную business-копию. Business reads/writes в `rabbitmq` режиме обязаны идти через queue/internal panel API.

Для panel-side child JDBC launch теперь допустим только canonical PostgreSQL datasource contract:

- `APP_DB_MODE=postgresql`
- `SPRING_DATASOURCE_URL`
- опционально `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `DATABASE_URL`

Если panel сама ещё находится в `APP_DB_MODE=sqlite`, child `java-bot` JDBC contract должен завершаться ошибкой, а не получать `APP_DB_BOT_RUNTIME` / `SUPPORT_BOT_DATABASE_PATH`.

Platform-specific:

- `VK`: `VK_BOT_ENABLED`, `VK_BOT_TOKEN`, `VK_OPERATOR_CHAT_ID`,
  `VK_WEBHOOK_ENABLED`
- `MAX`: `MAX_BOT_ENABLED`, `MAX_BOT_TOKEN`, `MAX_CHANNEL_ID`,
  `MAX_SUPPORT_CHAT_ID`, `SERVER_PORT`, `SERVER_ADDRESS`,
  `SPRING_MAIN_WEB_APPLICATION_TYPE`

Опциональные env keys зависят от платформы и network route:

- `VK_GROUP_ID`, `VK_CONFIRMATION_TOKEN`, `VK_WEBHOOK_SECRET`
- `MAX_WEBHOOK_SECRET`
- `APP_NETWORK_*`, `HTTP_PROXY`, `HTTPS_PROXY`, `ALL_PROXY`

Для live `rabbitmq` transport contour обязательны ещё и backend boundary keys:

- `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq`
- `APP_PANEL_INTERNAL_API_BASE_URL`
- `APP_PANEL_INTERNAL_API_TOKEN`

Если эти параметры не настроены, `java-bot` больше не должен молча откатываться
в local `JPA/SQLite` business-path для ticket/channel/feedback/blacklist
операций.

## Readiness Contract

- startup timeout задаётся через `app.bots.startup-readiness-timeout`;
- poll interval задаётся через `app.bots.startup-poll-interval`;
- success signal: Spring Boot `Started ...Application in ...`;
- failure signal: banner `APPLICATION FAILED TO START`.

Если success marker не найден до timeout, panel считает запуск неподтверждённым
и завершает процесс как startup failure.

## Diagnostic API

Для проверки контракта без реального старта используется endpoint:

- `GET /api/bots/{channelId}/runtime-contract`

Он возвращает:

- модуль бота;
- configured launch mode;
- resolved launcher kind;
- источник артефакта (`explicit-config`, `target-scan`, `maven-fallback`);
- путь к executable jar, если найден;
- required/optional environment keys;
- warnings по текущему launcher contract;
- readiness expectations;
- production readiness и blocking reasons;
- lifecycle expectations (`running/stopped/error`, startup/timeout behavior).

После шагов `01-181` diagnostic payload должен также явно показывать DB boundary:

- в `APP_DB_MODE=sqlite` warnings/blockers обязаны сигнализировать, что child JDBC contract больше не поддержан и backend нужно перевести на PostgreSQL;
- production-ready статус для bot runtime допустим только при canonical PostgreSQL backend + `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq` + isolated `APP_DB_MODE=worker`; прямой `SPRING_DATASOURCE_URL` в child process теперь считается нарушением production boundary.
- `APP_DB_MODE=postgresql` остаётся поддержанным java-bot compatibility/JDBC режимом для controlled migration/dev scenarios, но больше не является production worker contract.
- normal runtime default панели остаётся PostgreSQL; SQLite contract не должен восприниматься как implicit production path.
- per-channel `bot-<channelId>.db` больше не считается допустимым live runtime-path: legacy shard-файлы должны только импортироваться в canonical PostgreSQL contour.
- в `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq` bot-side business reads/writes должны идти через internal panel API или queue boundary; silent fallback в local business storage больше не считается допустимым live поведением.

## Production Recipe

Рекомендуемый production path сейчас такой:

1. Собрать prebuilt runtime jar для каждого bot module.
2. Положить артефакты в `dist/` внутри `java-bot` или в другой заранее известный каталог.
3. Заполнить `app.bots.executable-jars` явными путями к jar.
4. Оставить `app.bots.launch-mode=auto` или `jar`.

Production-ready contract считается выполненным, когда:

- canonical panel runtime работает на PostgreSQL;
- child transport явно равен `rabbitmq`;
- child database mode равен `worker` и inherited canonical DB credentials удалены до запуска;
- launcher резолвится в `jar`;
- executable jar найден;
- jar взят из `explicit-config`, а не через `target` scan;
- panel не зависит от `spring-boot:run` для боевого запуска.

## Lifecycle Contract Test

В test-layer есть адресный lifecycle contract test, который:

- собирает runnable test jar;
- запускает его через `BotProcessService`;
- подтверждает `running` после readiness marker;
- проверяет `stop/status` и cleanup pid-файла.

## Remaining Gaps

- ещё не оформлен end-to-end process contract test на полный lifecycle;
- не зафиксирован единый production deployment recipe для всех окружений;
- пока не введён отдельный supervisor/service поверх bot runtime.
