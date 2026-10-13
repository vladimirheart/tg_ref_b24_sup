# 01-278 / S2A / R68 — conditional outgoing history INSERT

Время: 2026-10-13 16:53:56 MSK.
Baseline: `68ed1b9304d5fa0e7ea855a9b6c687f1f3b85f25`.

Промт пользователя: «дальше».

Пользовательский R67 подтверждён: HEAD=origin/main, parent
`11a21b8ef75faf4f79f21f483f16be70f440675a`, message/9 changed files проверены;
checkout чистый. Удалённое описание R52 не восстанавливается.

Изменённые файлы (4 project files):

- `spring-panel/src/main/java/com/example/panel/service/DialogReplyTargetService.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogOutgoingConditionalInsertPostgresqlTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-13_165356_01-278_s2a_r68_conditional_outgoing_insert.md`

Фактический scope:

- Text/media outgoing INSERT преобразован в INSERT...SELECT одного typed
  VALUES row. Exact canonical ticket/user/channel EXISTS и count(raw ticket)=1
  проверяются внутри statement. Preliminary R64 proof остаётся, но stale
  positive proof не позволяет писать child после visible parent change.
- Java argument count/order/values и все media prepared setters сохранены:
  9 text и 10 media parameters. PostgreSQL casts соответствуют V1 schema
  (bigint/text/timestamptz), включая nullable provider/reply IDs. SQL field order,
  raw ID/message/caption, timestamp и sender normalization сохраняются.
- Writer требует affected row count=1. Zero INSERT вызывает прежний generic
  IllegalArgumentException wording; media останавливается до generated-key
  lookup/metadata. Successful key extraction/upsert остаются прежними.
  Это identity integrity gate, не business permission/strict filtering.

Detached validation:

- Baseline нового PG suite: 43 tests / 19 failures / 0 errors/skipped.
  Все 10 direct и 9 late reply-flow cases RED, 24 valid GREEN. Прежний INSERT
  писал history/metadata после того, как passed owner proof устаревал.
- Final Maven: 806/806 GREEN в 29 classes, 0 failures/errors/skipped.
  Включены все 763 regressions R67 и 43 новых cases. Fresh XML totals после
  завершения процесса прочитаны отдельно; все reports текущего run.
  Полные 643/446 main/test sources компилируются.
- 10 direct cases: committed duplicate/missing/user/channel/both parent change
  после real proof × text/media. Требуются прежний exception/message,
  zero history/metadata/activity и отсутствие metadata/storage calls.
- 9 orchestration cases: duplicate/missing/both change после второго real proof
  в writer × text/media/web-form. Conditional write не продолжается в activity/
  responsibility. Provider attempt/ledger могут уже быть выполнены; generic
  writer exception propagates, новый error-result mapping/rollback не добавлен.
- 24 positive cases: exact padded/slash/reserved/Unicode/percent-looking/
  null-literal IDs × text/media × nullable/non-null IDs. Actual JDBC проверяет
  tuple/fields, returned timestamp, text whitespace и ai_agent normalization.
  Real generated key media INSERT связывает actual metadata с history;
  ticket/channel/name/MIME/size и прежние upsert arguments проверены.
- Tests используют isolated Testcontainers PostgreSQL и настоящие history/
  metadata services/JDBC. Transport/ledger/responsibility/storage provider
  mocked; binary object I/O не выполняется. Interleavings committed sequentially
  до SQL, не multi-thread stress/serializable/locking proof, live provider/
  browser/production/S3 или complete business-scope acceptance.
- Source invariant: TargetService равен baseline после обратной замены двух
  SQL spans/count guards. Java bindings, prepared setters, timestamp/generated
  key/metadata code неизменны. ReplyService, metadata service и existing fixture
  полностью неизменны. Assertions не ослаблены.
- Десять unchanged JS files R27/R29/R33/R48/R50/R52/R53/R54/R55/R56 GREEN.
  Generated Sass CSS равен baseline после EOL normalization, не patch.

Sandbox GREEN и source apply разделены. Fresh HEAD/origin/status/blob guards,
exact four-file scope, normalized sandbox/root parity и diff check проверяются
отдельно. Edit/delete R66/R67, API/DTO/UI/cache, permissions, flags, schema,
receipts, object storage и attachment I/O не переписывались.
Working DB, migration/backfill, deploy, staging, commit/push не выполнялись.

Граница: statement-snapshot eligibility не замораживает owner после INSERT и
не делает provider/history/metadata/activity/responsibility атомарными. Успешный
provider side effect при позднем writer deny не откатывается. Locking/outbox/
reconciliation, public/composite linkage, storage namespaces и business-scope
acceptance — отдельные scopes. S2B/R12 PROPOSED_NOT_APPROVED; 01-278 остаётся 🟡.
