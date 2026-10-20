# 01-278 — выбор вопросника и сохранение опроса VK (R146)

Дата: 2026-10-20, Europe/Moscow.

Промт пользователя: «продолжай».

Значимое действующее уточнение: «продолжай. в текущей сессии не предоставляй команды для ручной фиксации».
Команды не предлагаются; staging/commit/push агент не выполняет.
Рабочая копия/экземпляр находятся на этой машине; необходимый restart разрешён,
но в этом пакете не потребовался. Это не разрешение на rollout/flags/migration.

## Baseline и границы

HEAD/origin/main/actual remote: 58366bd2c77bc1f15bc6d2bb8d89e98ab4ad1865,
parent 4fda7f9ff8382140840992939f85d4da13c10070. Проверен R145 (16 файлов),
исходные main/index чистые. Detached sandbox:
.tmp/01-278-r146-vk-questionnaire-choice. Тот же apply_patch набор после GREEN
переносится с exact manifest, baseline blob guards и normalized UTF-8/LF SHA-256.

Docker Server 29.7.2 подтверждён для Testcontainers. Новых SQL migrations нет,
минимум V73. Рабочие DB/flags/контейнеры/rollback pointers не изменялись;
новое source поведение не выдаётся за runtime acceptance.

## Выполнено

- VK показывает BUSINESS выбор до первого вопроса. Использует существующие
  forStart/writer-version ACK, signed choices/select и selected loader R145,
  без локальных/global settings, channel overrides, mandatory business/problem
  либо переноса прошлых business answers. Сохранены flow/settings, произвольный
  порядок вопросов, scope/ID/SHA/catalog, bootstrap и ответы.
- Нативные text buttons с JSON payload, nonce и membership проверкой. Меню,
  страница и стартовый message ID записаны до отправки. До 100 бизнесов,
  по 8 на странице, 2 в ряду + навигация, максимум 5 рядов/10 кнопок.
  Names ограничены без разрыва surrogate pair; ID suffix различает усечённые
  labels. Есть текстовая расшифровка и ручной generation-bound код.
- Исправлено смешение понятий в новом пути: vk-bot.channel-id в существующем
  application-shared.yml — VK_OPERATOR_CHAT_ID, не canonical channels.id.
  Новые sessions идут в vk-questionnaire-v1 + canonical channel ID; внутренний
  delivery guard тоже использует canonical ID. Legacy namespace совместим
  отдельно, рабочие ключи не мигрируют и не удаляются.
- Callback/Long Poll используют общую идентичность peer/conversation_message_id.
  Список принятых answer events хранится с сессией: потеря delivery cache не
  продвигает опрос повторно. Сообщение до нового /start не становится ответом
  другого бизнеса. Старые/чужие кнопки не выбирают owner и не отвечают на вопрос.
- Long Poll обновляет ts только после успешной пачки. Повтор пропускает ранее
  законченные события; CAS exhaustion остаётся retryable, а не ложным ack.
  BUSINESS flow требует положительный stable conversation message ID.
- Перед первой публикацией повторно проверяются current choice и отсутствие
  другого active root. Последний ответ и fixed submission key сохранены до queue.
  Ошибка публикации либо сохранения terminal marker повторяет тот же command.
  Новый follow-up при восстановлении публикации не теряется; ждёт собственного
  canonical root, сверяет user/active ID и не выбирает чужой active ticket.
- Новый selected путь text-only; вложения явно отклоняются до storage/ответа.
  Raw operator-chat summary/follow-up и прежний rating prompt не отправляются;
  shared-channel active shortcut без scope закрыт. Legacy/GENERAL session после
  rebind требует /start. Новые markers не удаляются legacy first-response job.
- Текущая карта/очередь задачи обновлена; S0 описывает новый протокол, namespaces
  и оставшиеся границы. Общий статус 01-278 остаётся 🟡.

Структура text action/keyboard/payload сверена с
[официальной схемой VKCOM](https://github.com/VKCOM/vk-api-schema/blob/master/messages/objects.json).
Бюджет страниц — собственный консервативный invariant, проверенный сериализацией
реальных типов установленного VK SDK, не доказательство live provider acceptance.

## Проверки

593/593 GREEN, 74 свежих XML, failures/errors/skips=0.

Полный java-bot reactor: core 217/38 suites, Telegram 20/6, VK 68/3, MAX 105/18;
всего 410/65. Новые VK protocol/handler suites — 21/45 tests. Свежесть 8 compiled
classes сверена с final source, финальный reactor повторён после всех правок.

Backend 183/9: selected creation 36, ingestion 20, inbox 9, SINGLE resolver 1,
shared registry HTTP/HMAC 21, business/general registries по 2, GENERAL HTTP 74,
readiness 18. В двух isolated schemas применены все 73 migrations, не в рабочей БД.
Node GENERAL queue R117/realtime R134 и git diff --check GREEN.

Проверены реальные bot/Callback/Long Poll dispatch, JSON session store/CAS и
delivery guard с doubles вместо provider/API/Redis: restore, bootstrap/order,
меню 1/8/9/100, stale/foreign buttons, другое поколение, другой owner/channel,
CAS race/exhaustion, menu/question/confirmation/queue/marker/ack failures,
замороженный command, retry пачки и потеря dedup cache. Положительные legacy/
GENERAL сценарии сохранены. Два канала одного клиента с общим служебным чатом
не смешивают новые sessions; operator ID не становится canonical owner.

До GREEN исправлены новый Java 17 accessor, явное создание VK SDK action types
вместо Gson-десериализации interface и новые AssertJ/Mockito/token fixtures.
Существующие проверки не ослаблялись. Финальные логи — ignored target:
java-bot/target/r146-fleet-final-v3.log и spring-panel/target/r146-panel-final.log.
Generated CSS проверен по baseline Git blobs и не переносится.

## Осталось

Telegram, явная GENERAL option общего канала, scoped locations/presets,
scoped media/feedback, live Redis/VK/MAX/Long Poll/proxy, TTL/retention сессий и
dedup, legacy lease namespaces, rolling upgrades и несколько active бизнесов.
Локальная защита selected support-chat не заменяет общий destination/recipient
policy и атрибуцию всех дочерних/follow-up events. Другие шесть крупных блоков
сводки также открыты. Нет staging/commit/push/deploy, рабочих migrations/backfill/
flags, rebuild/restart/prune/cleanup и реальных provider sends.

## Точный manifest (7 файлов)

- `ai-context/changelog/2026-10-20_01-278_vk_questionnaire_selection_r146.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkSupportBot.java`
- `java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkQuestionnaireChoiceSupport.java`
- `java-bot/bot-vk/src/test/java/com/example/supportbot/vk/VkQuestionnaireChoiceSupportTest.java`
- `java-bot/bot-vk/src/test/java/com/example/supportbot/vk/VkQuestionnaireSelectionTest.java`
