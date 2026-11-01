# 01-278 R248 — блокировки контекста feedback relay до broker confirm

Дата:2026-11-01, Europe/Moscow. GREEN12:56+03, проверка artifact12:57+03.
Промт пользователя:

> продолжай.

Значимый ресурсный контекст:

> давай дальше. по ресурсам рабочей машины ничего дополнительно изменить не могу

Commit/push до конца31 октября больше не разрешены; прежнее «разрешаю» было
использовано для runtime R247, не является новым разрешением Git/rollout.

## Фактическое изменение

Clean HEAD/origin/main/actual remote2ffd7c1b2226887bd1141dfb213655ed3fcf19b7,
parent9f163dfec, свежий пользовательский commit metadata R247, scope3 проверен.
Новый scope5:

- spring-panel/src/main/java/com/example/panel/service/integration/OutboundFeedbackPromptPublishOutboxService.java;
- spring-panel/src/test/java/com/example/panel/service/integration/OutboundFeedbackPromptPublishLockPostgresqlTest.java;
- ai-context/tasks/task-details/01-278.md;
- ai-context/rules/03-multibusiness-s0-contract.md;
- этот новый append-only changelog.

Каждый relay publish+markPublished теперь в отдельной JDBC REQUIRES_NEW/
READ_COMMITTED транзакции. Сначала pending FOR UPDATE, затем root FOR SHARE;
повторная проверка captured context/epoch/created_at/expiry после lock wait.
Locks сохраняются во время Rabbit send и confirm до commit/rollback. SQL
budget30s/confirm15s, без обещания общего wall-clock deadline. Claim/load/recovery
и failed/retry снаружи; NACK откатывает транзакцию, затем сохраняет failed/retry.
Конструктор/enqueue snapshot/transport DTO/API/resources/schema прежние,
миграций не добавлено, требуется существующий V84.

## Валидация

42 новых real PostgreSQL/V84 cases:3 scopes×2 exact raw IDs/BIGINT>2^53,
24 двухточечных NOWAIT55P03 probes pending/root во время send/confirm с реальными
update/delete и rollback после release,12 ACK/NACK controls release/status/retry,
6 expiry changes при ожидании pending-lock без publish. Unchanged product
RED42/30 assertion failures/12 controls/0 errors/skips. Source/bytecode/case sets
заморожены; старые assertions/fixtures не изменялись.

Один GREEN run1246/19 suites:42 новых+1204 прежних,0 failures/errors/skips.
Fresh XML/native resource proof/PG budget до readiness/exact cases/frozen hashes
проверены. Helper512MiB/1.5CPU, PG192MiB/1CPU, heap160/meta224/code32/stack512k,
sequential. Incremental compile1 main/22 test sources,1424 main classes:
3 changed в одной family/1421 unrelated exact. Не full Maven build/image.
Rabbit send/confirm — doubles, не live broker/provider delivery.

Tested archive .tmp/01-278-r248-evidence/tested-main-classes.tar,
SHA256FD05DAD0246C4D5C449DB7620C936369E364B2BD1050482F295C14738021203C,
1424 class entries без resources; независимое извлечение/проверка всех hashes.
Первый runtime operator не сохранял stdout read-db: исходный read-only JSON
восстановлен из сохранённого tool output, не заменён поздним snapshot. v2 исправил
pipeline; v3 корректно декодирует vendor JSON byte[] PowerShell5.1. Первый archive
comparator сравнивал абсолютные build paths с relative paths; v2 проверяет exact
keys/hashes и extracted archive. RED artifacts/diagnoses сохранены; исходные
логические/class guards и тестовые assertions не ослаблены.

Detached sandbox/replay/exact baseline/blob/hash/scope/diff guards до source
apply, без staging/commit/push/deploy. Evidence .tmp/01-278-r248-evidence/:
baseline/RED/GREEN/frozen/compiled inputs/test-proof/tested-artifact/runtime-proof/
sandbox/replay/before-apply/apply results. Тестовый helper удалён после сохранения
артефактов; рабочие сервисы ради ОЗУ не останавливались.

Read-only runtime control:18 containers/IDs/images/start/restarts/ENV/mounts/
HostConfig и Compose/env/shared inputs прежние, mutable backup heartbeat отдельно.
Web+worker остаются R246/9f163dfec, healthy/restart0/web readinessUP. DB84/V84
checksum -783018026/settings/accounts/grants/roots/pending/ratings/outbox прежние:
126 published/actionable0/active pending0. Рабочие migrations/backfill/flags/
queues/provider writes не выполнялись, rollback/backup R247 сохранены. Новые
restore/actual rollback не выполнялись; operational gates R247 этим не закрыты.

## Граница результата

Защищены существующие pending/root от concurrent update/delete до confirm.
DB→Rabbit не является атомарным commit: crash после send/ACK до DB commit может
дать повтор. Открыты broker-queued/provider delivery/recipient authorization,
естественное expiry после final check, same-created_at request-ID reuse до relay,
duplicate-root phantoms/DB uniqueness/lifecycle/owning child-rating attribution,
media/policies/AI/fleet/questionnaires/flags/S6/DLQ/TLS/RMS. Следующий delivery
gate — revalidation непосредственно перед bot/provider send. R248 не развёрнут;
стенд R246/R247, S2B снятR190, задача🟡.
