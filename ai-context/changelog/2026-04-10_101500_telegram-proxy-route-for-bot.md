# Явное применение proxy-роута в `bot-telegram`

- Время: `2026-04-10 10:15:00 +0300`
- Файлы:
  - `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java`
  - `ai-context/tasks/task-list.md`
  - `ai-context/tasks/task-details/01-004.md`
- Что сделано: конструктор `SupportBot` переведен на `TelegramLongPollingBot(DefaultBotOptions, token)` с явной сборкой proxy-настроек из `APP_NETWORK_PROXY_*`.
- Что сделано: добавлено маппирование proxy-схем `http/https/socks4/socks5/vless` в `DefaultBotOptions.ProxyType`, где `vless` направляется через `SOCKS5`.
- Что сделано: добавлен лог применяемого proxy-маршрута при инициализации бота для упрощения диагностики сетевого контура.
- Что сделано: задача `01-004` переведена в статус `🟣`.
