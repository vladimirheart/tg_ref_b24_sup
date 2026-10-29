# 01-278 R219 — подбор AI-материалов и handoff по ID сессии

Дата:2026-10-29, Europe/Moscow. Baseline 4c1cdd6eba351d610fcc9b444d499088488b2b49,
parent 18a00b3dc38ba7b2624cca40deda9be941a28c77; HEAD/origin/main/actual remote совпали.
Subject/18 incoming paths/blobs/content проверены, R216/R217/R218 сохранены.

Промты пользователя:

> репо обновлён. проверь и продолжай по задача

> продолжай. коммит и пуш разрешаю до конца суток
> что ещё осталось по задаче?

## Изменение

Source.retrieve/isCurrentCandidate, HandoffKnowledge.candidates и scoped facade
retrieveScoped получают Authentication вместо String identity. Controller передаёт
сессию целиком. SELECT связывает ID/exact raw username и проверяет own enabled/not
blocked/SQL fold=n.user_identity; для Source переиспользован BOUND_ACTOR R218.
Unicode neighbour не может предоставить статус/права. String-only overloads
четырёх API удалены; все11 actor API ScopedAiSourceService теперь bound.

READ membership/ALL_BUSINESSES, active business/current unique account, request
owner tuple/collision, APPROVED/revision/payload и MEMORY origin, latest handoff
event/privacy/filter-before-limit сохранены. Readers не пишут state/source/event/
evidence. Trusted record SQL, legacy retrieval и autoReplyAllowed=false прежние;
без scoped source facade отказывает без legacy fallback. Statement-time защита;
ранее разрешённые control/messages могут остаться в модели после позднего
candidates denial — response-wide/global lock не заявляется.

Handoff получает package-private NamedParameterJdbcOperations constructor для
statement-boundary tests; публичный JdbcTemplate constructor и Spring wiring
сохранены с явным @Autowired. Trusted record SQL неизменен.
Scope12 paths:4 main,3 existing fixtures,2 new tests,task/S0/changelog.
55 identity arguments адаптированы через прежние initial-login helpers;
нет request-time account/grant repair, assertions не менялись, inverse/replay точны.
UI/SCSS/JS/policy/dependencies/83 migration sources не менялись.

## Проверки

370 tests /8 suites GREEN, failures/errors/skips0. Новых155 frozen cases:
143 SQL+12 real form-login/CSRF HTTP. Baseline155/61 assertion failures/0 errors,
controls94, product неизменен. Те же два source bytes и155 case names GREEN.
Реально повторены Source82/Workbench47/Principal19/Dialog35/Fold27/Legacy5;
810/13 R218 остаются историческим run другой сессии и за новый не выдаются.
Проверены mismatch/reuse/rename/delete/case/padding, invalid auth, raw/BIGINT IDs,
live status/grants, SQL-boundary mutations, Unicode neighbour, request/origin/
scope/approved evidence, summary privacy/read-only и отсутствие legacy fallback.
HTTP отзывает account/grant после R189 и прежних разрешённых чтений перед
candidates SQL; проверены оба page rights/anonymous/flags, real case-insensitive
login с stored raw name и отсутствие source body в candidate summary.

Compile-v2/v3 остановили adapter, повторно обернувший два уже bound assignment/
participant calls. Receiver-specific transform v2 восстановил исходные чужие
вызовы; полный replay из Git baseline и inverse проверены, compiler logs и v1
manifest сохранены. Test assertions и product code при исправлении не менялись.
Подготовительный guard-v1 ошибочно требовал raw blob equivalence у vendor NOTICE
с CRLF/LF при -text: normalized content совпал, NOTICE не меняли. Guard-v2 сохраняет
raw root snapshots и fresh incoming blob/content guards; main до GREEN неизменен.

1353 Java inputs:1340 cache R215 совпали по normalized content,13 новых/изменённых
и2 новых test sources скомпилированы на baseline. Emitted main/test Java compile
и source/class/XML/log SHA/freshness проверены; Maven wrapper/JDK17. JS не rerun.
Native PostgreSQL16.15/loopback54167, fresh isolated schemas; explicit ignored
R200 supplier с SHA/version/exact data-directory guards. Tracked JDBC helper/base
прежние (штатно Testcontainers). Docker/Testcontainers не запускались.
Новый SQL class держит собственную autocommit connection/schema с полным row reset
перед case и закрывает её; PostgreSQL остановлен, artifacts сохранены.
Flyway только isolated test schemas,83 migration sources неизменны.
Это selected validation, не whole repository/runtime/provider acceptance.

## Среда и дальнейшая работа

Source/test: C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на другой машине/
сессии: C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Полные R184/R198/R204/R207/R216
runtime/rollback/V83 blocks сохранены. Последний документированный runtime R216:
webR215/workerR198, foundationOFF. ReadinessUP; общий health503 из-за прежнего
inboundDLQ=1 не закрыт. Queue не consume/ack/purge/requeue; working datasource,
deploy/restart/working migrations/flags/provider calls не использовались.
R217–R219 не в работающем image; нужен новый panel-web image, новых migrations
нет, V83 не повторять. Старые rollback/backup не удалялись.

Далее аудит соседних bound-ID/name-based READ/fold paths, child/media/feedback/
GENERAL, общие tasks/projects/system policies, privacy/LLM/delivery/retention и
channel/fleet/flags/S6 acceptance. Historical S2B снятR190, общий Task🟡.
Source apply/Git publisher/runtime разделены. Пользователь29 октября продлил
commit/push до конца суток Europe/Moscow; отдельный publisher действует после
GREEN/exact path/content checks. Это не rollout/working migration/flags action;
фактические Git-результаты отдельно в result/final evidence.
Evidence .tmp/r219-baseline-red.json, r219-java-green.json, r219-validation.json,
r219-result.json, r219-final.json и versioned operators/logs (ignored, не в scope).
