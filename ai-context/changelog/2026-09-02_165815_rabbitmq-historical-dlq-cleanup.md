# RabbitMQ historical DLQ cleanup

## Пользовательский запрос

`так исправь чтобы всё работало`

## Фактические изменения

- Без извлечения пользовательского содержимого проверены причины и сигнатуры сообщений в inbound и ticket-created DLQ.
- Подтверждено, что это исторические feedback-prompt payload'ы без обязательного `eventType`, отклонённые до текущего rollout; повторная доставка не могла быть успешной.
- Очищены только `iguana.integration.inbound.panel.dlq` и `iguana.integration.ticket-created.panel.dlq`.
- Рабочие inbound/ticket-created очереди не изменялись и сохранили активных consumers.

## Проверки

- Обе DLQ содержат 0 ready и 0 unacknowledged сообщений.
- Рабочие очереди содержат 0 ready сообщений; integration outbox не содержит pending/failed записей.

