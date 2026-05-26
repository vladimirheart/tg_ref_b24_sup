# Изменения

- В `spring-panel/src/main/java/com/example/panel/service/BotRuntimeContractService.java` добавлен анализ свежести `target`-JAR для bot runtime в режиме `app.bots.launch-mode=auto`.
- Если найденный через `target-scan` JAR старее исходников соответствующего модуля или `bot-core`, runtime теперь не запускает его, а переключается на `maven:spring-boot:run`.
- Для проверки свежести учитываются `java-bot/pom.xml`, `java-bot/<module>/pom.xml`, `java-bot/<module>/src`, а также `java-bot/bot-core/pom.xml` и `java-bot/bot-core/src`, потому что `bot-max` зависит от `bot-core`.
- В `spring-panel/src/test/java/com/example/panel/service/BotRuntimeContractServiceTest.java` добавлен тест на runtime-contract, где устаревший `target`-JAR должен приводить к `resolvedLauncherKind = maven`.
- В `spring-panel/src/test/java/com/example/panel/service/BotProcessServiceTest.java` добавлен тест на `resolveLaunchPlan(...)`, подтверждающий fallback на Maven при stale `target`-артефакте.
- В `ai-context/tasks/task-details/01-110.md` зафиксирована найденная operational-причина: `bot-max` реально поднимался из JAR с timestamp `13.05.2026 12:26:22`, поэтому stop/start канала воспроизводил старое поведение, несмотря на новые исходники.

# Проверки

- `spring-panel`: `./mvnw.cmd -DskipTests compile` — успешно.
- `spring-panel`: `./mvnw.cmd "-Dtest=BotRuntimeContractServiceTest,BotProcessServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` — неуспешно из-за уже существующих проблем в посторонних test-классах `DialogWorkspaceWorkflowSnapshotServiceTest` и `DialogWorkspaceParityServiceTest`, не связанных с текущими изменениями.
- Диагностика runtime-артефакта: `java-bot/bot-max/target/bot-max-0.0.1-SNAPSHOT.jar` имеет `LastWriteTime = 13.05.2026 12:26:22`, что подтверждает запуск устаревшего JAR до правки runtime-contract.

# Промпты пользователя

- `поведение осталось неизменным. возможно я что-то делаю не так, нужен анализ или продублируй поведение бота телеграм`
