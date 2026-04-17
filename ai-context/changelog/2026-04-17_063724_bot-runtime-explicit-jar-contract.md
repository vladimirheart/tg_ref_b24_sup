# 2026-04-17 06:37:24

## Заголовок

Phase 5 для 01-024: явный jar-discovery contract для bot runtime и синхронизация safety net

## Что изменено

- в `BotProcessProperties` добавлен конфиг `app.bots.executable-jars` для явного
  указания jar-артефактов по bot-модулям;
- `BotProcessService` теперь сначала проверяет configured jar path, а уже затем
  использует legacy fallback со сканированием `target/*.jar`;
- `application.yml` дополнен документирующей точкой входа для нового runtime
  contract без поломки обратной совместимости;
- `BotProcessServiceTest` расширен сценариями на приоритет configured jar и
  fallback к directory scan, а сам тест переведён с inline Mockito на простые
  реальные зависимости, чтобы стабильно работать на Java 25;
- `SupportPanelIntegrationTests` синхронизирован с текущим split `settings`
  и больше не обращается к удалённому `SettingsBridgeController.updateItEquipment(...)`;
- roadmap `01-024` обновлён: explicit artifact discovery contract перенесён из
  remaining work в уже начатый runtime boundary слой.

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/config/BotProcessProperties.java`
- `spring-panel/src/main/java/com/example/panel/service/BotProcessService.java`
- `spring-panel/src/main/resources/application.yml`
- `spring-panel/src/test/java/com/example/panel/service/BotProcessServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/SupportPanelIntegrationTests.java`
- `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`

## Проверка

- `spring-panel/.\\mvnw.cmd -q -DskipTests compile`
- `spring-panel/.\\mvnw.cmd -q "-Dtest=BotProcessServiceTest" test`
