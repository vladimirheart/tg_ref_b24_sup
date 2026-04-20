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

Обязательные cross-platform env keys:

- `APP_DB_TICKETS`
- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_BOT_USERNAME`
- `GROUP_CHAT_ID`
- `APP_BOT_LOG_PATH`
- `SPRING_PROFILES_ACTIVE`
- `JAVA_TOOL_OPTIONS`

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

## Production Recipe

Рекомендуемый production path сейчас такой:

1. Собрать prebuilt runtime jar для каждого bot module.
2. Положить артефакты в `dist/` внутри `java-bot` или в другой заранее известный каталог.
3. Заполнить `app.bots.executable-jars` явными путями к jar.
4. Оставить `app.bots.launch-mode=auto` или `jar`.

Production-ready contract считается выполненным, когда:

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
