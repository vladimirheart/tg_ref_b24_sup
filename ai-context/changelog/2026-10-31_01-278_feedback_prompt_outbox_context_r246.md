# 01-278 R246 — сохранённый контекст prompt в backend outbox

Дата:2026-10-31, Europe/Moscow, текущая Windows/Docker-сессия.
Промт пользователя:

> давай дальше

Значимые действующие ограничения и разрешения:

> давай дальше. по ресурсам рабочей машины ничего дополнительно изменить не могу
> комит и пуш разрешаю до конца суток

Последнее «разрешаю» относилось к V84 и panel-web R244 (runtimeR245), не к worker.
Этот пакет не повторяет rollout/migration и не расширяет разрешение на worker.

## Реализация

Baseline clean HEAD/origin/main/actual remote538dfb0db1babb2947173be2c9d3687ebc73029b;
R245 parent2d55cd9ae/metadata scope3 проверены. Scope7: один main
OutboundFeedbackPromptPublishOutboxService, новый OutboundFeedbackPromptContextPostgresqlTest,
FeedbackPromptOutboxTestSupport, old OutboundFeedbackPromptPublishOutboxServiceTest
fixture, task01-278/S0/этот append-only changelog.

Private feedbackTicketContext внутри payload_json сохраняет epoch/public/business/
GENERAL/request created_at из текущего persisted V84 pending/root. Capture/повтор
после serialization не допускают unbound/expired/missing/foreign context и raw-ID
ambiguity. Relay сверяет envelope и frozen context с current pending/root до
Rabbit publish. Исторические payload без snapshot не recapture; прежний failed/
retry path, без автоматического удаления/quarantine. Private node удалён до
transport DTO, bot/API/wire constructors и schema/migrations прежние; нужен V84.

## Проверки

126 новых real PostgreSQL/V84 cases в3 scopes×2 exact raw IDs, BIGINT>2^53:
snapshot/wire6, enqueue deny48, delayed relay deny48, historical payload deny6,
late-change после legacy guard6, positive/control12. Public-ID/channel ABA,
business change, recreated root/request с другим created_at, expiry, missing/
unbound context проверены. Trigger bypass только в isolated historical fixture.
Baseline126/114 assertion failures/12 controls/0 errors/skips; новые sources/
bytecode/case names frozen. GREEN137/2 suites затем расширенный единый GREEN1204/
18 suites/0 failures/errors/skips:126+прежние1067 R244+11 прежних outbox cases.
Результаты двух прогонов не суммируются. Старый fixture адаптирован под V84;
exact inverse setup восстанавливает все прежние test bodies/assertions.

Verified R244 JAR/1423 classes; compiled1 main/21 tests,1424 main classes/1421
unrelated unchanged,3 class paths одной source family. Tested class archive и
source/class manifests сохранены. Не full Maven/image build. Helper512MiB/1.5CPU,
PG192MiB/1CPU before readiness; heap160/meta224/code32/stack512k/sequential.
Fresh XML/frozen hashes/resource native JSON/stdout/empty watcher stderr exact;
null Start-Process ExitCode не используется вместо доказательства limits.
Rabbit publish/confirm — doubles, не live broker/provider/security acceptance.

Detached sandbox/replay/exact scope/content/diff guards до source apply;
apply отдельно от разрешённых до конца31 октября Europe/Moscow commit/push,
без force. Evidence .tmp/01-278-r246-evidence/: RED/GREEN XML/resources/frozen
source+bytecode/fixture inverse/tested archive/runtime/DB/apply/publish results.

## Среда и остаток

RuntimeR245/webR244/workerR198/DB84/foundationOFF/18 containers/config/DB fingerprints
прежние, mutable backup heartbeat отдельно; backup/rollback R245 сохранены.
Working migration/backfill/flags/queues/provider writes/rebuild/restart/deploy не
выполнялись. WSL/VS Code/working services ради ОЗУ не останавливали. SourceR246
ещё не развёрнут; old worker не проверяет новый private context. Смешанный rollout
не принят, worker rollout требует отдельного согласованного image/runtime scope.

Не закрыты DB→Rabbit TOCTOU после последнего SQL, broker-queued delivery/retry/
recipient authorization, request-ID reuse с тем же created_at, non-cooperating
writers/DB uniqueness/phantoms/lifecycle revision/owning child-rating attribution,
остальные media/policies/AI/fleet/questionnaires/flags/S6/DLQ1 и фактический rollback.
Это backend outbox admission, не end-to-end isolation. S2B снятR190, задача🟡.
