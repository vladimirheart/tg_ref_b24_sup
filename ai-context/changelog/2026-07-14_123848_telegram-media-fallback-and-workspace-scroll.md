# 2026-07-14 12:38:48 — telegram media fallback and workspace scroll

## Промпт пользователя

> уже намного лучше. но на отправке файла jpg, mp4 возвращает ошибку "Внутренняя ошибка сервера" и не отправляет. при этом документы, архивы и даже скрины или jfif-файлы отправляет корректно.
>
> дополнительно: при отправке любого сообщения, история должна спускаться вниз.

## Что изменено

- Для Telegram-ответов с медиа добавлен безопасный fallback: если отправка через `sendPhoto` или `sendVideo` не проходит, файл повторно уходит как `sendDocument`.
- Вынесен отдельный request-path `sendTelegramMediaRequest(...)` и добавлен тестовый контур на сценарии `photo -> document` и `video -> document`, чтобы не сломать обработку ответов операторов клиенту.
- Для workspace-ленты добавлена принудительная прокрутка вниз после локального добавления сообщения и после перерендера ленты.
- Отправка вложений из workspace теперь дожидается `afterSuccess` и перезагрузки секции сообщений, чтобы очистка composer и автоскролл происходили в правильном порядке.
- Обновлённые frontend-ресурсы скопированы в `spring-panel/target/classes/static/js` для немедленного подхвата локальным рантаймом.

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/service/DialogReplyTransportService.java`
- `spring-panel/src/main/resources/static/js/dialogs-details-history-runtime.js`
- `spring-panel/src/main/resources/static/js/dialogs-workspace-runtime.js`
- `spring-panel/src/test/java/com/example/panel/service/DialogReplyTransportServiceTest.java`

## Проверка

- Выполнены тесты: `.\mvnw.cmd -q "-Dtest=DialogReplyTransportServiceTest,DialogReplyServiceTest,DialogQuickActionServiceTest" test`
- Результат:
  - `DialogReplyTransportServiceTest` — `Tests run: 3, Failures: 0, Errors: 0`
  - `DialogReplyServiceTest` — `Tests run: 5, Failures: 0, Errors: 0`
  - `DialogQuickActionServiceTest` — `Tests run: 32, Failures: 0, Errors: 0`
