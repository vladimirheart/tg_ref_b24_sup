# 2026-07-14 13:13:54 — telegram document send and history sent status

## Промпт пользователя

> с jpg ситуация не изменилась. с mp4 стал возвращать "NetworkError when attempting to fetch resource.". и наверное нужно показывать статус отправки\доставки\прочтения  сообщения в поле истории

## Что изменено

- Для Telegram-ответов операторов отправка вложений переведена на единый путь `sendDocument`, без попыток слать `jpg/mp4` как `sendPhoto` или `sendVideo`.
- Во frontend-обработке отправки медиа добавлен более устойчивый разбор ответа: если backend вернёт не JSON, оператор увидит текст ошибки, а не только слепой `NetworkError`.
- В истории диалога для исходящих операторских сообщений добавлен честный статус `Отправлено`.

## Ограничения

- Статусы `Доставлено` и `Прочитано` для Telegram сейчас не выводятся, потому что в текущей интеграции и в хранилище `chat_history` нет таких receipt-данных, а Telegram Bot API в этом потоке их не поставляет.

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/service/DialogReplyTransportService.java`
- `spring-panel/src/main/resources/static/js/dialogs-details-history-runtime.js`
- `spring-panel/src/test/java/com/example/panel/service/DialogReplyTransportServiceTest.java`

## Проверка

- Выполнены тесты: `.\mvnw.cmd -q "-Dtest=DialogReplyTransportServiceTest,DialogReplyServiceTest,DialogQuickActionServiceTest" test`
- Результат:
  - `DialogReplyTransportServiceTest` — `Tests run: 3, Failures: 0, Errors: 0`
  - `DialogReplyServiceTest` — `Tests run: 5, Failures: 0, Errors: 0`
  - `DialogQuickActionServiceTest` — `Tests run: 32, Failures: 0, Errors: 0`
