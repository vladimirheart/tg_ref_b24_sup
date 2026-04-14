# 2026-04-14 12:15:01 - автозакрытие по активности, уведомления и кодировка runtime-логов

## Что изменено

- Добавлен task `[01-017]` и его детализация по исправлению ложного auto-close,
  уведомлений операторов и кодировки runtime-логов.
- В `java-bot` автозакрытие по неактивности теперь смотрит не только на
  `ticket_active.last_seen`, но и на последнее реальное сообщение в
  `chat_history` от клиента, оператора, support/admin или AI-агента.
- Для auto-close по `inactivity` системное событие в истории диалога стало
  явным: `Диалог автоматически закрыт из-за отсутствия активности.`
- В `spring-panel` `OperatorNotificationWatcher` научен отдельно распознавать
  это системное событие и отправлять уведомление участникам диалога, а при
  пустом списке получателей — всем операторам.
- В panel добавлена совместимость по значению `resolved_by`: auto-close теперь
  корректно распознаётся и для `auto_close`, и для legacy-значения
  `Авто-система`, при этом в UI продолжает отображаться человекочитаемая
  подпись `Авто-система`.
- Убрана жёсткая зависимость startup/runtime SQL-триггеров от кириллического
  `resolved_by = 'Авто-система'`: SQLite/PostgreSQL триггеры теперь понимают и
  ASCII-маркер `auto_close`, и старое значение.
- В `BotProcessService` для запуска дочернего bot-процесса добавлено
  принудительное `UTF-8` для `stdout/stderr`, чтобы process-логи меньше
  зависели от системной кодовой страницы Windows.

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-017.md`
- `ai-context/changelog/2026-04-14_121501_auto-close-activity-notifications-and-log-encoding.md`
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/SqliteTriggerInitializer.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/repository/ChatHistoryRepository.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/TicketService.java`
- `java-bot/bot-core/src/main/resources/schema-postgres.sql`
- `spring-panel/src/main/java/com/example/panel/model/dialog/DialogListItem.java`
- `spring-panel/src/main/java/com/example/panel/service/BotProcessService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogService.java`
- `spring-panel/src/main/java/com/example/panel/service/OperatorNotificationWatcher.java`
