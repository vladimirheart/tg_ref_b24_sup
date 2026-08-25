# PostgreSQL Cutover Rehearsal

Актуально на `2026-08-25`.

## 1. Назначение

Этот документ нужен для rehearsal перед production cutover Iguana, если запуск идёт:

- на новом canonical contour `PostgreSQL + Redis + RabbitMQ + S3-compatible storage`;
- с импортом или сверкой legacy данных;
- с требованием заранее проверить rollback, а не придумывать его в день запуска.

Документ не заменяет общий [production-launch-checklist.md](./production-launch-checklist.md), а детализирует именно:

- rehearsal миграции;
- data reconciliation;
- контрольные точки rollback;
- фиксацию фактического времени и результата каждого шага.

## 2. Когда rehearsal обязателен

Rehearsal обязателен, если выполняется хотя бы одно условие:

- есть legacy SQLite-данные, которые должны попасть в новый production contour;
- production окружение поднимается не как пустой clean start;
- команда не проходила end-to-end сценарий cutover на production-like контуре;
- rollback зависит от backup/restore, но этот путь ещё не проверялся руками;
- планируется переключение webhook ingress или live long-poll ownership в том же окне.

Если запуск действительно чистый и без переноса исторических данных, rehearsal всё равно рекомендуется, но может быть сокращён до dry-run deploy + smoke без data backfill.

## 3. Артефакты, которые должны быть готовы до rehearsal

- зафиксированный release commit SHA;
- production-like env-файл или секретный набор переменных;
- отдельный rehearsal contour для `PostgreSQL`, `Redis`, `RabbitMQ`, object storage;
- копия production-like данных или согласованный sanitized snapshot;
- список каналов, входящих в launch scope;
- тестовые аккаунты операторов и тестовые внешние аккаунты для `Telegram`/`VK`/`MAX`;
- место, куда команда складывает evidence:
  - timestamps;
  - SQL-сверку;
  - ссылки на логи;
  - скриншоты UI;
  - итоговое go/no-go решение.

## 4. Роли на rehearsal

- `Release owner`: ведёт тайминг, фиксирует решение go/no-go.
- `Backend owner`: отвечает за `spring-panel`, migrations, readiness, internal API.
- `Integration owner`: отвечает за `java-bot`, ingress, outbound delivery, provider credentials.
- `Infra owner`: отвечает за `PostgreSQL`, `Redis`, `RabbitMQ`, object storage, reverse proxy, backups.
- `Support lead`: подтверждает operator UX и прикладной smoke в panel.

Если один человек совмещает несколько ролей, это нужно явно отметить до старта rehearsal.

## 5. Rehearsal scope

Минимальный scope rehearsal:

1. развернуть release-кандидат на production-like infra;
2. выполнить миграции и, если требуется, import/backfill;
3. проверить runtime gates:
   - `Settings -> Production readiness`;
   - `GET /api/bots/{channelId}/runtime-contract`;
   - `/api/analytics/integration-transport`;
   - `/api/incidents`;
4. пройти E2E smoke по основному пользовательскому и операторскому пути;
5. проверить rollback path хотя бы до restore rehearsal-снимка;
6. зафиксировать фактическое время на каждый шаг.

## 6. Шаблон тайминга rehearsal

Рекомендуемая таблица фиксации:

```text
T-00  Freeze и старт окна
T+05  Проверен backup source и target
T+10  Развёрнут release-кандидат
T+20  Выполнены migrations / import
T+35  Пройдена data reconciliation
T+45  Пройдён E2E smoke
T+55  Пройдён rollback drill или подтверждён restore path
T+60  Зафиксировано решение rehearsal: PASS / CONDITIONAL / FAIL
```

Команда должна записывать не только плановое время, но и фактическое отклонение.

## 7. Подготовка данных

Перед rehearsal нужно зафиксировать:

- что является source of truth:
  - legacy SQLite snapshot;
  - текущая pre-prod PostgreSQL копия;
  - object storage snapshot;
- какие сущности обязаны совпасть после cutover;
- какие сущности допускают естественное расхождение:
  - временные runtime locks;
  - ephemeral queue counters;
  - last-seen/heartbeat значения.

Если берётся production-like копия с редактированием персональных данных, список выполненной sanitization тоже должен войти в evidence.

## 8. Rehearsal шаг за шагом

### 8.1. Freeze

- Зафиксировать release SHA.
- Остановить несвязанные schema/runtime изменения.
- Зафиксировать окно rollback cutoff.
- Убедиться, что все owners на связи.

### 8.2. Backup и снимки

- Снять backup source PostgreSQL или legacy SQLite snapshot.
- Снять snapshot object storage или экспорт списка attachment keys.
- Снять конфигурационный snapshot production-like secrets без раскрытия секретных значений в общем чате.
- Проверить, что restore-инструкция существует и исполнима теми же людьми, кто дежурит на cutover.

### 8.3. Развёртывание release-кандидата

- Развернуть `spring-panel` на rehearsal contour.
- Развернуть `java-bot` и bot child runtimes с production-like конфигурацией.
- Проверить, что:
  - `APP_DB_MODE=postgresql` у panel;
  - `APP_DB_MODE=worker` у bot child;
  - `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq`;
  - `APP_COORDINATION_MODE=redis`;
  - `APP_STORAGE_OBJECT_MODE=s3`.

### 8.4. Миграции и import

- Выполнить Flyway migrations.
- Если есть legacy import/backfill, пройти его на rehearsal-данных.
- Зафиксировать:
  - длительность migration phase;
  - длительность import phase;
  - количество предупреждений;
  - количество ошибок;
  - были ли ручные вмешательства.

Если шаг требует ручного SQL или перезапуска сервиса, это считается defect rehearsal-процесса и должно быть явно записано.

### 8.5. Runtime gate после старта

После deploy и migrations обязательно проверить:

- `Settings -> Production readiness` возвращает `ready`;
- `/api/bots/{channelId}/runtime-contract` не содержит прямых `SPRING_DATASOURCE_*` для production bot child;
- `/api/analytics/integration-transport` открывается и показывает ожидаемый health snapshot;
- `/api/incidents` доступен и не заполнен необъяснимыми active incidents;
- логи `spring-panel` и bot runtime читаются в UTF-8.

## 9. Data reconciliation

### 9.1. Что сверять обязательно

- `channels`;
- `tickets`;
- `messages`;
- `chat_history`;
- `chat_attachment_metadata`;
- `users`;
- `incidents`, если incident contour входит в launch scope.

### 9.2. Что считать допустимым расхождением

- runtime timestamps, появившиеся уже после старта rehearsal;
- heartbeat/lease-related данные;
- ephemeral queue backlog counters;
- временные system events, созданные уже в ходе smoke.

### 9.3. Базовые SQL-проверки в PostgreSQL

```sql
SELECT COUNT(*) AS channels_count FROM channels;
SELECT COUNT(*) AS tickets_count FROM tickets;
SELECT COUNT(*) AS messages_count FROM messages;
SELECT COUNT(*) AS chat_history_count FROM chat_history;
SELECT COUNT(*) AS attachment_metadata_count FROM chat_attachment_metadata;
SELECT COUNT(*) AS users_count FROM users;
SELECT COUNT(*) AS incidents_count FROM incidents;
```

### 9.4. Сверка последних данных по времени

```sql
SELECT MAX(created_at) AS latest_message_created_at FROM messages;
SELECT MAX(timestamp) AS latest_chat_history_at FROM chat_history;
SELECT MAX(created_at) AS latest_incident_created_at FROM incidents;
```

### 9.5. Сверка ticket/channel связности

```sql
SELECT COUNT(*) AS orphan_tickets
FROM tickets t
LEFT JOIN channels c ON c.id = t.channel_id
WHERE t.channel_id IS NOT NULL
  AND c.id IS NULL;

SELECT COUNT(*) AS orphan_messages
FROM messages m
LEFT JOIN channels c ON c.id = m.channel_id
WHERE m.channel_id IS NOT NULL
  AND c.id IS NULL;

SELECT COUNT(*) AS orphan_history_rows
FROM chat_history h
LEFT JOIN channels c ON c.id = h.channel_id
WHERE h.channel_id IS NOT NULL
  AND c.id IS NULL;
```

### 9.6. Сверка ticket presence между основными read-моделями

```sql
SELECT COUNT(*) AS tickets_missing_in_messages
FROM tickets t
LEFT JOIN messages m ON m.ticket_id = t.ticket_id
WHERE m.ticket_id IS NULL;

SELECT COUNT(*) AS history_missing_ticket
FROM chat_history h
LEFT JOIN tickets t ON t.ticket_id = h.ticket_id
WHERE h.ticket_id IS NOT NULL
  AND t.ticket_id IS NULL;
```

### 9.7. Attachment boundary сверка

```sql
SELECT COUNT(*) AS attachments_without_storage_key
FROM chat_attachment_metadata
WHERE storage_key IS NULL OR BTRIM(storage_key) = '';

SELECT COUNT(*) AS attachments_without_ticket
FROM chat_attachment_metadata
WHERE ticket_id IS NULL OR BTRIM(ticket_id) = '';
```

### 9.8. Что фиксировать по итогам сверки

- абсолютные counts по ключевым таблицам;
- число orphan/invalid rows;
- максимальное расхождение относительно source snapshot;
- список расхождений, которые признаны допустимыми;
- список расхождений, которые блокируют production cutover.

## 10. Acceptance criteria для rehearsal

Rehearsal считается успешным, если одновременно верно всё ниже:

- release-кандидат стартует без ручного hotfix в коде;
- `Production readiness = ready`;
- bot runtime contract соответствует worker/rabbitmq boundary;
- data reconciliation не показывает блокирующих расхождений;
- inbound -> ticket -> operator reply -> outbound путь проходит end-to-end;
- attachment path проходит без потери ссылок;
- rollback path понятен, исполним и укладывается в ожидаемое окно.

Если любой из пунктов не выполнен, rehearsal нельзя трактовать как формальность "в целом сойдёт".

## 11. Rollback checkpoints

Ниже контрольные точки, на которых команда должна сознательно решить, идём дальше или откатываемся.

### C0. До миграций

- backup source подтверждён;
- restore-путь подтверждён;
- release SHA зафиксирован.

Rollback здесь самый дешёвый и должен быть мгновенным.

### C1. После migrations, до import/live runtime

- schema применена;
- startup проходит;
- readiness не деградировал;
- критичные SQL checks не сломаны.

Если уже здесь есть schema/runtime проблема, дальше не идём.

### C2. После import/backfill, до bot start

- counts выглядят правдоподобно;
- orphan-проверки не показывают системного повреждения;
- attachment metadata не потеряна.

Если здесь есть defect, откат дешевле, чем после включения трафика.

### C3. После старта bot runtime, до live ingress

- bot child поднят;
- internal API авторизуется;
- queues и DLQ выглядят штатно;
- нет неожиданных incident signals.

### C4. После smoke на закрытом контуре

- inbound и outbound path подтверждены;
- operator workspace работает;
- incidents и transport surfaces читаемы;
- логи понятны.

### C5. После включения live traffic

- первая волна реального трафика проходит;
- нет всплеска 401/403/5xx;
- DLQ не растёт;
- support lead подтверждает рабочий UX.

Если после `C5` появляются блокирующие defects, rollback всё ещё допустим, но уже дороже по операционному следу. Это должно быть заранее проговорено с release owner.

## 12. Итоговый протокол rehearsal

После завершения rehearsal нужно зафиксировать:

- дата и время;
- release SHA;
- owners;
- объём данных;
- длительность migrations/import;
- результат reconciliation;
- результат smoke;
- результат rollback drill;
- список дефектов;
- итог:
  - `PASS`;
  - `PASS WITH CONDITIONS`;
  - `FAIL`.

## 13. Что считается дефектом процесса, а не "разовым шумом"

- ручной SQL, который не был заранее частью runbook;
- необходимость искать secrets в личных сообщениях;
- непонятно, какой backup последний;
- неясно, какой канал/бот реально входит в launch scope;
- нет человека, который умеет выполнить restore;
- smoke проходит только у автора изменений, но не у support lead;
- evidence не собран и решение go/no-go невозможно восстановить через день.
