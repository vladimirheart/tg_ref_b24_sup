# Production E2E Smoke

Актуально на `2026-08-25`.

## 1. Назначение

Этот документ фиксирует E2E smoke-пакет для Iguana после deploy или production cutover.

Он нужен, чтобы команда после запуска не ограничивалась формулой "страница открывается", а подтверждала весь прикладной путь:

- ingress от внешнего канала;
- создание и чтение обращения;
- операторские действия;
- outbound delivery клиенту;
- readiness, incidents и transport diagnostics;
- rollback checkpoints после включения реального трафика.

## 2. Когда запускать этот smoke

- после rehearsal cutover;
- после production deploy;
- после изменения transport boundary;
- после изменения internal bot API контракта;
- после ротации webhook/provider credentials;
- после инцидента, затронувшего ingress/outbound path.

## 3. Кто должен участвовать

- `Support lead` или оператор, который реально работает в panel;
- `Backend owner`;
- `Integration owner`;
- при production cutover также `Release owner`.

Smoke не должен считаться завершённым, если его прошёл только разработчик без operator-side проверки.

## 4. Стартовые условия

Перед smoke должны быть выполнены базовые gates:

- `Settings -> Production readiness = ready`;
- bot runtime для launch channels запущен;
- `GET /api/bots/{channelId}/runtime-contract` соответствует production boundary;
- `/api/analytics/integration-transport` доступен;
- `/api/incidents` доступен;
- нет необъяснимого всплеска ошибок в логах.

## 5. Evidence, которое нужно собрать

- timestamp начала smoke;
- channel id и платформа для каждого пройденного сценария;
- ticket id созданного обращения;
- ссылка или скриншот dialogs workspace;
- скриншот `Production readiness`;
- скриншот или snapshot transport diagnostics;
- результат по каждому шагу: `PASS` или `FAIL`;
- финальное решение: `launch confirmed` или `rollback / fix required`.

## 6. Минимальный smoke-пакет

### 6.1. Gate 1. Readiness и runtime contract

- Открыть `Settings -> Production readiness`.
- Подтвердить статус `ready`.
- Проверить хотя бы один production channel через `GET /api/bots/{channelId}/runtime-contract`.
- Убедиться, что:
  - `APP_DB_MODE=worker`;
  - `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq`;
  - нет `SPRING_DATASOURCE_URL`;
  - нет `DATABASE_URL`.

Если этот gate не проходит, дальше прикладной smoke выполнять бессмысленно.

### 6.2. Gate 2. Ingress

Для каждого канала из launch scope:

1. отправить тестовое сообщение извне;
2. убедиться, что новое обращение появилось в dialogs;
3. записать `ticket_id`;
4. убедиться, что тикет читается без 4xx/5xx и без пустого workspace.

Минимум один реальный канал должен быть проверен end-to-end. Если в релиз входят `Telegram`, `VK` и `MAX`, лучше пройти все три.

### 6.3. Gate 3. Operator workspace

По созданному обращению:

1. открыть карточку обращения;
2. проверить, что история сообщения отображается корректно;
3. назначить или подтвердить ответственного;
4. убедиться, что ticket не исчезает из списка и не ломает фильтры;
5. убедиться, что участники, статус и базовые quick actions работают штатно.

### 6.4. Gate 4. Reply path

1. Отправить ответ оператором.
2. Подтвердить, что клиент реально получил reply.
3. Проверить, что reply отразился в `chat_history`.
4. Убедиться, что transport analytics не показывает failed outbound event.

### 6.5. Gate 5. Attachment path

Если attachments входят в launch scope:

1. отправить вложение клиентом или оператором;
2. проверить отображение вложения в UI;
3. проверить, что объект читается по S3-compatible boundary;
4. убедиться, что нет broken reference или пустого storage key.

Если attachments не входят в текущий релизный scope, это должно быть явно записано, а не молча пропущено.

### 6.6. Gate 6. Incident и transport surfaces

1. Открыть `/api/analytics/integration-transport`.
2. Убедиться, что backlog и DLQ не растут аномально.
3. Открыть `/api/incidents`.
4. Убедиться, что нет unexplained active incident, связанного с только что пройденным smoke.

### 6.7. Gate 7. Logs

Проверить логи:

- `spring-panel`;
- `java-bot`;
- при необходимости reverse proxy / ingress.

Важно подтвердить:

- нет repeated 401/403 на internal bot API;
- нет repeated 5xx на transport path;
- нет mojibake в UTF-8 логах;
- нет циклических retry без прогресса.

## 7. Расширенный smoke-пакет

Если релиз влияет на операционный поток глубже обычного, дополнительно пройти:

- `take -> reply -> resolve -> reopen`;
- редактирование операторского сообщения;
- редактирование клиентского сообщения, если платформа это поддерживает;
- feedback/rating path;
- unblock/blacklist scenario;
- incident redelivery/replay surface;
- `MAX` webhook relay path;
- `VK`/`MAX` secret verification path;
- internal bot API write idempotency через повтор безопасной команды.

## 8. Минимальные SQL/API-проверки после smoke

### 8.1. SQL

```sql
SELECT COUNT(*) AS smoke_ticket_count
FROM tickets
WHERE ticket_id = :ticket_id;

SELECT COUNT(*) AS smoke_history_rows
FROM chat_history
WHERE ticket_id = :ticket_id;

SELECT COUNT(*) AS smoke_message_rows
FROM messages
WHERE ticket_id = :ticket_id;
```

### 8.2. UI/API

- `/api/dialogs`
- `/api/dialogs/{ticketId}`
- `/api/dialogs/{ticketId}/history`
- `/api/analytics/integration-transport`
- `/api/incidents`

API не обязательно вызывать вручную браузером на каждом релизе, но команда должна понимать, какие surfaces подтверждают тот же прикладной путь.

## 9. Pass/Fail критерии

### Smoke = PASS

- inbound сообщение дошло до panel;
- ticket открылся и читается;
- операторский reply ушёл клиенту;
- transport diagnostics не показывает unexplained failed backlog;
- readiness остаётся `ready`;
- нет блокирующих инцидентов и 5xx на ключевом пути.

### Smoke = FAIL

- ticket не создаётся или не открывается;
- reply уходит в никуда или не подтверждается клиентом;
- DLQ/failed backlog растёт уже на smoke-сообщении;
- readiness после запуска стал `degraded`;
- internal bot API сыплет 401/403/409 вне ожидаемого сценария;
- operator UI не даёт штатно обработать обращение.

## 10. Rollback checkpoints после включения live traffic

### R1. Первые 5 минут

Проверить:

- появляется ли новый трафик;
- создаются ли тикеты;
- нет ли мгновенного всплеска 5xx;
- не растёт ли DLQ.

Если здесь есть блокирующая деградация, откат ещё должен быть быстрым.

### R2. Первые 15 минут

Проверить:

- проходит ли хотя бы один реальный операторский reply;
- не возникли ли массовые 401/403 на internal bot API;
- нет ли repeated stuck outbound/inbound events;
- support lead подтверждает, что UI работоспособен.

### R3. Первые 30-60 минут

Проверить:

- нет ли накопления backlog;
- нет ли repeated incident creation по transport path;
- сохраняется ли `Production readiness = ready`;
- нет ли деградации object storage или webhook ingress.

Если `R3` не проходит, релиз уже нельзя считать стабилизированным даже при успешном стартовом smoke.

## 11. Рекомендуемый протокол фиксации

```text
Smoke start:
Release SHA:
Channels in scope:
Operator account:

Gate 1 Readiness:
Gate 2 Ingress:
Gate 3 Workspace:
Gate 4 Reply:
Gate 5 Attachments:
Gate 6 Incidents/Transport:
Gate 7 Logs:

Decision:
Follow-up defects:
Rollback required:
```

## 12. Чего нельзя делать

- считать smoke успешным без реального входящего сообщения;
- считать smoke успешным без реального reply клиенту;
- смотреть только на UI и игнорировать `/api/analytics/integration-transport`;
- игнорировать `degraded` readiness со словами "потом разберёмся";
- не сохранять `ticket_id`, по которому проверялся smoke;
- переводить релиз в accepted без evidence и без support-side подтверждения.
