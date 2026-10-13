# 01-278 / S2A / R66 — conditional single-row operator edit/delete

Время: 2026-10-13 15:23:43 MSK.
Baseline: `0b6e34917c238080fed8c960cfb7f4dc98572368`.

Промт пользователя: «дальше».

Пользовательский R65 подтверждён: HEAD=origin/main, parent
`ac51d758fe68d10cc654df7066dd2d19d02c3c0b`, message/6 changed files проверены;
checkout чистый. Удалённое описание R52 не восстанавливается.

Изменённые файлы (5 project files):

- `spring-panel/src/main/java/com/example/panel/service/DialogReplyTargetService.java`
- `spring-panel/src/test/java/com/example/panel/service/BotRuntimeTicketWriteServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogOperatorConditionalMutationPostgresqlTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-13_152343_01-278_s2a_r66_conditional_operator_mutation.md`

Фактический scope:

- Только два identical SQL additions в markOperatorMessageEdited/Deleted:
  raw ticket count=1 и operator history count=1 по exact ticket/user/channel/
  provider-message tuple. Existing canonical ticket EXISTS и preliminary
  legacy guard сохранены. UPDATE не выбирает произвольную строку при ambiguity
  и не доверяет прежнему положительному guard вместо statement-snapshot check.
- SET, JDBC parameter count/order/values, COALESCE original_message, edited_at/
  deleted_at, return count и valid repeated-edit/delete quirks не изменены.
  Raw ticket ID не trim-ится/не декодируется. Это legacy integrity gate,
  не business grant/strict filtering и не изменение schema.
- BotRuntime production и ReplyService R64/R65 неизменны. В existing runtime
  suite добавлен один actual duplicate-history deny test; старые fixtures/
  assertions не ослаблены. Новый отдельный PG suite покрывает direct writer
  и controlled post-guard/post-preflight changes.

Detached validation:

- Baseline: 90 tests в 2 classes / 11 failures / 0 errors/skipped:
  46 новых PG cases — 10 failures; BotRuntime 44 cases — 1 new failure,
  existing 43 GREEN. Writer менял две operator rows либо принимал поздний
  duplicate ticket после guard; runtime объявлял updated=true.
- Final Maven: 704/704 GREEN в 27 classes, 0 failures/errors/skipped.
  Все 657 regressions R65 и 47 новых cases включены. Fresh XML totals после
  завершения процесса прочитаны отдельно; все reports текущего run.
  Полные 643/444 main/test sources компилируются.
- 18 direct PG cases: valid/duplicate history/absent message/wrong user/wrong
  channel/non-operator/duplicate ticket/missing ticket/null provider ID ×
  edit/delete. Deny сохраняет полный snapshot history без metadata calls.
- 4 controlled interleavings вводят committed duplicate history или ticket
  после real legacy guard, до UPDATE statement. Оба writer требуют zero rows
  и неизменный snapshot относительно состояния после controlled insert.
- 12 exact-ID cases: padded/slash/reserved/Unicode/percent-looking/null-literal
  raw IDs × edit/delete. Повторная valid mutation остаётся допустимой;
  initial original text, attachment/message_type/reply_to и foreign/non-operator/
  trimmed-sibling rows сохраняются. Нет guessed locator или identity normalisation.
- 12 post-preflight cases: duplicate history/ticket/channel change × edit/delete
  × mocked provider/web-form hook. Existing R65 preflight реально проходит;
  controlled change происходит после него, а mark writer не меняет history
  и не вызывает responsibility. Provider attempt/ledger могут уже состояться:
  test не заявляет rollback внешнего side effect.
- Новый actual BotRuntime case возвращает updated=false, exists=true и не пишет
  ticket_active/responsibles/participants, ui_event_outbox, provider_delivery_ledger.
  Tests используют isolated Testcontainers PostgreSQL и real JDBC/services,
  не working DB. Orchestration provider/ledger/responsibility controlled mocks.
- Это sequential committed interleaving до statement snapshot, не multi-thread
  stress test, lock/serializable proof, live Telegram/browser/production/S3
  или complete business-scope acceptance.
- Десять unchanged JS files R27/R29/R33/R48/R50/R52/R53/R54/R55/R56 GREEN.
  Source invariants: TargetService равен baseline после удаления двух SQL
  additions; ReplyService/BotRuntime production полностью неизменны; old runtime
  test source равен baseline после удаления одного new method.
  Generated Sass CSS равен baseline после EOL normalization, не patch.

Sandbox GREEN и source apply разделены. Fresh HEAD/origin/status/blob guards,
exact five-file scope, normalized sandbox/root parity и diff check проверяются
отдельно. API routes/DTO/UI/cache, preflight R64/R65, permissions, business flags,
schema и attachment I/O неизменны. Working DB, migration/backfill, deploy,
staging, commit/push не выполнялись.

Граница: SQL checks ограничивают eligibility в snapshot самого UPDATE, но не
замораживают canonical owner/provider target и не делают provider mutation +
DB update атомарными. Concurrent change после snapshot, canonical transfer
с существующей другой history row, expected tuple freeze/outbox/reconciliation,
public/composite linkage и storage namespaces — отдельные scopes.
S2B/R12 PROPOSED_NOT_APPROVED; задача 01-278 остаётся 🟡.
