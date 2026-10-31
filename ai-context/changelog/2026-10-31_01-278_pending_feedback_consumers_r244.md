# 01-278 R244 — сохранённый контекст в трёх обработчиках feedback

Время:2026-10-31,18:24+03:00, Europe/Moscow, текущая Windows/Docker-сессия.
Промт пользователя:

> делай

Продолжение согласованного следующего пакета: подключение сохранённого контекста
к pending read, prompt dispatch и storeFeedback. Значимые действующие уточнения:

> давай дальше. по ресурсам рабочей машины ничего дополнительно изменить не могу
> комит и пуш разрешаю до конца суток

## Реализация

Baseline clean HEAD/origin/main/actual remote57447e0ecad76179bedfe4eb54a1443632146ac9,
parentdf1bfc6da, проверены R243 subject/8 paths. Source scope18: новый repository
PendingFeedbackContext, PendingFeedbackRequestRepository, BotRuntimeTicketReadService,
BotRuntimeTicketWriteService, FeedbackPromptDispatchSchedulerService; новый
PendingFeedbackConsumersPostgresqlTest и PendingFeedbackContextTestSupport;
8 прежних service test fixtures; task01-278/S0/этот append-only changelog.

Native projection связывает persisted pending tuple+epoch с единственным exact
current root. Historical unbound/previous generation не recapture. Read делает
final check после rating read и для fallback. Dispatch берёт locks pending→root,
повторяет context/expiry/unsent перед publish, recipient из locked request;
persisted sent_at не заменяется stale managed entity. Writer сохраняет R241
root/rating locks и frozen outbox tuple, проверяет saved context до rating lookup
и после всех ожиданий; final expiry из persisted projection. Native timestamptz
использует Instant. API DTO/constructors/bots и migrations не менялись; нужна V84.

## Приёмка

Новые132 cases:18 positives,90 unbound/scope ABA/channel ABA/recreate/public
assignment,18 late changes после read/rating lookup/template,6 stale managed
unsent candidates. Real Hibernate/PostgreSQL, V84 на минимальной root schema;
provider publish doubles. Baseline132/114 assertion failures/18 controls/0 errors/
skips. Новый source/class/cases frozen после валидного RED. Compile до RED
исправлен для AssertJ generic inference локальными variables, не weakening.
Первый product132:0 failures/30 errors из-за native Instant→OffsetDateTime;
product projection исправлен, tests прежние, повтор132 GREEN.

Accepted1067 tests/16 suites,0 failures/errors/skips одним run:
132+прежние900 R241+read27+dispatch7+R243 migration contract1, включающий156 SQL
assertions (не отдельные156 JUnit).8 old fixtures:V84/explicit INSERT columns для
2 real JPA fixtures; explicit admission doubles для6 mock fixtures. Checked inverse
восстанавливает все прежние sources/assertions точно после EOL normalization.
Helper не выдаётся за persisted proof;132 новых/real JPA96 проверяют native путь.
Controller11 по-прежнему filters=false/token delegation, не full security chain.

Trusted R241 JAR SHA79815e522ea82f9bb211ec77b7ad6cd88c37789a26e44a02b0e1ffaf09ebbf3f;
incremental compile5 main/18 test sources,1423 main classes/1409 unrelated прежние,
14 changed/added только в5 source families. Не full Maven build или новый runtime JAR.
Helper512MiB/1.5CPU, PG192MiB/1CPU, heap160/meta224/code32/stack512k, sequential.
Первый full1067 GREEN не имел PG bound proof: watcher File.ReadAllText не прочитал
open log. Весь набор повторён с FileShare.ReadWrite; bound установлен до PG readiness.
Wrapper после GREEN ошибочно отверг null Start-Process ExitCode; независимый smoke
воспроизвёл HasExited=true/ExitCode=null. Acceptance-v3 проверил native bound JSON/
stdout/ID/session/time/empty stderr, fresh XML, frozen source/bytecode/cases и
fixture inverse, без третьего прогона/правок tests/ослабления memory limits.
Первый и повторный1067 не суммируются. Failed logs/operators сохранены.

Detached sandbox/replay/exact18 scope/content/diff guards до main apply, который
сам не stage/commit/push/deploy. Отдельный publisher проверяет срок разрешения
31 октября Europe/Moscow и actual remote без force; факты — publish.json.
Evidence .tmp/01-278-r244-evidence/: baseline/green XML, hashes, input/fixture
proof, resources/acceptance-v3, snapshots, tested classes archive, cleanup/apply/Git.

## Среда и остаток

Рабочие18 containers/IDs/images/start/restarts/ENV/mounts/HostConfig/Compose/env/
shared settings и DB fingerprints прежние, mutable backup heartbeat отдельно.
V83/readiness200UP/webR241/workerR198/runtimeR242, rollback сохранён. Working
migration/backfill/flags/queues/provider writes, rebuild/restart/deploy не делались.
WSL/VS Code/working services ради ОЗУ не останавливали, освободить память не просили.
R243/R244 source не развёрнуты; для выкладки сначала нужна отдельно разрешённая V84.
Не закрыты queued prompt/provider delivery, persisted child-rating attribution/
historical rating reuse, lifecycle revision/non-cooperating writers/DB uniqueness/
phantoms/pending-ID reuse, media/policies/AI/fleet/questionnaires/flags/S6/DLQ1.
Это backend admission, не end-to-end isolation. S2B снятR190, задача остаётся🟡.
