# 01-278 / R291 — изоляция transport principals дочерних ботов

Промты пользователя:

> выполни пункт 1

> продолжи с места остановки

Дата:2026-11-06, Europe/Moscow +03.

## Source R291 — 2026-11-06: transport principals каждого child

Выполняется пункт1 нового списка после R290 — оставшаяся сквозная изоляция,
а не уже завершённый R289 программный блок delivery/rating. В rabbitmq contour
каждый channel/platform получает отдельные domain-separated API/SQL/Rabbit
credentials; общий master/signing secret и privileged SQL/Rabbit login не
передаются детям. Launcher очищает inherited configuration overrides. Startup
проверяет реальный SQL session_user, отсутствие business/DDL/role privileges и
наличие FORCE RLS. V86 вводит bot_child_principals и RLS трёх transport tables;
LOGIN roles создаются отдельно администратором, не миграцией или приложением.
JDBC dev contour не объявляется новым production contract.

Signed child API требует live canonical channel/platform/principal и owning
transaction, ограничивает root, pending feedback, media и channel routes до
idempotency replay. Unknown routes denied; child не меняет support-chat config
и не использует retired unowned edit. История и unblock summary ограничены
authenticated channel до SQL LIMIT; inconsistent/duplicate root history не
выдаётся. Remote-only runtime settings не читают общие локальные файлы.
Служебный чат допускается только по свежему exact support_chat_id и sender,
без positive cache/fallback. AMQP user_id дополнен HMAC исходных bytes;
backend повторяет channel/platform/actual user/raw ticket admission под locks.
После revoke запрещены новые действия, но допустима фиксация положительной
квитанции уже принятой отправки, без resend неизвестного результата.

Совокупно 1023 JVM tests/43 suites GREEN,0 failures/errors/skips. Это retained
latest XML разных targeted runs, не один full-suite запуск. В том числе55
реальных PostgreSQL/API/inbound cases с полной Flyway V1–V86,8 native scoped
history SQL cases и15 actual bot startup SQL cases с отдельно provisioned
LOGIN roles. Свой native PostgreSQL16.15, loopback/nonstandard port и nonce
guard; рабочие datasource/config не читались. Provider doubles и loopback HTTP
не заменяют live acceptance. PowerShell5.1 generator checks14 GREEN, runner
self-test GREEN. scripts/prepare-child-transport.ps1 создаёт только закрытые
ACL файлы SQL/Rabbit definitions/manifest в новом .tmp, ничего не применяет.
Инструкция согласованного release/provision/drain/rollback:
docs/runbooks/child-transport-isolation.md.

Baseline HEAD/origin/main/actual remote a7ce504dd (R290), parent3ba33f464.
Source готовился в detached sandbox; raw guards4108 tracked files, exact
file set и перенос тех же bytes проверяются отдельно от staging/commit/push.
Ранние RED сохранены: короткие старые test-secret fixtures, неверный reactor
argument/API контейнера, отсутствующая owned feedback test DB и неполный
JdbcTemplate test double; исправлены точечно, assertions не ослаблены. Проверка
history query обнаружила неверное имя tie-break column: применён настоящий
messages.group_msg_id и actual repository SQL повторён на PostgreSQL.

38 MAX PG/Rabbit cases остаются PENDING_DOCKER: local engine недоступен.
15 principal cases прошли native; Docker variant отдельно pending. Полный
acceptance runner после пользовательского commit ожидает26 contracts и53 IT
(38 media +15 principals), пишет r291-proof.json. Source R291 требует V86 и
согласованного backend/launcher/child release с отдельным provision. На другой
рабочей машине/сессии DB/broker/rebuild/restart/config/flags не менялись;
последний документированный release R280/R279, новый deploy не подтверждён.

Пункт1 целиком не закрыт: MAX forwarded/reply media и сохранение их provenance,
остальные legacy/direct paths, cross-platform GENERAL/questionnaire restore/
replay/rebind и live provider acceptance остаются. Это не полная изоляция ОС/
Redis/object storage. Политики специальных разделов/ролей, AI и первый
production S6 также остаются; task🟡. S2B снятR190. Ручная fleet restart
приёмка R289/R290 остаётся в production-e2e-smoke §7.1 MANUAL_PENDING.

Изменённые файлы:

- `ai-context/changelog/2026-11-06_01-278_child_transport_principals_r291.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `docs/BOT_RUNTIME_CONTRACT.md`
- `docs/runbooks/child-transport-isolation.md`
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/ChildTransportDatabase.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/DataSourceConfig.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/RabbitIntegrationTransportConfig.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/ChildTransportEnvelope.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/IntegrationTransportOutboxService.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/MessagingService.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/OutboundTransportDeliveryLedgerService.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/PanelOutboundSenderAuthorizationClient.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/RuntimeConfigService.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/SharedConfigService.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/config/ChildTransportDatabaseTest.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/ChildRuntimeIsolationTest.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/ChildTransportEnvelopeTest.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/MessagingPlatformIsolationTest.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/MessagingSenderIdentityTest.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/PanelSupportChatAuthorizationTest.java`
- `java-bot/bot-feedback-acceptance/pom.xml`
- `java-bot/bot-feedback-acceptance/run-max-media-acceptance.ps1`
- `java-bot/bot-feedback-acceptance/src/test/java/com/example/acceptance/ChildTransportPrincipalIT.java`
- `java-bot/bot-feedback-acceptance/src/test/java/com/example/acceptance/MaxFollowupMediaChainIT.java`
- `java-bot/bot-feedback-acceptance/src/test/java/com/example/acceptance/MaxMediaChainFixture.java`
- `java-bot/bot-max/src/test/java/com/example/supportbot/max/MaxPublishedFollowupMediaTest.java`
- `scripts/prepare-child-transport.ps1`
- `scripts/sql/provision-child-transport.sql`
- `scripts/tests/prepare-child-transport.tests.ps1`
- `spring-panel/src/main/java/com/example/panel/controller/BotOutboundSenderAuthorizationController.java`
- `spring-panel/src/main/java/com/example/panel/controller/BotRuntimeReadApiController.java`
- `spring-panel/src/main/java/com/example/panel/controller/BotRuntimeWriteApiController.java`
- `spring-panel/src/main/java/com/example/panel/controller/BusinessQuestionTemplateRuntimeController.java`
- `spring-panel/src/main/java/com/example/panel/controller/FeedbackPromptDeliveryAuthorizationController.java`
- `spring-panel/src/main/java/com/example/panel/controller/GeneralChannelQuestionTemplateRuntimeController.java`
- `spring-panel/src/main/java/com/example/panel/controller/SelectedTicketFollowupMediaController.java`
- `spring-panel/src/main/java/com/example/panel/controller/SharedExpandedQuestionnaireRuntimeController.java`
- `spring-panel/src/main/java/com/example/panel/controller/SharedGeneralQuestionnaireRuntimeController.java`
- `spring-panel/src/main/java/com/example/panel/controller/SharedQuestionnaireRuntimeController.java`
- `spring-panel/src/main/java/com/example/panel/controller/SharedResourceQuestionnaireRuntimeController.java`
- `spring-panel/src/main/java/com/example/panel/repository/MessageRepository.java`
- `spring-panel/src/main/java/com/example/panel/security/ChildBotCredentials.java`
- `spring-panel/src/main/java/com/example/panel/security/ChildBotInboundPrincipalService.java`
- `spring-panel/src/main/java/com/example/panel/security/ChildBotRequestScopeService.java`
- `spring-panel/src/main/java/com/example/panel/security/InternalBotApiRequestGuardService.java`
- `spring-panel/src/main/java/com/example/panel/service/BotOutboundSenderAuthorizationService.java`
- `spring-panel/src/main/java/com/example/panel/service/BotProcessService.java`
- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeBlacklistService.java`
- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeContractService.java`
- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeTicketReadService.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/ConversationTicketCreatedListener.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/InboundClientMessageListener.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V86__child_transport_principals.sql`
- `spring-panel/src/test/java/com/example/panel/security/ChildBotCredentialsTest.java`
- `spring-panel/src/test/java/com/example/panel/security/ChildBotRequestScopeServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/security/ChildPrincipalPostgresTest.java`
- `spring-panel/src/test/java/com/example/panel/service/BotOutboundSenderAuthorizationTest.java`
- `spring-panel/src/test/java/com/example/panel/service/BotRuntimeContractServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/BotRuntimeTicketReadServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/BotSenderLaunchContractTest.java`
- `spring-panel/src/test/java/com/example/panel/service/ChildProcessEnvironmentTest.java`
- `spring-panel/src/test/java/com/example/panel/service/ChildScopedHistoryPostgresTest.java`
