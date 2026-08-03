# 2026-08-03 09:43:20 - bot autostart autowired fix

- Затронутые области:
  - `java-bot/bot-core/src/main/java/com/example/supportbot/service/AutoCloseFollowUpTaskService.java`
  - `ai-context/tasks/task-list.md`
  - `ai-context/tasks/task-details/01-167.md`
- Пользовательский промпт:
  - `допустим пусть так, но ещё есть рантайм в сайдбаре, в котором отображается статус ботов. сейчас показано что все боты остановлены, и они действительно не работают`
- Что сделано:
  - локализована реальная причина `stopped`-статусов: `BotAutoStartService` отрабатывал, но bot runtime падал во время старта Spring-контекста;
  - по `spring-panel.log` и `support-bot-telegram-3-process.log` подтверждено, что падение происходило на создании bean `AutoCloseFollowUpTaskService` с ошибкой `No default constructor found`;
  - в `AutoCloseFollowUpTaskService` явно помечен runtime-конструктор через `@Autowired`, чтобы Spring однозначно выбирал конструктор с `PlatformTransactionManager`, а не искал несуществующий пустой;
  - оформлена follow-up задача `01-167` в task-tracking.
- Проверки:
  - `java-bot\mvnw.cmd -q -pl bot-core -am -Dtest=AutoCloseFollowUpTaskServiceTest test` — success
  - `java-bot\mvnw.cmd -q -pl bot-telegram,bot-max -am -DskipTests compile` — success
