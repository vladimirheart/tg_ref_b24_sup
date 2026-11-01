# 01-278 R249 — авторизация получателя перед bot feedback send

Дата:2026-11-01, Europe/Moscow. GREEN13:53+03, artifact/runtime proof13:54+03.
Промт пользователя:

> делай

Непосредственный контекст: «Далее — проверка контекста и прав получателя
непосредственно перед доставкой ботом». Ограничение ресурсов прежнее:

> давай дальше. по ресурсам рабочей машины ничего дополнительно изменить не могу

Git-разрешение до конца31 октября истекло. «Делай» относится к объявленному
source packet, не новому commit/push/deploy. Clean HEAD/origin/main/actual remote
83eb8e0aee977baeff305e6582d4b9883696db2f, пользовательский R248,
parent2ffd7c1b2/scope5 проверены.

## Scope12

- java-bot/bot-core/src/main/java/com/example/supportbot/service/OutboundFeedbackPromptDispatchService.java;
- java-bot/bot-core/src/main/java/com/example/supportbot/service/PanelFeedbackPromptDeliveryClient.java;
- java-bot/bot-core/src/test/java/com/example/supportbot/service/OutboundFeedbackPromptDispatchServiceTest.java;
- java-bot/bot-core/src/test/java/com/example/supportbot/service/FeedbackPromptDeliveryAuthorizationTest.java;
- spring-panel/src/main/java/com/example/panel/service/integration/FeedbackPromptDeliveryAuthorizationService.java;
- spring-panel/src/main/java/com/example/panel/controller/FeedbackPromptDeliveryAuthorizationController.java;
- spring-panel/src/test/java/com/example/panel/service/integration/FeedbackPromptRecipientAuthorizationPostgresqlTest.java;
- spring-panel/src/test/java/com/example/panel/controller/FeedbackPromptDeliveryAuthorizationControllerTest.java;
- docs/BOT_RUNTIME_CONTRACT.md;
- ai-context/tasks/task-details/01-278.md;
- ai-context/rules/03-multibusiness-s0-contract.md;
- этот новый append-only changelog.

## Изменение и проверка

Новый signed internal read-only POST авторизует exact полученное событие и route
по private snapshot owning outbox и текущим V84 pending/root/epoch/created_at/
expiry/unique owner/channel/platform/scope bindings. Client-supplied snapshot не
принимается. Processing/published допускаются из-за consumer-before-marker race.
Missing/corrupt snapshot/schema/DB failure не дают allow, recapture/fallback нет.
BUSINESS/standalone GENERAL/shared GENERAL используют explicit active bindings
и существующие flags; legacy unresolved только foundationOFF без scoped bindings.
Ответ только eventId/boolean allowed, без контекста/prompt. API не использует
write idempotency cache и повторно проверяет отзыв при одинаковом key.

Bot проверяет active/platform и вызывает новый API после ledger/channel работы
непосредственно перед send. Подписан весь JSON body; ошибочный status/ID/type/
payload/deny/exception не вызывают provider. Ledger failed и прежний listener
retry/DLQ contract сохранены. Already-delivered duplicate API не вызывает.
Rabbit DTO/schema/migrations/feature defaults прежние, минимумV84.

RED unchanged dispatcher54/51 assertion failures/3 duplicate controls/0 errors/
skips; constructor bridge только в regression setup, не runtime bypass.
Frozen new bot source/bytecode/case names. GREEN556/9 suites двумя sequential
runs: bot58/3, panel498/6.371 новых:54 signed loopback bot+312 PostgreSQL/V84+
5 controller с реальной internal signature guard;185 прежних targeted cases.
Это не полный повтор1246 R248 и не live Rabbit/provider/fleet acceptance.
Canonical V84 поверх minimal owning schema; read-only fingerprints проверены.
Old bot fixture меняет только dependency setup; checked inverse восстанавливает
прежние bodies/assertions. Assertions не ослаблялись.

Bot wrapper/reactor parent+bot-core137 main/3 tests:203→205 classes,202 exact,
1 dispatcher changed/2 client added. Panel incremental2 main/8 tests:
1424→1428 classes, все1424 прежних exact. Helper512MiB/1.5CPU, PG192MiB/1CPU
до readiness; heap160/meta224/code32/stack512k, sequential. Первый helper prepare
и v2 остановились на CRLF Linux scripts из Windows checkout. v3 нормализовал
только copied mvnw/Maven executable в helper; repo wrapper не менялся, offline
repository wrapper/reactor сохранены. Failed evidence оставлены.

Оба classes archives независимо распакованы/сверены, без resources:
panel1428/SHA2560DF23F1C36C6BAB4249749CBE85F4847234479DA0042D7B9013D51A002FBF4B9;
bot205/SHA2564BC222F94C36008B730833F65E1B6C8A27898B728DB2200C6B3D5F50DEE729CC.
Не full fleet build, executable packaging/image не выполнялись.

Detached sandbox/replay/exact baseline/blob/hash/scope/diff guards до apply;
без staging/commit/push/deploy. Evidence .tmp/01-278-r249-evidence/: baseline,
RED/GREEN XML/frozen/inverse/resources/compiled inputs/test-proof/tested-artifacts/
runtime/sandbox/replay/apply. Helper удалён после сохранения evidence.
Рабочие18 containers/IDs/images/start/restarts/ENV/mounts/HostConfig и config
прежние, mutable backup heartbeat отдельно. DB84/settings/accounts/grants/
root/pending/ratings/outbox прежние,126 published/actionable0/active pending0,
web+worker R246/9f163dfec healthy/restart0/web readinessUP. Миграции/backfill/
flags/queues/provider writes не выполнялись; backup/rollback R247 сохранены.
WSL/VS Code/working services ради ОЗУ не останавливались.

SQL decision→API reply→provider TOCTOU, in-flight revoke/expiry, per-child channel
identity/provider retry/exactly-once, same-created_at ID reuse/DB uniqueness/
phantoms/lifecycle/child-rating/media/AI/другие S3–S6/operational gates R247 открыты.
Будущий rollout: новый panel-web API до новых bot artifacts; web404 для нового
бота fail-closed. R248/R249 ещё не развёрнуты; source work не является rollout
или новым разрешением на него. S2B снятR190, задача🟡.
