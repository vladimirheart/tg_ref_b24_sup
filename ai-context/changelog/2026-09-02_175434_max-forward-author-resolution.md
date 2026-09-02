# MAX forwarded author resolution

## Пользовательский запрос

`да, в мах приходят пересланные сообщения, но не отображается от кого именно, возвращая "Переслано от клиент"`.

## Фактические изменения

- Подтверждено по PostgreSQL: новые MAX history records сохраняли literal `forwarded_from = клиент`, хотя прежние записи содержали фактический username.
- MAX parser теперь ищет автора пересылки в `author`, `original_author`, `original_sender`, `forwarded_from`, `from`, `user`, `sender` и `owner` как в original message, так и в link envelope.
- Профиль внешнего клиента исключён из кандидатов автора: если MAX не передал отдельного автора, UI не получает ложную подпись «Переслано от клиент».
- Regression test покрывает envelope sender, совпадающий с внешним клиентом, и отдельное поле `author` исходного сообщения.
- Собран новый `iguana-panel:local`; `bot-runner` заменён безопасной последовательностью без recreate infrastructure и завершил автозапуск трёх активных каналов.

## Проверка

- `java-bot\\mvnw.cmd -q -pl bot-max -am "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=MaxWebhookControllerTest" test`
- `bot-runner`, PostgreSQL и RabbitMQ имеют status `healthy`.
- Лог `Bot auto-start completed. Started 3 active bot(s).`
