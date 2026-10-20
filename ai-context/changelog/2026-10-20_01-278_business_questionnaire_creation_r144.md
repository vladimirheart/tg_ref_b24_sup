# 01-278 — создание BUSINESS по выбранному вопроснику (R144)

Дата: 2026-10-20, Europe/Moscow.

Промты пользователя:

1. «продолжай. рабочая копия проекта находится на этой машине, поэтому проект при необходимости можно перезапускать»
2. «продолжай»

## Baseline и границы

HEAD/origin/main/actual remote main: 2ecfc44ff8643897b2362a6e8ce844bbd401ef5b;
parent 0803565890a0c7b7695185be25c6dfd499d93e50. Проверены R143 и его 11 файлов.
Main/index исходно чистые. Detached sandbox:
.tmp/01-278-r144-business-questionnaire-writes. Guarded source apply использует
тот же apply_patch набор, 25 baseline blob guards, отсутствие трёх новых targets,
exact manifest и normalized UTF-8/LF SHA-256.

Source-only, не rollout. Необходимый restart разрешён пользователем, но здесь
не требовался. Docker Server 29.7.2 подтверждён для Testcontainers; рабочая
БД/flags/контейнеры не менялись. Runtime checkpoint R143 не объявляется новой
приёмкой рабочего экземпляра.

## Выполнено

- Typed BUSINESS scope: положительный businessId, USER_SELECTION, stable
  template ID, SHA-256 шаблона и catalogSha256. Неполный BUSINESS JSON не
  превращается в unresolved/GENERAL; старые constructors/GENERAL JSON совместимы.
- Command, bot/panel event, publisher и finalizers Telegram/VK/MAX передают
  owner/version/catalog из session scope без повторного lookup. Меню ещё не подключено.
- Backend проверяет три flags, platform, текущий catalog R143, membership выбора
  и exact template version до root INSERT. Stale/rebind/edited/mixed/disabled
  варианты закрыты. Новый legacy/unresolved envelope для EXPLICIT_SELECTION
  закрыт даже без strict flag; прежний SINGLE_BUSINESS path сохранён.
- V73: nullable immutable business_selection_source/questionnaire_catalog_sha256,
  CHECK полного snapshot, trigger запрета изменения/удаления и readiness probe.
  Исторические rows не заполняются из сегодняшней конфигурации;
  GENERAL поля не переиспользуются.
- Outbox/inbox сверяют fingerprint до duplicate/reclaim. Canonical replay сверяет
  frozen owner/source/template/catalog/channel, не нынешние bindings.
  Другой event ID и отключение strict не снимают проверку provenance.
- Обновлены текущая карта/очередь 01-278 и долгоживущий S0-контракт.
  Кнопки, GENERAL shared option, persisted pending choice/provider resume,
  scoped locations/presets и остальные крупные блоки остаются открытыми.

## Проверки и результат

479/479 GREEN, 69 свежих Surefire XML, errors/failures/skips=0.

Полный java-bot reactor: 296/60 suites (core 205/37, Telegram 20/6,
VK 2/1, MAX 69/16), main/test compilation всех модулей.
Backend 183/9: selected creation 36, ingestion 20, inbox 9, SINGLE resolver 1,
shared registry 21, business registry 2, general registry 2, GENERAL HTTP 74,
readiness 18. В двух isolated Spring contexts применены все 73 migrations.
Selected creation отдельно повторён после усиления trigger assertions:
36 GREEN, включая CHECK-valid изменение/удаление immutable fingerprint.

Проверены three-platform INSERT, stale выбор до root, frozen replay после
rebind/deactivation, owner/source/template/catalog tampering, flags rollback,
transactional root/children rollback и exact retry, JSON/CAS scope и MAX state JSON.
Проверены 24 compiled classes, свежесть XML и compiled V73 resource относительно
source. Node queue R117/realtime R134 и git diff --check GREEN.

До GREEN исправлены новые JSON test harness: OffsetDateTime mapper bot-core
и Spring-compatible mapper MAX для computed DTO properties. Первый redirected
Maven launch не стартовал без target; ignored каталог создан, прогоны повторены.
Existing assertions не ослаблялись. Логи — в ignored sandbox target.

Нет staging/commit/push/deploy, рабочих migrations/backfill/flags,
rebuild/restart/prune/cleanup или реальных provider sends.
Generated Sass CSS не переносится. Задача остаётся 🟡.

## Точный manifest (28 файлов)

- `ai-context/changelog/2026-10-20_01-278_business_questionnaire_creation_r144.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/ConversationTicketCreatedEvent.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/ConversationTicketCreatedPublisher.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/ConversationTicketCreationCommand.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/IntegrationTransportOutboxService.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/QuestionnaireScope.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/TicketService.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/BotSessionStoreServiceTest.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/ConversationTicketCreatedPublisherTest.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/IntegrationTransportOutboxServiceTest.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/QuestionnaireScopeTest.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/TicketServiceInboundTransportTest.java`
- `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxWebhookController.java`
- `java-bot/bot-max/src/test/java/com/example/supportbot/max/MaxConversationSessionTest.java`
- `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java`
- `java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkSupportBot.java`
- `spring-panel/src/main/java/com/example/panel/config/PostgresRuntimeReadinessVerifier.java`
- `spring-panel/src/main/java/com/example/panel/entity/Ticket.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/BusinessTicketWriteScopeResolver.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/ConversationTicketCreatedEvent.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/ConversationTicketCreationIngestionService.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/IntegrationInboundEventInboxService.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V73__business_questionnaire_selection_provenance.sql`
- `spring-panel/src/test/java/com/example/panel/config/PostgresRuntimeReadinessVerifierTest.java`
- `spring-panel/src/test/java/com/example/panel/service/integration/ConversationTicketCreationIngestionServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/integration/SelectedBusinessTicketCreationPostgresTest.java`
