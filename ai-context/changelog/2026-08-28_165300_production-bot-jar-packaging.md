# 01-219 - production bot jar packaging and launcher recovery

## Промпт пользователя

`другая задача:
перестали запускаться боты. при попытке ручного запуска, на примере телеграм-бота, возвращает в панели: "Не удалось запустить бота: Не удалось запустить бота: Не найден собранный jar для модуля bot-telegram. Соберите java-bot или переключите app.bots.launch-mode в auto/maven."`

## Что изменено

- В `docker/panel.Dockerfile` добавлен отдельный build stage для `java-bot`, который собирает `bot-telegram`, `bot-vk` и `bot-max`, а затем кладёт готовые runtime jars в `/opt/iguana/java-bot/dist/` внутри production image панели.
- В `spring-panel/src/main/resources/application.yml` `app.bots.executable-jars` больше не пустой placeholder: для `bot-telegram`, `bot-vk` и `bot-max` добавлены явные env-driven пути.
- В `docker-compose.production-contour.yml` закреплены production env-пути `APP_BOT_EXECUTABLE_JAR_TELEGRAM`, `APP_BOT_EXECUTABLE_JAR_VK` и `APP_BOT_EXECUTABLE_JAR_MAX`, которые указывают на prebuilt jars в `dist/`.
- В `ai-context/tasks/task-details/01-219.md` зафиксирована отдельная задача на восстановление production bot launcher и ручного запуска каналов.

## Диагностика причины

- На 28 августа 2026 года внутри `panel-web` установлен `APP_BOT_LAUNCH_MODE=jar`.
- При этом в контейнере присутствует только JRE (`javac` отсутствует), поэтому fallback `auto -> maven` не может считаться рабочим вариантом для production.
- В `/opt/iguana/java-bot/` были исходники, но не было `bot-telegram/target`, `bot-vk/target` и `dist/bot-*.jar`, из-за чего `jar`-launcher падал ещё до старта процесса.

## Проверка

- `spring-panel\\mvnw.cmd "-Dtest=BotRuntimeContractServiceTest,BotProcessServiceTest" test` -> `BUILD SUCCESS`
- `docker compose ... exec panel-web /bin/sh -lc 'javac -version || true'` -> `javac: not found`
