# 2026-04-16 00:12:10 - dialog config router and test safety net

## Что сделано

- добавлен `SettingsDialogConfigRoutingService`, чтобы вынести key-routing из
  coordinator `SettingsDialogConfigUpdateService`;
- добавлены unit-тесты `SettingsDialogConfigRoutingServiceTest` и
  `SettingsDialogConfigSupportServiceTest` для routing/validation сценариев;
- legacy `DialogApiControllerWebMvcTest` синхронизирован с новой
  controller-разбивкой после dialog domain split, чтобы не ломать
  `testCompile`.

## Проверка

- `spring-panel\mvnw.cmd -q -DskipTests compile`
- `spring-panel\mvnw.cmd -q "-Dtest=SettingsDialogConfigRoutingServiceTest,SettingsDialogConfigSupportServiceTest" test`

## Эффект

- `SettingsDialogConfigUpdateService` стал ещё тоньше и больше не держит у себя
  routing-правила;
- у этапа `01-024` появилась минимальная test safety net без поднятия всего
  legacy test-suite.
