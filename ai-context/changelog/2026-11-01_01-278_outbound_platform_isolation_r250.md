# 01-278 R250 — точная платформа и локальный канал перед отправкой

Дата:2026-11-01, Europe/Moscow. GREEN14:24+03, acceptance/artifact14:25+03.
Промт пользователя:

> давай дальше

Ресурсное ограничение прежнее: пользователь не может дополнительно освободить
ОЗУ/остановить WSL/VS Code. Git-разрешение до конца31 октября истекло;
staging/commit/push/rollout не выполнялись. Fresh clean HEAD/origin/main/remote
b9700927f9e4d37d3de6c8a8da63d30cb8a54ed2, пользовательский R249,
parent83eb8e0ae/scope12 проверены. Один повторный remote read потерял сеть;
retry с теми же guards подтвердил тот же baseline, guard не снимался.

## Scope8 и результат

- java-bot/bot-core/src/main/java/com/example/supportbot/service/MessagingService.java;
- java-bot/bot-core/src/main/java/com/example/supportbot/service/OutboundFeedbackPromptDispatchService.java;
- java-bot/bot-core/src/test/java/com/example/supportbot/service/MessagingPlatformIsolationTest.java;
- java-bot/bot-core/src/test/java/com/example/supportbot/service/FeedbackPromptChannelRecheckTest.java;
- docs/BOT_RUNTIME_CONTRACT.md;
- ai-context/tasks/task-details/01-278.md;
- ai-context/rules/03-multibusiness-s0-contract.md;
- этот новый append-only changelog.

MessagingService больше не выбирает Telegram при missing/unknown platform либо
отсутствующем provider. DM/support-chat требуют resolved positive channel ID,
active channel и explicit platform/exact registered messenger. Case-insensitive
lookup сохранён; duplicate normalized providers fail при construction вместо
выбора credentials list order. Provider false/exception не вызывает fallback.
Feedback dispatcher сверяет ID/active/platform с event до и после authorization
I/O; changed local Channel не отправляется, ledger failed/retry contract прежний.
R249 API/DTO/signature/constructors/schema/resources/flags не менялись.

92 новых tests (47 router/DM/support-chat и45 dispatcher+router early/late channel,
Telegram/VK/MAX,BIGINT>2^53), API/provider doubles. RED unchanged product92/52
assertion failures/40 controls/0 errors/skips. Source/bytecode/case sets frozen.
GREEN150/5 suites:92 новых+58 прежних R249,0 failures/errors/skips; old tests/
fixtures/assertions не менялись. Не backend498/full1246/fleet/provider rerun.
Wrapper/reactor parent+bot-core137 main/5 tests,205 classes/2 changed/203 exact.
Helper512MiB/1.5CPU, heap160/meta224/code32/stack512k,sequential, без test DB.
Classes archive205 entries, без resources, независимо распакован/сверен:
SHA25654F374DCE47B6CDD31F6084CDAC61F322EE894B8C304844CD1FD957F9B6382D3.
Executable packaging/image/rebuild/restart не выполнялись; helper удалён после
сохранения evidence. Detached sandbox/replay/exact blob/hash/scope/diff guards
до source apply, без staging/commit/push/deploy.

Evidence .tmp/01-278-r250-evidence/: RED/GREEN XML/frozen/compiled inputs/class/
resource proofs, tested artifact/runtime/sandbox/replay/apply results. Working18
containers/IDs/images/start/restarts/ENV/mounts/HostConfig и config прежние,
mutable backup heartbeat отдельно. DB84/settings/accounts/grants/root/pending/
ratings/outbox fingerprints прежние,126 published/actionable0/active pending0;
web+worker R246 healthy/restart0/web readinessUP. Миграции/backfill/flags/working
queues/provider writes не выполнялись, backup/rollback R247 сохранены.
WSL/VS Code/working services ради памяти не останавливались.

Это cross-platform routing/local Channel protection, не authenticated per-child
channel/credential identity и не DB/API→provider atomicity/in-flight revoke/
expiry/последующая мутация. Права external service-chat recipients, provider
retry/exactly-once, same-created_at pending reuse/uniqueness/phantoms/lifecycle/
child-rating/media/AI/другие S3–S6/operational gates открыты. R248–R250 source
не развёрнуты; новый panel-web API R249 нужен до будущего bot rollout.
S2B снятR190, задача🟡.
