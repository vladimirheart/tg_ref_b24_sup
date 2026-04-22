# 2026-04-21 17:58:13 - public form bell routing and dialog sort

- добавил канал-специфичную маршрутизацию panel-уведомлений в `questions_cfg.panelNotifications` с настройками событий `newPublicAppeal` и `firstResponseOverdue`
- вывел настройку адресатов bell-уведомлений в модалку канала на вкладке `Доставка`: режим получателей, отдел, whitelist/exclude и режим доставки
- сделал fallback для новых обращений из внешней формы на bell всем операторам, даже если старый скрытый `alertQueue` не был настроен вручную
- добавил watcher на просрочку первой реакции по `sla_target_minutes` с audit-дедупликацией `first_response_overdue_notification`
- исключил дубль bell-уведомления для самого первого сообщения public form, которое уже обрабатывается отдельным alert-route
- поменял сортировку списка диалогов на последнюю активность по `chat_history.timestamp`, чтобы свежие обращения и ожившие диалоги поднимались наверх
- расширил `DialogAuditService` и `NotificationService` вспомогательными методами для проверки audit-событий и выборки всех операторов
