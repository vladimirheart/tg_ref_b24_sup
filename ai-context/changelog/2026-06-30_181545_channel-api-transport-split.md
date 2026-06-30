# 2026-06-30 18:15:45 - channel api transport split

- Файлы:
  `spring-panel/src/main/java/com/example/panel/controller/ChannelApiController.java`,
  `spring-panel/src/main/java/com/example/panel/controller/ChannelBotCredentialApiController.java`,
  `spring-panel/src/main/java/com/example/panel/controller/ChannelTelegramDiagnosticsApiController.java`,
  `spring-panel/src/main/java/com/example/panel/controller/ChannelNotificationApiController.java`,
  `spring-panel/src/main/java/com/example/panel/service/ChannelTransportService.java`,
  `spring-panel/src/test/java/com/example/panel/controller/ChannelApiControllerWebMvcTest.java`,
  `ai-context/tasks/task-list.md`,
  `ai-context/tasks/task-details/01-131.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промт пользователя:
```text
теперь пошли выполнять задачу 01-131
```
- Что сделано:
  `ChannelApiController` разрезан по transport responsibility: исходный класс
  оставлен тонким `/channels` CRUD/public-id wrapper, а bot credentials,
  Telegram diagnostics и channel notifications вынесены в отдельные
  controllers.
- Что сделано:
  общая orchestration и helper-логика вынесена в
  `ChannelTransportService`, чтобы controller-слой перестал быть mixed
  hotspot'ом и сохранил старые `/api/...` маршруты и payload-контракты.
- Что сделано:
  `ChannelApiControllerWebMvcTest` обновлён под multi-controller boundary;
  для Java 25 из теста убраны нестабильные inline Mockito-моки
  `SharedConfigService`, `IntegrationNetworkService`, `HttpClient` и
  `HttpResponse`, вместо них добавлены test stub/fake-реализации.
- Проверка:
  выполнен `./mvnw.cmd -Dtest=ChannelApiControllerWebMvcTest test` в
  `spring-panel`, результат — `BUILD SUCCESS`.
