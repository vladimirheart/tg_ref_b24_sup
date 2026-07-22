# 2026-07-22 16:59:32 — bot settings ui tail and sse timeout fix

## Контекст
- Пользователь: `что-то ты тут не доделал. шаблон оценок я всё ещё вижу легаси, до и в системе оценок в настройка есть об этом упоминание... а шаблонов вопросов вообще не отображается ни одного активного`
- Дополнительный контекст от пользователя: приложен лог shutdown/timeout panel с `HttpMessageNotWritableException` для `text/event-stream`.
- Значимый контекст из `01-150`: после основного cleanup compatibility-слоя выяснилось, что в user-facing UI ещё остался legacy wording, а SSE timeout/shutdown path всё ещё проходил через generic REST exception handler.

## Что сделано
- В `spring-panel/src/main/resources/templates/settings/index.html` доработан user-facing блок bot templates:
  - добавлен отдельный diagnostic placeholder для активного шаблона вопросов;
  - legacy wording в diagnostic placeholder заменён на нейтральное `Источник настроек шаблонов`.
- В `spring-panel/src/main/resources/static/js/bot-settings.js` доведён UX шаблонов до явного canonical поведения:
  - добавлен рендер активного question template (`Активный шаблон: ... | id: ... | вопросов: ...`);
  - активные question/rating cards теперь получают badge `Активный шаблон` и выделение рамкой;
  - при переключении default radio question/rating templates карточки и diagnostics сразу перерисовываются;
  - user-facing text `Legacy-аудит ...` заменён на нейтральный diagnostic об источнике canonical данных.
- В `spring-panel/src/main/java/com/example/panel/config/RestExceptionHandler.java` закрыт SSE shutdown/timeout хвост:
  - для `AsyncRequestTimeoutException` добавлен bodyless `503 Service Unavailable`;
  - generic `handleUnexpected(...)` теперь возвращает bodyless response для event-stream запросов `/api/events/stream`, не пытаясь сериализовать `ApiErrorResponse` в `text/event-stream`.
- Добавлены и обновлены тесты:
  - `spring-panel/src/test/java/com/example/panel/config/RestExceptionHandlerTest.java`
  - `spring-panel/src/test/java/com/example/panel/controller/ManagementControllerWebMvcTest.java`
- В `ai-context/tasks/task-details/01-150.md` дописан отдельный execution-slice про UI tail cleanup и SSE timeout fix.

## Проверки
- `spring-panel\\mvnw.cmd "-Dtest=ManagementControllerWebMvcTest,RestExceptionHandlerTest" test`

## Следующий шаг
- Ручная проверка страницы `/settings`:
  - убедиться, что в разделе шаблонов вопросов виден активный шаблон и активная карточка;
  - убедиться, что в разделе системы оценок больше нет user-facing упоминания `Legacy-аудит`.
- При следующем штатном stop/restart panel проверить, что warning про `No converter for ApiErrorResponse with preset Content-Type 'text/event-stream'` больше не появляется.
