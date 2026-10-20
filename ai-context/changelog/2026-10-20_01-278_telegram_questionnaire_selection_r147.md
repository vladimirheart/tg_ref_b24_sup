# 01-278 — выбор вопросника и сохраняемая сессия Telegram (R147)

Дата: 2026-10-20, Europe/Moscow.

Промт пользователя: «продолжай».

Действующее уточнение: «продолжай. в текущей сессии не предоставляй команды для ручной фиксации».
Команды не предлагаются; staging/commit/push агент не выполняет. Рабочая копия
и экземпляр находятся здесь, необходимый restart разрешён, но не потребовался.

## Baseline и границы

HEAD/origin/main/actual remote: 37dd8b95ce10591d42fead6fa20fbcf3ea99515a,
parent 58366bd2c77bc1f15bc6d2bb8d89e98ab4ad1865. Проверен R146 (7 файлов),
исходные main/index чистые. Detached sandbox:
.tmp/01-278-r147-telegram-questionnaire-choice. Тот же validated apply_patch
набор переносится после GREEN с exact manifest, baseline blob guards и
normalized UTF-8/LF SHA-256. Новых migrations нет, минимум V73.

## Выполнено

- Новый TelegramQuestionnaireHandler подключён к реальному SupportBot до
  private legacy edits/feedback/active/media. Проверяет active Telegram channel,
  user/chat tuple и blacklist; GENERAL-only/unbound возвращает прежнему flow.
  BUSINESS использует существующие forStart/writer-version ACK и selected loader,
  без channel/local/global overrides, mandatory business/problem и прошлых answers.
- Нативные reply buttons и ручной generation-bound код: catalog SHA/nonce/page
  и стартовый provider ID сохраняются до отправки. До 100 бизнесов, 8 на страницу,
  2 в ряду. Text labels сохраняют суффикс выбора при усечении, surrogate pairs
  не разрываются. Выбранный payload с исходным order и scope/template ID/SHA
  записывается до первого вопроса.
- JSON/CAS namespace telegram-questionnaire-v1 привязан к canonical channels.id,
  не к служебному chat ID; delivery guard тоже canonical-scoped. Ответы, history
  с provider IDs, visits и accepted-answer ledger переживают восстановление.
  Старые кнопки, повтор ответа и более раннее сообщение до /start не меняют
  owner и не заполняют следующий вопрос. Два канала с общим operator/client
  не смешивают новые sessions. Старый in-memory bootstrap после rebind не переносится.
- Сохранены select по номеру/label, forward routes, back с очисткой следующих
  ответов, optional skip и произвольный состав/порядок. Нет принудительного
  добавления бизнес-вопросов. Legacy/GENERAL-only остаются совместимы отдельно.
- Перед первой публикацией проверяются current выбор и отсутствие другого
  active root. Последний ответ/fixed submission message key сохраняются до queue;
  ticket ID marker — до confirmation. Ошибки queue/marker/confirmation/ack
  повторяют тот же frozen command, не свежие bindings или изменённый текст.
- Follow-up ожидает свой canonical root/user/active ID. Во время восстановления
  pending публикации новому сообщению явно предлагается повторная отправка;
  оно не становится последним ответом другого опроса. CAS exhaustion не ack.
- Новый путь text-only: media/unsupported payload и edits отклоняются до legacy
  storage/writer. Raw support-chat summary/relay/rating prompt не отправляются;
  selected commands не запускают unscoped ticket-list/reply. Новые terminal
  markers не находятся в legacy scheduler map.
- Обновлены текущая карта/очередь задачи и S0-контракт. Добавлен test-only
  mock-maker-subclass, аналогично другим bot modules; main packaging не меняется.

Поведение text reply buttons сверено с
[официальным Telegram API](https://core.telegram.org/bots/api#keyboardbutton).
Проверена сериализация типов установленного SDK; это не live provider acceptance.

## Проверки

658/658 GREEN, 76 свежих XML, failures/errors/skips=0.

Полный java-bot reactor: core 217/38 suites, Telegram 85/8, VK 68/3, MAX 105/18;
всего 475/67. Новые Telegram menu/handler suites — 18/47 tests. Проверены 8 свежих
compiled classes относительно final source. Targeted и full runs прошли;
существующие assertions не ослаблялись.

Backend 183/9: selected creation 36, ingestion 20, inbox 9, SINGLE resolver 1,
shared registry HTTP/HMAC 21, business/general registries по 2, GENERAL HTTP 74,
readiness 18. Все 73 migrations — в двух isolated schemas. Node GENERAL queue
R117/realtime R134 и git diff --check GREEN. Docker Server 29.7.2 подтверждён
для Testcontainers, не как проверка рабочего экземпляра.

Реальные SupportBot ingress/handler, JSON/CAS и delivery guard с doubles вместо
SDK/API/Redis: restore, menu 1/8/9/100, страницы, bootstrap/order, malformed/stale
выбор, route/back/skip, foreign owner/history/answer keys, CAS race/exhaustion,
menu/question/queue/marker/confirmation/ack failures, frozen command, cache loss,
другой active root, два canonical канала и positive GENERAL-only.
Логи: java-bot/target/r147-fleet-final.log и spring-panel/target/r147-panel-final.log.
Generated CSS проверен по baseline Git blobs, не переносится.

## Осталось и operational boundary

GENERAL/USER_SELECTION option общего канала требует собственного binding/writer;
общая GENERAL-only привязка не расширялась. Scoped locations/presets, media/
edit/feedback, несколько active бизнесов, TTL/retention/4096-event budget,
non-Rabbit/API-off и rolling-upgrade audit остаются открытыми. Legacy/GENERAL
Telegram sessions по-прежнему in-memory. Новая session — transport state, не DB.
При direct coordination сам store также in-memory: durability process restart
требует Redis mode; тестовое восстановление объектов не является live Redis restart.

Unit replay не доказывает Telegram Long Poll offset/re-delivery после exception.
Новый текст во время восстановления publication получает явную просьбу повторить
его, но durable follow-up queue не реализована. Нужны live Redis/Telegram/VK/MAX/
proxy и provider recovery acceptance. Локальная защита нового support-chat пути
не закрывает общий recipient/child attribution policy. Прочие шесть крупных
блоков 01-278 также открыты; задача 🟡.

Нет staging/commit/push/deploy, рабочих migrations/backfill/flags,
rebuild/restart/prune/cleanup, реальных provider sends и изменения rollback.

## Точный manifest (9 файлов)

- `ai-context/changelog/2026-10-20_01-278_telegram_questionnaire_selection_r147.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java`
- `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/TelegramQuestionnaireHandler.java`
- `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/TelegramQuestionnaireMenu.java`
- `java-bot/bot-telegram/src/test/java/com/example/supportbot/telegram/TelegramQuestionnaireMenuTest.java`
- `java-bot/bot-telegram/src/test/java/com/example/supportbot/telegram/TelegramQuestionnaireSelectionTest.java`
- `java-bot/bot-telegram/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
