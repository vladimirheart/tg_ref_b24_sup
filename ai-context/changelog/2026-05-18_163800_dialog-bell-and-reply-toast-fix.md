# 2026-05-18 16:38:00 - dialog bell and reply toast fix

## Промпты пользователя

```text
1. при отправке сообщения оператором, возврещается popup сообщение "Сообщение отправлено". Убери его.

2. при новом обращении не приходят в колокольчик оповещение, так-же не приходит оповещение о новом сообщении из открытого диалога в этот-же колокольчик

продолжи
продолжи
```

## Что сделано

- Убраны success-popup уведомления `Сообщение отправлено` из обоих клиентских сценариев отправки ответа в `spring-panel/src/main/resources/static/js/dialogs.js`.
- В `OperatorNotificationWatcher` добавлено bell-уведомление для первого входящего сообщения после `public_form_submit`, чтобы новое обращение из web-form тоже попадало в колокольчик операторов.
- В `DialogReplyTargetService` активность диалога теперь привязывается к `operatorIdentity`, а не к `userId` клиента, чтобы новые сообщения в уже открытом диалоге приходили в bell нужному оператору.
- `DialogReplyService` обновлён на передачу логина оператора в `touchTicketActivity(...)`.
- Тесты обновлены под новую модель `ticket_active.user_identity`; добавлена интеграционная проверка bell-уведомления для нового сообщения в уже открытом web-form диалоге.
- В интеграционных тестах добавлен отдельный временный `users` sqlite-файл и reset watcher-state между тестами, чтобы сценарии с `OperatorNotificationWatcher` работали изолированно.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=SupportPanelIntegrationTests#operatorNotificationWatcherCreatesBellNotificationForFollowUpClientMessage+publicFormDialogSupportsOperatorRepliesThroughSharedLinkHistory+notificationServiceCountsAndMarksAsRead" test`

## Итог

- Оператор больше не видит popup `Сообщение отправлено` после отправки ответа.
- Bell-уведомление теперь создаётся для нового сообщения в уже открытом диалоге web-form.
- Для нового обращения из web-form добавлена серверная логика создания bell-уведомления.
