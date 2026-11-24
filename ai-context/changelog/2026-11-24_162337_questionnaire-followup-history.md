# 01-308 — История сообщений после опросника

24.11.2026, 16:23 Europe/Moscow.

Промт пользователя:

> найден не слабый баг: после прохождения клиентом опросника, на описание проблемы, если оператор не ответил, то все сообщения клиентом перетираются и остаётся только последнее сообщение. например последний сегодняшний диалог

## Фактические изменения

- Исследован последний диалог: PostgreSQL сохраняет все сообщения, поэтому
  проблема была только в UI-фильтре истории, а не в RabbitMQ или записи в БД.
- `DialogConversationReadService` выдаёт доказанный флаг
  `questionnaireAnswer`: он относится только к client-записи без public ID,
  чьи `ticket_id`, owner user и channel совпадают с обращением, имеющим public
  ID. Операторские, follow-up и чужие строки не получают этот флаг.
- `dialogs-details-history-runtime.js` сворачивает только начальный блок
  доказанных ответов опросника. Сообщения, присланные уже после создания
  обращения, всегда остаются в таймлайне до первого ответа оператора и после
  него.
- Добавлены Java и Node регрессии, обновлены cache-version assertions
  изменённого browser runtime.

## Проверки и границы

- Targeted Maven: `DialogConversationReadServiceTest` — 20 tests GREEN,
  failures/errors/skips=0; disposable PostgreSQL Testcontainers.
- Node: r308 regression, r52/r55/r56 API/cache checks — GREEN. Синтаксис JS и
  `git diff --check` — GREEN.
- Миграций, backfill, изменений рабочих диалогов, staging, commit, push и
  deploy нет. Для применения нужен новый image и пересоздание `panel-web`.
