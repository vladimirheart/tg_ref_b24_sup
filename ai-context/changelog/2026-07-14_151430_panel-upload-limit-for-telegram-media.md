# 2026-07-14 15:14:30 — panel upload limit for telegram media

## Промпт пользователя

> по отправке видео. посмотри лог панели. там явно указана ошибка

## Что изменено

- В логе панели подтверждена фактическая причина падения отправки медиа: `Maximum upload size exceeded` на `POST /api/dialogs/{ticketId}/media`.
- В `spring-panel/src/main/resources/application.yml` добавлены явные лимиты multipart-загрузки с env-переопределением:
  - `APP_MULTIPART_MAX_FILE_SIZE` по умолчанию `64MB`
  - `APP_MULTIPART_MAX_REQUEST_SIZE` по умолчанию `64MB`
- В `RestExceptionHandler` добавлен отдельный ответ для `MaxUploadSizeExceededException` со статусом `413 Payload Too Large` и кодом `FILE_TOO_LARGE`, чтобы фронт получал понятную причину, а не общий `500`.
- Добавлен тест `RestExceptionHandlerTest`, который проверяет новый ответ обработчика для oversized upload.

## Затронутые файлы

- `spring-panel/src/main/resources/application.yml`
- `spring-panel/src/main/java/com/example/panel/config/RestExceptionHandler.java`
- `spring-panel/src/test/java/com/example/panel/config/RestExceptionHandlerTest.java`

## Проверка

- Выполнены тесты: `.\mvnw.cmd -q "-Dtest=RestExceptionHandlerTest,DialogReplyTransportServiceTest,DialogReplyServiceTest,DialogQuickActionServiceTest" test`
- Результат:
  - `RestExceptionHandlerTest` — `Tests run: 1, Failures: 0, Errors: 0`
  - `DialogReplyTransportServiceTest` — `Tests run: 3, Failures: 0, Errors: 0`
  - `DialogReplyServiceTest` — `Tests run: 5, Failures: 0, Errors: 0`
  - `DialogQuickActionServiceTest` — `Tests run: 32, Failures: 0, Errors: 0`
