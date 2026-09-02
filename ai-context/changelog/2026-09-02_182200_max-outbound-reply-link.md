# Исходящий MAX reply-link для оператора

Дата: 2026-09-02

## Пользовательский запрос

> мах. если клиент или оператор отвечает на какое-то сообщение, не видно что это именно ответ на конкретное сообщение, то есть приходит как самостоятельное сообщение

## Причина и изменения

- После первичной проверки inbound-пути был обнаружен второй независимый
  разрыв: `DialogReplyTransportService` принимал `replyToTelegramId` для MAX,
  но не передавал его в запросы `sendMaxText` и `sendMaxMedia`.
- Текстовый и media reply оператора теперь добавляют в тело запроса MAX
  `link: { "type": "reply", "mid": "<id>" }`.
- Текстовый MAX transport переведён на рекомендуемый текущей документацией
  endpoint `platform-api2.max.ru`; media path уже использовал его первым с
  fallback на legacy host.
- Добавлен `DialogReplyTransportServiceTest.sendMaxTextIncludesReplyLink`,
  который проверяет URL и JSON-body фактического HTTP-запроса.

## Проверка и rollout

- `./mvnw -q "-Dtest=DialogReplyTransportServiceTest" test` завершён успешно.
- `node --check src/main/resources/static/js/dialogs-details-history-runtime.js`
  завершён успешно.
- Собран Docker image `iguana-panel:local` `sha256:9e360163...`.
- Контролируемо заменены только `panel-web` и `bot-runner`; обе роли, а также
  неизменённый `ops-worker`, имеют статус `healthy`.
