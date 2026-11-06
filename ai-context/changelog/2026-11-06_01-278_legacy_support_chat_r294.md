# 01-278 / R294 — прямые legacy уведомления Telegram/VK

Промт пользователя:

> репо обновлён. проверь и продолжай по задаче

Дата:2026-11-06, source/test-сессия, рабочая среда на другой машине.
Fresh baseline/HEAD/origin/main/actual remote d23602ee4c43d99406cbbb4debe37de1a97532ec,
parent e3ede2da79861fc7bab6f767cccc65a0e998fa4b. Проверены message/parent/changed paths
R293 (4 metadata files) и R292 (58 exact paths предыдущего source proof).
Рабочий checkout clean; detached worktree с raw guards всех4148 tracked files.
Ни актуальный image R293, ни факт нового source не считаются deployment.

## Source R294 — 2026-11-06: legacy уведомления Telegram/VK в разрешённый чат

Продолжен пункт1 после sourceR292/buildR293. Legacy сводки обращений, relay
клиентских ответов и запросы/digest разблокировок Telegram/VK больше не используют
canonical ID записи канала как внешний chat/peer ID. Все эти отправки проходят
SupportChatDeliveryService: bound sender/token + текущий exact support_chat_id,
две uncached signed backend decisions R291, повторная локальная сверка после I/O.
Missing/invalid recipient, revoke, rotation/rebind или unavailable API запрещают
SDK handoff без fallback в properties/shared chat. Telegram signed negative
group IDs поддерживаются; VK peer должен быть canonical positive int32.

Telegram media pin сохраняется до materialize; после чтения файла и перед
document fallback проверка повторяется. Временный файл очищается и после
успешного preferred send. Fallback разрешён только после явного Telegram400;
403/429/5xx/transport exception/lost HTTP response не дают автоматический resend.
Изменения не добавляют очередь восстановления service-chat уведомлений.

430 свежих targeted JVM tests/10 suites GREEN,94 новых. Signed loopback HTTP
проверяет actual panel client; Telegram multipart/JSON проходит настоящий SDK
на локальный HTTP stand, включая потерю ответа после получения файла. VK SDK
заменён test double. Все4 bot modules main/test compiled JDK17/repo wrapper;
это не live provider, PostgreSQL/Rabbit или full application restart acceptance.
RED первой версии теста сохранён: subclass Mockito не подменяет final media
execute; fixture заменён actual SDK/HTTP, assertions не ослаблялись.

Source/test-сессия не подключалась к рабочим DB/Redis/Rabbit/провайдерам другой
машины. Local Docker endpoint недоступен; engine не запускался. R293 image
ff8c8f29d64d содержит R292 и этих изменений не содержит; build R293 не является
активацией. Нужна новая сборка bot jars/image и согласованный выпуск по R291/
R292 runbooks; новых schema migrations в R294 нет. Deployment/commit/push=false.
Инструкция: docs/runbooks/legacy-support-chat-delivery.md.

Общий пункт1 остаётся в работе: inbound operator/group permissions и другие
legacy пути, cross-platform restore/replay/rebind и live/provider/rollback gate.
42 MAX +15 transport-principal Docker cases по-прежнему PENDING_DOCKER;
S6/production-e2e-smoke §7.1 MANUAL_PENDING. Общая задача🟡, S2B снятR190.


## Валидация и исправления

Найдены конкретные обходы R291: Telegram/VK legacy summary/relay/unblock/digest
использовали raw SDK и properties.channelId как внешний recipient. Telegram
отбрасывал negative support chat и мог fallback в ID записи панели. Подключены
к единому target gate без цикла MessagingService→provider→bot. Сохранена
семантика selected questionnaire; скорректированы3 существующих constructor
fixtures и добавлены3 meaningful suites (94 cases).

V1: core194 GREEN;23 Telegram test fixture errors при попытке subclass-mock final
SDK media execute. RED log/XML/source hashes сохранены, source root не менялся.
V2: actual Telegram SDK ходит в loopback HttpServer; assertions усилены для
multipart/JSON и definite400-only fallback;127 Telegram +108 VK GREEN.
V3: добавлена actual HTTP response-loss проверка; единый финальный run430/10
GREEN, failures/errors/skips0, full main/test compile всех4 bot modules.
При generic/5xx failure автоматическая пересылка документа запрещена; это
не ослабление теста и не обход проверки допуска. Final source/class/report hashes
сохранены отдельно в validation. Старые XML copied RED, финальные GREEN отдельно.

git diff --check и PowerShell5.1 parsing runbook/commit block GREEN. Layout
report exit0; размеры существующих крупных orchestration classes — review
signals, baseline не переписывался, несвязный structural split не выполнялся.
Docker probe local-only/npipe failed; не запускались engine/Testcontainers/
рабочие процессы/DB/Redis/Rabbit, миграции/provision/flags не менялись.

## Exact project scope (15 files)

- ai-context/changelog/2026-11-06_01-278_legacy_support_chat_r294.md
- ai-context/rules/03-multibusiness-s0-contract.md
- ai-context/tasks/task-details/01-278.md
- docs/BOT_RUNTIME_CONTRACT.md
- docs/runbooks/child-transport-isolation.md
- docs/runbooks/legacy-support-chat-delivery.md
- java-bot/bot-core/src/main/java/com/example/supportbot/service/SupportChatDeliveryService.java
- java-bot/bot-core/src/test/java/com/example/supportbot/service/SupportChatDeliveryTest.java
- java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java
- java-bot/bot-telegram/src/test/java/com/example/supportbot/telegram/TelegramOwnerClientEditTest.java
- java-bot/bot-telegram/src/test/java/com/example/supportbot/telegram/TelegramQuestionnaireSelectionTest.java
- java-bot/bot-telegram/src/test/java/com/example/supportbot/telegram/TelegramSupportChatDeliveryTest.java
- java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkSupportBot.java
- java-bot/bot-vk/src/test/java/com/example/supportbot/vk/VkQuestionnaireSelectionTest.java
- java-bot/bot-vk/src/test/java/com/example/supportbot/vk/VkSupportChatDeliveryTest.java

## Граница

После технического GREEN metadata обновлены и exact validated bytes перенесены
в основной checkout. Итог отдельно фиксирует sandbox_green/source_applied;
staged/committed/pushed/deployment=false. Нет разрешения staging/commit/push
из этого запроса; готовый guarded PowerShell block сохранён для пользователя.
R294 требует новую сборку bot jars/image; существующий R293 образ sourceR292
не менялся. Inbound operator/group и другие legacy paths, full Docker/live/
restore/rebind/rollback/S6 остаются. Task🟡; S2B не возвращён в обязательный план.
