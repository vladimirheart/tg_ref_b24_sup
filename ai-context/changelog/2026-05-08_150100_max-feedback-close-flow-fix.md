# 2026-05-08 15:01:00 - max feedback close flow fix

## Промпт пользователя

```text
не читай ai-context\baseline.
выявил проблему что например в мах не принимается оценка диалога по завешению, да и сам диалог не закрывается, потому что после отправки оценки пользователем и новом сообщении, всё приходит в старый диалог
```

## Что сделано

- В `DialogTicketLifecycleService` исправлен lifecycle закрытия/переоткрытия диалога со стороны панели:
  - при `resolve` теперь удаляется запись из `ticket_active`;
  - при `reopen` запись в `ticket_active` восстанавливается заново по `user_id` тикета.
- За счёт этого MAX/ботовая сторона больше не видит закрытый диалог как активный после ручного закрытия из панели.
- В `TicketService` добавлен метод очистки `clearTicketActivity(ticketId)` для безопасного удаления stale-active состояния.
- В `MaxWebhookController` добавлена защита от залипшего `ticket_active`:
  - если active-запись указывает на `resolved/closed` тикет или на отсутствующий тикет, она очищается перед дальнейшей маршрутизацией сообщения;
  - после этого цифровой ответ клиента корректно попадает в pending feedback, а не записывается в старую переписку.
- Обновлены тесты:
  - `DialogTicketLifecycleServiceTest` теперь проверяет удаление `ticket_active` при закрытии и восстановление при переоткрытии;
  - `MaxWebhookControllerTest` проверяет сценарий с stale active-записью и успешным сохранением оценки.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=DialogTicketLifecycleServiceTest" test`
- `java-bot\mvnw.cmd -q -pl bot-max -am "-Dtest=MaxWebhookControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dnet.bytebuddy.experimental=true" test`

## Итог

- Оценка после закрытия в MAX снова принимается корректно.
- Новое сообщение клиента после закрытого диалога больше не прилипает к старому `resolved`-тикету из-за stale записи в `ticket_active`.
