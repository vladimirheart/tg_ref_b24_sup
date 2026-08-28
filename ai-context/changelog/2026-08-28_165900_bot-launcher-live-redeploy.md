# 01-219 - live redeploy of production bot launcher packaging

## Промпт пользователя

`другая задача:
перестали запускаться боты. при попытке ручного запуска, на примере телеграм-бота, возвращает в панели: "Не удалось запустить бота: Не удалось запустить бота: Не найден собранный jar для модуля bot-telegram. Соберите java-bot или переключите app.bots.launch-mode в auto/maven."`

## Что выполнено

- Пересобран docker image `iguana-panel:local` после обновления `docker/panel.Dockerfile`.
- Перевыкатены `ops-worker`, `panel-web` и `panel-direct` в production contour.
- В `ai-context/tasks/task-list.md` задача `01-219` переведена в статус `🟣` как выполненная AI и ожидающая ручной проверки в UI.
- В `ai-context/tasks/task-details/01-219.md` добавлен фактический результат выкладки и список ручных проверок.

## Проверка на живом контуре

- `docker compose ... exec panel-web /bin/sh -lc 'printenv | grep APP_BOT_'` показал:
  - `APP_BOT_LAUNCH_MODE=jar`
  - `APP_BOT_EXECUTABLE_JAR_TELEGRAM=dist/bot-telegram-runtime.jar`
  - `APP_BOT_EXECUTABLE_JAR_VK=dist/bot-vk-runtime.jar`
  - `APP_BOT_EXECUTABLE_JAR_MAX=dist/bot-max-runtime.jar`
- `docker compose ... exec panel-web /bin/sh -lc 'ls -la /opt/iguana/java-bot/dist'` подтвердил наличие:
  - `bot-telegram-runtime.jar`
  - `bot-vk-runtime.jar`
  - `bot-max-runtime.jar`
- `curl -I http://127.0.0.1:8080/login` после перевыкладки вернул `HTTP/1.1 200`.

## Ограничение проверки

- Полный smoke ручного запуска канала через `/api/bots/{channelId}/start` не выполнен автоматически, потому что endpoint требует авторизованную пользовательскую сессию панели.
