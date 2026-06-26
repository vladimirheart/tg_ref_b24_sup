# 2026-06-25 18:18:21 - publicform backend/test removal

## Промпты пользователя

- `возьми в работу и выполни задачу 01-134`

## Что изменено

- из `spring-panel/src/main/java/com/example/panel/service/OperatorNotificationWatcher.java`
  убрана special-case ветка `public_form_submit` /
  `public_form_new_appeal_notification`; входящие внешние сообщения снова
  идут через единый generic alert flow с `alertQueueService` и fallback
  `notifyAllOperators`;
- из `spring-panel/src/test/java/com/example/panel/service/OperatorNotificationWatcherTest.java`
  удалены unit-сценарии, завязанные на удалённый `PublicForm` bootstrap flow;
- из `spring-panel/src/test/java/com/example/panel/service/SupportPanelIntegrationTests.java`
  выпилен весь оставшийся `PublicFormService`-контур: импорты, DI и
  integration/runtime-методы, которые больше не соответствуют текущему
  product scope;
- удалён устаревший template-contract тест
  `spring-panel/src/test/java/com/example/panel/controller/PublicShellTemplateContractTest.java`,
  который держал ложный active-scope вокруг уже отсутствующего public shell;
- статус задачи `01-134` в `ai-context/tasks/task-list.md` переведён в `🟣`
  после выполнения AI-части работы.

## Проверка

- `rg -n --glob '!**/target/**' --glob '!ai-context/changelog/**' --glob '!ai-context/tasks/**' --glob '!docs/**' "PublicForm|public_form|publicForm|PublicShell" spring-panel/src/main spring-panel/src/test ai-context`
- `.\mvnw.cmd -q -DskipTests compile`
- `.\mvnw.cmd -q -Dtest=OperatorNotificationWatcherTest test`
- `.\mvnw.cmd -q -Dtest=SupportPanelIntegrationTests test`

## Примечания

- `SupportPanelIntegrationTests` после removal по-прежнему падает, но на
  несвязанных workspace-rollout / telemetry assertions (`workspaceRollout*`,
  `workspaceTelemetrySummary*`), а не на `PublicForm`-контуре.
