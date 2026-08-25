# Changelog

- Добавлен monitoring-контур `provider_delivery_ledger`: новая таблица, PostgreSQL migration `V39__provider_delivery_ledger.sql` и SQLite bootstrap в `MonitoringDatabaseBootstrapService`.
- Реализованы `ProviderDeliveryLedgerEntry`, `ProviderDeliveryLedgerRepository`, `ProviderDeliveryLedgerService` и `ProviderDeliveryLedgerApiController` для persisted outbound delivery attempts, overview и history.
- Переписан `DialogReplyTransportService`: transport result теперь возвращает classification, severity, retry state, HTTP status, provider code/message, response excerpt и duration для `Telegram` / `VK` / `MAX`.
- `DialogReplyService` теперь пишет outbound outcomes в provider delivery ledger для text/media reply-path и фиксирует misconfiguration-сценарии без токена.
- В аналитике добавлена новая страница `provider-delivery`, JS-клиент и переход с общего analytics dashboard.
- Добавлены тесты `ProviderDeliveryLedgerServiceTest`, обновлён `DialogReplyServiceTest`, расширен `AnalyticsControllerWebMvcTest`; целевой прогон `DialogReplyTransportServiceTest, DialogReplyServiceTest, ProviderDeliveryLedgerServiceTest, AnalyticsControllerWebMvcTest` выполнен успешно.

## User Prompt

`давай дальше`
