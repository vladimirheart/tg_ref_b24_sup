# 2026-04-29 17:07:15

## Что изменено

- Добавлен scheduler `IikoDepartmentLocationsSyncScheduler`, который раз в 5 минут синхронизирует live-дерево департаментов iiko в `config/shared/locations.json` и обновляет справочники параметров.
- `IikoDepartmentLocationCatalogService` научен собирать эффективный payload для `locations.json`: подставлять live `tree`, сохранять `statuses` и генерировать `city_meta` / `location_meta` для iiko-локаций.
- Страница настроек теперь отрисовывает блок `Структура локаций` из эффективного live-каталога, а не из сырого fallback JSON.
- MAX-бот переведён с вечного кэша локаций на TTL 5 минут и принимает `отмена` / `cancel` как команду остановки текущего сценария.
- Добавлены и обновлены тесты для синхронизации shared locations, effective payload панели и отмены сценария в MAX-боте.

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/service/IikoDepartmentLocationCatalogService.java`
- `spring-panel/src/main/java/com/example/panel/service/IikoDepartmentLocationsSyncScheduler.java`
- `spring-panel/src/main/java/com/example/panel/controller/ManagementController.java`
- `spring-panel/src/test/java/com/example/panel/service/IikoDepartmentLocationCatalogServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/IikoDepartmentLocationsSyncSchedulerTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/ManagementControllerWebMvcTest.java`
- `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxWebhookController.java`
- `java-bot/bot-max/src/test/java/com/example/supportbot/max/MaxWebhookControllerTest.java`
- `java-bot/bot-max/pom.xml`
