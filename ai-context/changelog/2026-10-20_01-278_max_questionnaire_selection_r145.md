# 01-278 — выбор вопросника в MAX до первого вопроса (R145)

Дата: 2026-10-20, Europe/Moscow.

Промт пользователя: «продолжай. в текущей сессии не предоставляй команды для ручной фиксации».

Ручные команды фиксации не предлагаются. Это не разрешение агенту выполнять
staging/commit/push; эти действия не выполняются.

## Baseline и границы

HEAD/origin/main/actual remote: 4fda7f9ff8382140840992939f85d4da13c10070,
parent 2ecfc44ff8643897b2362a6e8ce844bbd401ef5b. Проверен R144 (28 файлов);
main/index исходно чистые. Detached sandbox:
.tmp/01-278-r145-max-questionnaire-choice. Тот же apply_patch набор переносится
после GREEN с exact manifest, baseline blob guards и normalized UTF-8/LF SHA-256.

Docker Server 29.7.2 подтверждён для Testcontainers. Рабочая БД/flags/контейнеры
не менялись; разрешённый необходимый restart не потребовался. Новых migrations
нет; minimum schema V73. Source apply не является rollout/приёмкой.

## Выполнено

- В MAX подключён BUSINESS выбор до первого вопроса: нативные message buttons,
  сохранённые catalog SHA/nonce/start event key и user/channel/chat binding.
  Старое меню или чужой код не выбирает новый owner и не становится ответом
  на вопрос. /start меняет генерацию; /cancel удаляет сессию.
- Writer-gated choices/selection: forStart=true, три flags, HMAC всего query и
  обязательный X-Iguana-Questionnaire-Writer-Version: 1 на обоих ответах.
  Старый backend без ACK не считается совместимым. Read-only R143 совместим.
- Выбранный payload загружается без повторного sanitizer/order re-index,
  channel override и local/global settings. Нет обязательных business/problem
  вопросов. Legacy session после смены режима не продолжает старый global flow.
- JSON session store/CAS сохраняет pending menu, выбранный flow/scope/version,
  bootstrap и ответы. Последний ответ и ключ публикации переживают ошибку очереди.
  После принятия в очередь сохранён ticket ID marker; restart/ack failure/
  dedup-cache loss не запускают новый опрос. Follow-up ждёт именно этот root.
- Stale/unavailable выбор перед первой публикацией требует явного /start без
  recapture. После подготовки публикации retry использует frozen command.
- Для нового selected MAX пути подавлены raw creation/follow-up уведомления
  в непроверенный support chat. Это не общая recipient policy всех маршрутов.
- Обновлены текущая карта/очередь задачи и S0-контракт; задача остаётся 🟡.

Формат MAX message/inline_keyboard сверён с
[официальным контрактом](https://dev.max.ru/docs-api/use-cases/sending-messages/keyboard).
До 100 вариантов, по 4 в ряду; текст ограничен 128 UTF-16 units без разрыва
surrogate pair, бизнес виден до кода поколения меню.

## Проверки

527/527 GREEN, 72 свежих XML, errors/failures/skips=0.

Полный java-bot reactor: core 217/38 suites, Telegram 20/6, VK 2/1, MAX 105/18;
всего 344/63. Новые MAX handler/protocol suites: 26/10 tests; start bridge 8,
signed SDK 37. Main/test compilation всех provider modules входит в прогон.

Backend: 183/9 suites — selected creation 36, ingestion 20, inbox 9,
SINGLE resolver 1, shared registry/HTTP 21, business/general registry по 2,
GENERAL HTTP 74, readiness 18. Все 73 migrations применены в двух isolated schemas.
Проверены writer ACK/gates, HMAC/query tampering, stale selection и прежние пути.

Handler tests используют реальный обработчик, settings DTO, JSON session store/
CAS и delivery guard с doubles вместо внешней доставки; это не live Redis/MAX E2E.
Проверены restore, order/bootstrap, чужие/старые кнопки, CAS race, сбои доставки,
публикации и ack, dedup loss, corrupt owner/channel/chat/flow, active-root mismatch,
отмена и отсутствие raw support-chat send. Проверены 13 compiled classes и
свежесть XML. Node R117/R134 и git diff --check GREEN.

До GREEN исправлены bot Channel accessor и новые test fixtures (token default,
mapper без зависимости core от Spring Web). Handler assertions выявили реальный
order re-index при повторной нормализации; loader исправлен без ослабления тестов.
После финального writer ACK протокола прогоны повторены. Логи — в ignored target.

Никаких staging/commit/push/deploy, рабочих migrations/backfill/flags,
rebuild/restart/prune/cleanup или реальных provider sends.
Generated Sass CSS не переносится.

## Осталось

Telegram/VK, явная GENERAL option общего канала, scoped locations/presets,
live Redis/MAX/proxy и rolling-upgrade acceptance. TTL/retention, одновременные
обращения разных бизнесов, общий destination policy и non-Rabbit/disabled-API
modes требуют отдельного аудита. Остальные крупные блоки 01-278 остаются открытыми.
Flags/рабочие миграции/rollout этим пакетом не разрешены.

## Точный manifest (16 файлов)

- `ai-context/changelog/2026-10-20_01-278_max_questionnaire_selection_r145.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/PanelRuntimeConfigClient.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/RuntimeConfigService.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/settings/BotSettingsService.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/RuntimeQuestionnaireStartTest.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/SharedQuestionnaireClientTest.java`
- `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxApiClient.java`
- `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxConversationSession.java`
- `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxQuestionnaireChoiceSupport.java`
- `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxWebhookController.java`
- `java-bot/bot-max/src/test/java/com/example/supportbot/max/MaxQuestionnaireChoiceSupportTest.java`
- `java-bot/bot-max/src/test/java/com/example/supportbot/max/MaxQuestionnaireSelectionTest.java`
- `spring-panel/src/main/java/com/example/panel/controller/SharedQuestionnaireRuntimeController.java`
- `spring-panel/src/test/java/com/example/panel/service/SharedQuestionnaireRegistryPostgresTest.java`
