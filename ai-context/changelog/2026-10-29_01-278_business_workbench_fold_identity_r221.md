# 01-278 R221 — Unicode-барьер назначений, участников и BUSINESS lifecycle

Время: 2026-10-29T06:01:17.744Z (UTC; Europe/Moscow +03:00).
Baseline 2cf96266decdbe836031b1b33d416b0ab8da1adc, parent 1afa4c574da45df26ceffa3ef6bd6c186c2f4da6.
HEAD/origin/main/actual remote совпали, subject/6 previous paths/blobs/content R220
проверены; новых commits после R220 при старте нет, AI-изменения сохранены.

Промт пользователя:

> делай

Контекст: продолжение предложенного аудита назначений, участников и действий
с обращениями; ранее commit/push разрешены до конца29 октября Europe/Moscow.

## Изменение

BusinessTicketAssignmentService/ParticipantService/WorkService связывают READ
с собственной учётной записью через общий ACTOR_ACCOUNT: enabled/not blocked и
lower(actor.username)=n.user_identity при прежнем ID/exact raw-name JOIN. Это
закрывает U+0130/i+U+0307 Java/PG gap, когда другой ID предоставлял READ/status.
Guard включён в общий ACTOR readers, 3 authenticated writer JOIN и прямо в
финальный work UPDATE. Существующие import callers Participant/Work пересобраны
вместе с Assignment: stale inlined constants не используются.

12 actor API/16 веток: 9 readers и take/assign, add/remove, close/reopen/categories.
Публичные API, READ/OPERATE/ALL grants, target eligibility, full owner/unique root,
CAS revisions, advisory/row/grant locks, audit/domain/outbox atomicity, scoped
counters/categories и legacy boundaries сохранены. UI/schema/dependencies/flags
и login normalization прежние. Это statement/transaction-time защита, без
response-wide/global lock или внешней post-lock revocation гарантии.

Scope7 files: 3 main services, новый BusinessWorkbenchFoldIdentityPostgresTest,
task detail01-278, S0 contract и этот changelog. Старые tests/fixtures/assertions
не менялись, adapters не понадобились.

## Проверки

774 tests /6 suites GREEN, failures/errors/skips=0. Новых frozen SQL275:
baseline275/44 assertion failures/0 errors, controls231 GREEN; те же source bytes,
3 compiled class hashes и275 case names GREEN. Проверены active/disabled/blocked/
no-rights Unicode neighbour и отсутствие соседа, exact raw names, READ/OPERATE/
ALL grants, BIGINT actor/owner IDs, private payload/read-only denial, raw audit,
full-scope outbox и отсутствие legacy category/participant side effects.
9 read-boundary и10 mutation/confirmation cases проверяют status. Writer callback
меняет его внутри writer transaction; при отказе domain/audit/outbox не остаются.
Реальные advisory lock waits с rename/delete и concurrency повторены existing
actor suites Assignment36/Participant66/Work97; Reader75 и HTTP225 повторены.
Исторический R220327/4 за новый run не выдаётся.

Полный v2 run774/6 имел 0 assertion failures и1 setup error в Participant.fixture:
SocketTimeoutException при SSL negotiation native PostgreSQL, до service call.
708 cases остальных5 suites GREEN v2; unchanged Participant66 отдельно GREEN v3.
774/6 — сводная валидация, не новый полный clean run. Все6 v2 XML/log сохранены;
source/class/provider hashes совпали до/после retry, tests/assertions неизменны.

1356 Java baseline inputs совпали с проверенным R220 cache. Новый test compiled
v1, 3 main compiled v2 (Maven wrapper/JDK17); frozen assertions после RED прежние.
Git replay/inverse, source/class/XML/log hashes/fresh reports и exact scope
проверены до main apply. Native PostgreSQL16.15/loopback54167/fresh schemas,
explicit ignored R200 supplier с SHA/version/exact directory guards; tracked
JDBC helper/base прежние (Testcontainers). Новый class использует собственную
autocommit connection/schema с полным row reset и реальные writer transactions.
Connection и owned server закрыты, artifacts сохранены. Docker/Testcontainers
не запускались; все83 migration sources прежние, Flyway только test schemas.
JS не rerun. Selected validation, не весь repository/runtime/provider acceptance.

## Среда и остаток

Source/test C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на другой
машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Полные R184/R198/R204/
R207/R216 runtime/rollback/V83 blocks сохранены. Последний документированный R216:
webR215/workerR198/foundationOFF; readinessUP, общий health503 из-за прежнего
inboundDLQ=1 остаётся operational gate. Queue не consume/ack/purge/requeue;
deploy/restart/working migrations/flags/provider calls здесь не выполнялись.
R217–R221 не в документированном R215 image; нужен новый panel-web image,
новых migrations нет, V83 не повторять. Старые rollback/backup сохранены.

Далее — четыре отдельных projection services AssignmentRead/CurrentAssignmentRead/
ParticipantRead/WorkRead: ID/raw/status есть, fold связь с name-based READ требует
отдельных frozen SQL cases; в R221 они только прочитаны. Child/media/feedback/
GENERAL, общие task/project/system policies, AI privacy/context/retention/delivery,
channel/fleet/flags/S6 acceptance открыты; historical S2B снятR190, Task🟡.
Source apply/Git publisher/runtime разделены, разрешение commit/push до конца
29 октября сохраняется; publisher проверяет expiry/path/content после GREEN.
Фактические Git-факты — .tmp/r221-result.json и r221-final.json. Evidence также
r221-baseline-red.json/r221-java-green.json/r221-validation.json и operators/logs.
