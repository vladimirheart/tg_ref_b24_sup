# 01-278: вопросник канала без бизнеса

Время: 2026-10-16 08:56 МСК.

Промты пользователя:

- `обращения без бизнеса вообще. вопросни должен уметь быть свободно собираемым с разным порядком и вопрсами`
- `продолжай`

Изменённые области и файлы:

- `spring-panel/src/main/resources/db/migration/postgresql/V57__general_channel_question_template_bindings.sql`
  — отдельная opt-in привязка general-only канала к каноническому шаблону и его SHA-256.
- `spring-panel/src/main/java/com/example/panel/service/GeneralChannelQuestionTemplateService.java`
  — проверка активного канала, явного выбора шаблона, отсутствия business bindings,
  неизменности вопросника; выдача одного шаблона без бизнес-локаций.
- `spring-panel/src/main/java/com/example/panel/controller/GeneralChannelQuestionTemplateAdminController.java`
  и `GeneralChannelQuestionTemplateRuntimeController.java` — административный API
  с системными правами и CSRF и защищённый internal bot API.
- `spring-panel/src/main/java/com/example/panel/config/MultibusinessFeatureFlags.java`,
  `spring-panel/src/main/java/com/example/panel/service/BusinessQuestionTemplateRegistryService.java`
  и `spring-panel/src/main/java/com/example/panel/service/integration/ConversationTicketCreationIngestionService.java`
  — отдельный default-off флаг, взаимная защита general/business runtime и
  fail-closed запись `GENERAL_CHANNEL` только после проверки привязки.
- `spring-panel/src/test/java/com/example/panel/controller/GeneralChannelQuestionTemplateAdminControllerWebMvcTest.java`,
  `GeneralChannelQuestionTemplateRuntimeControllerWebMvcTest.java`,
  `spring-panel/src/test/java/com/example/panel/service/GeneralChannelQuestionTemplatePostgresIntegrationTest.java`
  — новые WebMvc и PostgreSQL regression-тесты.
- `spring-panel/src/test/java/com/example/panel/config/MultibusinessFeatureFlagsTest.java`,
  `spring-panel/src/test/java/com/example/panel/service/BusinessQuestionTemplateRegistryPostgresIntegrationTest.java`,
  `TicketLocatorFlywayMigrationTest.java`,
  `spring-panel/src/test/java/com/example/panel/service/integration/ConversationTicketCreationIngestionServiceTest.java`
  — проверка default-off, конфликта scopes, строгой записи и Flyway V57.
- `ai-context/rules/03-multibusiness-s0-contract.md` и
  `ai-context/tasks/task-details/01-278.md` — уточнён контракт и остаток работ.

Проверки: чистый targeted panel `clean test` 24/24 GREEN и отдельный flags test
2/2 GREEN. Рабочая БД, конфигурация флагов, deployment и git staging/commit/push
не изменялись. Bot Telegram/VK/MAX ещё не потребляет новый endpoint, а права и
очередь general tickets не подключены; это не разрешение на rollout.
