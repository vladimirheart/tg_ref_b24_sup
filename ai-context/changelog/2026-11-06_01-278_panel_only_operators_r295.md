# 01-278 / R295 — группы только для уведомлений

Промты пользователя:

> делай! долго очень всё тянется!

Уточнение по запросу о legacy Telegram group replies:

> Операторские действия только из панели; группы — для уведомлений (рекомендую)

Fresh baseline/HEAD/origin/main/actual remote96d4426d4d879e161cfb1338803217bc68effe6a,
parentd23602ee4c43d99406cbbb4debe37de1a97532ec. Проверены message и15 exact paths
R294. Root clean; detached sandbox с raw/blob guards всех4154 tracked files.
Рабочая среда — другая машина/агентская сессия. Контейнеры/DB/flags не менялись.

## Source R295 — 2026-11-06: операторские действия только в панели

Решение пользователя: «Операторские действия только из панели; группы — для
уведомлений». Членство/admin в Telegram-группе и строка operatorIdentity не
подтверждают учётную запись или business/GENERAL grants панели. Привязка
внешних операторов к panel accounts в обязательный scope не добавляется.

Telegram принимает только private chat с actual positive sender=chat owner
до dedup/канала/вопросника. Групповые /reply, редактирование ответов, auto-bind
и /confirm удалены; группы не могут дописать частную сессию. VK отсекает group/
foreign peer до claim и restore. Ранее выбранный BUSINESS/GENERAL scope не
переопределяется, private сообщения продолжают прежний selected workflow.

Шесть unowned internal operator/config routes retired: authenticated control-
plane получает410 до prepareWrite/idempotency replay, child —403 до ticket
lookup. Это reopen, activity PUT/DELETE, relay, operator edit и support-chat
update. Core HTTP clients не вызывают эти routes; local operator relay/edit
и support-chat update не делают JDBC/JPA fallback. Authenticated panel controls
и R294 уведомления продолжают работать по прежним actor/recipient guards.

460 targeted JVM cases/17 suites GREEN,52 новых; среди них50 реальных
PostgreSQL regression cases на nonce-owned test fixture и24 MVC/cache cases.
Main/test compile backend и bot modules выполнен; working DB/Redis/broker/
provider другой машины не использовались. Новых migrations/flags нет.
Backend и bot jars/image требуют новой согласованной сборки: старый Telegram
может отправить через SDK до обращения к API, одного нового backend мало.
R293 image содержит sourceR292, не R294/R295; deployment=false.

Программный подблок legacy group/operator закрыт. Не планировать его заново.
Следующий общий gate пункта1 — существующие42 MAX +15 principal PG/Rabbit cases
и cross-platform restore/replay/rebind/live acceptance на выделенном стенде.
Local Docker недоступен; этот gate PENDING_DOCKER. S6 и §7.1 full in-flight
restart остаются MANUAL_PENDING. Task🟡; S2B снятR190; delivery/rating R289
не открывается заново. Инструкция: docs/runbooks/panel-only-operator-actions.md.


## Верификация и RED

460/17 final targeted cases GREEN (bot/backend отдельные runs);50 SQL cases
повторены полностью на реальном disposable PostgreSQL, после Docker unavailable.
Новая policy разрешена явным ответом пользователя. Старые5 core relay/edit,
6 MVC delegation и channel-config expectations обновлены до отказа/no I/O;
read exact IDs, client edit, feedback/unblock и source owner checks сохранены.
V1 bot RED5 старых protocol assertions; v2 bot compile RED — generic JsonNode
AssertJ overload, исправлен явным типом; v3 RED1 ожидание позднего owner exception,
заменено более ранним no-I/O denial. RED XML/logs сохранены, assertions не skip.
V1 panel24 new MVC/cache и прочие non-DB GREEN,50 SQL RED из-за отсутствия Docker.
Native initdb30/60s timeout operators сохранены, не weakening теста; штатно
возобновлён свой ранее остановленный R292 fixture, nonce endpoint guard сохранён,
fresh test schemas; рабочая БД не использовалась. Fixture остановлен после run.
Main/test compile backend/bot modules; Sass output не переносится в source.
PowerShell5.1 parser runbook/commit script и git diff --check GREEN. Список
validated source/class/report hashes и exact changed-file set — в local proof.

## Граница

Программный group/operator подблок закрыт целиком согласно выбранной политике.
Нет новой actor-link таблицы/новых migrations/данных/flags. Нужна новая сборка
backend+bot image; один backend не блокирует SDK старого child. Source applied
после sandbox GREEN, staged/committed/pushed/deployment=false; manual commit
block подготовлен, не исполнялся. Общее01-278🟡:57 PG/Rabbit cases и сквозная
restore/rebind/provider/rollback/S6 приёмка остаются, S2B снят, R289 не повторяется.

## Exact changed project files

- ai-context/changelog/2026-11-06_01-278_panel_only_operators_r295.md
- ai-context/rules/03-multibusiness-s0-contract.md
- ai-context/tasks/task-details/01-278.md
- docs/BOT_RUNTIME_CONTRACT.md
- docs/runbooks/child-transport-isolation.md
- docs/runbooks/legacy-support-chat-delivery.md
- docs/runbooks/panel-only-operator-actions.md
- java-bot/bot-core/src/main/java/com/example/supportbot/service/ChannelService.java
- java-bot/bot-core/src/main/java/com/example/supportbot/service/PanelChannelClient.java
- java-bot/bot-core/src/main/java/com/example/supportbot/service/PanelTicketWriteClient.java
- java-bot/bot-core/src/main/java/com/example/supportbot/service/TicketService.java
- java-bot/bot-core/src/test/java/com/example/supportbot/service/ChannelServiceTest.java
- java-bot/bot-core/src/test/java/com/example/supportbot/service/PanelTicketClientsExactLegacyIdTest.java
- java-bot/bot-core/src/test/java/com/example/supportbot/service/RetiredOperatorBoundaryTest.java
- java-bot/bot-core/src/test/java/com/example/supportbot/service/TicketServiceInboundTransportTest.java
- java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java
- java-bot/bot-telegram/src/test/java/com/example/supportbot/telegram/TelegramPrivateIngressTest.java
- java-bot/bot-telegram/src/test/java/com/example/supportbot/telegram/TelegramQuestionnaireSelectionTest.java
- java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkSupportBot.java
- java-bot/bot-vk/src/test/java/com/example/supportbot/vk/VkQuestionnaireSelectionTest.java
- spring-panel/src/main/java/com/example/panel/controller/BotRuntimeWriteApiController.java
- spring-panel/src/main/java/com/example/panel/security/ChildBotRequestScopeService.java
- spring-panel/src/test/java/com/example/panel/controller/BotRuntimeWriteApiControllerWebMvcTest.java
- spring-panel/src/test/java/com/example/panel/controller/RetiredOperatorApiTest.java
- spring-panel/src/test/java/com/example/panel/security/ChildBotRequestScopeServiceTest.java

Дополнение к верификации того же пакета: XML proof parser v1 ошибочно читал
classname вместо testcase name; v2 использует границу имени атрибута, сохранив
обязательные проверки новых test method names. Child scope cases повторены
отдельно с actual POST/PUT/DELETE:32 GREEN, включая7 новых; прежний cached class
с25 cases не засчитан как proof нового coverage. Итог460 — latest successful
reports335 bot +93 прочих backend +32 child,17 suites,52 новых; не один общий
Maven run. Exact source/class/XML hashes сохранены после завершения всех JVM.
