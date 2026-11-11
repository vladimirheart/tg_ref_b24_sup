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
  - `APP_DB_MODE=postgresql`;
  - `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq`;
  - `SPRING_DATASOURCE_URL` начинается с `jdbc:postgresql:`;
  - заданы `SPRING_DATASOURCE_USERNAME` и `SPRING_DATASOURCE_PASSWORD`.

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

### 7.1. 01-278: ручная приёмка аварийного перезапуска доставки

Перенесено в приёмку 2026-11-05 по указанию пользователя, Source R290.
Программная часть пункта доставки/оценок завершена в R289; это не новый
development blocker. Операционный разбор: [feedback-delivery-recovery.md](feedback-delivery-recovery.md).

- [x] Доставка и сохранение оценки: пользователь подтвердил прежние живые
  проверки 5 ноября («тесты уже проводились ранее - всё было ок.»).
  Дата и revision самих прогонов не сообщены; повтор ради R290 не требуется.
- [x] Есть прежние изолированные доказательства R265 (kill delivery JVM на пяти
  границах с исходным broker redelivery), R266 (rating/rollback/duplicate) и
  R267 (cold start штатного launcher/children). Это исторические результаты.
- [x] Разбор одной зависшей попытки и восстановление по существующей квитанции
  реализованы R289;73 disposable PostgreSQL cases — прежний source proof.
- [ ] **MANUAL_PENDING:** аварийный перезапуск штатного bot application/launcher/
  всей группы ботов с сообщениями в обработке на машине рабочего проекта.

Оставшаяся проверка выполняется на выделенном тестовом канале/получателе
в отдельном runtime-окне. Сохранить исходные commit/image, IDs контейнеров,
child PID, платформу/канал/получателя, exact event ID и SHA256 payload,
состояния ledger/receipt/outbox/очереди, число отправок и строк rating.
Рабочий экземпляр находится на другой машине и в другой сессии; source-сессия
R290 его не перезапускала и текущий runtime state не измеряла.

Порядок приёмки:

1. Зафиксировать контролируемое событие в обработке, затем аварийно остановить
   соответствующее штатное приложение/launcher. После запуска подтвердить
   новые PID и возврат исходного unacked payload, без нового publish/event ID.
2. Для committed положительной квитанции проверить переход в delivered без
   нового SDK/provider call и без изменения receipt/outbox/получателя.
3. Для неизвестного результата без квитанции подтвердить сохранённую блокировку:
   нет повторной отправки, создания новой попытки или смены статуса на retryable.
4. Для уже delivered проверить прежние строки и timestamps, отсутствие второго
   сообщения. После ответа на оценку — одна rating и одно scoped UI событие,
   сохранение результата после reload/restart.
5. Проверить ready/unacked/DLQ и отсутствие утечки сообщения в другой канал или
   бизнес; сохранить итог PASS/FAIL, дату, revision/image и пути к evidence.
   Успешный source test или старый cold start не заменяют этот протокол.

До получения этого evidence пункт остаётся MANUAL_PENDING. Он учитывается
в общей S6/release/rollback приёмке; не возвращать доставку/оценки целиком
в список нереализованных программных блоков. Откат и первый production запуск
сохраняют собственные gates этого runbook и production-launch-checklist.

<a id="01-278-s6-acceptance"></a>

### 7.2. 01-278: конечный лист приёмки S6 — R321

Рабочий экземпляр R319 проверен на машине текущей runtime-сессии. Пометки
«другая машина» в source checkpoints относятся к их исходным сессиям. Этот лист
объединяет оставшиеся проверки; наличие source кода или старого пункта backlog
не требует нового refactor. Дальнейшие исправления привязаны к конкретному FAIL.
Задача остаётся 🟡 до общей приёмки пользователем.

Уже полученные proof сохраняются и не запускаются заново без изменения пути:

| Принятый этап | Evidence и границы |
| --- | --- |
| Доставка/оценка R289/R290 | Пользователь подтвердил прежние живые тесты; остался только in-flight launcher/fleet restart §7.1. |
| MAX/principals R301 | 90 contracts +42 MAX +15 principal =147 GREEN; не live callback. |
| Shared work/common system R305/R309 | Выпуск V89, права, аудит и доставка приняты; политика не переоткрывается. |
| Backup/schema/backend R315 | Отдельный restore V89→V91 и fingerprints157 таблиц, выпуск R311–R314; не рабочий DB restore/downgrade. |
| AI UI/Telegram R316 | 32 Edge checks, один подтверждённый SENT, signed AMQP replay до/после worker restart; не реальный callback/создание root ботом. |
| Concurrent replay R317 / backend R318 | 15 native PG claims для Telegram/VK/MAX повторно GREEN в342 tests/9 suites; committed63f1fb416/imagea7518faf8c41 установлен на web/worker, V91/flags/children сохранены. Не реальный provider callback. |
| Intake/исправление старта R319 | BUSINESS4/GENERAL выбор Telegram1, GENERAL MAX2/Telegram3 настроены. Реальный /start дошёл до polling и выявил403 token-only channel lookup. Клиент исправлен,690 bot tests/16 suites GREEN, bot-runner обновлён. Новый клиент с тремя действующими child окружениями проходит own-channel API; шесть unscoped/foreign запросов403. Пользователь подтвердил появление меню после исправления; полный provider сценарий ещё не принят. |

R320 дополнительно принял HUMAN BUSINESS ветку Telegram1: настоящий root с
замороженным template/catalog v4, scoped карточка и скачивание исходных154 bytes/
SHA256, exact replay двух подписанных wire без новых root/history/media/outbound.
После отдельного502 зеркала только канал1 переведён на официальный API/direct;
Redis questionnaire сохранился при нормальном restart child1. Отдельный DIRECT
panel reply ID10469 подтверждён получателем. Исходная попытка502 не повторялась.
Подготовлен QA reviewed exact AI trigger S6-AI-795118ad86c3; его входящий текст/
SENT/replay ещё ожидаются, правило пока активно. GENERAL/VK/MAX и общие gates
этим результатом не закрываются. Доказательства — Runtime R320 карточки01-278.

Открыты пять runtime gates. Для каждого сохранить PASS/FAIL, дату, фактические
revision/image/Compose/flags, платформу/канал, full ticket owner/public/business
identity, event ID и путь к evidence вне Git. Непроведённый gate — PENDING,
не PASS. Секреты, client payload и закрытые дампы в Git не помещать.

| Gate | Проверка и критерий завершения | Статус |
| --- | --- | --- |
| S6-UI | One/many/ALL_BUSINESSES/GENERAL: list/direct/search/export/count/media/SSE не раскрывают чужой scope; save/reload/revoke действуют в следующем запросе. Системный ROLE_ADMIN сам по себе не выдаёт business AI доступ. Ранее принятые проверки использовать как evidence; допроверить только непокрытые комбинации. | PENDING |
| S6-PROVIDER | Telegram/VK/MAX: реальный входящий callback → анкета → canonical BUSINESS/GENERAL root, текст/вложение → панель → реальный ответ клиенту. Сверить bytes/size/SHA256 скачанного вложения, owner/history, отсутствие дублей после точного replay. Для BUSINESS AI — разрешённое reviewed exact правило и один SENT; GENERAL/начальная анкета без incoming_history_id сохраняют handoff. | PENDING |
| S6-RESTORE | В выделенном контуре восстановить DB и нужные media bytes; проверить возобновление сохранённой анкеты/утрату Redis session/rebind, frozen template/catalog и прежний owner. Повтор исходного события не создаёт новые history/media/ответы; противоречивый контекст отклоняется. Изолированный schema restore R315 засчитывается в своей части; рабочую БД ради этого листа не перезаписывать. | PENDING |
| S6-RESTART | Выполнить только оставшийся in-flight restart штатного bot launcher/fleet по §7.1: прежний unacked wire, одна подтверждённая доставка/rating; UNKNOWN/SENDING без повторного provider call. Worker restart R316 и старые isolated JVM probes уже засчитаны в своих границах. | MANUAL_PENDING |
| S6-ROLLOUT | Сверить совместимость фактических backend/children/schema и rollout flags; подтвердить controlled enable/disable и возврат к сохранённому image/flags в выделенном окне. Сохранить до/после ready/health/queue/DLQ и rollback evidence. Наличие rollback tag само по себе не доказывает выполненный откат. | PENDING |

R317 установлен R318 вместе с отдельной критичностью категорий P1–P5:
342 Java/9 suites, Edge320/1440 и18 рабочих checks GREEN, health/readiness UP/DLQ0.
В самом R318 новые migration/child JAR/provisioning и включение flags не требовались.
R319 сохранил backend/V91 и включил ticket-write-requires-business,
shared-channel-resolution, general-channel-resolution, general-ticket-queue.
Foundation/ui-selector ON; strict-read/manual creation OFF. Добавлены только
QA бизнес/три templates/bindings, старые roots не перераспределены.
После реального FAIL исправлен и обновлён только bot-runner:
imagedaafdaadb69e, baseline7493c908e +непроиндексированный client fix.
Текущий Compose — releases/r319-s6-telegram-20261111/private/compose.channel-fix.json
под ../iguana-runtime/tg_ref_b24_sup. Rollback runner image29f36986a0db сохранён
tag rollback-r319-bot-runner-20261111; backend imagea7518faf8c41 — двумя tags
rollback-r319-panel-web/ops-worker-20261111. Фактические runner-only защитные
возвраты прежнего образа не доказывают полный S6-ROLLOUT и in-flight restart.

Для возврата intake в drained окне остановить children, через admin API выключить
новые shared-general1/channel1-business4/business4-template и GENERAL2/3 bindings,
вернуть MAX2 template tpl_mpnp8g3k1qcg, затем восстановить прежние backend flags/
Compose и запустить children. Использованные QA templates/tickets/audits/receipts
сохранить. Старый Compose без согласованного возврата bindings неприменим.
Полный flags rollback пока не разыгран. Все пять gates выше остаются открытыми.
R320 не менял Compose/images/flags/schema. Независимый возврат маршрута только
Telegram1 — PATCH исходных platform_config/delivery_settings из защищённого
private/direct-route-rollback.json и normal restart child1 после drain;
остальные каналы не менять. Отмена QA AI допуска — revoke одного owned rule и
блокировка AI control текущего root, с сохранением sources/history/receipts.
R321: пользователь вернул сохранённый mirror profile канала1. Конфликтующий
official base_url удалён через admin PATCH, профиль сохранён; процесс теперь
использует https://telegram.ftl-dev.ru как Bot API root без CONNECT. getMe/long polling
подтверждены, другие children/containers/images/flags/settings не менялись.
Зелёный panel probe профиля проверяет только TCP доступность. Исходный platform_config
сохранён отдельно в R321/private/channel-rollback.json; S6 gates этим стартом не закрыты.
Исходные release/rollback pointers сохранены. Этот лист
сам не запускает rollout/replay/send/restart и не даёт разрешения на чужих получателей.

Выбранный exact-rule auto-reply не использует LLM; разработка LLM/full-payload
review нужна только при отдельном расширении режима. Historical S2B/backfill
снят R190. TLS/iiko/RMS operational checklist ведётся отдельно и не становится
новым программным блоком 01-278.

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
