# 2026-11-25 — 01-310 Notification load shedding

## Промт пользователя

> так, оповещения тогда сокращаем, как оповещения до критических и выше. плюс оповещения о эскалации.
> максимальное количество подключений к postgresql поднять до 550
> да, своё предложение тоже ок - делай.
> как ещё можно улучшить\ускорить работу

## Изменения

- Добавлена единая политика persisted-уведомлений: routine-события диалогов не пишут bell notifications, а critical incidents и escalation сохраняются.
- Оставлены SLA и AI эскалации; realtime-обновления диалога не отключены.
- Sidebar получил single-flight unread counter и backoff 5–60 секунд вместо overlapping `setInterval`.
- Добавлена PostgreSQL-миграция `V102` с частичным индексом unread notification по получателю.
- Compose, `.env.example` и локальный `.env` задают `APP_POSTGRES_MAX_CONNECTIONS=550`.
- Обновлены targeted Java tests и sidebar source contract.

## Проверка

- `node --check src/main/resources/static/js/sidebar.js`.
- Targeted Maven suite: `AlertQueueServiceTest`, `DialogRealtimeEventServiceTest`, `IncidentRouteDeliveryOutboxServiceTest`, `IncidentRouteIdentityBoundaryTest`, `OperationalNotificationPolicyTest`, `SidebarRuntimeUiSourceContractTest`.
