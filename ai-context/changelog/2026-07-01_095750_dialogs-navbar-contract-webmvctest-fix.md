# 2026-07-01 09:57:50 — dialogs navbar contract webmvctest fix

## Prompt

`забери в работу задачу 01-136`

## Что сделано

- `DialogsControllerWebMvcTest` переведён на реальный `NavigationService` через `@Import`, чтобы тест проверял настоящий server-side contract страницы `dialogs`, а не no-op mock.
- В тест добавлены явные стабы зависимостей navbar/navigation слоя и проверки ветвления sidebar для `dialogs`, `ai-ops`, `channels` и `settings`.
- Для совместимости локального test-runtime с текущим JDK добавлен `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` c `mock-maker-subclass`, чтобы обычные `@MockBean` снова создавались без inline redefinition.
- Попутно исправлен compile blocker в `ChannelApiControllerWebMvcTest`: добавлен недостающий import `java.net.http.HttpClient.Version`, мешавший любому целевому прогону тестов.
- Статус задачи `01-136` в `ai-context/tasks/task-list.md` переведён в `🟣` как завершённой AI и ожидающей ручной проверки.

## Затронутые файлы

- `spring-panel/src/test/java/com/example/panel/controller/DialogsControllerWebMvcTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/ChannelApiControllerWebMvcTest.java`
- `spring-panel/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
- `ai-context/tasks/task-list.md`
