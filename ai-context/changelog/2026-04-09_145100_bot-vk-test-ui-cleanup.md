# Task Batch 01-004..01-009

- Time: `2026-04-09 14:51:00 +0300`
- Scope:
  - `java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkSupportBot.java`
  - `java-bot/bot-core/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
  - `spring-panel/src/main/java/com/example/panel/controller/BotProcessApiController.java`
  - `spring-panel/src/main/resources/templates/dialogs/index.html`
  - `spring-panel/src/main/resources/static/js/dialogs.js`
  - `spring-panel/src/main/resources/templates/dashboard/index.html`
  - `spring-panel/src/main/resources/templates/tasks/index.html`
  - `spring-panel/src/main/resources/static/js/modal-helpers.js`
  - `spring-panel/src/main/resources/static/js/password-reset-requests.js`
  - `.gitignore`
  - `ai-context/tasks/task-list.md`
  - `ai-context/tasks/task-details/01-004.md`
  - `ai-context/tasks/task-details/01-005.md`
  - `ai-context/tasks/task-details/01-006.md`
  - `ai-context/tasks/task-details/01-007.md`
  - `ai-context/tasks/task-details/01-008.md`
  - `ai-context/tasks/task-details/01-009.md`

- Done:
  - Restored `bot-vk` build against current VK SDK (`1.0.16`): replaced deprecated long-poll API usage, switched to `sendDeprecated`, aligned `Long` id types, fixed `ChatHistoryService` call signature.
  - Stabilized `bot-core` tests on JDK 25 by forcing Mockito subclass mock-maker in test resources.
  - Hardened bot process API to keep `start/stop` endpoints POST-only.
  - Fixed corrupted UTF-8 texts in dialogs UI templates/scripts.
  - Removed UI noise (`console.log` + `alert`) in production templates and static scripts.
  - Started/implemented repository cleanup: removed tracked runtime/vendor artifacts (`spring-panel/.m2`, `logs/*`) from working tree and added ignore for `java-bot/.m2`.

- Verification:
  - `java-bot`: `mvnw -pl bot-vk -am -DskipTests compile` -> success
  - `spring-panel`: `mvnw -DskipTests test-compile` and `mvnw -DskipTests compile` -> success
  - `bot-core`: `mvnw -pl bot-core -Dmaven.repo.local=.m2 test -DskipITs` -> success
