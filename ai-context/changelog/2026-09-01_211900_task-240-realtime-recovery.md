# 2026-09-01 21:19:00 - Realtime inbox recovery

## Пользовательский запрос

> проанализируй лог панели. и лог запуска мах-бота и телеграм-бота "тестов". даже в единственном зарущенном боте, сообщения клиента, которые он отправил после сообщения оператора, отображаются только после отправки сообщения оператором.

> автоматические ответы на оценку приходят дважды

> сообщение "Использовать прошлые значения? ..." тоже пришло дважды

> медиа что-то тоже перестало приходить, либо панель попросту не успевает отрабатывать

## Изменения

- Web API lifecycle ботов ограничен ролью `bot-runner`, чтобы ручной запрос из `panel-web` не запускал duplicate Telegram consumer.
- `Notification.createdAt` использует native PostgreSQL timestamp mapping вместо String converter.
- `UiEventOutboxWatcher` продолжает обработку следующих событий при ошибке одного best-effort realtime event.
- Legacy feedback scan отключён: feedback ownership остаётся у `UiEventOutboxWatcher`.
- Docker contour ограничивает Hikari pool каждого Java runtime, чтобы увеличение числа каналов не исчерпывало PostgreSQL connections.
