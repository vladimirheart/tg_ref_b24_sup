# 2026-06-30 22:07:33 - analytics controller transport split

- Файлы:
  `spring-panel/src/main/java/com/example/panel/controller/AnalyticsController.java`,
  `spring-panel/src/main/java/com/example/panel/controller/AnalyticsControllerSupport.java`,
  `spring-panel/src/main/java/com/example/panel/controller/AnalyticsWorkspaceRolloutGovernanceController.java`,
  `spring-panel/src/main/java/com/example/panel/controller/AnalyticsWorkspaceContextController.java`,
  `spring-panel/src/main/java/com/example/panel/controller/AnalyticsSlaGovernanceController.java`,
  `spring-panel/src/main/java/com/example/panel/controller/AnalyticsMacroGovernanceController.java`,
  `spring-panel/src/test/java/com/example/panel/controller/AnalyticsControllerWebMvcTest.java`,
  `ai-context/tasks/task-list.md`
- Промт пользователя:
```text
Возьми в работу задачу 01-132
```
- Что сделано:
  `AnalyticsController` сжат до page/render/export responsibility, а
  governance transport-эндпоинты вынесены в отдельные bounded controllers для
  `workspace rollout`, `workspace context`, `SLA governance` и
  `macro governance`.
- Что сделано:
  общие request-records, UTC/normalization helpers и sanitization-логика
  вынесены в package-private `AnalyticsControllerSupport`, чтобы не дублировать
  transport-парсинг между новыми контроллерами и сохранить прежние
  endpoint-контракты.
- Что сделано:
  `AnalyticsControllerWebMvcTest` переведён на multi-controller `@WebMvcTest`,
  а задача `01-132` в `ai-context/tasks/task-list.md` проведена через статусы
  `🟡 -> 🟣`.
- Проверка:
  выполнен `.\mvnw.cmd -DskipTests compile` в `spring-panel`, результат —
  `BUILD SUCCESS`.
- Проверка:
  попытка выполнить `.\mvnw.cmd -Dtest=AnalyticsControllerWebMvcTest test`
  упирается в стороннюю compile-ошибку в
  `spring-panel/src/test/java/com/example/panel/controller/ChannelApiControllerWebMvcTest.java`
  (`cannot find symbol: class Version`), поэтому полная test-проверка
  текущего split-а осталась частично заблокированной внешним состоянием
  ветки.
