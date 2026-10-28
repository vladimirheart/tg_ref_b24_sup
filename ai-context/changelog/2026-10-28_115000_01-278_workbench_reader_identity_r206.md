# 01-278 R206 — девять чтений BUSINESS workbench по ID сессии

Дата: 2026-10-28, checkpoint 11:50 +03.
Baseline HEAD/origin/main/actual remote:
f94389ea4531f5395dff7c92e3dc6bb8956c74ed (R205),
parent aeff55ee6be2de39370af32c0c982fb8cbf9444e.
Промт пользователя:

> продолжай

## Фактические изменения

BusinessTicketAssignmentController и три services Assignment/Participant/Work:
девять find/candidates/recent/revision/members readers получают исходный
Authentication. Проверяется bound authenticated non-anonymous principal с
positive ID/raw username/status; read SQL связывает actor с session ID/exact raw
name, сохраняя прежние current BUSINESS READ/status/ambiguity/owner ограничения.
String-only read APIs удалены. Rename/delete + reused name и case change после
session refresh больше не подменяют читателя. Preliminary lifecycle POST find
тоже передаёт Authentication, без name fallback.

Сохранены READ/OPERATE и VIEWER/ALL_BUSINESSES, eligibility, полный owner tuple,
legacy raw-root collision refusal, caps 200/10, revisions, snapshots/categories/
status/private headers/PAGE_DIALOGS/CSRF/flags. Тела трёх writers неизменны.
Гарантия относится к отдельному statement; поздние поля пустеют при revoke,
законно прочитанные предыдущие поля страницы не изымаются. Atomic response или
revoke после SQL snapshot, новая child storage модель/policy не заявляются.

Четыре старые test fixtures адаптированы к Authentication; test-only helpers
сохраняют captured numeric IDs, Mockito matchers проверяют тот же ID/raw name,
не ищут replacement по текущему username. Новые BusinessWorkbenchFixture,
BusinessWorkbenchReaderIdentityPostgresTest и BusinessWorkbenchReaderIdentityWebMvcTest
проверяют все девять границ, текущие права/статус/флаги, canonical owner/collisions,
literal names/Unicode/BIGINT и read-only snapshots. HTTP использует реальный
login/R189 refresh и изменяет account/grant в spy перед конкретным чтением.
Обновлены текущие выполненное/остаток в task и S0; прошлые runtime blocks сохранены.

## Проверки и применение

Frozen 75 SQL + 56 HTTP на R205: 131 tests / 72 assertion failures / 0 errors/skips.
Те же case/support bytes GREEN. Первый медленный RED остановлен; только новая
fixture получила ANALYZE собственных отношений, затем полностью повторён RED v2.
Final regression: 937 tests / 14 suites, failures/errors/skips=0;
15 BUSINESS Node scripts GREEN, повторены в main. Сохранены 225 прежних full
Spring HTTP, 150 work SQL, 21 category, 19 ScopedPrincipal, writer identity
36/66/97 и reader/session regression R204/R205/R189.

Inverse transformations четырёх старых fixtures точно восстановили baseline,
assertions/case names не ослаблены. Compile 730 main + 603 test; все 1333 Java
inputs host/native совпали, 11 code/test SHA совпали sandbox/replay/native/main,
четыре tested main class SHA сохранены. Exact path guards/diff checks и UTC mtime/
per-report SHA всех 14 XML GREEN. Candidate/replay detached от pinned HEAD;
тот же guarded source patch применён в main, без staging/commit/push.
Testcontainers PostgreSQL 16.15, fresh test schemas; 83 migration sources
не менялись, рабочий datasource не использовался. Compile/test последовательно,
heaps 640/512 MiB, Maven launcher 128 MiB, helper memory 1280 MiB; свой helper
удалён после сохранения reports. Source scope: 4 main + 4 old fixtures + 3 new
test/support + task/S0/этот changelog = 14 files.

## Runtime и остаток

Source-applied=true; staged/committed/pushed/deployment=false. Panel-web остаётся
R204/image c1634138193d, worker R198, bot-runner прежний. Все 18 Compose containers
сохранили ID/image/StartedAt/restarts/ENV/HostConfig/mounts, web replica=1.
Health/readiness 200/UP; web logs за шаг 0 WARN/ERROR. Compose/env и shared settings/
locations/org_structure/backup policy hashes не менялись. Начальная строгая сверка
всех shared файлов обнаружила один отличающийся hash backup-policy-runner.status:
это изменяемый heartbeat штатного daemon (scripts/run-backup-policy.ps1:160,638),
не настройка. Он явно выделен в runtime-comparison-v2; не заявлено полное равенство
всех shared files. Docker/сервисы не перезапускались; working migrations/backfill/
flags/grants/build/deploy не запускались. Rollback tags/V83 backup сохранены.
Для включения R205/R206 нужен отдельный web build/rollout.

Evidence: .tmp/01-278-r206-evidence/, RED/GREEN compile/test logs/reports и
r206-source-v1.apply-patch рядом в .tmp. Task 🟡. Остались другие read/AI consumers,
scoped child/media/feedback/GENERAL, policy/task/project/system/admin, AI privacy/
retrieval/delivery/auto-reply, fleet/provider/retry/session acceptance и первый
production S6. Эти девять readers и history/roster R204/R205 повторно не считать
name-only остатком; historical S2B снят R190.
