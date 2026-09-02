# MAX forwarded message history

## Пользовательский запрос

`в мах-е пересылаемое сообщение от другого пользователя мах не отображается в истории у оператора. в телеграм такой проблемы нет`.

## Фактические изменения

- `MaxWebhookController` теперь распознаёт `message.link.type=forward` и извлекает текст или вложения из linked original message при пустом внешнем `body`.
- Внешний отправитель остаётся клиентом активной заявки, а автор пересланного сообщения записывается в existing `forwarded_from`, который UI показывает оператору как «Переслано от ...».
- Добавлен regression test для MAX `message_created` с null body и forwarded source.
- Создана и переведена в ожидание ручной проверки задача `01-247`.

## Проверка

- `java-bot\\mvnw.cmd -q -pl bot-max -am "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=MaxWebhookControllerTest" test`

## Deployment note

- Попытка собрать Docker image была остановлена длительным cold-download Maven-зависимостей. Текущий `bot-runner` не заменён и остаётся healthy; перед E2E-проверкой нужен успешный `docker compose ... build bot-runner` и controlled recreate `bot-runner`.
