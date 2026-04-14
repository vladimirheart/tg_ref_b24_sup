# Telegram-бот: исправлена кодировка и убрано принудительное proxy-включение

- Время: `2026-04-10 10:50:00 +0300`
- Файлы:
  - `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java`
  - `ai-context/tasks/task-list.md`
  - `ai-context/tasks/task-details/01-007.md`
  - `ai-context/tasks/task-details/01-008.md`
- Что сделано: исправлена битая кодировка пользовательских сообщений в `SupportBot.java`, тексты приведены к корректному UTF-8.
- Что сделано: применение proxy в Telegram-клиенте ограничено режимом `APP_NETWORK_MODE=proxy`; для остальных режимов принудительно используется `NO_PROXY`.
- Что сделано: лог применения proxy показывается только когда режим маршрута действительно `proxy`.
- Что сделано: задачи `01-007` и `01-008` переведены в `🟣`.
