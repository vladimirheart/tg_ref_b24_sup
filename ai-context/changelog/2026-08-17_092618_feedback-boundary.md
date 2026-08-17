# 2026-08-17 09:26:18 - feedback-boundary

## User prompt

`бери в работу следующий крупный шаг`

## Что изменено

- в `spring-panel` internal bot read API расширен lookup-операцией для активного `pending_feedback_request` по пользователю и каналу;
- в `spring-panel` internal bot write API добавлена mutation-операция для submit feedback по `pending_feedback_request.id`;
- `BotRuntimeTicketReadService` теперь backend-side отсекает уже оценённые заявки и возвращает canonical pending feedback request;
- `BotRuntimeTicketWriteService` теперь backend-side сохраняет оценку в `feedbacks`, append-ит `feedback_created` в `ui_event_outbox` и истекает pending request;
- `UiEventOutboxAppendService` получил panel-owned helper для `feedback_created`;
- в `java-bot/bot-core` `FeedbackService` переведён на panel-owned read/write path в `rabbitmq`-режиме с сохранением JDBC fallback;
- `PanelTicketReadClient` и `PanelTicketWriteClient` расширены feedback lookup/submit операциями;
- добавлены targeted tests для read/write controller, read/write service и bot-core `FeedbackService`.

## Проверка

- `cmd /c "mvnw.cmd -q -pl bot-core,bot-telegram,bot-vk,bot-max -DskipTests compile"` в `java-bot`
- `cmd /c "mvnw.cmd -q -DskipTests compile"` в `spring-panel`
- `cmd /c "mvnw.cmd -q -pl bot-core -Dtest=FeedbackServiceTest test"` в `java-bot`
- `cmd /c "mvnw.cmd -q -Dtest=BotRuntimeReadApiControllerWebMvcTest,BotRuntimeWriteApiControllerWebMvcTest,BotRuntimeTicketReadServiceTest,BotRuntimeTicketWriteServiceTest test"` в `spring-panel`

## Что дальше

- провести audit оставшихся runtime-side direct DB paths в `java-bot` и добить те, что ещё относятся к живым runtime сценариям;
- потом уже решать, нужен ли более глубокий legacy cleanup или можно переходить к следующему большому инфраструктурному блоку `01-181`.
