# 01-278 / R290 — автор клиентского редактирования и ручная приёмка

Промт пользователя:

> ок, перенеси это в документацию приёмки и приступай к выпонению следующего пункта

Дата:2026-11-05, Europe/Moscow +03.

## Source R290 — 2026-11-05: редактирование сообщения с точным автором

Пункт1 доставки/оценок программно завершён. Оставшаяся ручная fleet restart
приёмка перенесена в docs/runbooks/production-e2e-smoke.md, раздел7.1,
MANUAL_PENDING; прежние пользовательские live checks и R265–R267 evidence
сохранены, новые runtime факты не объявлены. Руководство R289 ссылается на чеклист.

Начат следующий блок изоляции. Прежний Telegram client-edit передавал только
channel/provider ID: единственная совпавшая запись другого клиента могла быть
изменена. Теперь private-chat handler передаёт platform/channel/actual author/
provider ID через TicketService и signed PanelTicketWriteClient в отдельный PUT
/internal/api/bot/channels/{channelId}/users/{userId}/messages/{providerId}/client-edit.
Body:platform,message. Весь путь сохраняет positive BIGINT без JS rounding.
Group/foreign-chat/missing author/wrong platform/invalid ID отклоняются; нет
JDBC или старого HTTP fallback. Старый HTTP client-edit без автора возвращает
410 Gone после прежней write authorization/idempotency boundary, без service DML.

Backend выбирает history только данного автора, проверяет единственный raw
canonical root и загруженный channel/platform/active. Owning UPDATE повторяет
exact child tuple, per-author history uniqueness, root public/business/general
snapshot и channel platform/active с FOR SHARE row locks. Другой клиент с тем
же provider ID не блокирует собственное редактирование и не меняется. При
изменении root/history/channel запись и UI handoff запрещены. Raw Unicode ticket
ID не trim; original_message сохраняется при следующем edit. UI exception
откатывает SQL update. Shared API/SQL child principals этим не разделены.

430 targeted JVM tests/14 suites GREEN,0 failures/errors/skips;
110 новых frozen tests +3 MVC cases. Baseline RED:110 tests/61 failures и49
positive controls. Три новых test sources,5 test classes и110 case names после
RED неизменны.81 новая owning-SQL проверка выполнена на собственном native
PostgreSQL16.15 с fresh nonce-owned database/schema и real transactions;
контролируемые drift происходят перед DML, не concurrent MVCC/rebind acceptance.
Ещё50 прежних BotRuntimeTicketWriteServiceTest без изменения assertions прошли
на той же owned БД. PostgresqlJdbcTestSupport сохраняет обычный Docker default;
native override требует exact loopback/nonstandard port/r290_edit/fake role
и случайный cluster nonce до создания schemas. Application datasource не читает.
HTTP loopback, backend MVC и реальный Telegram edit handler проверены отдельно;
repository/outbox/SDK seams замоканы. Wire/TicketService не используют fallback.
Модели и SQL schema/POM/session JSON не меняются. JVM17, Sass; CSS вне scope.

Baseline HEAD/origin/main/remote3ba33f464 (R289), parentae45b515d;9 previous changed
paths/blob content и4104 tracked files сверены. Первый RED reactor остановился
на core failures; второй RED-only run с test.failure.ignore собрал оба модуля:
его exit0 не означает GREEN, отчёты фиксируют все110 tests/61 failures. Обычный
GREEN bot run без ignore. Первый исправленный panel run имел0 failures/50
Docker-fixture errors; его XML/logs/class hashes сохранены. После точного native
test-helper opt-in повторены только эти50 checks; остальные205 panel и175 bot
проверены на том же неизменном product bytecode. Итог430/14 — совокупный proof,
не один Maven запуск. Evidence .tmp/r290-baseline-red.json/r290-java-green.json,
owned PostgreSQL logs, validation/result/final. Scope16, root apply отдельно
от staging/commit/push. Рабочий проект/Docker на другой машине/сессии не затронуты.

Для будущего выпуска нужны backend panel-web/ops-worker R290, затем Telegram
child; old backend не знает owner route (404), old bot после нового backend
получит410, без небезопасного fallback. Release отдельно, migrations/flags нет.
Выбранный Telegram v4 question handler по-прежнему явно запрещает редактирование
ответов; этот пакет не включает такую функцию. VK/MAX ingress edit здесь не
добавлен; backend/client контракт принимает три платформы. R28738 MAX PG/Rabbit
cases остаются PENDING_DOCKER. Остальные legacy/media/service-chat recipient/
child principals/policies/AI/S6 остаются, задача🟡; S2B снятR190.

Изменённые файлы:

- `ai-context/changelog/2026-11-05_01-278_owner_scoped_client_edit_r290.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `docs/BOT_RUNTIME_CONTRACT.md`
- `docs/runbooks/feedback-delivery-recovery.md`
- `docs/runbooks/production-e2e-smoke.md`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/PanelTicketWriteClient.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/TicketService.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/OwnerClientEditClientTest.java`
- `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java`
- `java-bot/bot-telegram/src/test/java/com/example/supportbot/telegram/TelegramOwnerClientEditTest.java`
- `spring-panel/src/main/java/com/example/panel/controller/BotRuntimeWriteApiController.java`
- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeTicketWriteService.java`
- `spring-panel/src/test/java/com/example/panel/controller/BotRuntimeWriteApiControllerWebMvcTest.java`
- `spring-panel/src/test/java/com/example/panel/service/BotRuntimeOwnerClientEditPostgresTest.java`
- `spring-panel/src/test/java/com/example/panel/support/PostgresqlJdbcTestSupport.java`
