# 2026-08-25 16:22:51 - provider delivery burn-rate alerting

## Initiated by

- `хорошо. бери в работу задачу 01-206`

## What changed

- Оформлена и выполнена задача `01-206`: создан `ai-context/tasks/task-details/01-206.md`, статус в `ai-context/tasks/task-list.md` переведён в `🟣`.
- Добавлен backend alerting layer поверх `provider_delivery_ledger`:
  - `ProviderDeliveryAlertingService`;
  - `ProviderDeliveryAlertingScheduler`;
  - `ProviderDeliveryAlertingApiController`.
- Реализован multi-window SLO burn-rate для двух независимых сигналов:
  - sustained provider delivery failures;
  - provider-side rate-limit pressure (`429` / `rate_limited`).
- Добавлена автоматическая синхронизация signal incidents через existing `IncidentService` с `open/refresh/resolve` lifecycle и history snapshots в `monitoring_check_history`.
- Обновлена страница `analytics/provider-delivery` и `provider-delivery-monitoring.js`:
  - burn-rate overview metrics;
  - channel-level failure/rate-limit breakdown;
  - related incident context;
  - отдельная история burn-rate snapshots.
- Добавлены targeted тесты:
  - `ProviderDeliveryAlertingServiceTest`;
  - `ProviderDeliveryAlertingApiControllerWebMvcTest`.

## Verification

- Выполнен targeted прогон:
  - `./mvnw -q "-Dtest=ProviderDeliveryAlertingServiceTest,ProviderDeliveryAlertingApiControllerWebMvcTest,AnalyticsControllerWebMvcTest" test`
- Дополнительно выполнена сборка:
  - `./mvnw -q -DskipTests compile`

## Files

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-206.md`
- `ai-context/changelog/2026-08-25_162251_provider-delivery-burn-rate-alerting.md`
- `spring-panel/src/main/java/com/example/panel/service/ProviderDeliveryAlertingService.java`
- `spring-panel/src/main/java/com/example/panel/service/ProviderDeliveryAlertingScheduler.java`
- `spring-panel/src/main/java/com/example/panel/controller/ProviderDeliveryAlertingApiController.java`
- `spring-panel/src/main/resources/templates/analytics/provider-delivery.html`
- `spring-panel/src/main/resources/static/js/provider-delivery-monitoring.js`
- `spring-panel/src/test/java/com/example/panel/service/ProviderDeliveryAlertingServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/ProviderDeliveryAlertingApiControllerWebMvcTest.java`
