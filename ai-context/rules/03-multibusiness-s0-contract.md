# S0-контракт: независимые бизнесы Iguana

## Статус

S0-контракт от `2026-10-10`; S1 source foundation подготовлен и проверен в
изолированной PostgreSQL-схеме. Документ фиксирует технический контракт,
который должен быть реализован по стадиям задачи `01-278`; он не разрешает
запуск Flyway-миграции, backfill, изменение production/runtime или внешних
систем.

Требующие бизнес-решения пункты помечены **PENDING**. Пока они не утверждены,
код не должен подставлять бизнес по текстовому имени, каналу, последнему выбору
оператора или иной эвристике.

## Первое production развёртывание и тестовые данные — R190

Пользователь уточнил 2026-10-27 (R190): проект ещё не запускался в production;
все имеющиеся данные тестовые и не имеют ценности. Сопоставление старых aliases,
ручная классификация пустых/спорных записей и исторический backfill S2B исключены
из обязательного плана. Сохранение старых ID, counts и связей не является
критерием выпуска. Цель — корректная новая модель и первый production запуск.
Это решение не утверждает политики будущих manual/global задач, проектов,
системных событий или права доступа к ним.

Ниже сохранены исторические checkpoint и результаты тестового стенда. Слова
production/рабочий в старых записях, именах Compose и образов не означают,
что production уже запускался. Эта оговорка, source-сводка R232 и runtime R223 имеют
приоритет над прежними требованиями переноса тестовых данных. Само уточнение
не выполняет очистку БД и не меняет среду.

## Среда выполнения и сессии

Source R232 (не развёрнут), актуальная source-сводка:
R232: BUSINESS catalog/list/page/export/status/category summaries и manual create
принимают исходный Authentication вместо публичного ID-only API. ReadActor
сохраняет ID/exact raw-name; 4 owning SELECT проверяют их в users вместе с
enabled=TRUE, strict is_blocked=FALSE и fold uniqueness. Membership/ALL_BUSINESSES,
active business, filters/search/order/limits/413 и private headers прежние.
ManualActor не разрешает unbound/anonymous/stale principal; первый owning INSERT
manual_business_clients атомарно проверяет исходные ID/raw-name, current account,
business и прежнюю OPERATOR/MANAGER/ADMIN роль. Транзакция, namespace allocation,
location/input validation и дальнейшие message/ticket/span writes прежние.
У отказа нет новых client/ticket/message/span rows; sequence может расходоваться.
Это граница owning statement snapshot, не обещание блокировки позднейшего отзыва.
R224 early entry SQL и весь R231 card/child/media блок побайтно сохранены.

627 tests /13 suites GREEN одним финальным selected run (v5).
388 новых:303 SQL и85 real form-login/R189 HTTP с реальными controller/service/
SQL, включая manual writes. Проверены exact raw names, Unicode/edge spaces,
BIGINT>2^53/Long.MAX, wrong ID/name/unbound/anonymous/state, rename/reuse/delete,
strict NULL blocked/duplicate/revoke/archive и membership/ALL_BUSINESSES.
112 SQL cases меняют account/scope непосредственно перед owning statement;
56 HTTP cases — после early entry. Positive VIEWER reads, forbidden manual
VIEWER, flags/PAGE_DIALOGS/CSRF и immutable owner attribution сохранены.
Прежние 8 изменённых fixtures пройдены целиком; дополнительно R224 SQL41 и
выбранные R231 SQL52/HTTP24 child/media cases. Остальные прежние R228–R231
матрицы целиком не повторялись; исторические totals не суммируются с этим run.
Baseline на неизменённом product:388 cases /225 assertion failures /
0 errors /163 controls. HTTP v1:85/42 failures; после исправления
только SQL positive controls весь SQL повторён v2:303/183.
HTTP/fixture sources и classes не менялись между этими baseline runs.
ALL_BUSINESSES корректно сохраняет видимость второго active empty business,
даже при архивировании первого; zero-ID manual ожидает AccessDeniedException.
Первый v1 (231 assertion failures/1 input error) и correction evidence сохранены.
После окончательной фиксации sources/class/case names новых тестов неизменны.

Maven wrapper/JDK17, проверенный R231 cache1381 Java/2251 classes; effective
ignored native supplier R231 Unicode ICU und pinned по SHA source/class.
Native PostgreSQL16.15/loopback54167/owned iguana_r231_unicode, fresh test schemas;
tracked supplier и migrations неизменны, Docker не запускался, owned PG остановлен.
Первый product v3 остановился на testCompile: adapter не распознал Mockito
.when(reads) receiver.8 fixtures восстановлены checked inverse, adapter v2
добавляет этот receiver; product/new tests не менялись. Полный v4:627 tests/
1 failure/0 errors, все388 новых GREEN; общий run RED. Прежний revocation test
ожидал ID-only чтение при неоднозначном логине. V3 fixture transform добавляет
явный deny assert, затем восстанавливает уникальность второго test account
перед прежними positive read/revocation asserts. Ни один прежний assert не удалён.
Все137 API/import/schema/fixture edits проверены replay/inverse. Затем весь
тот же selection повторён для финального GREEN, не собран из failed v4 run.
Новых migrations/UI/deps/feature flags нет. Scope18:4 main/8 old fixtures/
3 new tests/task/S0/new append-only changelog. Replay/inverse/compile/SQL/HTTP/
scope/content/source/class/XML/log checks выполнены до отдельного root apply.

Source/test сессия: C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочий экземпляр
на другой машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup; доступа к нему
не было. Последние документированные факты — runtimeR223: webR222, workerR198,
foundationOFF, readiness200UP, health503DOWN из-за прежнего inboundDLQ1.
Все runtime/rollback blocks сохранены; working DB/deploy/restart/flags/provider/
queues не затрагивались; V83 повторно не запускать. R224–R226 и R228–R232 ещё
требуют будущего panel-web image; R227 tests/docs отдельного rollout не требует.
Пользователь оставил push себе: прежнее дневное разрешение на agent push
заменено этой инструкцией; не возобновлять автоматический push без нового
указания владельца. Agent push в этом продолжении не выполнялся.
Commit разрешён до конца30 октября Europe/Moscow прежним явным ответом;
apply и commit разделены, факты Git — ignored r232-result/final JSON.

Далее: оставшиеся BUSINESS edit/feedback/realtime/admin account boundaries,
PENDING product policies task/project/system/global, AI privacy/context/
retention/delivery, fleet/flags/S6 acceptance и операционный DLQ gate.
Исторический S2B снятR190, общая задача остаётся 🟡; deployment не заявляется.

Source R231 (историческая source-сводка, не развёрнут):6 BUSINESS card/child/media entries принимают исходный
Authentication и private immutable ID/exact raw-name snapshot, без Long fallback.
Каждый owning SELECT проверяет current enabled/strict blockedFALSE/fold uniqueness
и прежний business scope. Controller сохраняет early entry и тот же actor после
hashed bytes; fallback/metadata/owner/limits/statuses/private headers прежние.
Catalog/list/summary/export/manual-create остаются следующим отдельным пакетом.
567 tests /10 suites GREEN одним selected run:412 новых (308 SQL/104 real-login
HTTP) и155 прежних cases. Включены R224 entry SQL41/HTTP23, old controller13,
download8/category21/read12/GENERAL read7 и30 selected GENERAL/BUSINESS media/
followup cases из6 methods. Frozen baseline на unchanged product:412 cases/
232 assertion failures/0 errors/180 controls; все positive controls GREEN.
Финальный baseline v3 повторён на UTF8/ICU und; дополнительно прежний search
control1 GREEN на неизменённом product. Source/class/cases новых412 те же, чтоv1.
Те же new source/class SHA и case names до/после. 6 прежних fixtures адаптируют
102 API-input/import edits с checked replay/inverse; result assertions прежние.
SQL:8 surfaces, membership/ALL sources, raw names/BIGINT>2^53/Long.MAX,
stale/unbound/current disabled/blocked/NULL/duplicate identity, scope revoke,
business archive;96 pre-statement mutations и4 legacy fallback mutations.
HTTP: real form login/R189/real controller+SQL,64 late changes,8 storage rechecks,
16 positives/flags/page right. Прежний R230 run780 и его334 новых остаются
историческими и не суммируются с этим run; новый R231 содержит412.
R228529/R229365 и R230334 целиком не rerun; их неизменённые sources/classes
проверены, GENERAL read7/download subset/media30 повторены явно выше.

Maven wrapper/JDK17, verified R230 cache1378 Java/2242 classes;2 main и6 old
fixtures recompiled, frozen3 test sources/classes прежние; не затронутые product/
test class hashes прежние, кроме явно pinned ignored native supplier variant.
Native PostgreSQL16.15/loopback54167/fresh schemas в отдельной scratch database
iguana_r231_unicode (UTF8/ICU und), ignored R231 supplier с SHA/version/exact
data-directory/locale/Unicode-search guards. Original R200 adapter source и
его C database сохранены; class substitution проверяется отдельно. Tracked supplier/83 migrations
не менялись; existing migrations только в test schemas. Docker не запускался,
owned PG остановлен, artifacts retained; JS не rerun. Scope14:2 main/6 existing
tests/3 new tests/task/S0/append-only changelog. UI/schema/deps/flags прежние.
Preview v1 method pattern пропустил card и fixture scanner — var mock receiver;
оба guards остановились до записи. V2 previews проверили6 methods/6 files;
исходные operators/JSON сохранены. Final v4 GREEN, exact scope/content/
source/class/XML/log/replay/inverse proof перед отдельным root apply.

Первый selected v2:567/1 failure/0 errors, все новые412 GREEN, общий run RED.
Единственный прежний textSearch test ожидал caseless Cyrillic Иван/иВан; native
C database lower оставлял1048, search0. На ICU und lower1080/search1; root search/
catalog implementation побайтно прежний. Создана отдельная owned scratch DB,
не менялись product SQL/collations/migrations/тестовые assertions. Все8 sandbox
product/fixture files восстановлены checked inverse, новый ignored adapter
compiled с pinned source/class; прежние v1/v2 XML/log/JSON сохранены. Baselinev3
на unchanged product дал те же232 RED и positive search1 GREEN, затем тот же
product/fixture transform повторён для финального v4. Это recovery test
environment, не фиксация GREEN по частям из failed run. Proof-emitter v1
остановился до записи из-за повторного substring guard; v2 исправлен и сохранён.

Source/test машина C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на другой
машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Последний документированный
runtimeR223: webR222/workerR198/foundationOFF/readiness200UP/health503DOWN из-за
прежнего inboundDLQ1. Полные R184/R198/R204/R207/R216/R223 runtime/rollback/V83
blocks сохранены. Working DB/deploy/restart/flags/provider/queue не затрагивались;
V83 не повторять. R224–R226 и R228–R231 требуют будущего panel-web image;
R227 tests/docs отдельного rollout не требует. Развёртывание R231 не заявляется.
Далее — BUSINESS catalog/list/summary/export/manual-create late actor binding,
оставшиеся edit/feedback/realtime/admin boundaries, PENDING product policies
task/project/system/global, AI privacy/context/retention/delivery, совместимый
fleet/flags/S6 acceptance и операционный DLQ gate. S2B снятR190. Общий статус — 🟡.
R230 опубликован 56daeb6cb1be2b07de6c17a90381edb6e78c1094; его прежние записи об истёкшем разрешении
описывают подготовку до ответа владельца. Владелец затем разрешил commit/push
до конца30 октября Europe/Moscow (2026-10-30T21:00:00Z); это разрешение действует
и для текущего продолжения. Source apply, отдельный Git publisher и рабочий
deployment разделены; фактические Git-флаги — r231-result/final.json.

Source R230 (не развёрнут):5 GENERAL read entries принимают исходный
Authentication; immutable ID/exact raw-name snapshot проходит через root/child/
document/questionnaire/legacy SQL, без public long-ID fallback. Controller
передаёт тот же actor, включая recheck после hashed bytes. READ/CURRENT/owner/
metadata/private headers/statuses/streaming semantics прежние; admin R229 не менялся.
780 tests /11 suites GREEN одним selected run:334 новых (272 SQL/62 real-login
HTTP) и446 прежних cases, включая30 selected GENERAL/BUSINESS media/followup
cases из6 methods. Три новых sources и compiled classes frozen до product edit;
case names и SHA проверены. Unchanged-product baseline:334/123 assertion failures/
0 errors/211 controls (SQL v1 272/104, HTTP v2 62/19). Первый run v1 334/127/0
сохранён:4 positive HTTP assertions неверно требовали отсутствие UTF-8 charset
у octet-stream. До product edit исправлена только совместимость MIME, сохранены
byte/status assertions; HTTP62 повторены. Это исправление нового fixture, не
изменение product headers. Девять прежних fixtures адаптируют73 API-input edit;
result assertions сохранены с checked replay/inverse. Прежние R228529/R229365
здесь не rerun: их sources/classes и work/write/SSE/admin policy прежние.
Старые suite totals не суммируются с нынешним selected run.

Maven wrapper/JDK17, verified R229 cache1375 Java/2234 classes; changed sources
перекомпилированы, не затронутые class hashes прежние. Native PostgreSQL16.15,
loopback54167/fresh dedicated schemas, ignored R200 supplier с SHA/version/data
directory guards; tracked supplier и83 migration sources прежние. Existing
migrations выполнены только в test schemas. Docker не запускался; owned server
остановлен, evidence retained. JS не rerun. Scope17:2 main,9 existing tests,
3 new tests,task/S0/append-only changelog. UI/schema/dependencies/flags прежние.
Sandbox_GREEN и exact content/scope/source/class/XML/log proof до root apply.
Source operator v1 остановлен до записи из-за substring guard; v2 исправил
уникальный full-line anchor. Selected v3/v4 остановились на javac до tests:
пропущены verify(reads) и local late receiver старого API. Диагноз inline-call
в v3 metadata был неточен; v4 evidence уточняет local late. Все9 fixtures
восстановлены checked inverse перед каждым scanner fix; исходные операторы,
compile logs/JSON сохранены. Scanner v3/final selected v5 GREEN.

Source/test машина C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на другой
машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Последний документированный
runtimeR223: webR222/workerR198/foundationOFF/readiness200UP/health503DOWN из-за
прежнего inboundDLQ1. Полные R184/R198/R204/R207/R216/R223 runtime/rollback/V83
blocks сохранены. Working DB/deploy/restart/flags/provider/queue не затрагивались;
V83 не повторять. R224–R226 и R228–R230 требуют будущего panel-web image;
R227 tests/docs отдельного rollout не требует. Развёртывание R230 не заявляется.
Далее — оставшиеся child/media/edit/feedback/realtime и admin boundaries,
PENDING product policies task/project/system/global, AI privacy/context/retention/
delivery, совместимый fleet/flags/S6 acceptance и операционный DLQ gate.
СнятыйR190 S2B не возвращён. Общий статус — 🟡.
Разрешение commit/push действовало до29 октября21:00 UTC (конец суток Moscow);
на30 октября не продлено. Этот пакет подготовлен без staging/commit/push;
source apply и рабочий deployment — отдельные факты. Git-факты — r230-result.json.

Source R229 (не развёрнут):5 GENERAL grant/list entries принимают исходный
Authentication, без actorless public fallback. Owning SQL проверяет current
ID/raw name/status/ambiguity и effective ROLE_ADMIN+PAGE_SETTINGS: direct,
assigned-role, mixed. Новый PanelRolePermissionSnapshot использует существующие
UserRepositoryUserDetailsService page parsers; SQL сверяет role ID/raw values/
current assignment и блокирует account/authority/role rows до конца statement.
Snapshot read не является разрешением; изменения после capture закрыты SQL proof.
GENERAL READ не требуется админу и не назначается ему автоматически. Target READ/
OPERATE eligibility, revoke cascade/idempotency/URL/payload/flags/CSRF прежние;
late deny403. 1108 tests /14 suites GREEN:365 frozen new cases и743
existing (30 selected media/followup included), errors/failures/skips0.
Expanded unchanged baseline365/200failures/0errors; frozen source/class/cases same.
30 backend Lock-wait races и15 role snapshot→SQL mutations проверены. Scope20:
3 main/11 old+3 new tests/task/S0/changelog. Старые assertions не ослаблены;
fixture admin отдельный, recipient roles не меняются. R228529 не rerun,
их source/class/CURRENT/READ/OPERATE прежние. V2 interrupted after direct-only
role coverage gap; partial XML/log retained,13 sandbox files restored before
180 extra role cases/v3 RED; final v4 GREEN. Verified1371Java/2226class cache,
changed sources recompiled/nativePG16.15/test schemas/ignored supplier guards;
Docker не запускался, owned server остановлен. Schema83/UI/deps/flags unchanged.
Source/test машина C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на другой
машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Последний документированный
runtimeR223: webR222/workerR198/foundationOFF/readiness200UP/health503DOWN из-за
прежнего inboundDLQ1. Полные R184/R198/R204/R207/R216/R223 runtime/rollback/V83
blocks сохранены. Working DB/deploy/restart/flags/provider/queue не затрагивались;
V83 не повторять. R224–R226 и R228–R229 требуют будущего panel-web image;
R227 tests/docs отдельного rollout не требует. Развёртывание R229 не заявляется.
Остаток: child/media/policy/AI/fleet/flags/S6/DLQ; Task🟡/S2B снятR190.

Source R228 (не развёрнут): GENERAL work/write/SSE принимает исходный
Authentication и immutable numeric ID/exact raw-name snapshot. 9 services/29 actor
entries,3 controllers; String fallback отсутствует. Owning SQL связывает account
по ID+raw name и сохраняет CURRENT/READ/OPERATE/fold-uniqueness policy. Durable
dispatch использует сохранённую пару; SSE delivery/heartbeat удерживают actor key,
не следуют за повторно использованным именем. Redis/public payload, URL, schema,
UI/flags/admin policy прежние. Boundary tests мутируют fixture перед SQL, не
доказывают внешний revoke после lock. 1287 tests /13 suites GREEN,
529 frozen new cases; unchanged product RED 232 failures/0 errors,
controls297. 9 old fixture files адаптированы к Authentication без
ослабления result assertions; GENERAL real-login/HTTP/SQL/realtime/Redis повторены.
Setup v1 529 identity-insert errors, v2 smoke19 JDBC-array snapshot failures и
v4 full32 NOT-NULL setup errors сохранены; fixture исправлен до product edits,
nullable compatibility probe снимает/восстанавливает constraint только test users.
v3 smoke32 GREEN/v5 full RED0 errors.
Verified R227 compile cache1368Java/2221classes, changed sources recompiled;
nativePG16.15/test schemas/ignored supplier guards, Docker не запускался, owned
server остановлен. Scope27; migration83/UI/deps/flags unchanged.
Source/test машина C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на другой
машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Последний документированный
runtimeR223: webR222/workerR198/foundationOFF/readiness200UP/health503DOWN из-за
прежнего inboundDLQ1. Полные R184/R198/R204/R207/R216/R223 runtime/rollback/V83
blocks сохранены. Working DB/deploy/restart/flags/provider/queue не затрагивались;
V83 не повторять. R224–R226 и R228 требуют будущего panel-web image; R227 tests/docs
отдельного rollout не требует. Никакого подтверждения развёртывания R228 здесь нет.
Остаток: admin grant atomicity, child/media/policy/AI/fleet/flags/S6/DLQ gate;
S2B снятR190/Task🟡. Отдельный Git publisher до конца29 октября после GREEN.

Source R227 (tests/docs): восстановлены четыре GENERAL suites из долга R226.
Work HTTP actor fixture использует bound ID/exact raw-name principal и реальные
DB authorities для R189; filters не mock/skip, прежние HTTP assertions сохранены.
Шесть новых cases: unbound, page revoke, disabled/blocked/rename/delete.
Три upgrades V69/V70/V71→V83 сравнивают все прежние values/history/grants,
exact old+6 column set и все новые defaults/null provenance; без скрытия drift.
Fresh baseline 343/89 failures/0 errors; после fixes 586 tests /11 suites
GREEN, failures/errors/skips0: 343+6 и237 unchanged entry/read/media cases.
148 identity cases R226 повторены; main/unmodified test class hashes прежние.
R226779/14 остаётся historical non-GREEN, полного779 rerun не заявлено.
Full compile731/637,1368 Java/83 migrations;4 tests recompiled. V1 setup176 errors
(Maven перезаписал native helper/Testcontainers без Docker) сохранены, provider
mtime/class восстановлены; v2 baseline/v3 GREEN nativePG16.15/fresh schemas,
ignored supplier SHA/version/exact-directory guards. Docker/containers не
запускались, owned server остановлен. Product/schema/UI/flags прежние, JS не rerun.
Source/test C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочая машина/сессия другая:
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. RuntimeR223/webR222/workerR198/
foundationOFF/readiness200UP/health503DLQ1, rollback/V83 blocks сохранены.
R224–R226 требуют нового web image; R227 tests/docs не требует rollout.
Working DB/restart/migrate/flags/provider/queue не затрагивались. Далее GENERAL
work/write/SSE ID-bound package, post-entry/child/media/policy/AI/fleet/S6;
Task🟡/S2B снятR190. Отдельный Git publisher до конца29 октября после GREEN.

Source R226 (не развёрнут): GENERAL read/access/page entry получает исходную
Authentication вместо повторного String lookup. GeneralTicketAccessService
проверяет bound PanelUserPrincipal/positive ID/exact raw name/principal status;
один SQL повторяет собственные ID/name/enabled, прежний COALESCE(blocked,FALSE)
и SQL-fold uniqueness. Без String overload/trim/Java fold/name fallback; login
не менялся. Все12 входов (5 REST reads/queue page/5 admin APIs/admin page)
проверяют bound current account. У accounts/READ grant/revoke/admin page guard
добавлен; PAGE_DIALOGS/ROLE_ADMIN+PAGE_SETTINGS/flags/CSRF прежние. Entry guard
не требует GENERAL READ, admin без READ управляет чужими grants. GENERAL
READ/OPERATE SQL, nullable blocked policy и отсутствие data bypass прежние.
237 targeted tests/7 suites GREEN:148 frozen SQL34/real-login HTTP114 на baseline
60 assertion failures/errors0, те же3 sources/5 class hashes/cases;89 прежних
cases повторены. Три fixtures меняют6 inputs+2 imports, inverse/assertions exact.
1368 Java/83 SQL/1847 inputs проверены;4 generated CSS EOL-only. Расширенный
прогон779/14 не GREEN:89 failures в4 старых наборах. Все89 повторены на исходном
baseline (135 cases):86 unbound session fixture/R189 login redirect и3 старых
SELECT * migration snapshots против добавленных колонок. Их assertions/файлы
не менялись; это отдельный тестовый долг, не скрытый GREEN общего набора.
Гарантия — account SQL на entry, не response/transaction lock или атомарная
проверка полномочий при поздней записи grant. GENERAL work/write/SSE String
callers, post-entry/child/media/provenance/policy/AI/fleet/flags/S6 остаются.
Scope13/source-only: runtimeR223/webR222/workerR198/foundationOFF/readinessUP,
health503DLQ1, без rebuild/restart/migrate/backfill/flags/queue/provider.
Rollback сохранён, задача🟡; подробные проверки и остаток — task01-278 R226.

Source R225 (исторический пакет до R226, не развёрнут): AttachmentService требует LegacyAttachmentReadAccess
для direct/path/key download families и всех4 legacy HTTP routes, после прежних
page/metadata/owner guards, до local/S3 open. Только bound Authentication с
positive ID/exact raw name/status; один SQL повторяет current own account/status/
SQL-fold uniqueness, non-GENERAL/unique raw root и общий current business READ.
SQL fold происходит от того же ID/raw-name row, без Java fold/name fallback.
Active membership/ALL_BUSINESSES поддерживают все4 READ роли; глобальный admin
не заменяет grant. UNRESOLVED только strictOFF/current account, GENERAL всегда
закрыт, foundationOFF не bypass. Отказ/DB error — пустой404 без binary calls.
Старого конструктора без guard нет; uploads/deletes/descriptors/knowledge/avatar
не менялись. 546 selected tests/16 suites GREEN, frozen252(SQL180/HTTP72) на
baseline155 failures/errors0, те же3 source/8 class hashes/cases. Прежние294 cases
повторены;13 fixtures лишь добавляют19 dependency args, inverse/assertions прежние.
1365 Java/83 SQL/1844 inputs проверены,4 CSS EOL-only; schema/flags/UI/JS прежние.
Это current READ перед open, не транзакционный metadata/READ lock, не полная
child business/generation attribution и не прекращение уже начатых streams
при revoke. GENERAL String lookup/child/media/policies/AI/fleet/flags/S6 открыты.
Runtime остаётся R223/webR222/workerR198/foundationOFF; не rebuild/deploy/migrate/
backfill/queue/provider, rollback сохранён. Полные runtime gates ниже, Task🟡.

Текущий runtime R223 — 29 октября,11:05+03, Windows/Docker SinicinVV:
только panel-web обновлён до clean R222 aa69238ca88000e99b7503d4132485627713397f,
image sha256:0558a3411df9aad28c98e7679d479feeb17f056535cd8c3d5c01f513aa60b8b9,
web1 healthy/restart0. WorkerR198 и остальные17 containers прежние; ENV/mounts,
Compose/env/shared inputs сохранены (exact mutable backup heartbeat отдельно),
HostConfig отличается только доказанной Binds permutation. JAR/classes proof,
1139 selected tests/11 suites GREEN,25 live read/access/readiness checks прошли.
Readiness200UP/logs0WARN0ERROR; общий health503DOWN остаётся из-за прежнего DLQ1.
Flyway/settings/accounts/grants fingerprints прежние,83 migrations/V83 уже была;
миграции/backfill/flags/queue/provider не затрагивались, foundationOFF.
Rollback iguana-panel:rollback-r223-web-20261029-b94104870164 сохранён, реальный
откат не запускался. Полный checkpoint/evidence — task01-278 R223. Не закрытие S6.

Source-уточнение R224 (пока не в image): canonical BUSINESS entry
BusinessScopedTicketReadService.requireAuthenticatedUserId принимает Authentication.
Требуются bound PanelUserPrincipal, positive ID/exact raw name/valid status;
один SQL проверяет собственные ID/name/enabled/not blocked и прежнюю SQL-fold
uniqueness. Нет name relookup/trim/Java fold/unbound fallback. Все12 canonical
read callers и manual creation controller передают исходную Authentication.
Page/flags/READ/OPERATE/owner/media proofs и downstream numeric-ID API прежние.
99 tests/6 suites GREEN: frozen64(SQL41/real-loginHTTP23) на baseline25 failures,
errors0; те же source/3 class hashes/cases, прежние35 cases и result assertions.
4 fixtures адаптируют inputs/status schema, inverse baseline exact;1360 Java/
83 migration inputs совпали,4 CSS EOL-only. Schema/UI/JS/grants/principal прежние.
Гарантия — только entry account SQL, не post-entry/response/transaction lock или
атомарная авторизация всех child reads/manual writes. Legacy AttachmentService
downloads всё ещё требуют отдельного business READ до local/S3 open; GENERAL
String lookup, child/media/feedback/realtime/policy/AI/fleet/flags/S6 остаются.
Source apply отдельно от rollout R223; R224 не deploy, commit/push не выполнялись.

Source-уточнение R222 (BUSINESS projection readers, до rollout R223):
AssignmentRead/CurrentAssignmentRead/ParticipantRead/WorkRead требуют собственные
ID/exact raw name/enabled/not blocked/lower(reader.username)=n.user_identity
в authorized_owner. Четыре SQL guards охватывают 5 API: assignment history,
current assignment, roster, participant history, work history. Java/PG Unicode
fold mismatch закрывает чтение вместо заимствования READ у другого ID.
READ/ALL_BUSINESSES (включая VIEWER), full owner/public uniqueness, MANUAL/SLA
provenance, current diagnostics, roster bound/eligibility, cursors и 503 прежние;
statement-time, без response-wide lock. Login normalization/API/UI/schema прежние.
347 tests /10 suites GREEN одним selected run, failures/errors/skips0:
150 frozen SQL (baseline15 failures/0 errors, controls135), те же source/3 class
hashes/case names; прежние SQL29/38/35/32, MVC14/9/14/14, real-login identity12.
20 SQL-boundary status/grant cases, BIGINT/private snapshots/read-only denial;
старые tests/fixtures/assertions прежние. 1357 baseline Java inputs проверены,
новый test/4 main compiled; native PG16.15/fresh schemas/ignored supplier,
owned connection/server закрыты. Docker/Testcontainers не запускались, 83 migration
sources прежние/SQL migrations только tests; JS не rerun.
Source/test машина sushi; рабочий экземпляр на другой машине/сессии
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Последний runtime R216/webR215/workerR198/
foundationOFF/readinessUP/health503DLQ1; полные rollback/V83 blocks сохранены.
R217–R222 требуют нового web image; новых migrations нет, V83 не повторять.
Рабочие runtime/flags/queue/provider здесь не затрагивались. Четыре readers из
плана R221 закрыты; сквозные child/media/edit/feedback/GENERAL/notification/
realtime, policy/AI/fleet/flags/S6 gates открыты, Task🟡/S2B снятR190.
Commit/push разрешены до конца29 октября, отдельный publisher после GREEN.

Source-уточнение R221 (BUSINESS workbench Unicode barrier, не развёрнуто):
Assignment/Participant/Work используют общий ACTOR_ACCOUNT: own enabled/not
blocked/lower(actor.username)=n.user_identity рядом с ID/exact raw-name JOIN.
Он применяется к readers/authenticated writers и прямо в финальном work UPDATE;
все 3 main скомпилированы вместе, оба import callers получили новый ACTOR.
12 actor API/16 веток, READ/OPERATE/ALL, owner tuple/collision, target eligibility,
revision/advisory/row/grant locks, atomic audit/outbox/legacy boundaries прежние.
774 tests /6 suites GREEN: 275 frozen SQL (baseline44 failures/errors0, controls231),
те же source/3 class hashes/case names; actor36/66/97, Reader75, HTTP225 повторены.
Сводно: v2 полный774/0 failures/1 SSL-connect fixture timeout; 708 cases5 suites
GREEN v2 + отдельно Participant66 GREEN v3. XML/log v2 и source/classes/provider
сохранены, retry без изменений code/tests; полного clean rerun774 не заявлено.
SQL-boundary status/rollback проверены; writer callbacks внутри writer transaction,
без post-lock/global revocation обещания. Старые tests/fixtures/assertions прежние.
1356 cached Java inputs проверены, новый test и 3 main compiled, SHA/fresh reports.
Native PG16.15/fresh schemas/ignored supplier, own connection/server закрыты;
Docker/Testcontainers не запускались, 83 migrations прежние/Flyway только tests.
UI/JS/schema/dependencies/flags прежние, JS не rerun. Source/test машина sushi,
рабочий экземпляр на другой машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup.
Последний runtime R216/webR215/workerR198/foundationOFF, readinessUP/health503DLQ1;
full rollback/V83 blocks сохранены. R217–R221 требуют нового web image; новых
migrations нет, V83 не повторять. Runtime/flags/queue здесь не менялись.
Далее четыре отдельных projection READ services; остальные child/GENERAL/policy/
AI/fleet/flags/S6 gates открыты, Task🟡/S2B снятR190. Отдельный publisher использует
разрешение commit/push до конца29 октября после GREEN; runtime не включён.

Source-уточнение R220 (workbench/persistence Unicode barrier, не развёрнуто):
В 9 actor API двух сервисов READ_ACTOR требует ID/exact raw name/own enabled/
not blocked/lower(actor.username)=n.user_identity. Обе CAS-ветки также используют
этот JOIN. Java/PG fold mismatch отказывает вместо заимствования READ/status у
соседа. READ/OPERATE/ADMIN CONFIGURE, owner tuple/collision, CAS/audit/evidence
и trusted worker record SQL прежние; statement-time, без response-wide lock.
327 tests /4 suites GREEN: 187 frozen SQL (baseline49/187 failures/errors0,
138 controls), те же source/3 class hashes/case names; Workbench47/Persistence58/
real-login DialogHTTP35. Старые tests/assertions/fixtures не менялись.
1355 cached Java inputs проверены; новый test и 2 main скомпилированы, SHA/fresh
reports проверены. Native PG16.15/fresh test schemas/ignored R200 supplier;
own connection/server закрыты. Docker/Testcontainers не запускались, 83 migrations
прежние, Flyway только tests; UI/JS/dependencies/flags прежние, JS не rerun.
Source/test машина sushi; рабочая машина/сессия другая:
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Последний документированный runtime R216:
webR215/workerR198/foundationOFF; readinessUP, health503/inboundDLQ=1 остаётся gate.
R217–R220 не в image, нужен новый web image; migrations/rollout/working flags нет.
Full rollback/V83 blocks сохранены. Далее assignment/participant/work bound-ID/
name-based READ audit; остальные child/GENERAL/policy/AI/fleet/flags/S6 gates
открыты. Task🟡, historical S2B снятR190. Commit/push разрешены до конца29 октября,
отдельный publisher после GREEN; runtime этим разрешением не выполняется.

Source-уточнение R219 (retrieval/handoff readers, не развёрнуто):
Source.retrieve/isCurrentCandidate и handoff.candidates принимают bound Auth;
SQL требует ID/exact raw name/own status/совпадение Java-PG fold, с прежними READ,
unique account/business/owner tuple/collision и source approval/payload/origin.
Wrapper/controller сохраняют Authentication, String-only identity overloads закрыты.
Все11 actor API Source теперь bound; latest handoff event/privacy/limits прежние.
Trusted record и legacy retrieval SQL не менялись; autoReplyAllowed=false.
Statement-time, без response-wide отзыва уже разрешённых control/messages.
370/8 selected suites GREEN:155 new frozen cases RED61/155/errors0→те же sources/
case names GREEN; прежние Source82/Workbench47/Principal19/Dialog35/Fold27/Legacy5.
55 old identity arguments/3 fixtures, assertions и чужие candidate API сохранены;
receiver-specific replay/inverse проверены. Compile-v2/v3 и prep NOTICE EOL false
RED сохранены; только adapter repair, frozen assertions/product при нём не менялись.
1353 Java inputs/cache1340/compile13+new2; main/test compile, SHA/fresh reports.
Native PG16.15/fresh schemas/ignored R200 supplier, server/own connection закрыты;
Docker/Testcontainers не запускались,83 migrations прежние, Flyway только tests.
UI/SCSS/JS/policy/flags/dependencies прежние, JS не rerun. Source/test машина sushi;
рабочая машина/сессия другая: C:\Users\SinicinVV\git_h\tg_ref_b24_sup.
Runtime R216/webR215/workerR198/foundationOFF и full rollback/V83 blocks сохранены;
health503/inboundDLQ=1 остаётся отдельным gate, readinessUP; очередь не трогали.
R217–R219 не в image, нужен новый web image; rollout/working migrations/flags нет.
Далее соседние bound-ID/fold paths и child/GENERAL/policies/privacy/delivery/fleet/
flags/S6; Task🟡, S2B снятR190. Commit/push разрешены29 октября до конца суток;
отдельный publisher после GREEN, без rollout/working migrations/flags.

Source-уточнение R218 (library read/configure и общий actor guard, не развёрнуто):
Пять source APIs availableBusinesses/canConfigure/catalog/find/history получают
Authentication с positive session ID/exact raw name; String-only overloads удалены,
оба контроллера передают Authentication. SQL сохраняет READ vs ADMIN CONFIGURE,
unique active account/business, MEMORY origin/collision и pre-limit scope filtering.
Общий BOUND_ACTOR для5 readers и4 writers требует own ID/raw username/enabled/
not blocked и lower(actor.username)=n.user_identity. При Java/PG fold mismatch
нет подмены соседом, способ нормализации оставлен прежним с fail-closed отказом.
U+0130/i+U+0307 gap подтверждён PostgreSQL16: intermediate783/12 GREEN, затем
supplement27/22 assertion failures/0errors. Первый frozen RED188/87/0 на R217.
Всего215 новых cases (163SQL+25 real-login HTTP+27 supplement), прежние595 cases.
Финальный полный повтор810/13 GREEN, failures/errors/skips0. Те же3 frozen source
files/8 class hashes/case names, freshness13 XML проверена до main apply.
61 fixture identity argument/4 never matchers/1 exact session spy, inverse/assertions
proof. Maven/JDK17/PostgreSQL16 Testcontainers/fresh schemas,1353 Java/83 migrations/
1832 inputs проверены,4 generated CSS EOL-only. JS/UI/schema/policy не менялись.
Statement-time, не response-wide lock: ранее разрешённый body не отзывается при
позднем history denial. CAS/audit/прежняя read failure semantics сохранены.
Baseline18a00b3dc+known R21712 paths; staging/commit/push/runtime mutations нет.
RuntimeR216/webR215/workerR198/foundationOFF, R217/R218 не в image. Retrieval/
handoff и прочие child/GENERAL/policy/privacy/delivery/fleet/S6 gates открыты,
Task🟡, S2B снятR190. Полный текущий source/runtime checkpoint в01-278.

Source-уточнение R217 (revise identity, ещё не развёрнуто):
ScopedAiSourceService.revise/reviseExpected принимают bound Authentication вместо
String identity; UPDATE требует session ID/exact raw name, current unique active
account, business READ/ADMIN CONFIGURE и прежний MEMORY origin/collision proof.
Source/revise-event сохраняют ID/raw name; controller передаёт Authentication.
CAS/review/status/content/opaque key/audit rollback сохранены, late HTTP409/no-store.
Общий create/revise principal guard, без String-only review overloads; statement-time,
не global post-snapshot revocation. UI/schema/policy/retrieval/handoff не менялись.
226 frozen cases (200SQL+26 real-login/CSRF): baseline RED78/226/errors0, те же
source bytes/3 class hashes/case names GREEN. Всего570/9 selected tests GREEN,
включая344/7 прежних. 34 fixture arguments/4 files — только initial-login helper,
inverse baseline/assertions proof. Maven/JDK17, PostgreSQL16 Testcontainers/fresh
schemas,1350 Java/83 migrations inputs проверены.1829 inputs:4 generated CSS
отличаются только EOL после Sass, другие raw hashes совпали. JS не rerun.
Compile import defect исправлен до freeze-v2; PS verifier metadata/array defects
исправлены вv3 без изменения Java/tests, evidence сохранён. Same patch/replay/
path/hash guards, без staging/commit/push. Runtime ниже R216 остаётся webR215,
workerR198/foundationOFF; R217 не в image, нужен отдельный web build. Миграции/
backfill/flags/working data не менялись. Read/configure/retrieval/handoff identity,
child/GENERAL/policies/privacy/delivery/fleet/S6 gates открыты, Task🟡/S2B снятR190.

Текущий runtime checkpoint R216 (2026-10-28, 23:04 +03): на рабочей машине обновлён
только panel-web до clean R215 `18a00b3dc38ba7b2624cca40deda9be941a28c77`.
Image b94104870164/container3449295889d1, одна реплика healthy/restart=0;
JAR SHA059ec09a3ba0d831 совпал package/image/live, 1414 classes — tested bytecode.
Compose/env и число реплик сохранены, build/dry-run/up только web с --no-deps.
Rollback tag rollback-r216-web-20261028-0e8a5171215f сохраняет R206; старые R184/
R198/R204/R207 rollback/backup сохранены. Остальные17 containers полностью
неизменны: workerR198/bot-runner прежний, Docker/WSL не перезапускались.
Web ENV/mounts совпали, HostConfig отличается только порядком4 Binds. Shared inputs
не менялись (кроме явно исключённого backup heartbeat); Flyway83/settings/users/
grants/roles fingerprints совпали. Web Flyway skip, 0 WARN/0 ERROR; migration/
backfill/flags/domain writes не выполнялись. Все83 migration sources прежние.
Свежий локальный повтор 344/7 existing tests GREEN, PostgreSQL16 Testcontainers,
схемы изолированы от стенда. 25 live access/readiness checks GREEN, реальный login/
logout и ROLE_ADMIN+PAGE_CHANNELS/PAGE_SETTINGS; foundation OFF сохранён.
Общий health503/DOWN **не принят**: iguanaProduction degraded из-за inboundDLQ=1.
Prometheus worker sample на19:55Z подтверждает этот backlog до web StartedAt19:59:41Z;
db/redis/rabbit connection/storage/readiness UP. DLQ не consume/ack/purge/requeue;
это отдельный operational gate. Полной runtime/provider приёмки не заявляется.
Полный checkpoint/evidence в01-278/.tmp/01-278-r216-rollout. R208–R215 развёрнуты
только в web; identity/child/GENERAL/policy/privacy/delivery/fleet/flags/S6 gates
открыты, Task🟡, historical S2B снят R190. R207 ниже — исторический checkpoint.

Предыдущий runtime checkpoint R207 (2026-10-28, 12:38 +03): на этой машине обновлён
только panel-web до clean R206 commit c2ee116f3147297a28b20165a8f1ac0a2a19aa86.
Одна реплика, healthy/restart=0, health/readiness 200/UP. Image 0e8a5171215f,
container d262f92f80d5, JAR SHA 9d7ce559a89ffb9f; полный checkpoint в 01-278.
Compose build → web-only up с --no-deps/--no-build/pull never. Старый R204 сохранён
tag rollback-r207-web-20261028-c1634138193d; откат не делали. У остальных 17
containers не изменились IDs/images/start/restarts/config: worker R198, bot-runner
прежний. Docker/WSL не перезапускались. Web ENV/mounts совпали; полный HostConfig
hash восстанавливается одной перестановкой четырёх Binds, других отличий нет.
Compose/env/settings/locations/org_structure/backup policy не менялись; mutable
backup daemon heartbeat явно исключён из утверждения равенства shared files.
Flyway83 history/users/grants/roles/settings fingerprints совпали, web Flyway
skip явный; migration/backfill/flags/domain writes не запускались.
Сборка из pinned clean worktree с heap384/helper896 MiB, без параллельных tests:
937/14 + 15 JS R206 повторно не запускались, source/reports проверены; 1333 Java
inputs функционально совпали (620 EOL-only различий), восемь main class SHA
точно совпали с tested. Image сохраняет R204 runtime/base/bot artifacts,
копируется только новый JAR, его SHA совпал package/image/live.
Финальные 26 live read checks GREEN, реальный login и logout; foundation на
стенде выключен, положительный live BUSINESS workflow не заявляется. V1 probe
ошибочно склеил URL и вызвал один ERROR; исправлен только скрипт, V2 повторён.
0 WARN/0 иных ERROR; этот один ERROR сохранён в evidence, не скрыт.
Старые R184/R198/R204 runtime/rollback/V83 backup сохранены. R205/R206 web rollout
завершён, но другие identity/child/GENERAL/policy/AI/fleet/flags/S6 gates остаются;
Task 🟡, historical S2B исключён R190. Этот checkpoint приоритетнее source-only
состояния среды R206 ниже.

Source-уточнение R215 (create identity, без rollout):
ScopedAiSourceService.createKnowledge/createMemory принимают bound Authentication;
SQL INSERT требует session ID/exact raw name, current unique active account,
active business/READ/ADMIN CONFIGURE, MEMORY origin tuple/collision denial.
Source/create-event сохраняют numeric ID/raw name. String create overloads удалены;
controller передаёт Authentication. DRAFT/revision1/content/key/idempotency/atomic
audit rollback сохранены. Statement-time, без post-snapshot revocation guarantees.
118 frozen new SQL/real-login cases: RED40/118, errors0; те же bytes GREEN.
Итог344/7 selected suites GREEN, включая5 прежних fixtures. Setup v1 и его
ошибка display_name сохранены; v2 чинит только test schema/lifecycle, assertions
не менялись. 1346 cache inputs проверены; main/test compile, native PG16.15/fresh
schemas/ignored R200 supplier. Test server остановлен; Docker/Testcontainers не
запускались,83 migrations неизменны, Flyway только test schemas, JS не rerun.
Source/test C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочая машина/сессия другая:
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Runtime R207/webR206/workerR198/foundation off,
полные rollback/V83 blocks сохранены; deploy/restart/working migrations/flags нет.
Нужен новый panel-web image, новых migrations нет. Source revise/read/configure/
retrieval/handoff identity и child/GENERAL/policy/privacy/delivery/fleet/flags/S6
gates открыты; Task🟡, historical S2B снят R190. UI/schema/policy неизменны.

Source-уточнение R214 (test-only source HTTP fixture, без rollout):
Source HTTP использует bound ID/exact raw name/DB page rights без request-time repair.
30 old case names сохранены; missing/disabled/blocked→302/login по R189, прежние
business403/late SQL409/CSRF/CAS/privacy/opaque-key/escaping/origin checks сохранены.
13 new cases:6 prepared account revoke/reuse,4 invalid principals,2 page revoke,
1 actual JDBC SESSION/CSRF create/review/reload/revoke/logout. 75/2 suites GREEN
(Source43+Session32); baseline30/30/0 после только datasource isolation, old auth/
test methods/assertions неизменны тогда. Product/main/UI/schema/policy неизменны.
Fixture имеет fresh schema/current-schema guard; прежние base mocks/properties
сохранены. Tracked helper/base неизменны; native PG16.15/ignored R200 supplier,
server остановлен. Docker/Testcontainers не запускались;83 migrations неизменны,
Flyway только test schemas. JS не rerun, старые R213 evidence не считаются новым run.
Source/test C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочая машина/сессия другая:
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Последний runtime R207/webR206/workerR198/
foundation off и полные rollback/V83 blocks сохранены; deploy/restart/working
migrations/flags не выполнялись. R214 не требует нового restart/build; прежние
product changes этим test-only шагом не развёрнуты. Source SQL identity и прочие
child/GENERAL/policy/privacy/delivery/fleet/flags/S6 gates открыты; Task🟡, S2B снят R190.

Source-уточнение R213 (внутренний non-CAS control, без rollout):
setControl получает bound Authentication; positive ID/raw name/status и SQL
actor.id/exact username защищают INSERT и conflict UPDATE. Audit columns сохраняют
ID/raw name; String-only overload удалён. READ/OPERATE/current account/uniqueness/
business/grants, полный owner tuple/SAME_EXCLUDED_OWNER/collision denial и reason
trim/null/cap500 сохранены; при SQL failure нет частичной записи. Primitive не
создаёт event. Production callers отсутствуют; controller использует прежний CAS.
Все остальные persistence методы/helpers, workbench/MEMORY/source/handoff, UI/schema
неизменны; statement-time, не post-snapshot global revocation. 230/5 selected SQL
suites +15 JS GREEN; frozen99 RED99/30/0, те же bytes GREEN. 22 old calls/4 fixtures
адаптированы, assertions прежние. Source proof subset6+1; HTTP не rerun, route/CAS
не менялись. Native PG16.15/fresh schemas/ignored R200 supplier, server остановлен.
Docker/Testcontainers не запускались;83 migrations неизменны, V62..64 только test
schemas. Source/test C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочая машина/сессия другая:
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Последний runtime R207/webR206/workerR198/
foundation off и полные rollback/V83 blocks сохранены; deploy/restart/working
migrations/flags не выполнялись. Нужен новый panel-web image, migrations нет.
Прямой source MEMORY primitive, source/handoff и остальные policy/privacy/delivery/
fleet/flags/S6 gates открыты; Task🟡, S2B снят R190.

Source-уточнение R212 (MEMORY writer, без rollout):
Workbench createMemory получает bound Authentication; positive ID/raw name/status
и SQL actor.id/exact username проверяются вместе с READ/CONFIGURE/current account/
owner/text evidence. Source и audit сохраняют raw имя, String-only overload удалён.
ADMIN/ALL_BUSINESSES, PAGE_DIALOGS+KB/CSRF/flags/reviewed/DRAFT, content validation,
idempotency и atomic source/audit/evidence rollback сохранены. Поздний отказ409/
no-store без частичной записи; statement-time, не global post-snapshot revocation.
Reads/helpers/persistence/source/handoff/UI/schema неизменны. 228/7 suites +15 JS
GREEN; frozen51 SQL+25 real-login HTTP RED76/20/0, те же bytes GREEN.
Setup v1:76 FK seed errors; v2 исправляет isolated seed TRUNCATE/ANALYZE;
в v3 убраны только собственные rollback-test CHECK между scenarios после control
failure в v2. Product constraints/triggers и SQL/HTTP assertions прежние; evidence
сохранены. 7 old fixture calls
адаптированы, assertions прежние. Native PG16.15/fresh schemas/ignored R200 supplier,
server остановлен. Docker/Testcontainers не запускались;83 migrations неизменны.
Source/test C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочая машина/сессия другая:
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Последний runtime R207/webR206/workerR198/
foundation off и полные rollback/V83 blocks сохранены; deploy/restart/working
migrations/flags не выполнялись. Нужен новый panel-web image, migrations нет.
Прямой source MEMORY primitive, non-CAS controls/source/handoff и остальные
policy/privacy/delivery/fleet/flags/S6 gates открыты; Task🟡, S2B снят R190.

Source-уточнение R211 (четыре persistence read API, без rollout):
findControl/canOperate/findState/findEvents получают bound Authentication, positive
ID/exact raw name/status; SQL actor связан с обоими полями. String read overloads
удалены; READ/OPERATE/current account/uniqueness/business/grants, full owner tuple,
control/state matching, collision denial и event order/cap сохранены. Authorized
absent-control defaults отличаются от denial Optional.empty. Поздний отказ одного
чтения не отзывает уже прочитанные поля; atomic whole-page revocation не заявляется.
Три writer bodies и workbench service неизменны; non-CAS/MEMORY/source/handoff
String boundaries открыты. 314/9 suites +15 JS GREEN; frozen56 SQL+31 real-login
HTTP RED87/30/0, те же bytes GREEN. V1 compile missing import, tests0; исправлен только
import; три SQL owner fixtures исправлены в v3 без изменения triggers. SQL RED v3
56/18/0 + HTTP v2 31/12/0, прежние evidence сохранены. 48 old calls адаптированы,
assertions прежние.
Native PG16.15/fresh schemas/ignored R200 supplier, JIT off only own process; server
остановлен. Docker/Testcontainers не запускались;83 migrations неизменны. Source/test
C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочая машина/сессия другая:
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Runtime R207/webR206/workerR198/foundation off,
runtime/rollback/V83 blocks сохранены. Remote/build/restart/deploy/working migrations/
flags не выполнялись. Нужен новый panel-web image, migrations нет. Task🟡, S2B снят
R190; остальные AI/GENERAL/policy/privacy/delivery/fleet/flags/S6 gates открыты.

Source-уточнение R210 (две AI read API, без rollout):
Workbench resolve/messages получают bound Authentication, positive ID/exact raw
username/status; SQL actor JOIN связан с обоими полями. String-only read overloads
удалены; current READ/ambiguity/account/business/grants, canonical locator/full
messages owner tuple, collision denial, text filters/preview/fingerprint/order/
limit50 и PAGE_DIALOGS/KB/flags/private404 сохранены. Statement-time, не atomic
whole-page revocation. Persistence reads/non-CAS/MEMORY/source/handoff остаются
отдельными String boundaries; MEMORY writer и persistence service не менялись.
249/7 suites +15 JS GREEN; frozen80 SQL+19 real-login HTTP RED99/32/0,
те же bytes GREEN. Четыре прежних fixture assertions сохранены; R20935/R20817/
R18932/workbench47/ScopedPrincipal19 включены. Native PG16.15, fresh test schemas,
ignored R200 supplier, JIT off only own process; server остановлен. Docker/
Testcontainers не запускались;83 migrations неизменны. Source/test здесь:
C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочая машина/сессия другая:
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Runtime R207/webR206/workerR198/foundation
off, checkpoints/rollback/V83 backups сохранены. Remote/build/restart/deploy/working
migrations/flags не выполнялись. Нужна новая panel-web сборка; migrations нет.
Task🟡, historical S2B снят R190; прочие identity/GENERAL/policy/fleet/S6 gates открыты.

Source-уточнение R209 (test-only, без нового rollout):
Долг прежней AI HTTP fixture закрыт:35 cases (22 прежних+13 новых),17 R208 HTTP,
32 R189 session — всего84/3 suites GREEN. Baseline22/22 failures/0 errors;
все прежние case names сохранены. Bound ID/exact raw account, DB page rights,
без repair helper; blocked case теперь требует302/login до controller по R189,
never(resolve)/zero writes. Прочие прежние privacy/404/CSRF/CAS assertions сохранены.
Проверены revoke/reuse/mismatch и настоящий JDBC SESSION/CSRF login-memory-control-
page-revoke-logout. Production/main/UI/schema/dependencies не менялись;83 migrations
неизменны. Native PG16.15, fresh schemas, ignored R200 supplier; Docker/Testcontainers
не запускались, test server остановлен. R208234/6+15JS — прежние evidence, не rerun.
Source/test здесь C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на другой
машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Runtime R207/webR206/workerR198,
foundation off и полные runtime/rollback/V83 blocks сохранены; deploy/restart/working
migrations/flags не выполнялись. Для R209 перезапуск не нужен; product R208 требует
отдельной panel-web сборки. Остальные AI identity/child/GENERAL/policy/fleet/S6
gates открыты, task🟡; historical S2B снят R190.

Source-уточнение R208 (source/test на другой машине, без нового rollout):
UI AI control setControlExpected получает Authentication с bound session ID/exact
raw username/status. INSERT и timestamp-CAS UPDATE связывают того же actor по
обоим полям, updated_by сохраняет raw username. Reused login/case change после
R189 refresh/owner lookup не меняют автора. READ/OPERATE/current account/uniqueness,
owner tuple, stale form refusal, atomic control/event rollback, PAGE_DIALOGS/CSRF/
flags сохранены. String-only overload этой UI API удалён; non-CAS setControl и
остальные AI String reads/writers остаются отдельными открытыми границами.
234 tests/6 suites + 15 BUSINESS JS GREEN; frozen61 SQL+17 real-login HTTP:
RED78/35/0, те же финальные bytes GREEN. Новая login/CSRF fixture исправлена и
повторно проверена на baseline; старые logs/reports сохранены. Отдельный прежний
AI HTTP probe1/1/0 на исходном HEAD выявил unbound user(String) fixture vs R189;
её assertion не менялся, весь старый HTTP suite не объявляется GREEN.
Native PostgreSQL16.15, JIT off только в own test process, fresh schemas, explicit
ignored R200 supplier; Testcontainers/Docker Desktop не запускались. Test server
остановлен; 83 migrations неизменны. Current working runtime по-прежнему R207,
webR206/workerR198, на C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Source/test здесь:
C:\Users\sushi\Git_H\tg_ref_b24_sup; remote/build/restart/deploy/working DB не
выполнялись. Нужна новая panel-web сборка, не migration. Runtime/rollback/V83
backups R184/R198/R204/R207 сохранены. Task🟡, historical S2B исключён R190.

Source-уточнение R206 (до rollout R207): все девять BUSINESS workbench reads

Assignment.find/candidates/recent, Participant.revision/members/candidates/recent,
Work.find/recent принимают тот же Authentication из controller. Требуются
authenticated non-anonymous bound principal, positive session ID, exact raw name
и principal status. Read JOIN связывает actor ID/raw name, прежний READ в том же
SQL проверяет current enabled/non-blocked account/ambiguity/active business/grants.
Нет String-only read APIs или rebind по reused name после refresh. VIEWER/OPERATE,
candidate rules, full owner/legacy collision refusal, caps/revisions/snapshots,
PAGE_DIALOGS/CSRF/flags сохранены. Тела writers не менялись; новых policy/grants/
schema/UI нет. Гарантия statement-time: позднее чтение при revoke возвращает
empty/null, уже законно прочитанные поля model не отзываются. Атомарность всей
страницы или revoke после начала SQL snapshot не заявляется.
Frozen 75 SQL + 56 real-login HTTP: baseline 72 failures/0 errors; те же bytes
GREEN. Final 937 tests/14 suites + 15 BUSINESS JS scripts GREEN. Четыре прежние
fixtures точно восстановлены inverse transforms; все 1333 Java inputs совпали
с native, 11 code/test paths совпали sandbox/replay/native/main. Полная compile
730 main/603 test. Testcontainers PG16.15, только тестовые схемы, 83 migrations
без изменений; helper удалён. Source-only на этой машине от f94389ea4 (R205),
parent aeff55ee6. Working web R204/image c1634138193d, worker R198; 18 containers
не перезапускались и сохранили ID/images/config, одна web-реплика, health/readiness
200/UP. Настройки не менялись; отдельно изменился hash штатного heartbeat-файла
backup-policy-runner.status. Build/deploy/working migration/backfill/flags/grants
не выполнялись. R205/R206 требуют отдельного web rollout. Все старые runtime/
rollback/V83 backup checkpoint сохранены. Другие read/AI consumers, child/media/
feedback/GENERAL, policy/AI/fleet и S6 остаются; эти девять readers закрыты.
Task 🟡, historical S2B исключён R190.

Историческое source-уточнение R205 (2026-10-28): три BUSINESS readers —
assignment history, participant roster/history — получают Authentication.
Требуются authenticated non-anonymous bound principal, positive session ID,
exact raw username/status. Один SQL связывает reader ID/name/enabled/non-blocked
с прежним BUSINESS READ/ambiguity/active business/full owner; reused name/case
change после refresh не подменяют читателя. String-only APIs удалены.
Full-owner audit, manual/SLA snapshots/tie refusal, member eligibility, roster
200/413, pagination/BIGINT/private headers, PAGE_DIALOGS/foundation сохранены.
Нет schema/grants/UI/writer/policy changes. Atomic in-flight revocation после
начала SQL snapshot не заявляется. Workbench find/recent/candidates и прочие
read/AI consumers остаются отдельными границами, не закрыты этим reader scope.
Frozen 93 SQL + 27 real-login HTTP cases: baseline 48 failures/0 errors;
те же bytes GREEN. Итог 509 tests/10 suites + 15 BUSINESS JS scripts GREEN.
Четыре прежние fixture sources восстановлены inverse transformations без
ослабления assertions/case names; 10 code/test SHA совпали в candidate/replay/
native/main. Штатный Testcontainers PG16.15, migration только test schemas.
Source-only на этой машине, baseline aeff55ee6 (R204), parent 53e858df0.
Рабочий web остаётся R204/image c1634138193d, worker R198. Все 18 Compose
containers неизменны за R205; Docker/build/deploy/working migration/flags/grants
не менялись. R205 rollout ещё нужен отдельно; R184/R198/R204 runtime evidence,
rollback/V83 backup и исторический memory recovery R204 сохранены. Task 🟡.

Подтверждённое source/runtime уточнение R204 (2026-10-28): BUSINESS work history reader
получает Authentication, bound positive session ID и exact raw username/status.
Один SQL проверяет текущий account + BUSINESS READ/ambiguity/active business.
Rename/delete + reused name и case change после session refresh не подменяют
читателя. String-only history API нет; owner tuple, immutable audit/categories,
pagination/BIGINT/private headers и 404/400/503 сохранены. Другие name-based
reads/find/recent/AI остаются отдельными границами; новых policy/grants/schema нет.
40 frozen новых cases: baseline 16 failures/0 errors; после патча GREEN.
Итог 601 tests/12 suites + 15 BUSINESS Node scripts GREEN; 349 завершённых cases
из прерванного run + 252 повторённых cases с неизменными native classes.
Шесть source/test files совпали в sandbox/replay/main; два main bytecode class
совпали с образом. Штатный Testcontainers PostgreSQL 16; migrations только test.

Рабочий экземпляр подтверждён на этой машине C:\Users\SinicinVV\git_h\tg_ref_b24_sup.
Panel-web обновлён до base 53e858df0 + локальный R204 (uncommitted=true), одна
реплика, healthy/readiness 200; live login/channel smoke 13 checks GREEN.
Image c1634138193d, JAR bfd68529a9d719ca; полный checkpoint/rollback hashes в 01-278.
Worker остаётся R198, bot/infrastructure images не обновлены. Однако за сессию
был Docker/WSL restart для восстановления после memory exhaustion; running
сервисы перезапускались, bot-runner один раз автоматически, затем healthy.
Web-only rollout после восстановления не менял остальные 17 containers.
83 migration sources, working Flyway/users/authorities/roles/settings/flags
не менялись. Final web ENV/mounts/HostConfig совпали с исходными; промежуточный
post-restart HostConfig hash отличался, не замаскирован как Binds-only.
Старые R184/R198 backup/rollback/evidence сохранены. Полная S6 приёмка не заявляется.

Историческое source-уточнение R203 (2026-10-28): manual BUSINESS close/reopen/categories
writer принимает Authentication с bound PanelUserPrincipal, positive session ID,
exact raw username/status. Locking root SELECT, conditional UPDATE и confirmation
после audit/outbox используют того же actor по ID/raw name. String-only API нет;
rename/delete/reused name после controller read/advisory wait и case change между
SQL boundaries не меняют автора. Отказ final UPDATE/confirmation откатывает root,
categories/counters/revision/audit/outbox. READ/OPERATE/ambiguity/status, owner
tuple/CAS/locks, category validation, reopen categories и PAGE_DIALOGS/CSRF/R189
сохранены. Assignment/participant writers R201/R202 не менялись; name-based reads/
AI и прочие consumers остаются следующими шагами. Atomic in-flight revocation
за пределами проверенных SQL snapshots не заявляется; новых policy/grants нет.
952 tests / 11 suites + 15 unchanged BUSINESS Node scripts GREEN.
Frozen 97 SQL + 12 HTTP новых cases: 52 failures / 57 controls / 0 errors;
с 213 прежними HTTP controls baseline 322/52/0. Final HTTP 225; три прежних fixture
sources/assertions восстановлены точно через inverse transforms. Native PostgreSQL
16.15 + explicit ignored R200 JDBC supplier, fresh schemas; Testcontainers/Docker
Desktop не запускались. Test server остановлен, 83 migration sources неизменны.
Source/test здесь; working project C:\Users\SinicinVV\git_h\tg_ref_b24_sup — другая
машина/сессия. Runtime остаётся R198; R199–R203 rollout не подтверждён. Нужна новая
сборка panel-web, новых migrations нет. Deploy/restart/working DB не выполнялись.
Полные R184/R198 runtime и исторический R200 Docker incident сохранены. Task 🟡,
historical S2B исключён R190.

Историческое source-уточнение R202 (2026-10-28): BUSINESS participant add/remove writer
принимает Authentication с bound PanelUserPrincipal; во всех writer actor joins
требуется positive session user ID + exact raw username. Нет String-only writer
или повторной привязки автора по lower(name) после controller read/advisory wait.
READ/OPERATE, uniqueness/status, full owner tuple, revision/CAS/locks, roster 200,
rollback/outbox сохранены; удаление неактивного/удалённого member остаётся доступным
по stored ID/snapshot. Assignment writer R201 не менялся; name-based participant/
assignment reads, work/AI и прочие consumers требуют следующих отдельных шагов.
Полная atomic revocation in-flight SQL snapshots не заявляется.
619 tests / 14 suites + 15 unchanged BUSINESS Node scripts GREEN.
66 SQL + 8 HTTP новых frozen cases: baseline 33 failures / 41 controls / 0 errors;
с 205 прежними HTTP controls baseline 279/33/0. Final HTTP 213, ScopedPrincipal 19;
3 старых fixture bodies/assertions восстановлены точно при inverse transform.
Owned native PostgreSQL 16.15 + explicit ignored R200 supplier, fresh schemas;
Testcontainers/Docker Desktop не запускались. Startup/status/PID/data/port/listen
проверены, test server остановлен после проверки. 83 migrations не менялись.
Source/test выполняется здесь, working project — на другой машине/сессии
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Runtime checkpoint остаётся R198;
R199–R202 rollout не подтверждён. R202 требует новой сборки panel-web, не новой
migration; deploy/working DB/restart не выполнялись. R184/R198 runtime и R200
исторический Docker incident сохранены. Task 01-278 остаётся 🟡, S2B исключён R190.

Историческое source-уточнение R201 (2026-10-28): manual BUSINESS take/assign writer
получает Authentication, positive numeric PanelUserPrincipal ID и exact raw name.
Locking root SELECT и final conditional write связывают actor по обоим полям;
String-only change overload удалён. Rename/reuse после refresh/read/lock wait
не меняют автора; take/audit сохраняют того же bound actor. PAGE_DIALOGS/CSRF,
READ/OPERATE/ambiguity, tuple/revision/CAS/locks/rollback/outbox сохранены.
394 tests / 9 suites и 15 BUSINESS Node scripts GREEN. Frozen 40 новых cases:
23 RED failures + 17 новых controls, ошибки 0; те же bytes/block GREEN.
201 прежних HTTP и 19 ScopedPrincipal cases/assertions сохранены. Real SQL и
advisory-lock wait — owned native PostgreSQL 16.15, fresh schemas; явный ignored
R200 JDBC supplier adapter, без изменения tracked helper и без Docker.
Это не Testcontainers/working/fleet acceptance. Новых migrations нет, 83 SQL
sources неизменны. Readers/participant/work/AI String paths остаются отдельным
scope, полной атомарной revocation всех in-flight requests не заявлено.
Текущая source/test машина C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочая
C:\Users\SinicinVV\git_h\tg_ref_b24_sup — другая машина/сессия.
Remote R198/V83 checkpoints, backup/rollback и R200 historical incident сохранены.
R201 требует новой сборки panel-web, не новой migration; здесь deploy не выполнялся.
S3–S6/policy/AI/bots/fleet открыты, historical S2B снят R190, статус 🟡.

Историческое source-уточнение R200 (2026-10-28): BusinessTicketAssignmentHttpPostgresTest
переведён на numeric-ID PanelUserPrincipal и DB authorities. Все прежние 190
cases/assertions сохранены, literal padded accounts не подменяются соседями.
Добавлены 11 account/page revoke/forged principal/reuse cases и настоящий
form-login/JDBC SESSION/CSRF/current/history путь. 201 HTTP cases GREEN;
всего 400 tests / 12 suites и 15 неизменённых BUSINESS Node scripts GREEN.
Baseline 190 cases воспроизвёл прежние 122 failures/1 error. v2 ошибка только
нового MockHttpSession fixture и v3 empty-cookie array исправлены cookie jar;
отдельный real-login v4 и общий final v5 GREEN.
Исполнение SQL — disposable native PostgreSQL 16.15 на loopback с временным
ignored JDBC supplier bytecode adapter; tracked Testcontainers helper не изменён.
Это не штатный Testcontainers runner или working/fleet acceptance. Production,
UI, 83 migrations, permissions и dependencies R200 не меняет. Runtime R198/V83
на другой машине, backup/rollback ниже сохранены; R199 source-only по checkpoint.
Локальный Docker был ошибочно запущен и автоматически поднял 14 старых контейнеров,
затем остановлен; их автоматические background writes не аудированы. Native
tests используют отдельную временную БД. Remote deploy/restart не выполнялись.
Текущая source/test машина C:\Users\sushi\Git_H\tg_ref_b24_sup, рабочая
C:\Users\SinicinVV\git_h\tg_ref_b24_sup — другая машина/сессия.
S3–S6/policy/AI/bots/fleet остаются открытыми, historical S2B снят R190, статус 🟡.

Историческое source-уточнение R199 (2026-10-27): текущий BUSINESS responsible доступен через
отдельный private READ endpoint и main card. Требуются bound reader ID/exact
username/status, PAGE_DIALOGS, foundation и текущий BUSINESS READ.
ASSIGNED требует live row + единственный raw root + одно MANUAL/SLA событие
ровно текущей responsibility_revision с full-owner tuple и exact snapshot;
history/raw row отдельно недостаточны. UNASSIGNED — только чистый revision=0,
остальное UNVERIFIED без raw disclosure. Manual ID frozen, SLA ID не выводится.
UI сохраняет string BIGINT, owner/revision checks, stale-response/XSS guards.
134 tests / 6 suites и 15 Node scripts GREEN. Старый широкий HTTP fixture
падает одинаково на R198 и R199: 122 failures/1 error из 190, требует отдельного
ID-bound session обновления; assertions/filter не ослаблены.
R199 source-only, новых migrations/flags/deploy нет. Работающие web/worker
остаются на R198; checkpoint/backup/rollback ниже не менялись. Статус — 🟡.
Актуальный runtime checkpoint R198 (2026-10-27, 22:53 +03): на машине
C:\Users\SinicinVV\git_h\tg_ref_b24_sup обновлены panel-web и ops-worker до
c3fa2777914095e91b783344072db01e10015682, по одной реплике, healthy/restarts=0.
Worker обновлён также для фонового ID-bound automation credential registry.
Одноразовый migrator применил V83 (82 → 83); штатный db-migrate не пересоздан.
Прежняя migration history, users/roles/grants/settings сохранились; остальные
16 Compose-контейнеров, ENV/mounts/HostConfig, flags/shared config не менялись.
666 tests / 15 suites GREEN включают реальные PostgreSQL/JPA/Flyway R197 и
window/Boolean R198 cases. Native Java inputs/deployed JAR сверены.
Health/readiness, real admin login, channels/settings/preferences, employee
options и automation history прошли HTTP-проверки; сессия закрыта.
Web logs без WARN/ERROR; worker без ERROR, но с предупреждениями внешнего
SSL-монитора, не блокирующими readiness. Мониторинг не отключался.
Оба rollback images, прежний R184 rollback и проверенный восстановлением pg_dump
сохранены. Детали — актуальный checkpoint в task-details/01-278.md и ignored
.tmp/01-278-r198-rollout-20261027/. Старые name-keyed настройки/ключи не
импортируются. Это не полная browser/provider/fleet/S6 приёмка. Статус — 🟡.

Историческое source-уточнение R198 (2026-10-27): общий legacy/global employee catalog
использует минимальную JDBC-проекцию вместо полного users SELECT; disabled,
blocked и case-fold ambiguous accounts не предлагаются. Window count включает
даже disabled/blocked соседей. Raw username остаётся точной строкой, label/order,
value/label response и прежние page gates сохранены. Основной PanelUser RowMapper
корректно читает JDBC Boolean и прежние numeric/string flags. Это не расширение
scoped candidates или assignment grants. Null status defaults совпадают с login.
38 suites / 974 tests GREEN; frozen 46 cases: baseline v2 8 failures / 38 controls /
0 errors, те же bytes GREEN. Новый PanelUserOptionsPostgresqlTest (8 cases)
только скомпилирован, реальная SQL/Boolean/window acceptance не выполнялась.
83 migrations неизменны, V83 здесь НЕ запускалась. Изолированные PostgreSQL
R195–R198 suites нужны до отдельно разрешённых migration/rollout.
Текущая source/test машина C:\Users\sushi\Git_H\tg_ref_b24_sup;
рабочий экземпляр/Docker C:\Users\SinicinVV\git_h\tg_ref_b24_sup на другой
машине/в другой сессии. R185–R198 не развёртывались отсюда; рабочая БД/данные,
grants/roles/config/providers и checkpoint/rollback R184 сохранены. S3–S6,
policy/AI/bots/fleet gates открыты; historical S2B исключён R190, статус 🟡.

Историческое уточнение R197 (2026-10-27): automation run history использует
Authentication и positive numeric actor_user_id. Этот ID сохраняется до первого
audit write/discovery; actor — только raw display snapshot. List/detail repository
queries ограничены automation key/owner ID (+ run ID для detail); child records
читаются после собственного parent. Нет actor.trim/IgnoreCase fallback. Top 20
упорядочены started_at DESC/id DESC. PAGE_DIALOGS/session/CSRF и UI contract сохранены.
Source-only V83 добавляет nullable positive BIGINT + composite index; legacy rows
остаются NULL и скрыты, без исторического backfill. V1–V82/старый index не менялись.
V83 здесь НЕ выполнялась; PostgreSQL/JPA acceptance и отдельная migration перед
согласованным rollout обязательны. Код без колонки не имеет schema/name fallback.
34 suites / 909 tests GREEN; frozen 43 cases: baseline v2 32 failures / 11 controls /
0 errors, те же bytes GREEN. Real security + stateful repository/JDBC и Spring Data
PartTree проверены; новый PostgreSQL/JPA/Flyway suite (8 cases) только скомпилирован.
Текущая машина C:\Users\sushi\Git_H\tg_ref_b24_sup — source/test.
Рабочий экземпляр/Docker C:\Users\SinicinVV\git_h\tg_ref_b24_sup — другая
машина/сессия. R185–R197 не развёртывались отсюда, рабочая БД/данные/schema,
roles/grants/flags/config/providers и checkpoint/rollback R184 не менялись.
Следующий runtime gate — isolated PostgreSQL suites R195–R197, затем отдельное
разрешение на migration/rollout. Остальные S3–S6/policy/AI/bots/fleet gates открыты;
historical S2B исключён R190, общий статус 🟡.

Историческое уточнение R196 (2026-10-27): личные automation credentials
Bitrix24/iiko используют bound Authentication и canonical numeric account ID в
employee_discount_automation_credentials.account.v2; нет trim/lower(name),
v1 fallback или импорта тестовых данных. Account guard/row locks и REQUIRES_NEW
охватывают read/merge/write; CAS/affected count защищают update, duplicate/corrupt
rows дают 409, SQL/transaction failures — 503. Все credential callers передают
Authentication; generic settings CRUD/list/model закрывает namespace даже admin.
Registry keys используют account ID + SHA-256 URL-key, не lossy name/profile slug.
31 suites / 862 tests GREEN; 56 frozen cases: baseline v6 53 failures / 3 controls /
0 errors, те же bytes GREEN. Stateful JDBC/transaction и loopback HTTP adapters
не доказывают PostgreSQL locking/rollback или live/fleet acceptance.
Текущая машина — C:\Users\sushi\Git_H\tg_ref_b24_sup, source/test checkout.
Стенд/Docker — на другой машине C:\Users\SinicinVV\git_h\tg_ref_b24_sup;
здесь daemon недоступен. R195 runtime probe — исторический факт другой сессии.
R185–R196 не развёртывались отсюда, рабочие данные/БД/роли/config/providers,
checkpoint/rollback R184 не менялись. Следующие конкретные проверки — отдельный
PostgreSQL для credentials и ID-bound automation history (ещё ActorIgnoreCase).
Остальные S3–S6/policy/AI/bots/fleet gates открыты; historical S2B не возвращается.
Статус — 🟡.

Историческое уточнение R195 (2026-10-27): personal UI/triage preferences
получают Authentication и хранятся по positive numeric ID в
ui_preferences.account.v2, без trim/lower(name) и legacy auto-import.
Account ID/exact raw username/status проверяются под row lock; транзакционные
read/merge/write сериализуют UI/triage saves, сохраняют частичный merge и UTC.
Duplicate/corrupt rows дают 409, ошибки SQL-чтения/записи — 503/rollback, не success.
Общий settings API/model исключает весь private namespace ui_preferences.*;
generic CRUD не обходит personal ownership даже для admin. Unbound optional
bootstrap/workflow даёт пустые defaults, не доступ к чужим настройкам.
Старые v1/shared-config записи не удалены; после будущего rollout defaults
без автоматического переноса соответствуют R190. Новых migrations нет.
30 suites / 704 tests GREEN; frozen 32 MVC cases: baseline v2 26 failures /
6 controls / 0 errors, те же bytes GREEN; 14 PostgreSQL cases включают реальные
concurrent saves и rollback. Workspace integration проверен одним методом.
Это не вся workspace identity, не новая global/business policy и не live
browser/fleet/JDBC session-store приёмка.

Сессия здесь на машине тестового стенда. Read-only probe: web 9fb4a33fd8a4,
R184 8310d1f7f0001314abdc60e52516e563168bfee1, healthy/restart 0.
R185–R195 не развёрнуты; данные/схема БД стенда, grants/flags/config/providers,
R184 checkpoint и rollback не менялись. Изолированные Maven/Testcontainers
не являются rollout; существующая Flyway chain запускалась только в тестовой БД.
Нет migrations/backfill/reset на стенде, staging/commit/push.
Следующий identity-кандидат — automation credentials и их rotation registry;
остаются scoped data/picker/bot/AI paths, manual/global/system policy и сквозная
приёмка/первый production S6. Historical S2B снят R190. Статус — 🟡.

Историческое уточнение R194 (2026-10-27): self workforce check-in использует
только ID-bound Authentication и exact stored username. Конфигурация смены
читается по numeric ID с row/status guards; conditional INSERT проверяет
identity/account/workforce status ещё раз в statement. Нет переноса смены
или уведомления к trim/case-fold соседу, обязательный check-in не пропускается
при ошибке account/workforce read. Opt-in, расписание и идемпотентность сохранены.
16 suites / 598 tests GREEN: 36 frozen MVC/security cases (baseline v6:
23 failures / 13 controls / 0 errors), 20 PostgreSQL workforce cases и
login/session/password/admin/activity/channel regressions. Девять SQL cases новые.
UI source contract обновляет только точную ожидаемую R187 asset version.
Schema/grants/flags/serialized principal не менялись. Это не новая общая
recipient/global/business policy и не сериализация всех account/config mutations.

Здесь текущая source/test сессия C:\Users\SinicinVV\git_h\tg_ref_b24_sup на
машине тестового стенда. Read-only probe подтвердил прежний R184 panel-web
9fb4a33fd8a4, healthy; R185–R194 не развёртывались. Изолированные Docker Maven/
PostgreSQL tests не меняют данные/схему БД стенда и не являются rollout.
Migration/backfill/reset/flags/config/provider sends, staging/commit/push не
выполнялись. Следующие gates: personal/triage preferences по ID, остальные
S3–S5 paths/policies, fleet/сквозная приёмка и первый production S6.
Historical S2B снят R190; runtime/rollback R184 сохраняются. Статус — 🟡.

Историческое уточнение R193 (2026-10-27): last_portal_activity_at обновляется
только по positive numeric ID из authenticated PanelUserPrincipal и exact raw
username. Нет case-fold fan-out, trim или unbound username fallback. Session
refresh/revoke R189 остаётся перед activity; timestamp UTC и best-effort/cache/
retry semantics сохранены, новых grants/schema/flags нет.
17 Maven suites / 727 tests GREEN; frozen 29 cases на baseline v1 —
10 failures / 19 controls / 0 errors, те же bytes GREEN. Real login/security/
session/remember-me/activity filters с mocked JDBC: collisions, rename/delete
races, revoke/read errors, activity retry/cache, raw keys и long IDs проверены.
Здесь source/test checkout C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочие
проект/Docker тестового стенда на другой машине/в другой сессии
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Baseline R192 23da82a09 сверён по
parent/subject/exact 7 files/content. R184 checkpoint сохранён точно;
R185–R193 здесь не развёрнуты. Runtime/БД/данные/flags не менялись;
Browser/Node/Docker/live DB/HTTP/providers не запускались. Grant commit/push
до 2026-10-27 21:00 UTC; apply и publisher разделены. Historical S2B снят R190;
остаются ID consumers (в том числе workforce/preferences), S3–S5 и первый
production запуск S6. Это не общая изоляция online/notification recipients. Статус — 🟡.

Историческое уточнение R192 (2026-10-27): AuthManagementApiController берёт
self/current user ID из authenticated PanelUserPrincipal, а field permissions
non-superuser читает по этому ID с exact username/status/row-count guards.
Нет переноса self/edit прав к case-fold соседу; self delete/block guards
сохраняют собственный ID. Avatar file cleanup требует разрешённого photo UPDATE.
Существующие page/global admin/portal/field policies сохранены; полная business
изоляция и атомарность всех admin CRUD этим шагом не объявляются закрытыми.
16 Maven suites / 698 tests GREEN; frozen 63 cases на baseline v4 —
49 failures / 14 controls / 0 errors, те же bytes GREEN. Real login/security/
method security/CSRF с mocked JDBC/config/file adapters, без working DB или browser.
Здесь source/test checkout C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочие
проект/Docker тестового стенда на другой машине/в другой сессии
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Baseline R191 8946949ea сверён по
parent/subject/exact 9 files/content. R184 checkpoint сохранён точно;
R185–R192 здесь не развёрнуты. Runtime/БД/flags/данные не менялись;
Browser/Node/Docker/live DB/HTTP/providers не запускались. Grant commit/push
до 2026-10-27 21:00 UTC; apply и publisher разделены. Historical S2B снят R190;
остаются новые scoped data flows/S3–S5 и первый production запуск S6. Статус — 🟡.

Историческое уточнение R191 (2026-10-27): /profile/password использует только
numeric ID из authenticated PanelUserPrincipal, с exact stored username и
current enabled/blocked checks. Одна null-safe CAS запись сравнивает исходные
password/password_hash и доступные status columns; race возвращает 409,
соседние case-fold аккаунты не изменяются. Legacy/unbound identity не получает
username fallback. Существующие password validation/current-password/CSRF guards
сохранены, UI preferences и общая revocation policy вне этого scope.
15 Maven suites / 635 tests GREEN; frozen 35 cases на baseline v3 —
18 failures / 17 controls / 0 errors, те же bytes GREEN. Stateful mocked JDBC
с real security/encoder — не PostgreSQL concurrency или browser acceptance.
Здесь source/test checkout C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочие
проект/Docker тестового стенда на другой машине/в другой сессии
C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Baseline R190 96878bd4b сверён по
parent/subject/exact 10 files/content. R184 checkpoint сохранён точно;
R185–R191 здесь не развёрнуты. Runtime/БД/flags не менялись, Browser/Node/Docker/
live DB/HTTP/providers не запускались. Исторический S2B снят R190; остаются
новые scoped data flows и первый production запуск. Grant commit/push до
2026-10-27 21:00 UTC; source apply отделён от publisher. Статус 01-278 — 🟡.

Историческое уточнение R190 (2026-10-27): здесь source/test checkout
C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочие проект/Docker тестового стенда
на другой машине/в другой сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup.
R189 a4d263eb3 сверён по parent/subject/exact 10 files/content. R190: первичный
login/remember-me принимает только одну case-insensitive account row, без
trim и выбора по регистру/порядку. Коллизия отклоняется до чтения прав;
открытая ID-bound сессия R189 не переходит на соседний аккаунт.
14 Maven suites / 600 tests GREEN; frozen 18 cases на baseline —
9 failures / 9 controls / 0 errors, те же bytes GREEN. JDBC mocked,
Browser/Node/Docker/live DB/HTTP/providers здесь не запускались. Checkpoint R184
сохранён точно как evidence тестового стенда; runtime/БД не изменены.
R185–R190 здесь не развёрнуты. Grant commit/push до 2026-10-27 21:00 UTC;
source apply и expiry-guarded publisher разделены. Исторический S2B снят,
остаются новые data flows/S3–S5, будущие product policies и первый запуск S6.
Правило входа/сессий: [08-panel-account-credentials](08-panel-account-credentials.md).

Историческое уточнение R189 (2026-10-27): рабочие проект и Docker остаются на
другой машине/в другой сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Здесь
source/test checkout C:\Users\sushi\Git_H\tg_ref_b24_sup: Maven MockMvc с
настоящими login/security/method security и MockHttpSession, только JDBC mocked.
R188 1ccdfc4c5 сверён по parent/subject/exact 7 files/content. 13 suites / 582
tests GREEN; новые frozen 32 cases на baseline — 24 failures / 8 controls / 0 errors.
Проверены refresh/revoke прав в той же сессии, numeric identity, статус/rename/
username reuse, строгий отказ при read failure, сериализация, public/CSRF и прежние
login/channel guards. Browser/Node/Docker/live DB/HTTP/providers не запускались.
Checkpoint R184 и пользовательский start/stop одного бота сохранены точно;
новых runtime probes/restart/deploy/migration/backfill/config/flags/данных нет.
R185–R189 здесь не развёрнуты. Grant commit/push до 2026-10-27 21:00 UTC
сохраняется; source apply и expiry-guarded publisher разделены. Статус 01-278 — 🟡.

R189 привязывает DB-backed principal к numeric account ID. Каждый защищённый
запрос проверяет ID + точный stored username, active status и текущие direct/role
authorities до controller/service. Права обновляются в том же запросе и явно
сохраняются в session context; старый/переименованный/удалённый аккаунт не получает
права нового пользователя с тем же логином. Legacy principal без ID требует
повторного входа. Read failure — 503 без старых прав, public routes независимы.
Подробнее и rollout/rollback compatibility нового serialized principal:
[08-panel-account-credentials](08-panel-account-credentials.md).
Это next-request guard, не cluster-wide deletion или полная S6 acceptance;
неоднозначный первичный login lookup и остальные legacy/S2B/S3–S6 gates открыты.

Историческое уточнение R188 (2026-10-27): рабочие проект и Docker остаются на
другой машине/в другой сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Здесь
source/test checkout C:\Users\sushi\Git_H\tg_ref_b24_sup: Maven MockMvc с
настоящими security/DAO/encoder/session/remember-me и Thymeleaf, JDBC mocked.
R187 d8a68ca80 сверён по parent/subject/exact 20 files/content. 12 suites / 550
tests GREEN; новые frozen 37 cases на baseline — 16 failures / 21 controls /
0 errors. Проверены точный логин и собственные authorities при похожих аккаунтах
с одним паролем, отказы, настоящая CSRF cookie/form token, session ID, logout,
remember-me restore/disable/tamper и прежние channel/credential/permission guards.
Browser/Node/Docker/live DB/HTTP/providers в R188 не запускались. Checkpoint R184
и пользовательский start/stop одного бота сохранены точно; новых runtime probes
или рабочего restart/deploy/migration/backfill/config/flags нет. R185–R188 здесь
не развёрнуты. Commit/push grant до 2026-10-27 21:00 UTC сохраняется; source
apply и expiry-guarded publisher разделены. Общий статус 01-278 — 🟡.

R188 сохраняет exact username/password уже в POST /login, до DAO: стандартный
filter больше не создаёт trim alias. Политика R187 нормализует только новые
и явно изменяемые логины, не вход или существующие account keys. Прежние
case-insensitive lookup, authority loading и framework session/remember-me
остаются; новое чтение authority/revoke уже открытых сессий не добавляется.
Подробнее: [08-panel-account-credentials](08-panel-account-credentials.md).
Другие legacy consumers, S2B и worker/fleet/S6 gates остаются открыты.

Историческое уточнение R187 (2026-10-27): рабочие проект и Docker остаются на
другой машине/в другой сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Здесь
source/test checkout C:\Users\sushi\Git_H\tg_ref_b24_sup: изолированные Maven
MVC/mock/security/Thymeleaf и browser/Node fixtures. R186 9b7bd6f6b проверен по
parent/subject/exact 9 files/content. 255 Java + 76 real DOM browser cases и
110 scoped Node checks GREEN. Полный Node run содержит один прежний R53 notes
fixture failure, отдельно повторённый на неизменённых baseline inputs.
Docker/live DB/HTTP/providers не запускались. Checkpoint R184 и пользовательский
start/stop одного бота сохранены точно, без новых runtime probes. Рабочие
restart/deploy/migration/backfill/config/flags и массовые изменения логинов
не выполнялись. R185–R187 здесь не развёрнуты. Grant commit/push до
2026-10-27 21:00 UTC сохраняется; source apply и publisher разделены.

Политика R187: новые/явно изменяемые логины — ASCII латиница, цифры и `. _ - @`,
обычные крайние пробелы удаляются; внутренние/другие символы отклоняются.
Старые account keys и grants не нормализуются при сохранении других полей.
`lower(trim(username))` допустим только для консервативного отказа в коллизии
новой записи/переименования, не для выбора identity или authority. Пароли
передаются точно, включая пробелы; общая write policy и подсказки описаны в
[08-panel-account-credentials](08-panel-account-credentials.md).
UserLastActivityFilter больше не trim'ит principal; прежние SQL case folding,
auth/blank guards и best-effort поведение сохранены. Это не миграция старых
логинов и не разрешение ослаблять exact-key guards других consumers.
Worker/fleet/S6 acceptance по-прежнему открыта, общий статус 01-278 — 🟡.

Историческое уточнение R186 (2026-10-27): рабочие проект и Docker остаются на
другой машине/в другой сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Здесь
source/test checkout C:\Users\sushi\Git_H\tg_ref_b24_sup: только изолированные
browser/Node fixtures детальной карточки инцидента. R185 3e5ceba37 проверен по
parent/subject/exact files/content. 203 browser cases + 110 scoped Node checks
GREEN; один прежний R53 notes fixture failure в полном Node run отдельно
повторён на неизменённых baseline inputs. Java/Docker/DB/live HTTP/providers
не запускались. Checkpoint R184 и сообщение пользователя о start/stop одного
бота сохранены из документации, без новых runtime probes. R185/R186 не
развёрнуты здесь; нет рабочего restart/deploy/migration/backfill/config/flags.
Grant commit/push до 2026-10-27 21:00 UTC сохраняется; source apply и publisher
разделены. Общая worker/fleet/S6 acceptance не выводится из UI fixtures.

Историческое уточнение R185 (2026-10-27): source/test checkout находится на
машине C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий проект и Docker — на другой
машине/в другой сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Из репозитория
прочитаны checkpoint panel-web R184 (8310d1f7f) и сообщение пользователя об
успешном start/stop одного бота. Это сохранённые runtime evidence другой сессии,
не новые probes здесь и не полная приёмка S6. R185 — только source и isolated
browser/Node fixtures создания инцидента: 117 browser cases + 110 scoped Node
checks GREEN; один прежний R53 notes fixture failure в полном Node run отдельно
повторён на неизменённых baseline inputs. Java/Docker/DB не запускались. Рабочие
revision/config/flags/worker/bot fleet не менялись, R185 не развёрнут. Grant
commit/push до 2026-10-27 21:00 UTC сохраняется; source apply/publisher разделены.

Историческое уточнение R184 (2026-10-27): рабочие проект и Docker остаются на
другой машине/в другой сессии. Здесь source и изолированные MVC/security/Thymeleaf
tests; Docker/PostgreSQL/browser/Node не запускались. R183 5aa3d5e46 проверен
по parent/subject/files/content. Grant commit/push до 27 октября 21:00 UTC
сохраняется; source apply и expiry-guarded publisher разделены. Рабочие
restart/deploy/migration/backfill/config/flags/боты не затронуты.

Историческое уточнение R183 (2026-10-27): рабочие проект и Docker находятся на
другой машине/в другой сессии. Здесь source и изолированные Maven service/MVC
fixtures. Read-only local docker version probe: daemon недоступен; Docker и
PostgreSQL tests не выполнялись. R182 ae325d000 проверен по parent/subject/files/
content. Grant commit/push до 27 октября 21:00 UTC сохраняется; source apply и
expiry-guarded publisher разделены. Рабочие restart/deploy/migration/backfill/
config/flags не выполнялись, рабочая acceptance неизвестна.

Историческое уточнение R182 (2026-10-27): рабочие проект и Docker находятся на
другой машине/в другой сессии. Здесь source и изолированные Maven unit/MVC fixtures;
Docker/browser/Node не запускались. R181 ad2b0af6b проверен по parent/subject/files/
content. Новый grant commit/push действует до 27 октября 21:00 UTC; source apply
и expiry-guarded publisher разделены. Рабочие restart/deploy/migration/backfill/
config/flags не выполнялись; рабочая acceptance неизвестна.

Историческое уточнение R181 (2026-10-26): рабочие проект и Docker остаются на
другой машине/в другой сессии. Здесь source и изолированные Maven MVC/service
fixtures; Docker/browser/Node в R181 не запускались. R180 f8a8367f3 сверён по
parent/subject/files/content. Пользователь явно разрешил commit/push до конца
26 октября Moscow (21:00 UTC); source apply и expiry-guarded publisher разделены.
Рабочие restart/deploy/migration/backfill/config/flags не выполнялись.

Историческое уточнение R180 (2026-10-26): рабочие проект и Docker остаются на
другой машине/в другой сессии. Здесь только source и изолированные browser/Node
fixtures; Java/Docker в R180 не запускались. R179 b92c1aa26 сверён по parent,
subject/files/content. Grant commit/push — до 26 октября 21:00 UTC; source apply
и publisher разделены. Рабочие restart/deploy/migration/backfill/config/flags
не выполнялись; рабочая acceptance неизвестна.

Историческое уточнение R179 (2026-10-26): рабочие проект и Docker остаются на
другой машине/в другой сессии. Здесь только source и изолированные Maven
unit/service fixtures; Docker/browser/Node в R179 не запускались. Новый baseline
a687d6b73 содержит независимые incident/pagination commits после проверенного
R178. Grant commit/push действует до 26 октября 21:00 UTC; source apply и Git
publisher разделены. Рабочие restart/deploy/migration/backfill/config/flags
не выполнялись, рабочая acceptance неизвестна.

Историческое уточнение R178 (2026-10-26): рабочие проект и Docker остаются на
другой машине/в другой сессии. Здесь source и изолированные browser/Node fixtures;
Docker/Java tests в R178 не запускались. R177 dd41dea3a опубликован и проверен
по parent/files/content. Grant commit/push на 26 октября действует до
2026-10-26 21:00 UTC; source apply и Git publisher разделены. Рабочие
restart/deploy/migration/backfill/config/flags не выполнялись.

Историческое уточнение R177 (2026-10-26): рабочие проект и Docker находятся на
другой машине/в другой сессии; здесь source/test и изолированные Testcontainers.
R176 e10cd9706 уже опубликован пользователем и сверён по parent/files/content.
Пользователь продлил commit/push на 2026-10-26 Moscow, expiry 2026-10-26 21:00 UTC.
Source apply и отдельный Git publisher имеют exact baseline/content/file guards;
publisher дополнительно проверяет срок и actual remote. Рабочие restart/deploy/
migration/backfill/config/flags не разрешались и не выполнялись.

Историческое уточнение R176 (2026-10-26): рабочий экземпляр и Docker остаются на
другой машине/в другой сессии; здесь source/test checkout и изолированные
Testcontainers. Git grant 23 октября истёк; нового разрешения на commit/push нет.
R176 заканчивается только source apply после GREEN, без staging/commit/push
и без рабочего restart/deploy/migration/backfill/config/flags.

Историческое уточнение R175 (2026-10-23): рабочий экземпляр и Docker остаются на
другой машине/в другой сессии; здесь source/test checkout и только изолированные
Testcontainers. Пользователь явно продлил commit/push на 2026-10-23 Moscow,
expiry 2026-10-24 00:00 +03 (2026-10-23 21:00 UTC). Source apply и Git publisher
разделены с exact baseline/content/files/remote и expiry guards. Это разрешение
не включает рабочие restart/deploy/migration/backfill/config/flags.


Историческое уточнение R168–R171 (2026-10-22): рабочий экземпляр и Docker находятся
на другой машине и в другой сессии относительно этой source/test-сессии.
Здесь разрешён только изолированный Testcontainers; рабочие stack/revision/
DB migrations/flags не проверялись и не менялись. Одинаковый путь checkout
не идентифицирует машину. Новый явный grant текущего пользователя разрешает
commit/push только 2026-10-22 Europe/Moscow, до 2026-10-22 21:00 UTC;
не включает рабочие restart/deploy/migration/backfill/config/flags.
Source apply и Git publisher разделены, с exact content/file и expiry guards.

Историческое уточнение runtime-сессии R163–R167: рабочий проект и Docker находятся
на машине текущей сессии; пользователь разрешил restart Docker/контейнеров.
В R167 заново проверены Docker Server 29.7.2 и healthy web/bot/worker/direct/
PostgreSQL/RabbitMQ/Redis. Это health, не функциональная приёмка и не проверка
рабочих revision/DB migrations/flags. Restart/rebuild/deploy/migration/backfill/
config/flags не выполнялись. Restart не разрешает migration при startup.
Git grant R160 из другой сессии не наследуется: staging/commit/push отсутствуют.

Историческое уточнение R160 (2026-10-22): рабочие Docker и проект находятся на
другой локальной машине, в другой сессии агента; здесь source/test checkout.
Наблюдения R143/R155–R159 о машине/health относятся к их runtime-сессии и не
переносятся сюда. Рабочие revision/DB migrations/flags/acceptance здесь неизвестны.
Рабочий stack не инспектируется и не изменяется; Docker допустим только для
изолированных Testcontainers. Одинаковый путь checkout не идентифицирует машину.

Пользователь R160 явно разрешил commit/push без повторного подтверждения только
2026-10-22 Europe/Moscow: expiry 2026-10-23 00:00 +03 (2026-10-22 21:00 UTC).
Новый grant текущей сессии не наследуется из прошлых записей. Source apply и Git
publisher разделены; publisher проверяет срок и exact file/content set. Разрешение
не включает рабочий deploy/restart/migration/backfill/config/flags.

Историческое уточнение другой сессии:

Уточнение R137 (2026-10-19): по уточнению пользователя рабочий Docker
и весь запущенный проект находятся на другой локальной машине, в другой сессии
агента относительно source/test-сессии R137. Docker/image observations R135/R136
относятся к той runtime-сессии; слова «на этой машине» из неё не переносятся в
текущую. Runtime revision/DB/flags/rollback отсюда не проверены. Местный Docker
допустим для изолированных Testcontainers и не доказывает состояние рабочего
экземпляра. Одинаковый путь checkout не идентифицирует машину или сессию.

## Зафиксированные продуктовые решения

- Бизнес — самостоятельная доменная сущность, а не iiko-объект, юридическое
  лицо, канал или текстовое поле локации.
- `Суши` и `Блины` создаются как обычные предустановленные записи с устойчивыми
  `code`; новое направление не наследует их обращения, вопросы и шаблоны.
- У одного сотрудника одна учётная запись `users.id`; доступ может быть к
  одному, нескольким или всем бизнесам.
- Режим «Все доступные» объединяет только уже разрешённые business IDs и не
  расширяет полномочия.
- Изоляция проверяется на сервере и в SQL read/write paths. UI filter,
  выбранный browser-local context либо название бизнеса не являются authority.
- PostgreSQL остаётся единственным relational source of truth, а Flyway и
  canonical business schema принадлежат `spring-panel`.
- Для ручного бизнес-обращения без бота/интеграции создаётся локальный клиент с новым
  ID, принадлежащий ровно одному выбранному бизнесу. Это не аккаунт `users.id`
  и не клиент внешнего канала. Ручной путь использует отрицательный `user_id`,
  выделенный отдельной последовательностью; ноль недопустим. Новый `ticket`
  получает явный `business_id` до записи корня, без вывода из legacy текста.
- В общем канале человек явно выбирает бизнес **или вариант «Без бизнеса» до первого вопроса**.
  `business_id` не выводится из названия локации, текста, последнего выбора
  оператора или факта привязки канала. Пока выбора нет, вопросник не начинается;
  бизнес-ответ сверяется с активными привязками канала, а выбранный scope
  сохраняется с сессией/обращением и переносится через transport/replay.

## Свежий baseline и проверенные факты

Аудит выполнен на `main`/`origin/main`
`08c1bd0add49cd39d9d23dd6240d27f985607f23`; рабочее дерево до S0 было чистым.
Предыдущий commit `da969404009276263c9b51bc362ce233ea6a7cf0` не содержит
мультибизнесовой реализации.

Подтверждено чтением текущего source и PostgreSQL migration chain V1–V50:

- `tickets` имеет ключ `(user_id, ticket_id)`, но `messages.ticket_id` пока
  уникален, а большинство дочерних read/write paths принимает один `ticket_id`.
  В частности, так работают `DialogLookupReadService`, bot runtime read/write,
  `UiEventOutboxWatcher` и часть task links. Новая модель не вправе считать
  строковый `ticket_id` вечным глобальным ключом.
- `ConversationTicketCreationIngestionService` в одной транзакции создаёт
  root `messages`, `tickets`, историю, attributes, active/span records; в
  payload есть только legacy `business` string. Поэтому scope обязан быть
  определён до создания root ticket/message и сохранён в event envelope.
- `DialogLocationBusinessApiController` меняет `messages.business` по одному
  `ticket_id`, а `DialogAuthorizationService` проверяет `PAGE_DIALOGS` и
  глобальные action permissions. Ни один из них пока не проверяет business
  scope.
- Аутентификация использует `users.id`; `user_authorities` и `roles` дают
  глобальные page authorities. `panel_users` не является отдельным
  каноническим account store. Business-role нельзя выводить из `ROLE_ADMIN` или
  из права на страницу.
- `channels.question_template_id` и `channels.questions_cfg` существуют,
  однако runtime берёт общую canonical `bot_settings` конфигурацию. В
  `SettingsParameterService` и `IikoDepartmentLocationCatalogService` всё ещё
  есть текстовые aliases и специальные имена двух legacy-бизнесов. Это
  compatibility input, но не будущий business identity.
- `ticket_attributes` хранит answers по `(ticket_id, question_id)`;
  `tasks`/`projects`/boards и `notifications` не имеют business scope;
  `ui_event_outbox` несёт `ticket_id` и `channel_id` без business ID.
- Ранее зафиксированный read-only data audit остаётся входным evidence: 47
  tickets и root messages, 619 history, 108 attachment metadata, 44 непустых
  legacy business labels, 3 пустых labels, 2 mixed channels из 3 и 3 history
  channel mismatch. Значения aliases не переносятся в этот открытый документ.

## Каноническая модель данных

### Бизнес, участник и полномочия

```text
users 1 ──< business_memberships >── 1 businesses
users 1 ──< business_access_grants
businesses 1 ──< business_channel_bindings >── 1 channels
businesses 1 ──< business_locations
businesses 1 ──< tickets
```

### Предлагаемые новые сущности

| Сущность | Обязательные поля и ограничения | Назначение |
| --- | --- | --- |
| `businesses` | identity PK, immutable unique `code`, display name, `status` (`active`/`archived`), timestamps, optional JSON settings | Единственный canonical business identity. Предустановленные записи получают codes `sushi` и `bliny`; display names не используются как ключи. |
| `business_memberships` | `user_id` FK `users`, `business_id` FK, `role_code`, `permission_overrides`, active state, unique `(user_id, business_id)` | Явный доступ и функциональная роль пользователя в одном бизнесе. |
| `business_access_grants` | `user_id` FK, `scope=ALL_BUSINESSES`, `role_code`, active state, один active grant данного scope на пользователя | Динамический доступ ко всем текущим и будущим businesses. Это не `ROLE_ADMIN` и не доступ к global/system data. |
| `business_channel_bindings` | `channel_id` FK, `business_id` FK, `resolution_mode`, active state, unique `(channel_id, business_id)` | Разрешает одному каналу обслуживать несколько бизнесов без неявного default. |
| `business_locations` | identity PK, `business_id` FK, display fields/status, unique key только внутри business | Позволяет одноимённым локациям существовать в разных бизнесах. Маппинг iiko хранится отдельно. |
| `external_location_mappings` | `business_location_id` FK, source type, external ID, unique `(source_type, external_id)` | Привязка iiko/CRM и иных источников к существующей локации, а не создание business identity из внешнего значения. |

`role_code` определяет только функциональные capability в business context
(`VIEW`, `OPERATE`, `MANAGE`, `CONFIGURE`); его точный vocabulary и relation с
глобальными page permissions должны быть утверждены до S1. Effective permission
всегда равен пересечению page authority, membership/all-business grant и
business capability. `ALL_BUSINESSES` добавляет data scope и базовую
business-role, но не system-admin capability.

### Tickets и дочерние записи

- Бизнес-обращение имеет canonical owner `tickets.business_id`. `NULL` в этом
  поле сам по себе означает только legacy/unresolved, но не подтверждённый
  отказ от бизнес-привязки. Намеренно общее обращение имеет отдельный признак
  `tickets.is_general=TRUE`, `business_id=NULL` и сохранённый источник
  `USER_SELECTION` (общий канал) либо `GENERAL_CHANNEL` (явно настроенный
  канал без бизнесов). Неопределённый scope нового ticket — отказ/ручная очередь,
  а не запись с guessed default.
- `messages.business` остаётся legacy display/compatibility metadata и не
  участвует в авторизации. Нельзя удалять или переписывать это поле в S1.
- S2A фиксирует новый public route key как отдельный immutable
  `ticket_public_id`: 32 lowercase hexadecimal symbols, уникальный только для
  non-null значений. Legacy routes не расширяются и остаются в подтверждённом
  compatibility window; internal callers без public ID обязаны нести полный
  `(user_id, ticket_id)` tuple. Bare `ticket_id` не считается глобальным ключом.
- S2A также предоставляет только read-only preview legacy assignment: он
  сравнивает `messages.business` с `business_legacy_aliases.alias` побайтно,
  без trim/lowercase или иных догадок. `UNRESOLVED`, duplicate mapping и
  conflict остаются отдельными состояниями; сервис не меняет `tickets` и не
  является backfill.
- Child records получают business scope через canonical ticket. Там, где
  таблица хранит только `ticket_id`, этап S2 обязан добавить/вывести достаточный
  tuple или public reference до включения strict filtering. `chat_history`
  принадлежит ticket, а не своему историческому `channel_id`.
- Изменение business у существующего ticket — отдельная audit-only privileged
  операция после проверки зависимостей. Смена UI filter не меняет owner data.

### Каналы и вопросники

`business_channel_bindings.resolution_mode` допускает только:

- `SINGLE_BUSINESS` — канал связан с одним бизнесом;
- `EXPLICIT_SELECTION` — пользователь сначала выбирает бизнес;
- `VERIFIED_EXTERNAL_MAPPING` — business получен из подтверждённого external
  mapping;
- `VERIFIED_LOCATION_MAPPING` — business получен из однозначного approved
  mapping локации.

При нескольких кандидатах, отсутствии кандидата или conflict runtime должен
создать управляемое `UNRESOLVED` состояние/ручное назначение и не начинать
question flow. Business, template version и selection source сохраняются с
ticket до конца retries, edits, feedback и replay.

Вариант `GENERAL` — самостоятельный сценарий без бизнеса, а не разновидность
`UNRESOLVED` или `GLOBAL_SYSTEM`. В общем канале он выбирается явно; в
предварительно настроенном канале только для общих обращений выбор не нужен.
Для него используется отдельный от
business-scoped шаблон из canonical `bot_settings.question_templates`:
произвольный набор вопросов, типов и порядок редактируются в редакторе
вопросников; бизнес-локации и бизнес-presets не подмешиваются. В общем канале
вариант «Без бизнеса» должен выбирать этот шаблон до первого вопроса.
Персоналу такие обращения нужны в отдельной явно разрешённой очереди; grant
`ALL_BUSINESSES` сам по себе не разрешает их читать или изменять.

Read-only очередь GENERAL использует отдельный явный grant
`general_ticket_read_grants` на канонический `users.id`, без автоматических
назначений. Доступ требует одновременно `PAGE_DIALOGS`, единственную canonical
учётную запись с enabled=TRUE/is_blocked=FALSE и активный READ grant. R134
проверяет уникальность case-insensitive username также при чтении, не выбирая
произвольную enabled запись из нескольких. `ROLE_ADMIN`, business membership и
`ALL_BUSINESSES` не заменяют этот grant. Установка/отзыв доступны только
системному администратору с `PAGE_SETTINGS` и CSRF. READ не разрешает отвечать,
менять статус либо выполнять другие write operations.

R135 вводит отдельный opt-in general_ticket_operate_grants: OPERATE действует
только вместе с current READ, не наследуется из ROLE_ADMIN/ALL_BUSINESSES и
никому не назначается миграцией. Системный admin с PAGE_SETTINGS/CSRF может
выдать его текущему READ account; отзыв READ атомарно отзывает OPERATE, возврат
READ сам по себе работу не восстанавливает. Новый write scope этого этапа —
только GENERAL triage pending↔open. Это не resolve/reopen/close, не назначение
ответственного и не отправка клиенту; legacy lifecycle/feedback не вызываются.

Страница /general-dialog-work/{publicId} требует PAGE_DIALOGS и current READ;
POST также OPERATE и настоящий CSRF. Public locator сервером разрешается в
expected GENERAL tuple/generation; raw owner не экспортируется в model.
V65 добавляет general_work_revision, автоматически увеличиваемую PostgreSQL
при смене GENERAL status/owner/scope даже другим writer, и запрещает её ручную
правку. CAS проверяет revision/status, exact owner, unique raw ID, current
account/READ/OPERATE в финальном UPDATE. В одном SQL атомарны status, immutable
metadata audit и V61 GENERAL outbox ticket_status_changed; ошибка любого INSERT
откатывает всё. Новых grants, классификации/backfill старых owner или runtime
flags миграция не создаёт. Readiness требует V65 schema до объявления READY.
На этапе R135 GENERAL reply/edit/delete/resolve/reopen/assignment оставались
PENDING; добавленный в R136 текстовый reply path описан ниже.

R136 добавляет отдельный текстовый reply path для GENERAL pending/open.
PAGE_DIALOGS, current READ + OPERATE и CSRF обязательны. Уникальный request key
привязан к frozen tuple/public ID, actor, revision, тексту и SHA-256 destination
snapshot; его повтор не создаёт вторую отправку.
Форма несёт fingerprint исходного destination; перенастройка канала до
POST требует перечитать форму, а не принять новый destination молча.
QUEUED→SENDING фиксируется до сети. Отправку инициирует только этот POST;
фонового sender/автоматического replay очереди в R136 нет.
После claim повторно проверяются root message/channel, account/grants,
revision и destination; блокировки этих rows удерживаются до результата.
Непосредственно перед HTTP send транспорт снова вызывает guard. Неизвестная
платформа не превращается в Telegram; credentials в outbox не сохраняются.

SENT и canonical history/public generation, metadata audit и GENERAL outbox
фиксируются одной транзакцией. При timeout/неясном результате — UNKNOWN;
crash/ошибка сохранения оставляют SENDING/UNKNOWN для ручной сверки, без
автоматического retry и без обещания exactly-once у внешнего провайдера.
Повторный POST того же request key не отправляет SENDING/SENT/UNKNOWN заново.
Очередь/состояния видны только через текущий GENERAL READ и exact public owner.
V66 additive, без backfill/grants. Новая история несёт ticket_public_id;
NULL старых rows не классифицируется автоматически. Reply media/edit/delete,
resolve/feedback, activity/span attribution, reconciliation/retention policy и provider E2E остаются gates.

R137 открывает ручные GENERAL pending/open→closed и closed→pending. Прежний
pending↔open сохранён. Во final SQL действуют те же owner/account/READ/OPERATE,
expected status/revision и новый V67 general_ticket_transition_allowed gate.
Close фиксирует resolved_at/resolved_by/closed_count; reopen очищает resolution,
повышает reopen_count/last_reopen_at и возвращает в pending. V65 увеличивает
revision; старую форму нельзя применить после close/reopen цикла. Status,
metadata/counters, immutable audit и GENERAL outbox атомарны; partial failure
откатывает всё. V67 меняет только allowed transition CHECK, сохраняет старые
records/grants и immutable trigger; readiness требует новую function schema.
Закрытый ticket не принимает новый reply, старый queued reply теряет revision;
reopen не включает retry. Close сериализуется с уже guarded network attempt
через существующие reply row locks. Legacy lifecycle/feedback/assignment,
GENERAL activity/span attribution и reconciliation остаются отдельными scope.

R138 добавляет наблюдаемые интервалы работы GENERAL, без attribution старых
ticket_spans/ticket_active/work_time_total_sec. Только новый manual pending→open
создаёт typed interval полного user/raw/public owner с revision/actor/time
snapshot. При open→pending/closed учитывается время только при совпадении
opened revision с прежним root revision и неубывающих часах. Внешний status/
owner/scope ABA и clock regression исключают интервал из итога (UNCONFIRMED
при чтении, INVALIDATED при следующем переходе того же полного owner).
Разные owner/public generation не наследуют интервалы. Старый open root без
observed start не получает выдуманный duration. Это частичный учёт.

V68 additive: unique OPEN для full tuple, immutable start/identity/terminal
snapshot, sequential close-before-insert helper внутри authorized root CAS.
Root status/metadata/counters/revision, interval, audit и GENERAL outbox атомарны;
failure любого шага откатывает всё. Новых grants/backfill/flags нет; readiness
компилирует table и helper probe WHERE 1=0 без выполнения writer.
Чтение summary/последних 10 повторно требует current READ/account/flags и exact
explicit GENERAL/NULL business unique owner до aggregate/order/limit. Только
COMPLETED входят в FLOOR суммы точных seconds; незавершённый интервал показан
отдельно. UI явно сообщает partial tracking, raw IDs не выдаёт.
Legacy attribution, session/activity heartbeat, assignment, reconciliation/
retention и runtime acceptance на другой машине/в другой сессии остаются gates.

R139 добавляет отдельное ручное GENERAL assignment pending/open: назначение,
смена и снятие ответственного через PAGE_DIALOGS + current READ/OPERATE + CSRF.
Target обязан быть canonical unique enabled/unblocked account с GENERAL
READ+OPERATE. Candidate pool закрыт для READ-only; page authority target по-прежнему
нужна для его входа в «Диалоги», назначение само не выдаёт доступ/grants.
V69 — append-only scoped assignment events с exact user/raw/public tuple,
actor/assignee snapshot и отдельной assignment revision. Новых grants/backfill
и переноса ticket_responsibles нет. Current/history требуют READ и exact owner
до latest/limit; owner/generation не наследуют старое назначение. Утративший
права target показан недоступным, автоматического переназначения нет.

Root FOR UPDATE сериализует assignment CAS с status/owner и другим assignment;
account/grants actor и target удерживаются FOR SHARE до commit. Проверяются
expected work revision, assignment revision, explicit GENERAL и unique raw ID.
Event и GENERAL assignment_changed outbox атомарны; stale/ABA/no-op отказ не
создаёт event. Root status/work revision не меняются, interval/reply не прерываются.
Closed assignment — read-only. Readiness требует V69; missing schema не открывает
legacy fallback. Metadata-only invalidation идёт через отдельный GENERAL
SSE/Redis/current READ path без legacy callbacks. Автоназначение/балансировка,
историческая attribution, bell/служебная доставка и retention остаются gates.

R140 открывает отдельное ручное GENERAL creation без бизнес-привязки и канала.
Нужны PAGE_DIALOGS, current GENERAL READ+OPERATE/account и одновременно default-off
foundation/general-ticket-queue/manual-ticket-creation. Форма не принимает
business/client ID: новый local client из manual_general_clients получает
отрицательный ID общей non-cycling V54 sequence с проверкой занятых namespaces.
Business-local registry остаётся отдельным. Ticket создаётся pending с revision 0,
explicit GENERAL/NULL business и MANUAL source, без legacy spans/active records.
V70 source allowlist сохраняет USER_SELECTION/GENERAL_CHANNEL, допускает MANUAL
только в GENERAL и сохраняет non-general source=NULL. Старые rows не меняются,
новых grants/backfill нет; bot transport allowlist не расширен.

Request key привязан к actor ID/input SHA-256 и immutable exact tuple/public ID.
Same-key запросы сериализуются advisory lock; account/grants удерживаются FOR SHARE
до commit и проверяются в client INSERT. Повтор возвращает прежний public ID
только при current exact GENERAL MANUAL owner/root/registry и том же actor/input;
при owner/generation/source changes, revocation, missing root или collision
нет дубля/нового owner capture. Client/root message/ticket/operator_note history/
request audit/GENERAL ticket_created outbox атомарны. Строковые SQL inputs digest
явно CAST text и проверены в default/unspecified JDBC mode. No provider/AI/legacy
callbacks; interval возникает только при последующем manual переводе в работу.
UI предупреждает о локальном клиенте и отсутствии канала отправки; redirect
содержит только public ID. Readiness требует V70 tables/function. Retention/
reconciliation keys, media/feedback, auto-assignment и live acceptance остаются gates.

R141 добавляет отдельные Telegram EDIT/DELETE только для подтверждённых R136
GENERAL text replies. Нужны PAGE_DIALOGS/current READ+OPERATE/account, CSRF,
foundation/general-ticket-queue и pending/open exact owner с unique raw ID.
Canonical history имеет exact tuple/public generation, sender operator,
operator_message, без attachment; старые/NULL-generation/client/note rows
не классифицируются. Source reply/destination/provider ID и text/timestamps
фиксируются в форме/immutable command V71. Смена actor/input/snapshot/owner/
work revision/channel/config закрывает запрос без recapture. Reply/history
update locks и root/ticket/channel/account/grant share locks сохраняются через
provider call; transport повторяет guard прямо перед HTTP и использует frozen
channel. Старые transport entrypoints остаются совместимыми.

Single QUEUED→SENDING claim не допускает повторного HTTP для key. Подтверждение
provider атомарно меняет canonical text/edited_at либо deleted_at, APPLIED/audit
и metadata GENERAL outbox; исходная отправка сохраняется как audit. Любой
неясный внешний результат — UNKNOWN либо durable SENDING при ошибке closeout;
partial unique index блокирует последующие команды этого source reply. Нет
auto-retry или автоматической reconciliation. Safe guard denial — CANCELLED.
Устаревший content fingerprint не перезаписывает более новое сообщение.
Media, VK/MAX edit/delete, feedback и retention/reconciliation — gates.
V71 не переписывает старые data/grants и не включает flags.

R142 добавляет отдельную multipart/CSRF отправку Telegram GENERAL documents.
PAGE_DIALOGS/current READ+OPERATE/account, foundation/general-ticket-queue,
pending/open, exact tuple/public generation, revision и unique raw ID/root/channel
обязательны; только USER_SELECTION/GENERAL_CHANNEL, не MANUAL local client.
Один файл 1..10 МиБ, caption<=1024 UTF-16 units, безопасное имя<=200 units,
forced application/octet-stream, без inline preview. Raw ID<=255 из-за старого
metadata field; storage key основан на public ID. Actual bounded bytes и SHA-256
сохраняются в immutable V72 intent. Key привязан к actor/input/destination/work
snapshot; единственный QUEUED→SENDING claim, guarded HTTP и frozen payload/channel.
Share locks удерживаются через provider call и closeout. Только positive provider
ID и проверенные key/size/SHA object storage допускают atomic SQL history/
metadata/SENT/audit и metadata-only GENERAL outbox. Неясный результат — UNKNOWN
либо durable SENDING, auto-retry нет; новый key означает новый intent.

Document download требует SENT registry + exact canonical history/generation/
owner/provider и available unique metadata key/name/hash/size. Legacy fallback
исключает document marker ИЛИ registry-linked history даже при порче marker.
Bounded read проверяет actual bytes/size/SHA, затем повторяет current READ/metadata
proof после IO; attachment/octet-stream/nosniff/no-store, mismatch/revocation=404.
Object storage не участвует в SQL rollback, возможен orphan после внешнего успеха.
Immutable SQL payload и external objects требуют отдельной retention/quotas/
reconciliation policy; no auto-cleanup. V72 не меняет старые rows/grants/flags.
Inline media, VK/MAX uploads и media edit/delete остаются gates; live acceptance
на рабочей машине не выполнена.

`/api/general-tickets` и `/general-dialogs` закрыты foundation и отдельным
default-off `multibusiness.general-ticket-queue`. Каждый SQL read повторно
проверяет grant и выбирает только `is_general=TRUE`, `business_id IS NULL` и
канонический public ID. Legacy/unresolved NULL и бизнес-обращения не входят
в эту очередь. История использует composite owner; attributes и attachment
namespace дополнительно требуют однозначности legacy ticket ID. Grant этого
read-only контура не доказывает готовность legacy API, write paths,
глобального поиска/уведомлений и фоновых заданий; их изоляция остаётся gate до rollout.

R134 добавляет отдельный GET /api/general-tickets/events/stream: PAGE_DIALOGS
проверяется на handshake, foundation/general-ticket-queue и live GENERAL READ
проверяются сервисом. Каждый emitter перед отправкой получает отдельный SQL
proof текущего account/grant и frozen tuple/public generation с is_general=TRUE,
business_id=NULL и unique raw ID. Регистр username сопоставляется canonical
case-insensitive, пробелы principal не отбрасываются и не дают чужой grant.
GENERAL не становится допустимым UiEventTicketScope.isValid для legacy callbacks:
отдельный GeneralTicketEventScope создаётся только из полного GENERAL V61 snapshot.

Watcher направляет известные GENERAL event types только в этот realtime, до
initial-message lookup и legacy AI/bell/assignment/support-chat. Cursor проходит
отклонённые/старые rows. Consumer не захватывает owner повторно; смена tuple,
business/kind/generation, удаление, collision либо missing schema закрывают delivery.
Redis использует отдельный topic <existing-ui-topic>:general:v1 и version=1
envelope без текста/media. Старый subscriber не получает этот topic; local mode
применяет те же per-emitter guards. Redis error не открывает local/legacy fallback.
Сами invalidations best-effort, не журнал доставки и не durable event replay.

Браузер получает только ticketPublicId, известный reason и emittedAt в событии
general_tickets_changed; raw tuple/IDs и содержимое диалога не выдаются. Отдельная
GENERAL очередь coalesce-ит обновления, перечитывает список/открытую карточку через
текущие READ API, а reconnect исправляет пропущенные invalidations новым чтением.
Heartbeat повторно проверяет grant/account/flags; при отказе посылается только
general_access_revoked без ticket data, emitter закрывается. UI очищает данные
при отзыве/ошибке stream, отменяет pending refresh и не применяет старые HTTP
ответы. Pagehide закрывает подписку. Сам этап R134 не добавлял migrations/flags.
GENERAL triage/OPERATE, text reply, close/reopen, intervals, assignment, creation, Telegram text edit/delete и documents описаны в R135–R142 выше;
остальные GENERAL write, отдельные bell/внешняя доставка и live Redis/proxy/provider acceptance PENDING.

Основные legacy dialog reads (список/summary, карточка, история, предыдущие
диалоги, участники и client context), ticket-bound `DialogAuthorizationService`
и HTTP downloads `AttachmentService` исключают GENERAL независимо от flags
очереди. `hasUniqueNonGeneralTicket` требует единственного raw-ID кандидата
во всей таблице и явного `is_general=FALSE`. Сначала отфильтровать GENERAL,
а затем считать уникальность нельзя: mixed-scope collision остаётся отказом.
`business_id=NULL,is_general=FALSE` сохраняет legacy/unresolved поведение;
этот guard не проверяет business membership и не заменяет strict scope.
Внутренний `hasUniqueTicket` сохраняет проверку identity для ботов/background.
READ grant GENERAL не открывает старые маршруты и write actions.

Legacy ticket-bound realtime использует тот же non-GENERAL guard: проверка
выполняется до dispatch из UI outbox, до уведомлений/назначений/AI в
`DialogRealtimeEventService`, до публикации и повторно перед доставкой SSE.
Адресный Redis fanout не обходит проверку; `dialogs_changed` и
`dialog_history_changed` требуют точного строкового ticket ID, а любой иной
envelope с `ticketId` тоже проверяется по canonical ticket. Неизвестный marker,
отсутствующая схема, GENERAL и коллизия закрывают legacy delivery. Курсор
outbox продвигается и по отклонённым событиям. Отдельный realtime GENERAL R134
использует описанный выше собственный контракт с действующим READ grant. Системные SSE без
ticket ID сохраняют поведение.

Legacy ticket-bound SSE дополнительно проверяет business READ каждого
получателя непосредственно перед каждой отправкой в emitter, включая
несколько соединений одного пользователя. Локальная доставка и адресный/
broadcast Redis fanout применяют один и тот же guard. Один SQL snapshot
требует точного unique raw-ID, явного non-GENERAL owner и текущего business
READ по общему с notifications predicate: единственный enabled/unblocked
canonical account, active business, membership или ALL_BUSINESSES с READ.
Доверять полям scope/business из envelope нельзя; ROLE_ADMIN не даёт grant.
Отзыв прав не требует переподключения; отказ пропускает ticket event, сохраняя
соединение для остальных разрешённых событий. Ошибка/отсутствие access schema
закрывает ticket delivery. UNRESOLVED с доказуемым public locator совместим
при default-off strict-read; при strict-read-scope=true скрывается.

Перед ticket-bound fanout фиксируется внутренний UiEventTicketScope: точный
canonical tuple, non-null immutable public ID, BUSINESS/UNRESOLVED и business ID.
Snapshot читается одним SQL statement с unique raw-ID/non-GENERAL proof и
передаётся отдельно от browser payload в Redis envelope либо local fallback.
Перед каждым emitter один SQL snapshot сверяет tuple/public ID/scope/business
и текущий READ получателя. Смена owner, пересоздание tuple с другим public ID,
смена business/scope, удаление или raw-ID collision запрещают старую delivery
даже получателю с ALL_BUSINESSES. Consumer не перезахватывает текущий owner
для старого payload. Необязательное поле ticketScope сохраняет чтение старых
system envelopes; ticket events без snapshot, с некорректным snapshot либо
другим payload.ticketId отклоняются. Наличие ticketScope само делает событие
ticket-bound и не позволяет переименованием события обойти guard.

Ticket с NULL/invalid public ID либо отсутствующая locator schema не позволяют
публиковать ticket SSE, включая legacy/UNRESOLVED при strict-read=false:
одного composite tuple недостаточно, чтобы отличить его пересоздание.
Автоматического присвоения public ID/backfill нет. Browser event names/payload
сохраняются; внутренний owner в них не добавляется. Сам transport-only этап
не добавляет migrations/flags.

Persistent DB outbox получает additive V61 snapshot при исходном INSERT:
точный существующий ticket_id, ticket_user_id, nullable ticket_public_id,
ticket_scope_kind и ticket_business_id. Panel и bot writers копируют owner
одним INSERT SELECT с проверкой уникальности raw ID; GENERAL сохраняется
отдельным scope, не превращается в UNRESOLVED. Исторические/старые producer
rows остаются без snapshot; автоматического backfill нет. CHECK запрещает
частичный owner, trigger — изменение snapshot, включая raw ID и присвоение
owner старой строке. FK намеренно отсутствует, snapshot переживает owner.
Readiness требует четыре новые columns; отсутствие V61 не позволяет объявить
runtime READY. Сам этот source-пакет не запускает migration.

Watcher до initial-message lookup и legacy callback сверяет frozen tuple,
public ID, kind/business и текущую unique non-GENERAL identity одним SQL.
GENERAL, старые unclassified rows, NULL locator и устаревший owner закрыты;
typed UNRESOLVED с locator совместим только до strict-read. Курсор проходит
отклонённые события. Initial-message suppression сравнивает полный snapshot,
поэтому события другой generation не подавляют bell нового обращения.
Проверка перед dispatch сама по себе не блокирует owner на время callback.

Bell/SSE ветви callback принимают именно snapshot V61: scoped entrypoints
DialogRealtimeEventService передают его через AlertQueueService до условного
notification INSERT и через UiEventStreamService до fanout/local delivery.
Scope не захватывается заново по raw ID. Notification SQL и recipient child
reads требуют совпадения tuple/public ID/kind/business, non-GENERAL, unique ID;
действующий business READ каждого получателя R122/R123 сохраняется. Пустой или
невалидный expected scope закрывает scoped API, а не включает legacy fallback.
Scoped UNRESOLVED разрешён только до strict-read. Поздний отказ queue может
попробовать fallback recipients лишь с тем же frozen scope и тем же SQL guard.
Scoped incoming callback передаёт исходный V61 owner через отдельные
processScopedIncomingClientMessage facade/flow и notifyScopedOperatorsEscalation
до NotificationService.notifyScopedAiDialogUsers. Условный INSERT проверяет
frozen owner и текущий READ каждого получателя R122/R126 в том же SQL snapshot;
поздняя смена owner/grants не открывает raw fallback, recapture или повторный send.

Это безопасная эскалация оператору; полноценный business-scoped AI ещё PENDING.
V62 добавляет отдельные ticket_ai_scoped_control/state/events с обязательным
immutable tuple/public ID/kind/business. Ключ control/state — public generation
и business (NULL имеет отдельный generated key); новая generation или другой
business не наследует прежние записи. Events immutable, legacy AI tables не
изменяются и не переносятся. Снимок создаётся только условным SQL из current
unique non-GENERAL owner, active business и исходного V61 expected scope.
UNRESOLVED automation совместим только до strict, actor business access из
него не возникает. Отсутствие schema/flags и конфликт snapshot закрывают путь.

Scoped incoming при глобальном ai_agent_enabled=true атомарно записывает
non-processing state escalate_only и event operator_handoff_requested без
текста клиента/media; это запрос handoff, не доказательство доставки. Затем
может уведомить оператора bounded preview исходного текста. ai_disabled из
scoped control проверяется и в state/event statement, и в финальном AI bell
INSERT. auto_reply_blocked сохраняется, не препятствуя human handoff. Отказ
после записи handoff не переименовывает event в доставленный и не повторяет send.
Actor reads state/control/events требуют live READ до ORDER/LIMIT; control
mutation требует live OPERATE (OPERATOR/MANAGER/ADMIN через membership либо
ALL_BUSINESSES) в том же SQL. VIEWER только читает, ROLE_ADMIN без business grant
не даёт доступа. Account enabled/unblocked, unique canonical identity и active
business обязательны; отзыв grants действует на следующую SQL-операцию.
R133 открывает эти primitives через отдельную BUSINESS-страницу обращения;
raw controllers и legacy controls по-прежнему не используют scoped records.

Legacy state/control rows не имеют canonical owner; raw retrieval использует
MAX(messages.business) и общие knowledge/memory rows. Scoped путь не вызывает
raw media normalization, state/control/event writes, retrieval, LLM, memory
learning или auto-reply; attachment не загружается и его storage key не включается
в уведомление.
Глобальное ai_agent_enabled=false оставляет эту AI-ветвь без эффектов.
auto_reply/assist_only/escalate_only не дают scoped пути доступ к raw pipeline.
Per-dialog legacy AI control также не читается/не изменяется этим путём.
UNRESOLVED сохраняет compatibility handoff/notification только до strict;
GENERAL, NULL/invalid scope закрыты. Legacy direct callers без expected scope
пока сохраняют свой контракт и требуют отдельного аудита.

V63 вводит отдельные ai_scoped_sources/events для явно созданных business-owned
KNOWLEDGE и MEMORY. Business обязателен и immutable; identity источника —
business/kind/exact key, origin MEMORY — immutable полный BUSINESS ticket snapshot.
KNOWLEDGE не зависит от наличия обращения. Legacy memory/knowledge/link rows,
aliases и global/unclassified sources не копируются и не используются scoped API.
Global-source policy этого этапа — DENY: даже ALL_BUSINESSES не расширяет retrieval
одного обращения до других бизнесов или общих источников. Разрешение sharing/
global sources требует отдельного контракта, а не пустого business или text alias.

ScopedAiSourceService создаёт только DRAFT; явный revise с expected revision
выполняет review/approval/archive и атомарно пишет metadata audit без title/body.
Любая mutation требует текущего CONFIGURE (business ADMIN через membership либо
ALL_BUSINESSES), unique enabled/unblocked account и active business внутри SQL.
MANAGER не имеет CONFIGURE по текущей матрице. READ библиотеки/history использует
тот же live business guard. MEMORY при создании, изменении и чтении требует
действующего origin tuple/public ID/business, unique raw ID/non-GENERAL; смена
origin скрывает запись, не переопределяя её владельца. Новый origin не наследует
source identity; для нового MEMORY в той же business scope нужен отдельный key.

AiRetrievalService.retrieveScoped — отдельный actor READ API без legacy intent/
context/scoring. Только APPROVED текущего BUSINESS попадают в SQL до ORDER/LIMIT;
GENERAL/UNRESOLVED/invalid scope закрыты независимо от strict compatibility
human handoff R130. Проверяется текущий request owner и scoped ai_disabled.
Источник возвращается отдельным immutable Source snapshot с revision/origin;
isCurrentCandidate повторно проверяет полный payload, version, request/origin,
READ/control перед будущим handoff. Это statement-time proof, не network lock.
Scoped result всегда autoReplyAllowed=false; raw Candidate/policy не используются.
R133 подключает к incoming отдельный trusted-worker поиск approved references
для human handoff, описанный ниже. Actor API retrieveScoped сохраняет отдельный
контракт. Автоматического learning/legacy sync нет.
R132 добавляет server-rendered каталог /business-ai-sources и editor/review:
foundation+ui-selector gates, явный один business без aggregate/default-all,
PAGE_KNOWLEDGE_BASE на каждом GET/POST и текущий READ внутри data SQL. Список
доступных business names также требует enabled/unblocked unique canonical account
и live READ. Catalog возвращает summaries без body/origin; literal q, kind/status
и current MEMORY origin фильтруются до id-keyset ORDER/LIMIT (25 строк на странице).
Private HTML получает Cache-Control: no-store; titles/body/keys экранируются
Thymeleaf, raw origin/user ID не передаются в editor model. Навигация использует
existing PermissionService и flags; авторитетным page gate остаётся server @PreAuthorize.

POST create создаёт только KNOWLEDGE DRAFT с новым opaque key формы; переданный
client status/kind/actor не определяет тип, одобрение или автора записи. POST revise
требует текущего CONFIGURE и CSRF стандартного SecurityFilterChain. В final UPDATE
reviseExpected совпадают immutable source ID, business/kind/exact key и revision;
старую форму нельзя применить к другому business или пересозданному key с той же
revision, даже при ALL_BUSINESSES. Отзыв account/grants после controller precheck
закрывает SQL mutation. Existing R131 revise API сохранён для внутренних callers.
Draft save — первая/default кнопка; approval/archive требуют отдельного действия.
Конфликт identity/version/доступа либо ошибка atomic audit дают 409 без overwrite.

R133 добавляет /business-dialog-ai/{publicId}, доступную из карточки BUSINESS
обращения. Foundation/uiSelector, PAGE_DIALOGS и current business READ обязательны;
GENERAL/UNRESOLVED/коллизии не открывают страницу. Public locator разрешается
сервером, raw ticket/user IDs не передаются в model. Private HTML no-store,
Thymeleaf экранирует текст. Управление требует live OPERATE и стандартного CSRF.
Старая форма доказывает business и expected updated_at в final mutation;
отсутствующая запись создаётся только ON CONFLICT DO NOTHING. Existing control
меняется только CAS с полным current owner и monotonic updated_at; audit atomic.
Конфликт владельца, прав, версии либо ошибка audit дают 409 без overwrite.

Создание MEMORY требует дополнительно PAGE_KNOWLEDGE_BASE и live CONFIGURE.
Оператор выбирает текстовый вопрос клиента и последующий ответ человека из
existing composite-owned history; NULL-owner/unlinked, AI, deleted и attachment
rows исключены. Показаны последние 50 сообщений, preview до 1000 символов.
Пара ID и SHA-256 полного текста, current root tuple/public generation/business,
аккаунт и grants повторно доказываются в одном INSERT snapshot. Подготовленный
вручную текст и явное подтверждение удаления PII/secrets обязательны; автоматической
PII-проверки нет. Создаётся только MEMORY DRAFT, затем требуется отдельный review
в каталоге R132. Новые metadata evidence хранят ID пары и fingerprints, не raw
диалог. Source, metadata audit и evidence атомарны; повтор key не создаёт дубль.
Это не backfill и не доказательство исторической business/generation принадлежности
всех chat_history rows: используется существующая привязка user_id/ticket_id,
а полная child attribution и перенос старых данных остаются отдельным gate.
Правка history после создания не переписывает curated source или его provenance.

При BUSINESS incoming глобальный ai_agent_enabled и scoped ai_disabled сохраняются.
Trusted worker использует frozen V61 scope и current owner/control в едином SQL:
literal case-insensitive substring до 500 символов, только APPROVED того же business
с current MEMORY origin, LIMIT 3 после фильтров. Query/body/title не копируются
в event/evidence. State escalate_only, event и ссылки source ID/revision записываются
атомарно; отсутствие совпадений не отменяет handoff. UI показывает summaries
последнего handoff только при PAGE_KNOWLEDGE_BASE и live READ; повторно проверяются
request owner/control, точная source revision/approval/origin. Отзыв прав, archive,
новая revision или смена owner скрывают ссылку без raw/global fallback. Evidence
не означает доставку оператору и не разрешает автоответ. Для UNRESOLVED остаётся
прежний compatibility handoff до strict, без поиска источников.

Полноценный scoped AI требует scope до media/LLM handoff и client delivery,
privacy/retention, более точного ranking и policy источников для автоответа. Они
PENDING вместе с producer context до INSERT и global page/system policy. V62–V64 —
source migrations, проверяемые в Testcontainers; V64 добавляет только отдельные
immutable-update evidence tables, без классификации старых rows. Рабочая DB/backfill
не выполняются; новых flags/rollout нет.

Auto-assignment из scoped ticket_created callback передаёт исходный V61
snapshot в DialogResponsibilityService.assignScopedResponsibleIfMissing.
Условный INSERT и последующее чтение existing/new responsible каждый
требуют полного expected tuple/public ID/kind/business, unique raw ID и
non-GENERAL marker в своём SQL snapshot. NULL/invalid scope либо отсутствие
flags/access schema закрывают scoped путь без raw fallback. BUSINESS INSERT
также требует текущего OPERATE у предложенного ответственного: единственный
enabled/unblocked canonical account, active business и active membership
либо ALL_BUSINESSES с OPERATOR/MANAGER/ADMIN, согласно initial capability
matrix BusinessAccessService. VIEWER, чужой business и ROLE_ADMIN этого не дают.
Existing responsible не переназначается; прежние trim/actor fallback/ai_agent
ограничения сохранены. Typed UNRESOLVED с locator совместим только до strict.
Отказ назначения не подменяет независимый recipient READ для bell/SSE.
Это statement-time proof, не блокировка owner на всё исполнение callback и
не attribution/backfill старых child rows. Раннее business-scoped построение
candidate pool/load balancing, остальные raw assignment writers и миграция
child identity ещё PENDING; отказ не запускает выбор другого кандидата.

R157 закрывает ранний BUSINESS candidate pool для scoped ticket_created callback,
не все assignment writers. ScopedAssignmentCandidateService одним SQL snapshot
сверяет frozen user/raw/public/business/kind, unique raw ID, non-GENERAL, canonical
channel и его active state. BUSINESS требует foundation flag; schema/owner mismatch
не открывают global fallback. Текущие enabled/unblocked/однозначные canonical
accounts с OPERATOR/MANAGER/ADMIN membership либо ALL_BUSINESSES отбираются до
single/pool/department strategy. VIEWER, чужой business, ROLE_ADMIN без business
OPERATE и ai_agent не входят. Это тот же internal OPERATE contract R125; назначение
не выдаёт page authority, membership или recipient READ.

Для least_loaded читается число открытых assigned roots только текущего business,
с unique raw ID и non-GENERAL; closed/resolved строки исключаются по прежней status
семантике, NULL status считается open. Глобальный loadDialogs не вызывается.
Это текущая canonical привязка ticket_responsibles, не backfill/доказательство
исторической business/generation attribution старых child rows.
Round-robin namespace разделяет canonical business/channel и route/pool; scoped
ветка использует полный long cursor. Глобальные queue/notification routing calls
не продвигают этот business counter.
AlertQueue использует resolveRecipients: тот же список получателей без расчёта
assignee, global workload reads и любых assignment cursor side effects.

Метаданные directory запрашиваются только для допущенных usernames; пустой набор
никогда не означает всех пользователей. Scoped directory читает только canonical
migrated users/roles, без optional-column metadata других schemas и без legacy
users.role fallback. Hash/least_loaded/round_robin выбирают
только отфильтрованный pool. Итоговый условный INSERT R125 по-прежнему проверяет
current owner/OPERATE; это не lock на всё время callback. Поздний отказ не запускает
автоматический подбор следующего кандидата. Existing responsible не заменяется.
Notification READ остаётся независимым: READ-only получатель не исключается из
bell/SSE только из-за отсутствия OPERATE. UI/global directory catalog не менялся.
Typed UNRESOLVED делегирует прежней стратегии только после exact root/channel
proof до strict; GENERAL и invalid scope не попадают в BUSINESS routing.
SLA/escalation/manual/raw assignment writers, child attribution, внешние chat
destinations и live Redis/fleet acceptance остаются отдельными gates. Новых SQL
migrations/flags/рабочих изменений или rollout R157 не выполняет.

R158: background SLA использует отдельный typed source. Frozen canonical
user/raw/public/business/kind/channel, responsibility_revision и точный stored
responsible читаются тем же SQL, что и SLA-атрибуты. Нет позднего разрешения owner
по raw ID; message user/channel mismatch и ambiguous root исключаются.
BUSINESS candidate pool использует R157 current OPERATE перед стратегией;
skills/queues/max-open проверяются по business pool и workload. Round-robin
разделён по canonical business/channel/route/pool и использует полный long.
Старые routing preview API остаются read-only, не authority для назначения.

Финальный ScopedSlaAssignmentService открывает отдельную транзакцию, берёт
legacy-ID advisory guard и root/channel locks, повторяет frozen owner/generation,
active channel, open/non-GENERAL/unique root, current OPERATE и CAS по exact
previous responsible + revision. V77 trigger увеличивает root revision на
assignment insert/change/delete, включая legacy writers; last_read_at сам по
себе её не меняет. Поэтому A→B→A и delete/insert не оживляют устаревшее решение.
Назначение и новый append-only scoped_sla_assignment_events с frozen provenance
commit вместе; отказ и rollback не создают raw success audit. Повтор того же
снимка не назначает повторно. Upgrade V76→V77 оставляет прежние root/assignment
поля без изменений, новая revision=0; это не historical attribution/backfill.

GENERAL не допускается; typed UNRESOLVED — только после current exact proof до
strict. Поздний отказ не запускает global/raw fallback или подбор следующего.
Current grant проверяется SQL на момент записи, не блокируется на всё время
внешнего workflow; max-open остаётся snapshot policy, не резервированием лимита.
Raw/manual writers в целом не переведены на scoped API; порядок их locks,
старые child rows, внешний SLA webhook destination/recipient READ policy,
scoped UI/read и retention нового audit, live/fleet acceptance остаются gates. Frozen контекст
не сериализуется во внешний decision payload. V77 — source-only migration с
readiness probe, без рабочего migration/flags/rollout.

R159: отдельный BUSINESS manual assignment workbench по public ID, ссылка из
scoped карточки. Нужны PAGE_DIALOGS + current canonical READ/OPERATE + CSRF.
Полный expected user/raw/public/business и responsibility_revision формы
проверяются с текущим корнем и повторно SQL перед записью; raw ID непрозрачен
и не trim'ится. Инициатор и target — unique enabled/unblocked accounts с business
OPERATOR/MANAGER/ADMIN membership либо ALL_BUSINESSES OPERATE; ROLE_ADMIN сам
по себе не обходит эту проверку. Candidate pool доступен только OPERATE;
READ-only видит проекцию и последние 10 новых событий, но не список кандидатов.
Назначение само не выдаёт page authority, membership или READ.

Take всегда назначает аутентифицированного actor, не submitted target. Assign
использует canonical numeric user ID; foreign/VIEWER/disabled/blocked/duplicate/
ai_agent не допускаются. Root lock + advisory legacy-ID guard + общая V77
revision/CAS не дают stale/ABA или SLA/manual решению перезаписать другое.
Accounts/business и qualifying memberships/ALL grants удерживаются FOR SHARE;
после блокировки grants финальный SQL заново проверяет права. Закрытый root,
GENERAL/UNRESOLVED, collision, owner/generation mismatch, no-op дают отказ.
Канал может отсутствовать: это локальная операция, не доставка клиенту.

Projection ticket_responsibles, immutable frozen audit V78 с actor/assignee IDs
и снимками имён, а также business_assignment_changed outbox commit атомарно.
Failure любого шага откатывает всё. Новый callback лишь инвалидирует metadata
по сохранённому BUSINESS owner через existing current READ SSE/Redis path:
без позднего raw capture, bell, очереди, AI, участников и support-chat/provider.
Смена owner/generation не открывает старую историю; new audit читается с exact
owner + current READ до limit. Старый responsible показан как legacy-проекция,
не доказательство historical attribution. V77→V78 сохраняет все старые root/
assignment поля и не создаёт прошлых событий.

Новый экран и ссылка скрываются при недоступном scope, старый URL очищается
при смене карточки/ошибке. Страница имеет no-store, POST требует CSRF и проверяет
full owner/revision формы. Старые raw take/reassign/reply/bot APIs не переводятся
на новый workflow этим пакетом. Участники/AI control, снятие ответственного,
историческая attribution, retention, старые writers и external recipient policy
остаются отдельными gates. V78/readiness — source-only, без рабочего rollout.

R160: bare-ID take/reassign и implicit assignment допускаются только для одного
текущего UNRESOLVED/non-GENERAL root до strictReadScope. BUSINESS использует
scoped R159, GENERAL — собственный workflow. Capability не является authority:
writer повторяет owner/public/channel/revision/namespace/open proof под общим
advisory и root lock; zero-row/SQL failure не запускает success cleanup/AI/
уведомления. Callback выполняется под root lock и общим panel transaction manager,
совместимым с JPA-уведомлениями. Прежний V61 scoped callback берёт тот же advisory
lock перед root/child, сохраняя final owner/current OPERATE SQL. Raw take/reassign
audit не пишется в BUSINESS/GENERAL или при strict. Reply/edit/delete/reopen/bot
relay больше не назначают scoped root через общий raw writer; сами legacy
delivery/lifecycle/participant/read APIs и historical attribution этим не закрыты.
Закрытый legacy root может вернуть существующего ответственного без новой записи;
redirect запрещён. Прежний bot relay contract сохранён.
Новая SQL migration не добавлена; source minimum V78.

В R160 compatibility IDs/actors и legacy redirect quirks сохранены; read marks не
меняются. Успешный workflow callback и назначение откатываются вместе при ошибке;
уведомления/SSE legacy остаются best-effort, без новой scoped outbox гарантии.
Assignment boundary не приписывает прошлым child rows business/generation и не
заменяет авторизацию остальных legacy операций. Все 956 Java / 20 XML GREEN
суммарно: 13 классов broad v3 и 7 классов / 320 tests corrected v4.
Новые миграции/рабочий rollout отсутствуют.

R161: узкий пакет одного legacy writer — markDialogAsRead. Bare-ID read mark
пишется только в UNRESOLVED/non-GENERAL до strictReadScope, для точного stored
responsible. При strict ранний lookup не выполняется; UPDATE повторяет namespace,
unique root и получает актуальное strict значение при формировании аргументов.
BUSINESS/GENERAL, collision, missing root/schema не меняют last_read_at. Расчёт
MAX(chat_history.timestamp) / CURRENT_TIMESTAMP, точные ID, actor matching,
assignment fields и прежняя обработка закрытых legacy roots сохранены.
Это не новый scoped BUSINESS/GENERAL read-mark API, не исправление всей legacy
read authorization и не historical business/generation attribution child rows.
Source minimum остаётся V78; SQL migration и рабочие изменения отсутствуют.

Проверки R161: 169 Java / 3 XML GREEN (24 новых PostgreSQL boundary,
139 прежних responsibility snapshots и 6 service tests), без failures/errors/skips.
Это один legacy write path; остальные raw read/write gates сохраняются.

R162: узкий пакет одного audit writer. Raw participants_add/participants_remove
получили ту же UNRESOLVED/non-GENERAL/unique-root границу INSERT, что take/reassign.
Для всех четырёх action codes strict проверяется до lookup и повторно после него.
Поздний business/GENERAL/collision/missing proof не добавляет новый raw audit,
включая result=success/error/not_found. Исторические строки остаются без изменений;
opaque ticket ID, case/trim/default actor/result/detail и timestamp сохранены.
Другие audit actions, successful-action lookup и telemetry сохраняют прежний
контракт. Это не current READ для raw audit и не business/generation attribution.
Операции с участниками, их final writer/recipient policy этим не закрыты;
strict resampling не сериализует in-memory flag после второй проверки.
Минимальная source-схема V78; новых migrations и рабочих изменений нет.

R162 проверен изолированно: 176 Java / 3 XML GREEN, без failures/errors/skips;
50 новых boundary cases, 117 прежних audit snapshots и 9 service tests.
Scoped participant audit/workflow, historical attribution и live acceptance
остаются отдельными gates; рабочие Docker/проект находятся на другой машине.

R163 закрывает обе bare-ID participant mutation SQL: addParticipant и
removeParticipant допускают только один текущий UNRESOLVED/non-GENERAL root
до strictReadScope. INSERT и DELETE повторяют business_id IS NULL,
is_general=FALSE и count всех raw-ID roots=1. Strict проверяется до guard/JDBC
и повторно при формировании SQL arguments; BUSINESS/GENERAL запрещены независимо
от foundation flag. NULL marker/неизвестная namespace schema/child schema дают
отказ без legacy SQL fallback. Успешный ранний unique proof не заменяет final SQL.

Сохранены opaque IDs без trim, username/added_by normalization, timestamps,
duplicate/no-op/catch semantics и отсутствие directory требования для legacy
mutation. Старые SQL rows и соседний trim-ID не переписываются. Новый scoped
BUSINESS/GENERAL participant UI/API не добавлен; recipient/current READ policy,
generation freezing/attribution и остальные legacy paths остаются gates.
In-memory strict после формирования arguments не блокируется до commit; это
тот же ограниченный sampling contract R161/R162, не serializable flag lock.

Baseline R163 отдельно воспроизводит stale R118 GENERAL-test: он ожидал bare-ID
назначение GENERAL, уже запрещённое R160. В новой проверке актуальные canonical
root fields заполнены, обе participant mutations и assignment отвергаются,
старые child rows сверены без изменений. UNRESOLVED golden assertions сохранены.
Source minimum R163 — V78; новой migration/backfill в R163 нет.
Текущее размещение runtime и разрешение restart приведены в актуальной сводке,
не в истории R160–R162.

### Новый BUSINESS roster участников — R164

V79 создаёт business_ticket_participants и append-only
business_ticket_participant_events. Только новые явные действия; никаких
INSERT SELECT из legacy participants, классификации или перепривязки старых
данных. Каждая связь/история содержит ticket_user_id, непреобразованный ticket_id,
ticket_public_id, business_id, member_user_id и actor_user_id со снимками имён.
Числовой users.id — идентичность участника; смена имени не переносит участие
новому аккаунту. UPDATE roster identity запрещён; event UPDATE/DELETE запрещены.
Удаление root не каскадирует аудит на другую генерацию.

GET/POST на странице business-dialog-assignment используют public locator,
PAGE_DIALOGS, no-store, CSRF и сверку full owner. Actor — текущий OPERATE
активного бизнеса и открытого обращения; member — текущий уникальный enabled/
unblocked READ этого бизнеса, включая VIEWER и ALL_BUSINESSES VIEWER. AI_AGENT
не допускается. Это не grant и не system-admin bypass. Удалять из roster можно
и участника с отозванным доступом, отключённым или удалённым аккаунтом.
READ history/list не требует OPERATE; candidate/forms недоступны read-only actor.

Независимая ревизия roster = max event revision данного полного owner (начало 0).
Она не меняет responsibility_revision. Shared advisory/raw-ID lock → root
FOR UPDATE → account/business/grants locks → повторная проверка current rights/
revision → roster и immutable event → frozen metadata outbox, всё в одной JDBC
транзакции. Предел 200 active members. Конкурентные/старые/ABA формы и no-op
отвергаются, ошибки event/outbox откатывают все записи; legacy callbacks,
assignment/AI и отправка клиенту не вызываются. Flags проверяются до входа и
после lock; это не serializable lock in-memory конфигурации.

Scoped BUSINESS recipient discovery использует full-owner roster и numeric
account join с current READ; raw ticket_participants и empty→all fallback для
этого entrypoint отсутствуют. Финальный notification INSERT повторяет full owner,
current READ и существование numeric roster/member username, чтобы удаление
участника/rename/username reuse до INSERT не наследовали старую доставку.
Ранее созданное уведомление удалением из roster не отменяется. Responsible/active
и explicit/channel recipient workflows сохраняют отдельную политику; их historical
attribution и внешние destinations остаются gates. UNRESOLVED legacy policy
не меняется; GENERAL participant workflow этим пакетом не добавлен.

business_participants_changed использует frozen outbox owner и current READ
для metadata-only dialogs_changed; нет raw recapture, bell, queue, AI или provider
callbacks при mutation. Source minimum V79, readiness проверяет обе таблицы.
Рабочая migration/flags/rollout не выполнены и требуют отдельного этапа.

Карточка маркирует старые workspace participants как legacy-проекцию, не новый
roster, и не утверждает historical attribution по одному unique raw ID.
Ссылка управления ответственным/участниками ведёт на отдельный scoped экран.
R164 проверен 567 Java tests / 15 XML (302 regression + 265 final) и 18 Node
tests; это sandbox evidence, не рабочая приёмка. Предыдущий статус S2B не меняется.

### Отдельный GENERAL roster участников — R165

V80 создаёт general_ticket_participants и immutable append-only
general_ticket_participant_events. Только новые явные add/remove; нет
классификации/копирования старых participants, BUSINESS roster или выдачи grants.
Идентичность GENERAL — exact ticket_user_id/ticket_id/ticket_public_id при
текущих is_general=TRUE/business_id IS NULL и unique raw ID. Участник и actor
фиксируются по numeric users.id со снимками имён; UPDATE roster identity и
UPDATE/DELETE event запрещены. Root/account deletion не каскадирует историю.

Страница general-dialog-work/{publicId} требует PAGE_DIALOGS/current GENERAL
READ, mutation также GENERAL OPERATE и pending/open. MEMBER достаточно READ,
включая read-only сотрудника; уникальная enabled/unblocked non-AI учётная запись.
ROLE_ADMIN и ALL_BUSINESSES не заменяют GENERAL grants, участие не выдаёт прав.
Формы передают CSRF/work revision/list revision/member ID, но не raw owner.
Сервер разрешает полный owner из public locator; V65 work revision отвергает
старую форму после смены статуса или владельца. Principal не trim-ится:
строка с добавленными пробелами не заимствует grant другой учётной записи.

Ревизия списка — max immutable event revision полного owner (начало 0), отдельно
от assignment/work revision. Shared advisory/raw-ID lock → root FOR UPDATE →
actor account/READ/OPERATE locks и target account/READ locks для ADD → final
INSERT SELECT/DELETE с повтором owner/unique namespace/current rights/обеих
revisions. ADD проверяет target READ и 200-bound; REMOVE не требует member READ,
чтобы отозванного/отключённого/удалённого участника можно было убрать.
Roster/event/frozen outbox коммитятся одной JDBC transaction; stale/ABA/no-op/
конкурентная форма отвергается без success, ошибка audit/outbox откатывает всё.
Flags foundation/generalTicketQueue проверяются до SQL и после lock, не являются
serializable lock конфигурации. Status/work intervals/assignment/AI не меняются.

participants_changed разрешён только как metadata reason отдельного GENERAL
realtime: frozen GENERAL outbox не проходит legacy callbacks, bell, provider,
AI или BUSINESS fanout. На browser — только public ID/reason/time. Сохранена
прежняя аудитория всех current GENERAL READ connections, а не roster-only;
членство не ограничивает чтение и не определяет новую bell/client policy.
Redis uses existing separate GENERAL topic; consumer/emitter повторяет current
owner и current READ. Старые consumers, не знающие reason, требуют совместимого
выпуска до включения потока. Source minimum V80, readiness проверяет обе таблицы.
Рабочие migration/backfill/flags/deploy не выполнялись; S2B остаётся PENDING.

R165: 526 Java / 8 XML и 2 GENERAL Node tests GREEN изолированно. Проверены
реальные grant/root lock waits и повторная current proof, final SQL injections,
CSRF/CAS/rollback/200-bound и сохранность legacy/BUSINESS/grant rows V79→V80.

### Граница bare-ID lifecycle и категорий — R166

DialogTicketLifecycleService не предоставляет authority для BUSINESS/GENERAL.
Resolve/reopen/categories и activity/feedback followups допускаются только для
текущего unique UNRESOLVED: business_id IS NULL, is_general=FALSE, strict выключен.
NULL marker/недоступная namespace schema дают отказ, ID не trim-ится.
Предварительная уникальность не заменяет namespace: все 13 существующих SQL
повторяют unique raw root и UNRESOLVED predicate; strict заново читается при
binding каждого statement. До входа и после eligibility probe strict также
проверяется. Reopen assignment callback требует повторной eligibility;
сам assignment writer сохраняет собственный R160 full-owner lock/proof.
Root status UPDATE проверяет namespace непосредственно в обновляемой строке,
не только во вложенном SELECT: после ожидания concurrent root lock PostgreSQL
повторно проверяет committed BUSINESS/GENERAL. Это покрывается четырьмя
реальными lock-wait cases, но не делает дочернюю цепочку транзакционной.

Quick actions после lifecycle повторяют допуск до success AI/notification/SSE/
provider callbacks; категории — до и после void writer, spam — до глобального
blockClient и после категорий. Ранний namespace/strict отказ не вызывает
блокировку или success callbacks. Это не транзакция с blacklist: если namespace
меняется после уже разрешённой блокировки, она не откатывается. Глобальные
client blacklist и внешняя recipient policy требуют отдельного решения.

Bare-ID audit quick_close/resolve/reopen/categories/mark_spam включён в набор
защищённых mutation actions R160/R162: strict до/после unique lookup,
UNRESOLVED/unique predicate в INSERT. Это sampled strict, не SQL/config lock.
Unrelated action/telemetry и read lookup audit не получают нового namespace
контракта. Не объявляется полная business/generation attribution старого audit.

Legacy нормализация status/categories/actor, timestamps/counters и best-effort
семантика followups сохраняются; новый общий transaction/rollback не вводится.
Перестановка/пересоздание UNRESOLVED root (ABA), частичный lifecycle и смена
флага после binding остаются gates. Проверки не замораживают user/public/channel
generation на всю цепочку и не сериализуют in-memory flags.

GENERAL manual lifecycle R137 остаётся отдельным scoped workflow. Для BUSINESS
нужны новые close/reopen/categories с полным owner/current OPERATE/CAS и atomic
audit/outbox: raw endpoint не должен временно заменять их даже для ROLE_ADMIN.
R166 source-only, source minimum V80 без новой migration; рабочие DB/flags/
restart/deploy не менялись. Общий статус 01-278 — 🟡.
Golden R139 V68→V69 сохранён; новые nullable/default поля V79 не смешиваются
с утратой старых значений при сравнении SELECT *. Это не live acceptance.

### Ручной BUSINESS lifecycle и категории — R167

V81 добавляет в canonical tickets business_work_revision и business_categories
(JSON array, default []). Это новые категории текущего полного owner, не импорт
и не fallback из raw ticket_categories. Старые поля/категории/назначения,
GENERAL и UNRESOLVED не классифицируются. Новый immutable
business_ticket_work_events хранит full user/raw/public/business, revision,
action, from/to status, previous/current categories и numeric actor со snapshot.
История не следует за другим business/tuple/generation; нет каскадного удаления.

Локальный BUSINESS workflow на существующем business-dialog-assignment требует
PAGE_DIALOGS, current READ и OPERATOR/MANAGER/ADMIN membership либо ALL_BUSINESSES
OPERATE активного бизнеса. ROLE_ADMIN, VIEWER и GENERAL grants не заменяют OPERATE.
Principal не trim-ится к чужому имени; account должен быть unique case-insensitive,
enabled/unblocked/non-AI. Полный posted owner, exact status и work revision
сверяются с current public lookup и повторяются в final UPDATE. GET не смешивает
work/assignment views разных owners между последовательными lookups.
POST с CSRF; чужой scope — 404, READ-only — 403, stale/no-op/invalid state — 409.
HTML CSRF denial сохраняет общий redirect /error/403; page/OPERATE denial — 403.
Это прежняя security policy, не изменение общих обработчиков.

Из pending/open закрытие переводит в resolved и требует 1–50 новых категорий;
из closed/resolved повторное открытие переводит в pending, сохраняя категории.
Сохранение/очистка категорий — только pending/open, no-op отвергается.
Категории trim/dedup, case-sensitive, до 160 Unicode code points каждая;
null/control/oversize и более 50 входных строк не принимаются.
Формы используют строки textarea, категории/history экранируются Thymeleaf.
Root counters/resolved_at/by/last_reopen_at изменяются только соответствующей
командой. Новое открытие не назначает ответственного автоматически.

Общий raw-ID advisory → root FOR UPDATE → actor/business FOR SHARE → qualifying
membership/ALL-grant FOR SHARE → final current rights/full owner/unique raw ID/
revision/status/categories/channel UPDATE. Root trigger увеличивает work revision
при статусе/категориях/owner/channel, включая внешние SQL writers; прямой revision
override запрещён. Статус/category ABA не оживляют старую форму. Assignment и
roster revisions не меняются. При смене full owner новая category projection
сбрасывается в [], не наследуется другим scope; значения, записанные этим
workflow, сохраняются в immutable истории исходного owner. Произвольный direct
SQL сам обязан обеспечивать свой audit; trigger не создаёт историю за него.

Root/categories, immutable event и frozen business_work_changed outbox атомарны
в отдельной JDBC transaction. Ошибка любого шага, недоступный revision trigger,
поздний owner/grant отказ либо неподтверждённый final state откатывают изменения.
Metadata-only watcher/current READ SSE не вызывает legacy lifecycle/feedback,
activity/spans, assignment/participants, AI, bell или client/support provider.
Flags foundation/uiSelector проверяются до/внутри/после mutation; in-memory
конфигурация не блокируется до commit. Это не полный внешний delivery workflow.

Source minimum V81; readiness проверяет новые root поля и event schema.
Рабочая migration/backfill/flags/restart/deploy не выполнены. Отдельными gates
остаются policy смены business и reconciliation/retention, оставшиеся global/legacy
read/analytics/export projections (новые BUSINESS пути закрыты R168), scoped feedback/providers/AI,
global blacklist, historical attribution legacy children и live acceptance.
Общий статус 01-278 остаётся 🟡.

### Canonical BUSINESS категории в чтении, поиске, экспорте и сводке — R168

R168: canonical категории V81 проведены через BUSINESS list/page/public card,
вложенные ticket snapshots history/answers/workspace/spans и JSON export.
Workspace больше не читает raw ticket_categories: categories всегда относятся
к текущему canonical root, даже при коллизии raw ID; relationVerified продолжает
защищать только оставшиеся legacy responsible/participants. Старые rows не
переписываются. Фильтр category — точное case-sensitive значение до 160 Unicode
code points, без trim/LIKE/JSON эвристик; blank/control/oversize дают 400.
List/page/export используют один SQL и одинаковые status/ticket/q/category
фильтры; q дополнительно ищет literal case-insensitive substring в canonical
категориях. Полные массивы категорий входят в JSON, порядок V81 сохраняется.
Новый private /api/business-scope/tickets/categories даёт отдельные counts
по business ID/category, один ticket на category, без объединения одинаковых
business labels. Более 1000 групп — 413, не частичная аналитика. Все новые
чтения повторяют active business/current membership либо ALL_BUSINESSES grant
в SQL и не открывают GENERAL/UNRESOLVED. UI показывает категории в списке и
карточке, общий фильтр экспорта и сводку; textContent и generation guards
защищают markup и поздние ответы после переключения контекста.
Source minimum остаётся V81; новой migration/flags нет. Scoped feedback/client
delivery, blacklist, остальные writers/legacy projections, S2B и live acceptance
не закрываются этим read-пакетом.

PAGE_DIALOGS/foundation сохраняются. Чтение категорий не требует OPERATE и
не выдаёт дополнительных прав. Optional category одинаков для list/page/export;
пустое либо некорректное значение не превращается в отсутствие фильтра.
Сводка имеет собственную границу 1000 business/category групп; не зависит от
фильтров списка и не является global analytics. UI JSON export сохраняет
полные category arrays и прежнюю границу 1000 tickets; category summary может
вернуть 413 независимо от размера ticket export. Историческая принадлежность
прочих legacy children и live provider delivery этим не подтверждаются.

### BUSINESS roster и immutable history READ — R169

R169: отдельный full-owner BUSINESS roster READ и paged immutable history
через /api/business-scope/tickets/{publicId}/participants[/history]. Один SQL
связывает user/raw/public/business текущего root со строками V79; canonical
данные читаются при raw collision и closed/resolved без OPERATE. PAGE_DIALOGS,
foundation и current unique enabled/unblocked READ активного бизнеса обязательны;
GENERAL/UNRESOLVED, чужой scope и неоднозначный public locator закрыты.
Principal lower-case без trim; бизнес не берётся из browser context или raw ID.
200 members либо явный 413; история newest-first, по 1–100 событий, strict
before-revision cursor и limit+1/hasMore. Default cursor допускает Long.MAX_VALUE.
Owner IDs, member/actor IDs, revisions/cursors передаются JSON strings без
потери BIGINT в браузере. Reader и member eligibility проверяются внутри SQL
по прежнему current READ predicate; VIEWER допустим, участие не выдаёт доступ.
Numeric member ID сохраняет snapshot при rename/delete/reuse имени; недоступный
member остаётся видимым с eligible=false, история не меняет account/grants.
Недоступная V79 schema даёт 503, а не пустой список или legacy fallback.
Main card показывает новый roster/history с pagination и textContent, проверкой
полного owner и равенства revision ответов; stale owner/revision/generation,
смена бизнеса и отказ доступа очищают панель. Cursor остаётся строкой.
Workspace больше не читает raw ticket_participants; compatibility participants
возвращается [] и не выдаётся за новый список. Старые rows не импортируются и
не меняются; remaining relationVerified/legacy projection относится только к
ответственному. Write/notification/provider workflows не менялись.
Source minimum остаётся V81, без новой migration/feature flags. Историческая
attribution legacy responsible/active/spans и external recipient policy,
S2B, scoped feedback/delivery и рабочая приёмка остаются gates.

Новые reads не вызывают write service и не строят owner через предварительный
raw lookup. Roster и current revision, history page и current revision получены
каждые одним statement snapshot; UI не смешивает ответы разных revisions.
Удаление участника не отзывает его business membership; READ определяется
только действующими account/grants. Наличие roster не меняет ответственного,
уведомления, status/work revision и права. Snapshot имён — audit, не authority.
Legacy compatibility workspace participants=[]; raw таблица не переписывается.
GENERAL roster API и остальные legacy read paths этим не объявляются закрытыми.

### BUSINESS manual/SLA assignment history READ — R170

R170: единый read-only BUSINESS audit ручных V78 и SLA V77 назначений через
/api/business-scope/tickets/{publicId}/assignments/history. Один SQL повторяет
current unique enabled/unblocked READ активного бизнеса (membership либо
ALL_BUSINESSES с допустимой ролью), foundation и PAGE_DIALOGS на HTTP-границе.
Principal lower-case без trim. GENERAL/UNRESOLVED и ambiguous public locator
не допускаются. Каждое событие связано с текущим полным user/raw/public/business;
raw collision и closed/resolved не мешают чтению подтверждённого audit.
События newest-first, по 1–100 на страницу; strict before-revision cursor,
limit+1/hasMore, default cursor включает Long.MAX_VALUE. Duplicate revision
между источниками в странице/дополнительной строке даёт 503: tied event не
пропускается курсором. Нет fallback на raw audit или молчаливой пустоты при
недоступной canonical schema. Success JSON private no-store/nosniff.
Owner IDs, numeric manual assignee/actor IDs, revisions и cursors — JSON strings.
SLA имена остаются историческими snapshots, numeric IDs для них не выводятся
по текущему username. SLA source/route и previous_responsible не выбираются
и не публикуются. История не устанавливает текущего responsible и не выдаёт
участникам либо назначенному новые grants.
Main card: отдельная раскрываемая история ручных/SLA назначений, textContent,
постраничная загрузка без повторного клика. Full owner и две версии — текущая
responsibility_revision и latestEventRevision scoped audit — защищают смешение
страниц; stale owner/version/generation, смена карточки/бизнеса и отказ доступа
очищают панель. Neutral Ops UI r9 и native disclosure сохранены.
Source minimum остаётся V81: новой migration/flags нет. Writer/notification/
provider paths не менялись. Legacy responsible/active/spans attribution,
retention/reconciliation, external SLA destination policy, S2B, scoped feedback/
client delivery и рабочая приёмка остаются gates.

Один statement snapshot возвращает owner, root revision, последнюю scoped audit
revision и страницу обоих источников. Audit не предоставляет authority текущего
назначения; raw legacy snapshot отдельно и не импортируется. Retention не менялась.

### BUSINESS lifecycle/category history READ — R171

R171: paged immutable BUSINESS lifecycle/category history через новый
/api/business-scope/tickets/{publicId}/work/history и отдельную панель main card.
Читаются только V81 business_ticket_work_events текущего полного user/raw/public/
business. Отдельный READ service не вызывает writer и не использует raw child
tables; raw collision, closed/resolved допустимы. PAGE_DIALOGS/foundation на
HTTP-границе, current unique enabled/unblocked READ активного бизнеса внутри
одного SQL: membership либо ALL_BUSINESSES с допустимой ролью, без OPERATE.
Principal lower-case без trim; GENERAL/UNRESOLVED/foreign и ambiguous public
locator закрыты. История не предоставляет authority текущего статуса/автора
и не восстанавливает чужие права.
События close/reopen/categories содержат frozen actor ID/name, from/to status,
previous/current ordered category arrays и timestamp. Категории не подменяются
текущим root или raw ticket_categories; clear сохраняет пустой after-snapshot.
JSON snapshots читаются как массивы строк без scalar/number/null coercion.
Повреждённый snapshot либо недоступная canonical schema дают 503, не пустую
историю и не legacy fallback. Success JSON private no-store/nosniff.
Newest-first, 1–100 entries/page, strict before-revision cursor, limit+1/hasMore;
default cursor включает Long.MAX_VALUE. Owner/actor IDs и revisions/cursors —
JSON strings. Один statement snapshot возвращает owner, root work revision,
latest scoped event revision и страницу; gaps после owner/channel/status writes
не объявляются потерянными событиями. UI проверяет обе версии и полный owner,
очищает stale/denied panel и игнорирует late responses после смены контекста.
TextContent защищает frozen actor/categories; повторный click во время загрузки
не создаёт второй запрос. Native disclosure и Neutral Ops r9 сохранены.
Source minimum остаётся V81, новой migration/flags нет. Write/feedback/provider/
assignment/participant workflows не менялись. Историческая attribution legacy
responsible/active/spans, retention/reconciliation, external recipient policy,
scoped feedback/client delivery, S2B и live acceptance остаются gates.

V81 snapshots не переинтерпретируются при изменении текущих категорий или имени
автора. Пагинация не заменяет current scope proof; каждая страница повторяет READ.
Retention, immutable-table maintenance и рабочая приёмка остаются отдельными gates.

### Authenticated BUSINESS principal без trim aliases — R172 (исторический checkpoint)

R172: authenticated BUSINESS principal сохраняется при привязке к SQL без
trim: допускается прежний lower-case Locale.ROOT, пробелы/табуляция не удаляются.
Исправлены BusinessTicketAssignmentService, BusinessTicketParticipantService,
ScopedDialogAiWorkbenchService, ScopedDialogAiPersistenceService,
ScopedAiSourceService и ScopedAiHandoffKnowledgeService — семь bindings в шести
сервисах. Authenticated account key не является display name/recipient config.
Прежний trim позволял principal другого/отсутствующего account стать именем
действующего ADMIN/OPERATOR и пройти current READ/OPERATE/CONFIGURE чужого бизнеса.
Теперь find/candidates/recent, assignment take/assign, participant add/remove,
AI owner/messages/state/events/control/CAS, source catalog/body/history/create/
revise/approval/retrieval/current-candidate proof, curated MEMORY и handoff
evidence проверяют именно переданный principal с сохранением whitespace.
Literal spaced account не запрещён: его собственные numeric ID и текущие grants
проверяются отдельно от похожего имени без пробелов. Upper/mixed case прежнего
canonical account допустимы; current uniqueness/enabled/unblocked, business
active, membership/ALL_BUSINESSES и разница READ/OPERATE/CONFIGURE сохранены.
В присвоенных записях/audit остаются ID и имя фактического account, не его соседа.
Тексты, reasons, raw/public IDs, source keys, candidate/ai_agent правила, lock/CAS,
scope proof, retention и grant policy не менялись. Source minimum V81; новой SQL
migration/flags нет. Это исправление конкретных BUSINESS actor/reader paths,
не полный аудит остальных authentication/notification/realtime identity paths.
SSE/bell identity normalization, global/system/PermissionService policy,
external recipients, legacy child attribution, S2B и live acceptance ещё gates.

Case folding не заменяет проверку numeric account/grants. Для principal нельзя
использовать normalization свободного текста, alias display names или списка
получателей. Новый scope не меняет authentication/login/account creation policy;
остальные legacy/SSE/bell/global identity boundaries проверяются отдельно.

### Exact SSE и notification recipient identity — R173 (исторический checkpoint)

R173: точная identity получателя SSE и уведомлений сохраняет whitespace при
прежнем lower-case Locale.ROOT. Исправлены четыре production service:
UiEventStreamService (connect/map key/targeted delivery/unregister),
UiEventTicketAccessService (current recipient READ SQL), UiEventFanoutPublisher
(Redis targetUser, Locale.ROOT) и NotificationService (authenticated list/page/
cursor/count/read/read-all, canonical recipient sets/exclusions/DB usernames).
Отдельный account с пробелами получает только свои права и уведомления; имена
с leading/trailing/both spaces, tab/newline не становятся соседним account.
Canonical roster использует numeric member ID/current username и final roster
proof; exact recipient не подменяется похожим именем после SQL lookup.
Current unique enabled/unblocked account, active business, membership либо
ALL_BUSINESSES READ, frozen full owner, raw uniqueness, late revoke, strict
UNRESOLVED и GENERAL exclusion сохранены. Новых grant rules, migration, flags,
UI/JS/templates нет; source minimum V81. Системные broadcast/heartbeat и прежние
blank fallbacks не менялись. Свободные legacy recipient lists по-прежнему
разбираются отдельным splitIdentities; их responsible/active/spans attribution
и NotificationRoutingService (base/config/DB audience), global/system/
PermissionService policy остаются следующими gates.

Проверки R173: 422 Java tests / 20 свежих XML GREEN,
failures/errors/skips=0, финальный Maven exit 0. Evidence: 15 сохранённых GREEN
suites v2 (.tmp/r173-panel-v2.log) + 5 полных rerun suites v4
(.tmp/r173-panel-v4.log), с exact source/classes/XML proof.
До исправления production-файлы были byte/blob-equivalent baseline: новый v1
дал 10/10 RED cases (5 recipient READ и 5 bell reads), failures=10/errors=0/
skips=0. XML/log/source SHA сохранены в ignored .tmp/r173-baseline-red.json и
r173-baseline-*.xml; отрицательные assertions не ослаблены.
Новый SSE suite использует реальные connect/emitter buffers, local fallback,
queued broadcast/target, publisher JSON и реальный subscriber с mock Redis,
две похожие реальные учётные записи разных бизнесов и late revoke/disable/
block/archive/duplicate. Новый notification HTTP suite использует полный
Spring/JPA/Flyway и действующие Security/CSRF filters; читает list/page/count,
проверяет read/read-all и неизменность полных notification rows при отказе,
canonical roster/exclusion и положительный собственный spaced account.
Шесть старых fixture suites явно обновлены под новый exact-name contract:
положительные owner/grant проверки используют uppercase canonical identity без
добавленных пробелов; targeted fanout assertion сохраняет literal spaces;
добавлен явный whitespace denial для четырёх READ-ролей. Прежние frozen-owner,
GENERAL/UNRESOLVED, current grants, pagination, outbox/roster assertions сохранены.
В v2 были 2 failures: старое ожидание /dialogs/<id> вместо уже канонического
/dialogs?ticketId=<id> (URL builder не менялся), и прежнее склеивание spaced
recipients в UNRESOLVED fixture. URL assertion уточнён, case dedup сохранён и
добавлена проверка разных notification IDs для plain/spaced recipients;
UNRESOLVED fixture теперь проверяет три точных имени вместо двух aliases.
Кроме того, новый HTTP fixture оставлял свои root rows без messages, и следующий
shared Spring context корректно отказался READY (16+8 errors). Добавлена очистка
собственных notification/roster/root rows после assertions. Production readiness
не отключалась, READ/owner predicates не менялись. Все пять затронутых suites
повторены целиком; оставшиеся 15 GREEN suites и production classes неизменны.
Вспомогательный v3 повтор остановлен до test execution из-за EOL guard
fixture operator: raw SHA сравнивался после LF normalization. Operator v4
проверяет raw bytes до normalization; основной checkout не менялся.
Source/class/XML freshness и exact generated CSS baseline blobs сверены.
Node/browser checks=0 (JS/templates неизменны). Live Redis/proxy/provider и
рабочая functional acceptance не выполнялись; задача 01-278 остаётся 🟡.

### Exact notification-routing audience identity — R174

R174: NotificationRoutingService сохраняет точные account keys без trim,
с прежним lower-case Locale.ROOT. Base recipients, DB usernames, typed JSON
employeeUsernames/excludeUsernames и сопоставление portal activity больше не
сливают plain/spaced accounts. Это важно для employees_only, department_all,
department_except, base/base_plus_route и online_only_fallback_all: состав и
активность конкретного account не переносятся похожему имени. Enabled/blocked,
department lookup, case dedup, disabled route, пустое сообщение и fallback при
отсутствии online recipients сохраняют прежний контракт.
Array element — точное имя account; отделённый legacy scalar text со списком
по comma/semicolon/newline сохраняет прежний явный trim отдельных tokens.
Blank elements исключаются. Department/event/scope/route labels и content не
становятся account keys и сохраняют свою нормализацию. Save/load, wrapped/direct
payload, top-level settings apply и HTTP settings сохраняют typed array names.
Изменён только этот production service; NotificationService/SSE predicates,
grant rules, default audiences, source minimum V81 и migration chain неизменны.
UI/JS/templates и рабочие настройки не менялись. Global/system/task policy,
AlertQueueService, ChannelAssignmentRoutingService, ChannelTransportService,
IncidentRouteDeliveryService user/freeform targets, legacy responsible/active/
spans recipient attribution и PermissionService остаются отдельными gates.
Этот шаг не вводит новую business-авторизацию системных уведомлений.

Проверки R174: 117 Java tests / 11 свежих XML GREEN,
failures/errors/skips=0, Maven exit 0, .tmp/r174-panel-v2.log. До исправления
production source был exact baseline blob: отдельный v1 дал 5/5 RED cases с
неверным plain recipient вместо leading/trailing/both spaces/tab/newline.
Original XML/log/source/test SHA — в ignored .tmp/r174-baseline-report.xml,
r174-panel-v1.log и r174-baseline-red.json. Тот же diagnostic source SHA
неизменен в итоговом GREEN; assertions не ослаблялись.
Новый PostgreSQL suite проверяет canonical DB directories двух qualifiers,
base sets, typed selectors/exclusions, чужой department, online activity,
fallback/case dedup, save/load/apply, legacy scalar text, disabled/blocked/
deleted/moved accounts и missing schema. Новый full Spring/JPA/Flyway HTTP suite
проверяет settings POST/GET с PAGE_SETTINGS и CSRF, перечитывание сохранённой
конфигурации, dispatch в реальную notifications row, точный SSE refresh target,
чтение/read-all только адресатом и повторный current directory lookup.
Существующие fixtures и production readiness/grants не изменялись. Source/class/
XML freshness и exact generated CSS baseline blobs сверены; Node/browser=0,
так как JS/templates неизменны. Live Redis/proxy/providers, рабочая functional
acceptance и rollout не выполнялись. Общий статус 01-278 остаётся 🟡.

### Exact channel notification audience identity — R175

R175: AlertQueueService сохраняет точные account keys без trim при прежнем
lower-case Locale.ROOT в typed employeeUsernames/excludeUsernames, DB usernames,
online activity и передаче уже разрешённых assignment recipients. Канальный
селектор больше не подменяет spaced account похожим plain account другого
department/business и не переносит его online activity или исключение.
ChannelTransportService использует отдельный exact-key normalizer только для
notification arrays: panelNotifications.routing, direct panelNotifications и
legacy alertQueue сохраняются через API без обрезки account names, с case dedup
и исключением blank entries. Department labels, preview и времена сохраняют свою
нормализацию; event defaults, online fallback и disabled/blocked filters прежние.
Current READ/full frozen owner/raw uniqueness/GENERAL denial по-прежнему проверяет
NotificationService перед INSERT; новых grant rules или channel business policy нет.
Production scope — только AlertQueueService и notification arrays в
ChannelTransportService; source minimum V81, migration chain и UI/JS неизменны.

Не закрыты: assignment resolver/config/directory/final writer identity
(ChannelAssignmentRoutingService, assignment settings ChannelTransportService,
DialogParticipantService и DialogResponsibilityService), UI CSV parsing/round-trip,
IncidentRouteDeliveryService user/freeform targets, legacy responsible/active/spans
attribution и global/system/task/PermissionService policy. Проверка source также
подтвердила отдельный gate: ChannelApiController использует общий authenticated
доступ/CSRF, без PAGE_SETTINGS на CRUD methods; R175 не меняет эту policy.
Положительный exact recipient handoff не доказывает корректность upstream assignment.

Проверки R175: 200 Java tests / 10 свежих XML GREEN,
failures/errors/skips=0: 9 GREEN suites v3 (.tmp/r175-panel-v3.log) и полный
повтор HTTP suite v4 (.tmp/r175-panel-v4.log), итоговый Maven v4 exit 0.
Сохранённые source/class/XML hashes девяти suites совпадают с финальными.
До изменения оба
production source совпадали с baseline blobs: v1 воспроизвёл 5/5 failures для
leading/trailing/both spaces/tab/newline (errors/skips=0). Original XML и test SHA
сохранены в ignored .tmp/r175-baseline-report.xml и r175-baseline-red.json;
diagnostic source SHA неизменен в итоговом GREEN, assertions не ослаблялись.
Новый PostgreSQL suite проверяет DB department keys, typed selector/exclusion,
online activity и fallback, exact assignment handoff, три config shapes,
save/reload, current disabled/blocked/moved/deleted и frozen scope передачи.
Новый full Spring/JPA/Flyway HTTP suite проверяет channel PATCH, реальную
persisted config и dispatch до notifications row, точный mocked SSE target,
bell GET/read-all, foreign READ denial и late revoke/disable/block/archive/
duplicate/public-ID change/raw collision/GENERAL. Authentication и CSRF действуют.
Одна старая положительная AlertQueue fixture явно заменяет padded Sasha на
uppercase canonical SASHA; прежние assertions case dedup/получатели/preview
сохранены, а literal whitespace denial проверяет новый suite.
V2 остановился до тестов на двух лишних закрывающих скобках нового HTTP test;
v3 исправляет только эти скобки и повторяет все 10 suites. V2 log/exit/input hashes
сохранены. В v3 новый HTTP suite получил 3 fixture errors: immutable public ID,
unique ticket ID/channel и GENERAL provenance check. V4 меняет только подготовку
этих трёх состояний: valid DELETE/INSERT с новым public ID, raw collision с
другим/null channel и GENERAL с general_selection_source=MANUAL. Добавлены явные
проверки состояния БД; исходные delivery denial assertions сохранены. Production
triggers/constraints не отключались; v3 XML/source сохранены в ignored
r175-http-v3-report.xml и r175-http-v3.java. Все 18 HTTP tests повторены целиком.
Production classes скомпилированы в v2 с теми же source SHA и pinned class SHA;
классы девяти v3 GREEN suites совпадают побайтно с финальными, их XML неизменны.
Новые HTTP classes/XML свежие относительно v4.
Остальные выбранные suites неизменны. Source/class/XML freshness и exact generated
CSS baseline blobs сверены; Node/browser=0, поскольку JS/templates не менялись.
Live Redis/proxy/providers, рабочая functional acceptance и rollout не проверялись.
Общий статус 01-278 остаётся 🟡.

### Global channel administration: утверждённая политика — R184

Baseline R184 (2026-10-27): clean main, HEAD/origin/main/actual remote
5aa3d5e465ab0fb21b08b9459383fb793d872415. R183 проверен по parent/subject/exact
8 paths/normalized content SHA. Detached sandbox .tmp/01-278-r184. Рабочие
проект и Docker остаются на другой машине/в другой сессии. Здесь source и
изолированные Maven MVC/security/Thymeleaf fixtures; Docker, PostgreSQL, browser
и Node в R184 не запускались. Рабочие revision/DB/config/flags/providers не
проверялись и не менялись; нет restart/deploy/migration/backfill или запуска
рабочих ботов. Grant commit/push от 27 октября сохраняется до 2026-10-27
21:00 UTC (28 октября 00:00 +03); source apply и Git publisher разделены.

Принятое решение владельца: «Только системный администратор с правом страницы».
Канал, его token/credentials и общая конфигурация управляются как глобальный
ресурс: нужен явный ROLE_ADMIN вместе с PAGE_CHANNELS или PAGE_SETTINGS в
Authentication. Business ADMIN, ALL_BUSINESSES, ROLE_PORTAL_ADMIN без ROLE_ADMIN,
одна page authority и исторический username admin сами по себе не подходят.
Новые grants/roles не назначаются; делегирование бизнес-администраторам всех
привязанных бизнесов не реализуется. Общий/чужой business binding не расширяет
право управлять каналом. Этот ранее открытый policy gate закрыт source-only
реализацией, без подтверждения рабочего rollout.

ChannelManagementAccess содержит общий PreAuthorize expression и raw-authority
predicate для навигации. Защищены 14 маршрутов R181 channel CRUD/aliases,
credentials, test-message/bot-info/channel-notifications и дополнительно четыре
BotProcessApiController: start, stop, status, runtime-contract. Все 18 route/verb
combinations требуют роль и одно page right до transport/repository/lifecycle
вызова; write CSRF остаётся обязательным. /channels использует ту же политику.
ROLE_ADMIN + PAGE_CHANNELS без PAGE_SETTINGS остаётся на legacy channels/index;
ROLE_ADMIN + PAGE_SETTINGS открывает /channels как alias в /settings?open=channels.
Redirect больше не использует широкий PermissionService admin bypass вместо
явного PAGE_SETTINGS. Navbar учитывает canManageChannelsInSettings, поэтому
channel-only администратор сохраняет ссылку на доступную страницу. Settings tile,
channelsModal, addChannelModal и channelEditorModal отсутствуют в серверном HTML
для остальных пользователей; сама /settings и другие её разделы сохраняют
прежние page permissions. Внешний th:if wrapper используется до th:replace.

SQL/schema, ChannelTransportService, bot lifecycle orchestration/runner internals,
internal token/HMAC APIs, settings save API и JS не менялись. Не добавляется
публичный read-only channel catalog/status; sidebar без нужных прав сохраняет
catch/hide на 403. Остальные global settings/proxy/template permissions, live
login/session authority refresh/revoke, incident UI/upstream identity, legacy
attribution/global/system policy, S2B и рабочие S3–S6 gates остаются отдельными.
Проверяется authority snapshot текущей Authentication, не новый DB membership
resolver. Общий статус 01-278 — 🟡.

Validation R184: 393 Java tests / 10 свежих XML GREEN, Maven v3 exit 0,
failures/errors/skips=0. ChannelManagementAuthorityWebMvcTest расширен с 170 до
268 cases: 162 отказа для 9 actor/authority combinations × 18 routes,
36 разрешений по ROLE_ADMIN + одному из двух page rights, 52 missing/invalid
CSRF отказа для 13 writes и 18 anonymous redirects. Проверены exact delegation,
response и long ID >2^53; отказы не вызывают transport, repository, runtime role,
runner, process service или SSE publisher. Настоящие SecurityConfig/filters/
method security/CSRF; реальные процессы, provider calls и runner — mocks.
Новый ChannelManagementPageSecurityWebMvcTest — 13 cases с реальными navigation,
PermissionService и Thymeleaf: 5 отказов /channels, 5 settings pages без channel
controls, обе разрешённые entry combinations и отказ роли без page rights.
281 policy cases суммарно; прирост к прежнему suite — 111 cases. До production
edits те же два test source SHA на 9 baseline blobs дали в v2 281 tests /
146 failures / 135 passing controls / 0 errors/skips. V1 сохранил неверное ожидание
redirect вместо прежнего HTTP 403 для page denial; исправлен только новый test
на фактический error contract, без production edit. После v2 оба policy test
source не менялись. RED XML/log/test SHA и baseline blobs сохранены в ignored
r184-baseline-red.json. ManagementController regression сохраняет все assertions;
5 requests получили явно разрешённые policy actors/page authorities.
Остальные финальные suites: ChannelApiController 50, BotProcessApiController 27,
ManagementController 16, SettingsApi/Bridge 5+5, HomeController 2 и UI source
contracts 6+1. Старые functional MVC suites с mocks/без filters не подменяют
отдельную security проверку. Java cache R183 разрешён после normalized SHA
comparison всех Java sources; 8 production classes реально перекомпилированы.
Source/class/XML/log SHA и actual rendered classpath templates сверены. Maven
сохраняет resource timestamps: v2 verifier проверяет exact new-source bytes и
отличие от baseline, вместо неверного требования mtime после начала test run.
Остальные cache inputs, generated CSS baseline и diff --check проверены.
Browser/live DB/provider/runner/rolling-upgrade acceptance отсутствует; прежний
R53 Node debt в R184 не запускался.

### Incident backend exact identity — R183

Baseline R183 (2026-10-27): clean main, HEAD/origin/main/actual remote
ae325d000f43d367f2e0c6ad344e1e30c34986b7. R182 проверен по parent/subject/exact
6 paths/normalized content SHA. Detached sandbox .tmp/01-278-r183. Рабочие
проект и Docker находятся на другой машине/в другой сессии. Здесь source и
изолированные Maven service/MVC fixtures. Read-only docker version probe показал
недоступный local daemon; Docker не запускался, PostgreSQL/Testcontainers здесь
не выполнялись. Рабочие revision/DB/config/flags/providers неизвестны и не менялись;
restart/deploy/migration/backfill отсутствуют. Grant пользователя на commit/push
от 27 октября остаётся до 2026-10-27 21:00 UTC (28 октября 00:00 +03).
Source apply и отдельный expiry-guarded Git publisher разделены.

R183 сохраняет точные непустые account keys в backend инцидентов. IncidentService
не обрезает owner, watcher_identity, created_by/added_by/event actor; прежние
case folding, порядок/dedup и empty-input validation сохранены. Загруженные owner
и watchers передаются в NotificationRoutingService без trim. Add/remove watcher
различают literal " lead " и отдельный "lead". Исторические строки не переписываются.
Singular route_type=user сохраняет scalar target в inline routes при создании,
add/update route, JDBC binding outbox, JSON envelope, row mapping и вызове
NotificationService.notifyUser. В outbox также сохраняются exact owner и
requested_by; прежний регистр этих metadata не меняется. Нормализация остальных
route types и строковая users CSV-грамматика с split/trim/case folding/dedup
сохранены; department и all_operators используют прежние API. Webhook targets,
display text, route retries/claim/lease/monitoring policy и SQL predicates не менялись.
Это исправление identity strings, не новый business scope, recipient eligibility,
правила глобальных incidents или выдача grants. UI/JS по-прежнему имеют отдельный
trim/CSV debt: полный browser→API round-trip R183 не закрывает. Старая уже обрезанная
outbox/history metadata не восстанавливается по догадке. Foreign/shared channel
CRUD policy, остальные upstream/global picker/attribution, S2B и рабочие S3–S6
rollout/acceptance остаются открытыми. Общий статус задачи — 🟡.

Проверки R183: 88 Java tests / 7 свежих XML GREEN, Maven v3 exit 0,
failures/errors/skips=0. Новые IncidentIdentityBoundaryTest — 50 cases и
IncidentRouteIdentityBoundaryTest — 19. Проверены create/update/reload projection,
owner/watchers/audit/notification delegation, add/remove literal watcher,
inline/add/update singular user route, enqueue bindings + JSON → реальный outbox
row mapper/dispatch → реальный delivery service → captured NotificationService,
long string >2^53, whitespace/CRLF/tab, padded null, delimiter/markup keys,
blank/case/order/dedup и CSV/department/operator controls. Repository/notification
зависимости — mocks; CapturingJdbc сохраняет параметры и возвращает их реальному
row mapper, но не исполняет SQL. DB locking/transactions/claims, live authentication,
сохранение SQL/фактическая доставка или browser/provider acceptance не заявляются.
До production edit те же test source SHA на трёх exact baseline blobs дали
69 tests / 48 failures / 21 passing controls / 0 errors/skips в v2. Первый v1
выявил missing findByIdForUpdate stub нового fixture; добавлен только lock lookup,
assertions не ослаблялись. V1 log сохранён; final test source заморожен после v2.
RED XML/log/test SHA и baseline blobs записаны в ignored r183-baseline-red.json.
Финально повторены существующие IncidentService (3), IncidentApiController (3),
route delivery diagnostics (1), monitoring policy (8), ops escalation (4).
Java cache R182 использован только после normalized SHA comparison всех Java
sources; новые tests скомпилированы в baseline runs, все три production class
реально перекомпилированы в v3. XML свежие; source/class/report/log SHA, unchanged
cache inputs, generated CSS baseline и diff --check проверены. Node/browser не
запускались; прежний R53 Node debt и PostgreSQL suites здесь не проверялись.
Рабочей приёмки нет.

### Exact principal в global PermissionService — R182

Baseline R182 (2026-10-27): clean main, HEAD/origin/main/actual remote
ad2b0af6bba77487a563e999732860e09b2b79e4. R181 проверен по parent/subject/exact
8 paths/normalized content SHA. Detached sandbox .tmp/01-278-r182. Рабочие
проект и Docker находятся на другой машине/в другой сессии; здесь только source
и изолированные Maven unit/MVC fixtures. Docker/browser/Node не запускались;
рабочие revision/DB/config/flags/providers неизвестны и не менялись. Нет рабочих
restart/deploy/migration/backfill. Явный grant commit/push действует до конца
27 октября Europe/Moscow: 2026-10-27 21:00 UTC (28 октября 00:00 +03).
Source apply и отдельный expiry-guarded Git publisher разделены.

R182 устраняет подмену account key в PermissionService.isSuperUser: username
сравнивается с прежним admin без trim. Отдельные аккаунты " admin", "admin ",
" admin ", tab/CR/LF variants больше не получают global superuser bypass
через похожий логин. Это распространяется на isSuperUser, hasAuthority и
hasAnyRole. Прежняя case-insensitive совместимость точного unpadded admin,
явные ROLE_ADMIN/ROLE_PORTAL_ADMIN, собственные page authorities, нормализация
role vocabulary и пустой allow-list сохранены. Бизнес-роли/ALL_BUSINESSES tokens
не заменяют системную административную роль. Пользователи/роли/grants в БД,
authentication provider, business membership/SQL, API payloads, UI и flags не
менялись. Удаление самого исторического admin-name bypass и переработка global
role policy не входят в R182. Foreign/shared channel CRUD policy остаётся
отдельным открытым решением; R181 page gate сохраняется. Incident user targets,
остальные legacy/global picker/attribution, S2B и rollout/acceptance открыты.
Общий статус задачи — 🟡.

Проверки: 307 Java tests / 8 свежих XML GREEN, Maven v2 exit 0,
failures/errors/skips=0. Новые 55 cases: PermissionIdentityBoundaryTest (27) и
AdminStorageInventoryIdentityWebMvcTest (28). Второй использует настоящие
SecurityConfig, PermissionService, method security, фильтры и CSRF; inventory
service — mock, скрипт инвентаризации не выполняется. Без admin privileges
12 literal accounts с PAGE_SETTINGS и valid CSRF получают 403 без вызова service;
сохранены 9 allowed combinations (unpadded admin case variants и две роли),
4 отказа без PAGE_SETTINGS, 2 CSRF отказа и anonymous redirect. Unit tests проверяют
все три permission API, собственные права literal account, role/empty-list и
unauthenticated controls. До production edit те же test source SHA на exact
baseline PermissionService blob дали 55 tests / 21 failures / 34 passing controls /
0 errors/skips: 13 unit и 8 HTTP failures, где другой аккаунт допускался к inventory.
Исходные XML/log/test SHA сохранены в ignored r182-baseline-red.json и копиях
report; тесты после RED не менялись. Финально повторены прежние PermissionService
(2), DialogAuthorization (6), SettingsMacroTemplate (1), ManagementController (16),
ChannelManagementAuthority (170) и AuthManagementApiController (57). Некоторые
прежние suites используют permission mocks/отключённые фильтры; новая MVC проверка
отдельно доказывает реальную permission boundary, не live login или DB acceptance.
Java cache R181 использован только после normalized SHA comparison всех Java
sources; оба новых test class скомпилированы в v1, изменённый PermissionService
реально перекомпилирован в v2. XML свежие; source/class/report/log hashes,
неизменность остальных cache inputs, generated CSS baseline и diff --check
проверены. Прежний R53 Node debt не запускался. Рабочей приёмки нет.

### Channel management page authority — R181

R181 вводит явную page authority для ChannelApiController,
ChannelBotCredentialApiController, ChannelTelegramDiagnosticsApiController и
ChannelNotificationApiController: hasAnyAuthority('PAGE_CHANNELS','PAGE_SETTINGS').
Это существующие права двух управляющих страниц: ManagementController /channels
требует PAGE_CHANNELS, settings API/редактор — PAGE_SETTINGS. Наличие одного
из этих прав сохраняет прежние controller→transport вызовы и ответы. Роль
ROLE_ADMIN, права других страниц и business/ALL_BUSINESSES tokens сами по себе
не заменяют page authority. Клиентский business_id тоже не даёт её.
Барьер закрывает GET/POST /api/channels, PATCH/PUT/POST/DELETE /api/channels/{id},
оба public-id/regenerate alias, bot credentials GET/POST/DELETE, test-message,
bot-info и channel-notifications — 14 route/verb combinations. Отказ происходит
до вызова транспорта, в том числе до загрузки credentials и внешней диагностики.
Настоящий CSRF для всех write aliases остаётся обязательным; GET не требует token.
Полный channel catalog больше не доступен любому authenticated пользователю;
sidebar без этих page rights получает прежний catch/hide при 403. Отдельного
расширения READ scope или нового публичного status endpoint здесь нет.
ChannelTransportService, DB/SQL, payload normalization, IDs, grants/roles, UI/JS,
schema/flags и internal bot APIs не менялись. Это global administration page
boundary, не business scoping каналов: foreign/shared channel CRUD policy,
membership/owner predicates и правила глобальных администраторов — следующий
отдельный gate. Остальные legacy/global picker/attribution, incident targets,
S2B и рабочий rollout/acceptance также открыты. Статус 01-278 — 🟡.

Проверки R181: 246 Java tests / 5 свежих XML GREEN, Maven v2 exit 0,
failures/errors/skips=0. Новый ChannelManagementAuthorityWebMvcTest — 170 cases
с реальной SecurityConfig, фильтрами, method security и CSRF. Для 14 routes
проверены 84 запрета при valid CSRF без нужной page authority, 28 разрешений
по PAGE_CHANNELS или PAGE_SETTINGS с exact delegation/response и long ID >2^53,
44 missing/invalid CSRF отказа и 14 anonymous redirects. Отказы не вызывают
mock transport. Session principal задаётся Spring Security test; live auth/DB/
provider acceptance не заявляется. Существующие 50 ChannelApiControllerWebMvcTest
с отключёнными фильтрами остаются функциональной регрессией; новый suite отдельно
доказывает security boundary. ManagementController (16), SettingsApiController (5)
и SettingsBridgeController (5) повторены без изменений.
До production edits тот же test source SHA на exact baseline blobs дал
170 tests / 84 failures / 86 passing controls / 0 errors/skips: чужие права
получали HTTP 200 вместо 403. XML/log/test SHA и controller blobs сохранены
в ignored r181-baseline-report.xml, r181-panel-v1.log, r181-baseline-red.json.
Тесты после RED не менялись. Кэш Java R179 использован только после сравнения
нормализованных SHA всех Java sources с текущим baseline; новый test и четыре
controller class реально перекомпилированы, XML свежие. Source/class/XML/log SHA,
generated CSS baseline и diff --check проверены. Node/browser=0: UI/JS не менялись;
прежний R53 debt из R180 здесь не запускался. Рабочей приёмки нет.

### Exact typed channel notification UI — R180

R180 сохраняет точные typed notification usernames от parseQuestionsCfg через
picker до JSON POST и reload. Только channelEditorPanelNotifEmployees и
channelEditorPanelNotifExclude opt-in в data-employee-value-format="username-json".
Их textarea type/id остаются прежними; .value содержит JSON-массив строк,
а backend payload по-прежнему содержит массивы employeeUsernames/excludeUsernames.
Typed values сохраняются без trim при прежнем case folding; case dedup сохраняет
первое вхождение. Старая строковая CSV-грамматика channel parser и прочих полей
picker не менялась. Справочник /api/panel-users/options остаётся прежним global/
legacy source, без нового доступа или расширения native/scoped options.
Явно пустое поле больше не восстанавливает старый список через value || existing;
fallback применяется только при отсутствующем control. defaultValue восстанавливает
загруженный выбор при native reset; ошибка справочника не стирает существующий
список, отсутствующий literal key не подменяется похожим plain account.
Повреждённый JSON/non-string entry блокирует сохранение до POST с сообщением UI.
Непустые строковые ключи, включая >2^53, остаются строками; markup — textContent.
Исправлен и общий observer loop: added node, удалённый до доставки MutationRecord,
больше не считается внешним добавлением и не запускает бесконечный scan/refresh.
SQL/backend predicates, grants, schema/flags, CSV legacy fields и assignment
native selectors не менялись. Это исправление channel UI strings и picker
refresh, без нового authorization scope. Остальные legacy/global picker identity,
ChannelApiController CRUD authority/business policy, incident targets, legacy
attribution, S2B и рабочий rollout/acceptance остаются gates. Статус 01-278 — 🟡.

Проверки R180: 88/88 headless Edge DOM scenarios GREEN (52 notification,
36 assignment regression), 110/110 Node checks из 40 scripts GREEN, 5 node --check.
Реальный channel-editor.html и config/editor/shell/persistence/controls/picker
JS используются с fixture API; GET справочника и JSON POST перехватываются,
внешние URL блокируются. Проверены whitespace variants, comma/semicolon,
string ID >2^53, markup, оба списка, missing literal, directory failure, clear,
reset, disabled/absent control, case folding, malformed typed value без POST,
compatibility shapes, неизменённый legacy CSV, keyboard/Escape/Enter/focus.
Исходный baseline зависал после remote picker open: CDP Runtime.evaluate timeout
180s, Debugger в refresh/scan. После единственного observer fix тот же QA source
SHA дал 52 tests / 47 failures / 5 passing controls / 0 errors в v2. Остальные
четыре production inputs тогда оставались baseline blobs. Final notification
v4 и assignment v1 GREEN; diagnostic test после RED не менялся. Исходные
blobs/test/report SHA и timeout observation сохранены в ignored
r180-baseline-observer.json, r180-baseline-red.json и r180-browser-v2.json.
Полный Node run: 41 scripts / 111 checks, 1 старый R53 notes fixture failure.
Он отдельно воспроизведён на неизменённом main b92c1aa26; все 5 dependencies
совпадают в root/sandbox с baseline. R53 и код заметок не менялись; scoped Node
run повторил остальные 40 scripts без skip/cancel. Весь Node набор не GREEN.
Evidence: r180-node-v1.json/.log, r180-node-baseline.json/.log, r180-node-v2.json/.log.
Source/report/log SHA и diff --check проверены. Java=0, Docker=0; это не MVC/DB/
working-instance acceptance, backend R175/R177 здесь повторно не проверялся.

### Exact SLA preview and audit identities — R179

R179: SLA snapshot, escalation candidates и governance rule parser сохраняют
точные непустые account keys. Scalar assign_to, typed assign_to_pool, current/
previous responsible и upstream audit actor больше не обрезаются. Общий
package-local scalar helper exactAccountKey сохраняет исходный регистр, blank
и unpadded null sentinel обрабатываются как прежде; padded " null " остаётся
literal identity в соответствии с resolver R177. Fallback presence diagnostic
использует тот же контракт. Типизированный pool сохраняет прежние порядок и
case-sensitive dedup; строковая comma/newline CSV-грамматика не менялась.
Snapshot/candidate используют rawResponsible(), а не presentation accessor
responsible(): display name или прошлый resolvedBy не создают текущего назначения.
Snapshot больше не принимает literal ответственного за похожий plain account
и не скрывает корректное reassign-решение. Governance assignee_target совпадает
с буквальной целью фактического resolver. Notifier передаёт exact actor вместе
с frozen context в scoped writer; прежний sla_orchestrator fallback сохранён.
Rule owner/reviewer metadata, labels/matchers, UTC, приоритеты/конфликты, режимы
monitor/assist/autopilot, includeAssigned gates и settings schema не менялись.
Scoped eligibility, final writer/locks/CAS/atomic event, UI/JS, SQL/migrations/
grants/flags не менялись. Это исправление identity strings, без нового
authorization scope для preview и без подтверждения live DB/worker поведения.
Общий статус 01-278 остаётся 🟡.

Проверки R179: 201 Java tests / 56 свежих XML GREEN, Maven v3 exit 0,
failures/errors/skips=0. Новый SlaRoutingIdentityBoundaryTest: 50 cases —
single/typed-pool live resolver → snapshot/governance audit, exact current
responsible и reassign, escalation candidate payload/includeAssigned, notifier
→ captured scoped writer (actor и frozen owner), mode/writer gates, отсутствие
legacy writer/raw audit/webhook calls, display name/resolvedBy isolation, case/order/delimiter typed pool, legacy
CSV/labels и missing/blank/null fallback. Шесть literal variants включают
leading/trailing/both spaces, tab, newline и padded null. Endpoint/DB acceptance
этим набором не заявляется: writer и DB reader в notifier fixture — mocks.
Первый baseline v1 дал 46 tests / 32 failures / 0 errors. Затем добавлены
четыре raw-vs-presentation regressions без изменения прежних 46 cases.
До production edits окончательный test SHA на exact baseline blobs в v2 воспроизвёл
36 failures / 14 passing controls, errors/skips=0. Исходные XML/log/test SHA
сохранены в ignored r179-baseline-report.xml, r179-panel-v2.log и
r179-baseline-red.json; diagnostic test не изменялся после RED. Финальный v3
повторяет все Sla*Test и четыре service consumer suites, существующие тесты
не менялись. Source/class/XML/log SHA, generated CSS baseline и diff --check
проверены. Java-only scope: прежний R53 Node fixture debt из R178 не изменён
и в R179 не запускался; полной Node/browser/live acceptance нет.

### Exact channel assignment UI keys — R178

R178: typed assignment selectors в редакторе каналов сохраняют точные
непустые username без trim, с прежним case folding. Исправлены parseQuestionsCfg,
assignment catalog, single/pool rendering и сборка JSON payload. Typed arrays
отделены от прежней строковой CSV-грамматики; case dedup сохраняет первое
typed вхождение. Присутствующий пустой single select больше не подставляет
старый configured username; обязательность single/pool по-прежнему проверяет
существующий persistence runtime, без POST для отсутствующего выбора.
Options строятся DOM properties value/textContent вместо HTML-атрибутов:
CR/LF account key не нормализуется HTML parser, labels не исполняются как markup.
defaultSelected сохраняет native reset. Не добавляются отсутствующие в каталоге
accounts; похожее обрезанное имя не выбирается автоматически. Порядок selected
pool остаётся прежним порядком DOM options; нового управления порядком нет.
Общий employee-picker.js, id/name полей, runtime API, CSS/HTML, Java/backend,
SQL/migrations/grants и strategy/cursor semantics не менялись. Native picker
использует только свои options, без расширения глобальным справочником.

Граница R178 — channel assignment native selects. Notification CSV/typed-array
UI round-trip, прочие legacy/global pickers, SLA preview/audit parser и actor
label, ChannelApiController CRUD authority/business policy, incident user
targets и legacy attribution остаются отдельными gates. Refresh unsaved state
и управление порядком пула не переработаны. Общий статус 01-278 остаётся 🟡.

Проверки R178: 36/36 headless Chromium/Edge DOM scenarios GREEN,
95/95 Node checks из 38 regression scripts GREEN, 4 node --check GREEN.
Browser fixture использует актуальный channel-editor.html и реальные config/
shell/editor/persistence/controls/employee-picker runtimes; внешние зависимости
страницы заменены fixture, POST только перехватывается, внешние URL блокируются.
Пять whitespace variants проверены для single/pool restore → picker → captured
JSON POST → parse/reload и для отсутствующего target при наличии plain account.
Также проверены comma/semicolon keys, string ID >2^53, markup label, case dedup,
clear, disabled mode, legacy CSV, keyboard/Enter/Escape/focus и native form reset.
До production edits тот же QA source SHA дал 22 failures / 14 positive passes,
errors=0 в v4; baseline blobs/report/test SHA сохранены в ignored
r178-baseline-red.json и r178-browser-v4.json. Final browser report — v5;
проверки не ослаблялись после baseline, test SHA совпадает.

Полный Node run: 39 scripts/96 checks, 1 failure старого R53 notes fixture
(ожидает «Заметок нет.», получает error state). Сбой воспроизведён отдельно на
неизменённом main dd41dea3a; все 5 dependencies совпадают с baseline/root/sandbox.
Старый DOM stub не реализует replaceChildren текущего renderer. R53 и код
заметок не менялись. Точечный scoped run повторил остальные 38 scripts, без
skip/cancel; весь Node набор не объявляется GREEN. Evidence: r178-node-v1.log,
r178-node-baseline.json/.log и r178-node-v2.json/.log. Этот прежний test debt
нужен отдельным шагом. Source/report SHA, exact scope и diff --check проверены.
Java=0: серверные изменения R177 не менялись и здесь повторно не проверялись.
Это изолированные source-проверки, не приёмка рабочего экземпляра.

### Exact active assignment routing identity — R177

R177 закрывает обрезку account key в активном выборе ответственного:
ChannelTransportService сохраняет scalar/list assignment selectors без trim,
с Locale.ROOT case folding и case dedup отдельно от названий отделов.
Notification arrays используют тот же exact-key normalizer, их контракт R175
сохранён. ChannelAssignmentRoutingService читает single/pool keys и каталоги
без trim; scoped и общий assignable catalog DialogParticipantService возвращают
реальные username. DialogOperatorOption сохраняет этот ключ в compact constructor;
displayName/avatar/department/role по-прежнему нормализуются как labels. Legacy participant CRUD,
profile lookup и их прежний normalizeIdentity остаются отдельными путями.
Assignable catalog introspection привязана через to_regclass к реально используемой
users relation; одноимённая users.role другой схемы больше не ломает каталог.
ScopedAssignmentCandidateService больше не исключает реальный literal username
только за внешние пробелы: blank/ai_agent, unique enabled/unblocked account,
current OPERATE membership/ALL_BUSINESSES, active business/channel, frozen owner
и exact workload predicates сохраняются. Похожий plain account не даёт прав.
SlaEscalationAutoAssignService сохраняет single/fallback/typed pool identity,
operator skills/queues map keys и previous responsible без trim; identity map
lookup использует Locale.ROOT. Прежние blank и unpadded null sentinel сохранены;
labels, rule matching, typed-list grammar и порядок pool не менялись.
Финальные writers R176, locks/CAS/atomic event, namespaces и long cursor semantics
не менялись; новых migrations, grants, flags, JS/UI и внешних вызовов нет.
SettingsDialogSlaAiConfigService уже копирует typed значения без нормализации,
его исходник не менялся; round-trip проверен через SharedConfigService.

R177 не закрывает UI CSV/picker round-trip, SLA policy preview/audit parser,
upstream SLA actor label, ChannelApiController CRUD authority/business policy,
incident user targets, legacy responsible/active/spans attribution,
global/system/task/PermissionService и S2B/рабочий rollout. Общий статус 🟡.

Проверки R177: 494 Java tests / 15 свежих XML GREEN,
failures/errors/skips=0, Maven v4 exit 0. Новый PostgreSQL suite:
71 tests, полная Flyway V81-схема, два похожих реальных account с разными
business grants; отрицательные selectors и успешные сохранение → каталог →
resolver → финальный writer, real DB channel questions_cfg save/reload и
typed SLA settings file save/reload. Проверены пять whitespace variants,
single/pool/fallback, три стратегии и department queue, exact workload/capacity,
skills/queues isolation, exact previous identity и SLA reassignment/CAS event,
current revoke/disabled/blocked/duplicate/VIEWER/archive/foreign/channel/owner.
На baseline production blobs новый diagnostic method дал 25/25 failures,
errors/skips=0 (пять путей × пять вариантов). XML/log/test SHA и production
blobs сохранены в ignored .tmp/r177-baseline-red.json,
r177-baseline-report.xml и r177-panel-v1.log. Исходный test source SHA сохранён вместе с XML; diagnostic method/aliases
и account/grant/root fixtures неизменны в финальном GREEN.
Первый полный v2 (455 tests) обнаружил 27 failures только нового suite:
дополнительный trim в DialogOperatorOption после SQL-чтения. Исходный XML и
диагностика сохранены в r177-routing-v2-report.xml/r177-fix-v3.json; baseline
DTO blob проверен. V3 исправляет DTO, повторяет все 13 suites и добавляет
DialogQuickActionServiceTest и DialogWorkspaceWorkflowSnapshotServiceTest как
потребителей DTO. V3: 494 tests, 6 failures/1 error только нового suite. Пять
failures выявили metadata union чужих schemas (u.role), что исправлено внутри
assignable catalog; явный foreign-schema decoy теперь есть в каждом catalog test.
Две новые fixture assumptions исправлены: immutable public ID меняется только
через delete/recreate root, а create writer без channel key получает результат
resolver при disabled channel. SLA active-channel и остальные прямые writer
negatives сохранены; triggers/constraints не отключались. Исходные source/XML
и три точечных test transformations сохранены в r177-routing-before-v4.java,
r177-routing-v3-report.xml и r177-fix-v4.json. Diagnostic method/aliases и
account/grant/root setup побайтово прежние; в v4 все 15 suites повторены целиком.
Две прежние scoped fixtures обновлены по контракту: single mixed-case ALICE
без добавленного padding и previousResponsible с исходными внешними пробелами;
grant/root/frozen assertions сохранены, новые negatives не ослаблены.
Выбранные regression suites: ScopedAssignmentWriterIdentityPostgresTest (55), ScopedAssignmentCandidatePostgresTest (43), ScopedAssignmentRoutingServiceTest (23), ScopedSlaRoutingServiceTest (19), ChannelAssignmentRoutingServiceTest (3), SlaEscalationAutoAssignServiceTest (4), DialogParticipantSnapshotPostgresqlTest (73), DialogParticipantLegacyTicketGuardTest (3), LegacyParticipantWriteBoundaryPostgresTest (51), ScopedSlaAssignmentPostgresTest (26), ChannelApiControllerWebMvcTest (50), ChannelNotificationIdentityPostgresTest (34), DialogQuickActionServiceTest (35), DialogWorkspaceWorkflowSnapshotServiceTest (4).
Проверены source/class/XML SHA/freshness, exact file set, diff --check и
generated CSS baseline blobs. Node/browser=0: JS/UI не менялись. Это source
проверки, не функциональная приёмка рабочего экземпляра на другой машине.

### Exact final auto-assignment writer identity — R176

R176: два финальных auto-assignment writers сохраняют точный account key
и непустой actor без trim, при прежнем case-insensitive SQL lookup:
DialogResponsibilityService.assignScopedResponsibleIfMissing и
ScopedSlaAssignmentService.assign. Имя с leading/trailing/both spaces, tab или
newline не может воспользоваться OPERATE похожего обрезанного account. Реальный
literal account с собственным current OPERATE сохраняется без подмены в
ticket_responsibles и SLA audit; loadScopedResponsible возвращает точное
сохранённое имя существующего/concurrent ответственного. Blank target/actor
по-прежнему обрабатывается прежними fallback правилами; canonical ai_agent
исключён, прежние length limits SLA применяются к полному значению.
Не изменены SQL grant/owner predicates, enabled/blocked/unique identity, active
business, membership/ALL_BUSINESSES роли, advisory/root locking, CAS revision,
атомарный SLA event и typed pre-strict UNRESOLVED compatibility. Legacy bare-ID
assignment/loadResponsible/mark-as-read используют прежние отдельные пути.
Source minimum V81; новых migrations, flags, UI/JS или grants нет.

R176 закрывает именно конечные writers, а не всю цепочку назначения. Upstream
ChannelTransportService assignment config, ChannelAssignmentRoutingService,
DialogParticipantService directory, ScopedAssignmentCandidateService eligibility
(в том числе username=btrim(username)) и SlaEscalationAutoAssignService config/
skills/queues/previous identity ещё требуют согласованного exact-key прохода.
Уже обрезанное upstream имя final writer восстановить не может. Сохранены gates
UI CSV/picker round-trip, ChannelApiController CRUD authority/business policy,
incident user targets, legacy attribution, global/system/task/PermissionService,
S2B и рабочий rollout/acceptance; общий статус 01-278 остаётся 🟡.

Проверки R176: 451 Java tests / 9 свежих XML GREEN,
failures/errors/skips=0: 8 GREEN suites v2 (.tmp/r176-panel-v2.log) и полный
повтор ScopedSlaAssignmentPostgresTest v3 (.tmp/r176-panel-v3.log), финальный
Maven v3 exit 0. Source/class/XML сохранённых suites совпадают с финальными.
До исправления оба
production source были exact baseline blobs: новый v1 воспроизвёл 10/10 failures
(два writers × пять whitespace variants), errors/skips=0. Original XML/log/test
SHA и production blobs сохранены в ignored .tmp/r176-baseline-report.xml,
r176-panel-v1.log и r176-baseline-red.json. Diagnostic source SHA неизменен
в финальном GREEN; отрицательные assertions не ослаблены.
Новый suite использует полную Flyway V81 PostgreSQL-схему, два похожих реальных
account с разными business grants, положительную собственную literal identity,
три OPERATE роли через membership/ALL_BUSINESSES, late disable/block/duplicate/
VIEWER/revoke/archive/system-only/foreign, frozen owner replacement/tuple/GENERAL/
raw collision, exact stored/concurrent read, actor fallback и SLA redirect/CAS.
Отказ сверяет полные root/assignment/audit snapshots; успех — exact target/actor,
previous responsible, revision и event. Existing SLA suite дополнительно проверяет
реальный frozen reader, rollback audit failure, revision ABA и гонку workers.
В старом DialogScopedResponsibilityPostgresTest положительные grants/fallback
используют canonical mixed-case target без добавленного padding; stored и
concurrent return assertions теперь сохраняют уже существовавший literal padding.
Их DB/grant/root assertions сохранены; новые tests отдельно проверяют отсутствие
trim alias. V2 выявил один прежний upgrade comparator: V76→latest после V77
учитывал только responsibility_revision, но V81 уже добавляет два root поля.
Исходный test blob и неизменность migration chain подтверждены; failure/XML/source
сохранены в ignored r176-sla-upgrade-v2-report.xml, r176-sla-upgrade-v2.java и
r176-fix-v3.json. V3 добавляет явные assertions business_work_revision=0 и
business_categories=[], сохраняя exact comparison всех прежних root/assignment
fields, revision tracking и отсутствие SLA audit при legacy writes. Миграции
не менялись, assertions не удалялись, triggers/constraints не отключались.
Весь SLA suite (26 tests) повторён целиком. Остальные выбранные suites неизменны. Source/class/XML freshness,
exact changed-file set и generated CSS baseline blobs проверены. Node/browser=0
(UI/JS неизменны). Проверки не являются live provider/Redis/proxy/fleet или
функциональной приёмкой рабочего экземпляра на другой машине.

### Scoped auto-close и внешняя доставка

Scoped auto-close callback передаёт исходный V61 scope и channel ID события
в DialogNotificationService.notifyScopedSupportChat. Один SQL capture требует
полного expected owner, unique raw ID/non-GENERAL, совпадения canonical
ticket.channel_id с событием, активного канала и, для BUSINESS, active business.
Он читает immutable target из canonical channels, не из JPA-объекта callback:
token, support_chat_id, platform, platform_config, delivery_settings,
credential_id. После сборки HTTP request и client/network setup один повторный
SQL непосредственно перед HttpClient.send сверяет тот же owner/channel и все
поля target, включая NULL. Telegram/VK/MAX используют этот scoped guard;
VK/MAX scoped client учитывает IntegrationNetworkService, прежние raw paths
сохраняют свой transport contract. Detached channel copies не меняют frozen
target; ошибки конфигурации/отправки не открывают raw fallback и в scoped
логах не раскрывают токен через exception text. Target.toString не выводит секреты.
UNRESOLVED с locator допускается только до strict-read; GENERAL, invalid scope,
missing schema, неактивный либо неизвестный destination закрывают отправку.
Это proof последнего SQL перед handoff, не транзакционная блокировка HTTP,
не version registry настроек канала на момент исходного события и не проверка
прав участников внешнего чата. Inherited/global network profiles и credential
contents отдельно не версионированы. Действующие grants внешнему destination,
остальные raw support-chat producers и полноценные scoped AI effects ещё PENDING.
Уже корректно записанный bell не откатывается при позднем отказе support-chat.

Mixed-version consumers без нового guard
не доказывают эту изоляцию; отдельный rollout/preflight остаётся обязательным.

Ticket-bound bell delivery сохраняет точный `ticketId` через
`NotificationService.notifyDialogUsers` до условного SQL INSERT: snapshot
требует единственного owner и явного `is_general=FALSE`. Возвращаются только
реально записанные получатели; отказ не считается успешной рассылкой.
Recipient child reads повторяют тот же predicate, а поздний отказ не запускает
fallback на всех операторов. Legacy queue, realtime, AI escalation и
`OperatorNotificationWatcher` используют этот путь. GENERAL history/SLA rows
не обрабатываются этим watcher. Общие system notification methods сохраняют
свой контракт; их URL не является authority для ticket scope.

Additive V60 сохраняет у новых ticket-bound уведомлений immutable snapshot:
точный `(ticket_user_id, ticket_id)`, nullable `ticket_public_id`,
`ticket_scope_kind` и `ticket_business_id`. Snapshot получается из owner
в том же условном INSERT, а не из URL/текста. BUSINESS требует business ID,
UNRESOLVED и GENERAL — его отсутствия; частичный tuple запрещён CHECK.
Trigger запрещает изменение всех пяти полей, включая назначение owner старой
строке с NULL. Исторические и generic/system/task уведомления сохраняют NULL
без автоматической классификации и backfill. FK на изменяемое/удаляемое
обращение намеренно отсутствует: snapshot остаётся историческим фактом.

Legacy bell list/page/count/read/read-all применяют один SQL predicate:
получатель совпадает, canonical tuple/public ID и business соответствуют
snapshot, текущий owner имеет `is_general=FALSE`. Исчезновение owner, смена
scope/business или другого non-null public ID закрывают чтение и read update.
GENERAL исключён даже при отдельном READ grant; его bell ещё не реализован,
а realtime R134 изолирован отдельными endpoint/topic. Фильтрация предшествует cursor pagination/aggregation, read
updates условны в том же SQL и сигнализируют только фактическое изменение.
Коллизия bare ID после сохранения не подменяет owner с полным tuple; legacy
dialog URL при этом по-прежнему закрывает свой ambiguous route.

При default-off `strict-read-scope` сохраняется совместимость unclassified
NULL и подтверждённого non-GENERAL UNRESOLVED. В strict-режиме оба варианта
скрыты, включая generic/system/task rows, пока для них не введена отдельная
явная global/system policy. Для типизированных BUSINESS-уведомлений действующий
business READ проверяется одним SQL predicate при INSERT, list/page/count и
условных read/read-all. Он требует единственного case-insensitive canonical
аккаунта получателя, `enabled=TRUE`, `is_blocked=FALSE`, активного бизнеса и
активной membership либо `ALL_BUSINESSES` с одной из текущих READ-ролей
`VIEWER/OPERATOR/MANAGER/ADMIN`. Системный `ROLE_ADMIN` и отдельный GENERAL READ
не подменяют business grant. Архивация бизнеса, блокировка аккаунта и отзыв
всех независимых grants закрывают следующую SQL-операцию, включая старую сессию.
Фильтр действует до pagination/aggregation; read updates не меняют скрытые rows.
Новый future business сразу доступен по действующему `ALL_BUSINESSES`.

Предикат business READ разделяется JDBC writer, JPA native repository и SSE;
проверка ticket identity/owner остаётся обязательной. Наличие read capability
у всех четырёх текущих ролей соответствует `BusinessAccessService`; разрешения
не выводятся из текста/URL, чужой users DB либо browser-selected business.
UNRESOLVED/unclassified сохраняют описанную выше совместимость; этот фильтр
не классифицирует исторические строки. Глобальная page authority, явная
global/system/task policy, полноценный scoped AI pipeline, внешняя support-chat recipient policy, остальные assignment writers и прочие producers ещё
требуют отдельного контракта/аудита. Это не разрешение включать strict flag
или выполнять rollout.

Для канала только с общими обращениями `GENERAL_CHANNEL` включается отдельной
записью `general_channel_question_template_bindings`: активный канал должен
явно выбрать тот же ID вопросника, не иметь активных business bindings, а
сохранённый SHA-256 должен совпадать с текущим шаблоном. Изменение состава или
порядка вопросов требует повторного подтверждения привязки. На текущем этапе
general-only runtime отклоняет preset-вопросы, пока для них не определён
безопасный, не связанный с бизнесами каталог. Отсутствие бизнес-привязки само
по себе никогда не включает `GENERAL`.

Основной internal channel runtime endpoint при активной general-only привязке
выдаёт тот же проверенный вопросник и явные `scopeKind=GENERAL`,
`scopeSelectionSource=GENERAL_CHANNEL`, ID/SHA-256 шаблона. Устаревшая привязка
или выключенный флаг дают конфликт, а не legacy fallback. Bot session обязана
сохранять выбранный scope до создания обращения; старые ответы другого scope
не подставляются в general-only опрос. ID/SHA-256 передаются из сессии через
command/event, outbox и inbox в nullable immutable поля ticket. Новое обращение
`GENERAL_CHANNEL` принимается только при точном совпадении версии с проверенной
текущей привязкой; устаревший или неполный snapshot отклоняется до записи корня.
После создания replay сверяет сохранённую версию ticket, не текущую привязку.
Смена/удаление ID либо SHA-256 при повторном event запрещена. Старые обращения
не получают версию из сегодняшней конфигурации. Завершение старых незаконченных
сессий после rebind пока требует отдельного version registry/UX; без него
устаревший опрос нужно начать заново. Очередь и права GENERAL, shared-channel
selection и сквозная provider-приёмка всё ещё обязательны до rollout.

На S4 вводится business-scoped registry template bindings. Existing
`question_template_id`, `rating_template_id`, `auto_action_template_id` и
`questions_cfg` сохраняются как legacy transport/config fields. Новый template
для business создаётся clone с новым stable template ID в canonical
`bot_settings`; редактирование template B не может менять template A. Пока
JSON-owner и precedence не утверждены, S1 не меняет эти поля.

### R143: read-only handshake выбора BUSINESS в общем канале

GET /internal/api/bot/channels/{channelId}/questionnaire/choices требует
существующий internal token/HMAC guard и foundation + shared-channel-resolution.
Меню версии 1 содержит только ID/code/display name бизнесов канала и ID/SHA-256
их шаблонов, без вопросов/глобальных settings. Все активные привязки должны
быть EXPLICIT_SELECTION, business/channel/template — active, шаблон — distinct,
не legacy/general-only, без unsupported presets и duplicate question IDs.
Неполный/stale/mixed набор закрывает весь новый каталог. До 100 вариантов;
более крупный каталог требует отдельного pagination/UX-контракта.

GET .../questionnaire/selection принимает businessId и catalogSha256 исходного
меню. Любое изменение видимого состава/имени/template version требует нового
выбора; шаблон не захватывается молча из свежей привязки. Конфигурация выбранного
BUSINESS/USER_SELECTION получается из того же read snapshot, только с одним
вопросником и пустыми locationTree/presetDefinitions. Оба ответа no-store.
Старый business runtime endpoint сохраняет R112 semantics.

Основной unscoped runtime endpoint больше не выдаёт legacy settings каналу
с активными business bindings, даже при выключенных flags: нужен явный выбор.
Общий PanelRuntimeConfigClient не кэширует legacy/general runtime между
lookup и не возвращает local fallback при ошибке настроенного API. Новый SDK
choices/select проверяет channel/business/version/catalog hash, membership
выбора, точную форму settings и SHA-256 фактических bytes шаблона. Неизвестные
scope/версии, подмена payload или глобальные presets/settings закрыты.
Совместимость legacy runtime остаётся только для unbound channels; отключённый
Panel API/другие transport modes ещё требуют отдельного аудита.

R143 сам по себе — read-only handshake, не право создать ticket. Контракт
BUSINESS producer/INSERT/replay добавлен в R144 ниже; MAX selection UX — в R145,
VK — в R146, Telegram — в R147. GENERAL option общего канала, scoped locations и сквозная
приёмка resume/UX после rebind остаются PENDING. Новый shared-channel flow нельзя включать до
подключения совместимых consumers; существующий general-only flow сохранён.

### R144: создание BUSINESS по сохранённому выбору вопросника

QuestionnaireScope для BUSINESS содержит положительный businessId,
USER_SELECTION, stable template ID, SHA-256 шаблона и catalogSha256. Неполный
BUSINESS snapshot не десериализуется как legacy/GENERAL. Старый GENERAL snapshot
совместим с nullable новыми полями. Command и оба event DTO передают catalogSha256
без повторного lookup; Telegram/VK/MAX finalizers используют businessId и
отпечатки из scope сессии. Это подготовка finalizers, не подключение меню выбора.

Новый BUSINESS/USER_SELECTION INSERT требует foundation, strict ticket writes
и shared-channel-resolution. До записи корня backend проверяет platform канала,
полный актуальный каталог R143, membership выбора и точные template ID/SHA.
Изменение любого варианта меню, rebind или stale template требует начать выбор
заново; нельзя молча получить новую версию или вывести owner из display business.
Канал с активным EXPLICIT_SELECTION не принимает новый legacy/unresolved или
непроверенный GENERAL envelope, в том числе при выключенном strict flag.
SINGLE_BUSINESS сохраняет отдельную прежнюю проверку однозначной привязки.

V73 добавляет nullable immutable business_selection_source и
questionnaire_catalog_sha256 в tickets. CHECK допускает только полный
BUSINESS/USER_SELECTION с owner и версией шаблона; GENERAL поля не переиспользуются.
Исторические строки не получают сегодняшнюю конфигурацию. SQL trigger запрещает
смену/удаление новых полей; readiness требует новую схему до READY.

Outbox и inbox сверяют catalogSha256 наряду с owner/scope/template identity
до пропуска duplicate или reclaim. Replay существующего canonical tuple
сверяет frozen owner/source/template/catalog/channel, а не нынешние bindings;
новый event ID не позволяет потерять selection provenance. Отключение strict
flag также не превращает уже выбранный BUSINESS root в legacy replay.
При ошибке дочерней записи root/children откатываются, failed inbox допускает
точный повтор. Сами transport event IDs и display поля не выдают права доступа.

Подтверждены typed JSON/CAS scope и MAX state round-trip. Подключение MAX
pending choice/selection UX описано в R145, VK — в R146, Telegram — в R147.
GENERAL option общего канала требует отдельной явной настройки, а не ослабления
general-only binding. Нет новых scoped presets/locations, version registry для
старых незавершённых опросов или разрешения на flags/migration/rollout.

### R145: MAX — выбор до первого вопроса и восстановление сессии

MAX при отказе primary runtime может запросить только проверенный BUSINESS
каталог, не локальные/global settings. Для choices/selection с forStart=true
обязательны foundation + shared-channel-resolution + strict writes, internal
token/HMAC, no-store и X-Iguana-Questionnaire-Writer-Version: 1. SDK требует
явный ответ этой версии на обоих этапах: старый backend, игнорирующий query,
не считается готовым к созданию обращений. Read-only R143 API совместим.
Новый selection path работает только с RabbitMQ + настроенным Panel API и
активным channel; audit старых non-Rabbit/disabled-API modes остаётся PENDING.

До первого вопроса сохраняются каталог, его SHA, случайный nonce меню и
исходный provider event key. Нативные MAX message buttons содержат business
name/code и ограниченный по длине код выбора. Код принадлежит этому nonce,
user/channel/chat и одному из business IDs сохранённого каталога. Чужая или
старая кнопка после /start не выбирает новый бизнес и не становится ответом
на первый вопрос. CAS не позволяет старому обработчику заменить чужой выбор.

Проверенный выбранный payload преобразуется в DTO без повторной нормализации,
смены order или channel/local overrides. Нет обязательных business/problem
вопросов и подстановки прошлых бизнес-ответов. Bootstrap, flow, ответы,
scope/ID/SHA/catalog и nonce сохраняются в существующем session store.
Старый legacy flow после смены режима канала требует новый /start.

До первой постановки в очередь выбор проверяется ещё раз; stale/unavailable
даёт явный /start без захвата новой версии. После подготовки публикации
сохранены последний ответ и фиксированный submission event key. Retry
публикует тот же command без свежих bindings. После принятия в очередь
сохраняется маркер с ticket ID: потеря delivery dedup cache не создаёт другой
опрос/обращение. Это транспортное состояние, не canonical ticket source of truth.
Новый follow-up ожидает именно этот canonical root, не другой active ticket;
pending/retry и terminal state не удаляются перед публикацией.

Создание и follow-up этого selected MAX flow не отправляют raw summary в
непроверенный support chat. Это локальная защита нового пути, не завершённая
recipient policy всех legacy/direct/active путей. Общие правила destinations,
TTL/retention сессий, одновременные обращения разных бизнесов, live Redis/MAX/
proxy и rolling-upgrade acceptance ещё обязательны до rollout.
Проверки используют реальный handler/JSON/CAS с изолированными doubles для
доставки и отдельные PostgreSQL suites; это не живой provider E2E.

После VK R146 и Telegram R147 GENERAL option общего канала, scoped locations/presets и release
остаются отдельными этапами. Новых migrations нет; minimum schema — V73.
Необходимость совместимого backend/fleet не разрешает менять рабочие flags,
запускать migration/backfill, rebuild или deployment без отдельного scope.

### R146: VK — выбор, сохранённый опрос и Callback/Long Poll retry

VK использует тот же writer-gated choices/selection и выбранный loader R145.
Новый BUSINESS опрос не использует channel/global overrides, legacy defaults,
прошлые business answers либо обязательный problem. GENERAL-only и unbound
legacy остаются отдельными совместимыми ветками, не вариантами общего меню.

До отправки меню сохраняются каталог/его SHA, nonce, стартовый conversation
message ID и страница. До первого вопроса сохранены scope/template ID/SHA,
flow/settings, bootstrap и ответы. Нативные text buttons несут JSON payload
с nonce и business ID; видимый label не является authority. До 8 бизнесов на
странице, 2 кнопки в ряду и отдельная навигация: не более 5 рядов/10 кнопок.
Усечённые имена сохраняют ID suffix; текст страницы и ручной код позволяют
различать варианты. Страницы не меняют каталог, /start создаёт новую генерацию.
Старые, чужие и malformed payload не превращаются в ответы вопросника.

В существующей конфигурации vk-bot.channel-id берётся из VK_OPERATOR_CHAT_ID:
это внешний служебный peer, не channels.id. Новые selected sessions используют
отдельный namespace vk-questionnaire-v1 + канонический channels.id, разрешённый
через token. Внутренний message guard тоже привязан к этому ID; общий служебный
чат не смешивает опросы двух каналов одного клиента. Старый vk/legacy namespace
совместим отдельно и не выдаётся за selected state. Рабочие ключи не переносятся
и не удаляются миграцией. Legacy ingress/job lease namespaces требуют отдельного
аудита; новая схема сессий не доказывает готовность всей fleet coordination.

Callback и Long Poll используют общий guard по peer/conversation_message_id,
а не только event_id envelope. В новой сессии сохранён список принятых answer
events (до 4096, превышение — отказ, не усечение). Он сохраняет дедупликацию
ответов при потере transport cache. Сообщение с conversation message ID меньше
стартового относится к предыдущему опросу и не заполняет новый бизнес.
Long Poll продвигает ts только после успешной обработки пачки; повтор пропускает
ранее завершённые сообщения. CAS conflict после трёх попыток остаётся retryable,
не подтверждается как обработанный. BUSINESS требует положительный stable
conversation_message_id; дата, текст и random ID его не заменяют.

До первой публикации проверяются актуальный выбор и отсутствие другого active
root. Последний ответ, submission key и accepted-answer ledger сохраняются до
очереди. Retry после ошибки очереди использует тот же command, даже при смене
bindings. После queue acceptance сохранён ticket ID marker; ошибка записи самого
marker допускает повтор только того же детерминированного command, не новый root.
Следующее сообщение при восстановлении pending публикации остаётся retryable,
не теряется под подтверждением завершения опроса. Follow-up ждёт именно свой
canonical root и проверяет user/active ID; другой active ticket его не перехватывает.

Новый selected VK путь text-only: вложения явно отклоняются до storage/ответа,
без unscoped upload. Raw support-chat summaries/follow-up и прежний rating prompt
для этого пути не отправляются; shared-channel active shortcut без сохранённого
контекста также не публикует raw сообщение. Это не завершённая общая destination,
media/feedback, follow-up event retention или permission policy остальных путей.

Selected sessions не удаляются legacy first-response scheduler: сохраняются до
явного /start, /cancel либо TTL store. TTL/retention, dedup TTL, non-Rabbit/API-off,
rolling upgrades, несколько active бизнесов и live Redis/VK/Long Poll/proxy
проверяются отдельно. Session store остаётся transport state, не canonical DB.
Новых migrations/flags нет; минимум V73. После Telegram R147 shared GENERAL option,
scoped locations/presets и release остаются PENDING; rollout этим не разрешён.

### R147: Telegram — отдельный сохраняемый BUSINESS опрос

Новый TelegramQuestionnaireHandler обрабатывает только private user/chat tuple
активного Telegram channel. Он вызывается основным SupportBot до legacy edits,
feedback, active shortcut и media. GENERAL-only/unbound runtime остаются в
прежней ветке; отказ настроенного scoped API не открывает local/global fallback.
BUSINESS старт/selection использует writer-version ACK и selected loader R145.

Pending меню хранит catalog/SHA, nonce, страницу, начальный provider message ID
и identity клиента. Нативные reply buttons посылают generation-bound текст;
до 100 бизнесов, 8 на страницу. Кнопки выбора/навигации после выбора не становятся
ответами. Выбранный payload, исходный order, ответы, history/provider IDs,
посещения вопросов и accepted-answer ledger сохраняются в JSON/CAS namespace
telegram-questionnaire-v1 + canonical channels.id. ID служебного чата не
используется как channel/session authority; delivery guard также canonical-scoped.

Вопросник не получает обязательных business/problem или прошлых business answers.
Сохраняются select по номеру/label, forward routes по option ID, возврат назад с
очисткой следующих ответов и optional skip. Bootstrap относится только к новому
началу: оставшийся legacy/GENERAL in-memory ответ при rebind не переносится в
новый BUSINESS history. Меню сохраняется до отправки; выбор и каждый ответ —
до следующего prompt. Selected marker не находится в legacy scheduler map.

До первой публикации проверяются current catalog/selection и отсутствие другого
active root. Последний ответ и fixed submission message ID сохраняются до queue.
После ошибки публикации/marker persistence повторяется тот же command, не новая
версия/owner/ответ. Сохранённый ticket ID marker и accepted ledger переживают
потерю update dedup cache. Сообщения до нового /start и запоздалые более ранние
ответы не продвигают новый опрос. CAS conflicts после трёх попыток не подтверждаются.

Follow-up ждёт свой canonical root/user/active ID; другой active ticket не
выбирается. Текст нового сообщения, пришедшего во время восстановления публикации,
не подменяет последний ответ: клиент получает явную просьбу повторить сообщение,
обработка завершается retryable error. Это не durable очередь follow-up. Живая
семантика Telegram Long Poll offset/re-delivery после exception ещё не доказана;
настоящие recovery/доставка требуют отдельной приёмки, а не только unit replay.

Новый путь text-only: media/unsupported payload и edits явно отклоняются до
unscoped storage/writer. Selected commands не вызывают legacy ticket-list/reply
маршруты; raw summary/support-chat relay/rating prompt не отправляются. Blacklist
сохраняется. Saved state проверяет user/chat/channel/version, template membership,
progress, только свои text history и известные answer keys. Это локальная защита,
не завершённый общий recipient/child attribution или media/feedback policy.

TTL/retention/4096-event budget, non-Rabbit/API-off modes, fleet/rolling upgrade, Long Poll/proxy/Redis,
GENERAL/legacy session persistence и несколько active бизнесов остаются PENDING.
Session state не является canonical relational source of truth.
При direct coordination BotSessionStoreService хранит state в памяти процесса;
durability полного process restart требует Redis mode и отдельной приёмки.
Тесты восстанавливают handler/бота с общим store, а не доказывают live Redis restart.
GENERAL option общего канала не добавлена: она требует собственного явного binding и writer,
а не ослабления BUSINESS или general-only API. Scoped locations/presets и release
также отдельные этапы. Новых migrations/flags нет; минимум V73, rollout не разрешён.

### R148: отдельная настройка и read-only v2 preview варианта «Без бизнеса»

V74 создаёт пустой `shared_channel_general_question_template_bindings` с FK канала,
unique template ID и SHA-256. Это не расширение GENERAL-only V57: для новой
привязки нужен active shared channel с полностью валидным BUSINESS/EXPLICIT_SELECTION
каталогом и отдельный canonical шаблон. Legacy/channel/global-active, business
и GENERAL-only шаблоны не переиспользуются. Шаблон без business/problem вопросов
допустим; его исходные question IDs/order сохраняются. Presets, пустые flow,
duplicate template/question IDs запрещены. Deactivate не освобождает template ID
для другого scope; повторное bind обновляет явную привязку и frozen SHA.

GET/PUT/DELETE `/api/business-admin/shared-channels/{channelId}/general-question-template`
требуют foundation и одновременно PAGE_SETTINGS + системный ROLE_ADMIN;
мутации — CSRF. Business ADMIN/ALL_BUSINESSES не заменяют эти полномочия.
Успешные bind/deactivate оставляют actor/channel/template audit log, но не создают
grants. Три template-binding writer используют общий PostgreSQL transaction-scoped
advisory lock по template ID; GENERAL-only сохраняет запрет active business bindings.
Удаление всех business bindings не открывает legacy runtime/INSERT: active shared
GENERAL registry сам требует explicit selection даже при выключенных flags.
GENERAL-only на том же канале также требует сначала явного deactivate shared GENERAL.
Внешние обходные SQL/config edits не узакониваются: runtime повторно проверяет
reserved references и fingerprints и закрывается при конфликте.

Подписанные GET `/internal/api/bot/channels/{channelId}/questionnaire/v2/choices`
и `/selection` — **только preview**. Требуют foundation + shared-channel-resolution +
general-channel-resolution, token/HMAC с query и no-store. Каталог версии 2 содержит
BUSINESS choices и только явно включённый GENERAL с `businessId=null`, не 0 и не
UNRESOLVED. Выбор задаётся `scopeKind`, nullable `businessId`, `catalogSha256`.
GENERAL имеет USER_SELECTION, собственный template ID/SHA и общий catalog SHA.
Payload содержит ровно выбранный вопросник; global settings/presets/location tree
не выдаются. В каталоге не более 100 вариантов **с учётом GENERAL**. Повреждённая
active GENERAL настройка закрывает весь v2 каталог; отключение/перепривязка GENERAL
и изменение любого видимого BUSINESS варианта делают прежний выбор устаревшим.
DB snapshot использует REPEATABLE_READ; настройки из файла проверяются по frozen SHA,
но не становятся частью PostgreSQL-транзакции. Preview меню не загружает runtime
payload всех бизнесов повторно; selection выдаёт только выбранный BUSINESS payload.

V1 API/SDK/боты и v1 writer ACK сохраняются отдельно. V2 `forStart=true` всегда 404
и **никогда не возвращает writer-version ACK**, даже при всех включённых flags.
V73 writer пока отклоняет GENERAL/USER_SELECTION с catalog SHA; preview не является
разрешением записи или запуска опроса. Следующий связный этап — v2 INSERT/replay и
immutable GENERAL provenance, затем SDK/session/menu consumers всех трёх ботов.
Readiness проверяет V74; рабочая migration/flags/restart/rollout не выполнялись
и не разрешены этим source-only шагом. Scoped locations/presets и общая приёмка
остаются открытыми.

### R155: Telegram selected followup media и frozen root version

V76 добавляет nullable immutable tickets.questionnaire_version. Новый selected
INSERT записывает 1–4 из проверенного backend-каталога; GENERAL-only/legacy/manual
без такого каталога остаются NULL. Existing rows не backfill-ятся и version NULL
не превращается в 4 после retry/rebind. SQL CHECK и trigger запрещают подмену и
удаление версии. Старое scoped чтение draft attachments не требует новой версии.

GET /internal/api/bot/channels/{channelId}/questionnaire-media?userId=...&ticketId=...
является token/HMAC-signed no-store admission только для Telegram root version=4.
Он проверяет canonical tuple, active channel/root, USER_SELECTION, frozen scope/
template/catalog и remainingFiles. BUSINESS/GENERAL различаются nullable business ID;
GENERAL дополнительно требует своего flag. Foundation/strict/shared flags обязательны.
Нет lookup текущих bindings/resources и нет local/legacy fallback. Capability version=1
в ответе — версия admission протокола, не версия каталога. SDK проверяет все поля
против сохранённого выбора и не кэширует admission.

Telegram допускает те же 8 типов и 1 байт–10 МиБ/файл после регистрации своего root.
Клиентский бюджет 10 файлов включает draft и followup; операторские/legacy attachment
writers этим пакетом не переводятся на общую quota policy. Неподтверждённый/чужой/
закрытый root, старые catalog sessions и исторический root без version=4 не открывают download.
Полный pending media command snapshot сохраняется CAS до enqueue. При первом таком
событии session становится v2; новый consumer читает старый v1 без followup fields,
а старый consumer отвергает v2 вместо потери prepared события. Rolling upgrade/fencing
требует живой приёмки. Нельзя объявлять её выполненной по unit JSON restore.

Inbound writer для selected Telegram media и новых scoped namespaces повторяет
проверку frozen canonical root и provider uniqueness до INSERT. Ticket/channel/active
locks вместе с существующим legacy-ID guard сериализуют close/media/quota paths.
Проверяются реальные bytes, key/type/name/hash/size; history/metadata/profile/activity
записываются одной транзакцией. Failed event допускает точный retry, processed inbox
duplicate не открывает файл и не зависит от нынешних flags/bindings. Creation history
и answers не дополняются followup payload; raw operator relay/AI/feedback не вызываются.
MAX legacy attachment admission сохраняется отдельно, не объявляется безопасным
новым протоколом. VK selected followup пока не открыт.

Namespace questionnaires/telegram и прежний scoped download contract сохраняются:
frozen root/provider proof, current READ и bytes SHA/size. Legacy URLs не открываются.
Остались old-root admission policy, VK followup, edit/delete/feedback, retention/orphan
reconciliation и live Redis/provider/S3/proxy/fleet acceptance. Source minimum V76;
рабочая migration/flags/restart/deploy этим этапом не разрешаются.

### R156: VK selected followup batch, точный replay и атомарный inbox

Дата: 2026-10-21. Продолжение R155; его запись о закрытом VK followup — история.
Новые v4 BUSINESS/GENERAL roots с frozen questionnaire_version=4 принимают фото,
документы и audio_message после регистрации. Исторические NULL/v1–v3 roots не
получают эту возможность автоматически. SQL minimum остаётся V76, новой migration нет.

- Перед файловым I/O отдельный token/HMAC GET questionnaire-media/vk-batch возвращает
  capability version=2. Telegram endpoint/version=1 не подменяет VK-допуск. SDK
  сверяет user/channel/ticket, nullable business/scope, frozen template/catalog и
  remainingFiles. Текущие bindings/resources не перечитываются для owner admission.
- 1 байт–10 МиБ/файл и общий бюджет 10 с учётом draft/принятых followup. Бюджет
  относится к этому client ingress, не ко всем операторским/legacy writers.
  До скачивания проверяются весь batch, свой active root и доступное число мест.
  VK CDN/redirect/size правила и owner/provider/slot/content-addressed keys R154
  сохраняются. Creation history/answers не меняются; raw relay/feedback/AI нет.
- Один conversation_message_id порождает одно versioned событие
  client_message.selected_media_batch.v1, не N одиночных событий с конфликтующим ID.
  UUID привязан к kind/channel/user/length-prefixed opaque ticket/provider.
  Слот — точный последовательный индекс 1..N; весь batch и профиль/время исходного
  сообщения сохраняются CAS до enqueue. Pending replay не скачивает файл, не
  перечитывает вопросник и не заменяет подготовленный payload изменённым retry.
- Ledger после submitted marker сохраняет новые provider keys, не меняя сам marker.
  Старый consumer R155 отвергает такой snapshot прежней проверкой «submission — last»;
  новый проверяет pending owner/slots/provider/time/budget и читает старые snapshots
  с отсутствующими pending/counter. Legacy sessions без selection сохраняют прежнюю
  attachment policy. Это source fencing, не доказанная rolling-upgrade приёмка.
- Backend под legacy-ID guard и root/channel/active locks проверяет frozen root,
  стабильный event ID, platform/type/name, уникальность provider, quota и реальные
  bytes/size/SHA всех slots до первого child INSERT. Затем history/metadata/profile/
  activity и processed inbox marker нового batch коммитятся одной транзакцией.
  Deferred commit failure не оставляет processed inbox без данных; после такой
  ошибки processing reclaim использует существующий timeout 15 минут.
- Outbox/inbox сохраняют полный batch payload; совпадение event ID с другим
  содержимым — отказ, включая failed/processed replay. Сравниваются JSON trees
  после одинакового serialize/parse, без различия внутренних IntNode/LongNode.
  Exact processed replay не читает storage/current flags/каталог. Одиночный selected
  VK media writer не является обходом нового batch verifier. MAX protocol не менялся.
- BUSINESS/GENERAL download использует прежние frozen root/provider/slot proof,
  metadata hash/size, bytes и current READ. Отзыв grant закрывает файл; legacy URLs
  не открываются. Enqueue не подтверждает backend commit, operator/client delivery.
- Для выпуска нужны совместимые panel web/worker, core outbox и VK consumer.
  Старый core outbox не публикует незнакомый event kind; R155 writer отвергает
  scoped VK вместо частичной записи, но это не разрешает mixed fleet со всеми
  историческими versions. Admission от нового web не доказывает обновление worker.
  Shared storage, Redis/Rabbit/VK/proxy, TTL/retention/orphans/DLQ/reconciliation,
  historical-root policy, scoped edit/feedback и прочие S2B/S3–S6 gates остаются.
  Рабочие DB/flags/контейнеры, deployment и rollback этим source-пакетом не меняются.

## Scope guard и API contract

Один сервис `BusinessAccessService` должен предоставлять:

```text
resolveAccessibleBusinessIds(authenticated users.id)
resolveEffectiveBusinessCapabilities(users.id, business_id)
requireReadableTicket(ticket locator, authenticated user)
requireWritableTicket(ticket locator, action, authenticated user)
requireSelectedBusiness(requested context, authenticated user)
```

Контракт:

- Доступ к сущности сначала резолвится по её canonical owner, затем проверяется
  against accessible IDs. Нельзя сначала загрузить entity по public ID и
  передать её в UI/notification до scope check.
- `business=all` — допустимый presentation context только для union resolved
  `accessibleBusinessIds`; конкретная write operation всегда имеет один
  business owner.
- Неавторизованный direct entity lookup возвращает нераскрывающий ответ (для
  data endpoint — `404`), а invalid/forbidden selected context — `403` с
  безопасным сообщением. Counts, pagination, search, export, attachments,
  autocomplete, SSE и AI retrieval используют тот же guard.
- Internal transport callbacks не получают operator grant. Они несут trusted
  resolved business context, который сверяется с ticket/channel binding и
  сохраняется до outbox. Unknown context останавливает обработку.
- У event/outbox/notification есть явный `scope_kind`:
  `BUSINESS`, `GENERAL`, `GLOBAL_SYSTEM` или `UNRESOLVED`. `GENERAL` — намеренно
  выбранное обращение без бизнеса, `GLOBAL_SYSTEM` — системные данные. Оба
  требуют отдельного права; `ALL_BUSINESSES` сам по себе его не предоставляет.

## Legacy compatibility и decision table

| Область | Безопасное временное поведение | Решение, необходимое до реализации |
| --- | --- | --- |
| Три непустых legacy aliases | Dataset тестовый, не переносится | **СНЯТО R190:** alias map и historical assignment не требуются |
| Три пустых `messages.business` | Dataset тестовый, не переносится | **СНЯТО R190:** решения по этим строкам не требуются; новые unresolved остаются fail-closed |
| 3 history channel mismatch | Dataset тестовый, не переносится | **СНЯТО R190:** классификация старой истории не требуется; новый child scope определяется canonical ticket |
| Mixed channels | Явный выбор бизнеса или «Без бизнеса» до первого вопроса; без выбора flow не запускается | **УТОЧНЕНО 2026-10-16:** general — отдельный scope, не unresolved. **PENDING:** UX-представление и precedence для иных verified sources |
| Ручные задачи / global Kanban | Две старые задачи не классифицируются и не переносятся | **PENDING:** R12 provenance/visibility/link policy для новых ручных задач и право на global Kanban |
| Новые incidents / system notifications | 105 старых инцидентов не классифицируются и не переносятся | **PENDING:** модель business/shared/global для новых событий, получатели и отдельные permissions |
| Existing ticket route | Legacy route доступен только в подтверждённом unique window | **РЕШЕНО S2A:** opaque immutable `ticket_public_id`; internal fallback — обязательный composite locator |
| Roles | Global page permissions не выдаются автоматически; channel management требует системного ROLE_ADMIN + права страницы (R184) | **PENDING:** оставшаяся global/system и business capability policy |
| Projects/boards | Не показывают inaccessible tasks | **PENDING:** scope project/board and explicit-global task policy |

## Поэтапное применение

1. **S1, additive foundation.** Свободная на дату реализации Flyway version
   выше V50; новые tables/nullable FKs/indexes, `BusinessAccessService`,
   default-deny contract и PostgreSQL integration tests. Feature flags off;
   никаких existing-row mutation, UI switch или strict filtering.
2. **S2, canonical identity.** S2A public/composite locator и security guards
   сохраняются для новых данных. Historical S2B alias assignment/dry-run/
   backfill снят R190. Проверяются чистая схема, canonical owner и child tuple
   integrity новых записей; перенос старого dataset не является gate.
3. **S3, server scope.** Read/write API, dialogs, attachments, tasks,
   projects/boards, analytics, notification and realtime/outbox paths; negative
   cross-business tests before strict flag.
4. **S4, channel runtime.** Shared-channel resolver, per-business template
   registry and frozen ticket context across Telegram/VK/MAX replay.
5. **S5, UI.** Accessible-business selector, business catalog/memberships,
   empty states and manual acceptance. Browser state can select scope but cannot
   grant it.
6. **S6, первый production запуск.** Чистая схема, immutable revision/image,
   совместимые consumers, отдельно согласованный runtime preflight/запуск,
   rollback plan и независимая приёмка новых data flows. Historical backfill
   не требуется; source implementation не выполняет runtime операции.

### S1 source checkpoint — 2026-10-10

- Добавлена V51, не запускавшаяся вне изолированной PostgreSQL Testcontainers
  schema. Она создаёт `businesses`, memberships, `ALL_BUSINESSES` grants,
  пустой approved-alias registry, channel/location mapping foundations и
  nullable `tickets.business_id` с FK/index; legacy rows не присваиваются.
- В V51 seed-ятся только предустановленные records `sushi` и `bliny`;
  реальных legacy aliases в migration нет.
- `BusinessAccessService` резолвит direct membership и `ALL_BUSINESSES` grant,
  применяет initial role matrix `VIEWER`/`OPERATOR`/`MANAGER`/`ADMIN` и по
  умолчанию отказывает при отсутствии active grant. Business `ADMIN` — не
  системный `ROLE_ADMIN`.
- `requireSelectedBusiness` принимает типизированный single/all context:
  конкретный business разрешён только из accessible IDs, а all-view возвращает
  их union (включая пустой). Это presentation scope, не grant и не write scope.
- `requireReadableTicket` пропускает только `TicketReference` с non-null
  canonical owner и effective `READ`; unassigned legacy ticket и чужой owner
  получают fail-closed отказ. Метод не подключён к endpoint до S3.
- Targeted PostgreSQL integration test подтверждает legacy ticket без
  assignment, denied-by-default, изоляцию selected membership, fail-closed
  selected/ticket contexts и включение future business в `ALL_BUSINESSES` без
  capability `CONFIGURE`.
- Existing API/UI/read paths намеренно не подключены к сервису до S3, поэтому
  V51 source foundation не меняет поведение existing users.

## Feature flags и rollout boundary

Предлагаемые flags выключены по умолчанию:

```text
multibusiness.foundation.enabled=false
multibusiness.ticket-write-requires-business=false
multibusiness.strict-read-scope=false
multibusiness.shared-channel-resolution=false
multibusiness.general-channel-resolution=false
multibusiness.ui-selector=false
multibusiness.manual-ticket-creation=false
```

`MultibusinessFeatureFlags` binds these exact keys and defaults all of them to
`false`. At the S2A source checkpoint no runtime path reads this bean: adding
the configuration does not enable filtering, assignment or a UI switch.

Ручное создание дополнительно требует `foundation.enabled=true`,
`ui-selector=true` для страницы и отдельный `manual-ticket-creation=true` для
POST. Исходное значение флага — `false`; наличие source/migration не включает
операторскую запись в рабочей среде.

Первый запуск с чистой базой не требует historical assignment. Каждая новая
показываемая запись должна иметь canonical business/general/global scope и
соответствующий допуск; unresolved и противоречивые rows не дают доступ по
догадке. Остатки тестового dataset исключаются либо удаляются при отдельно
подготовленном пересоздании стенда, без silent fallback. Каждый flag требует
своей приёмки и rollback в false; source merge не является rollout.

## Обязательная test matrix

- PostgreSQL: FK/check constraints, unique business codes, membership and
  `ALL_BUSINESSES` effective scopes, no cross-business duplicate location
  collision.
- Security: user A не читает/не меняет B через list, direct ID, attachment,
  search, export, count, task, notification, SSE и AI context; `ALL_BUSINESSES`
  не получает global-system data.
- Ticket identity: новые записи имеют устойчивую public/composite identity;
  ambiguous bare ticket_id отклоняется, child lookups подтверждают canonical
  owner/generation и FK integrity. Сохранение ID/history старого dataset
  не является требованием (R190).
- Transport: single and shared channel, explicit selection, verified mapping,
  conflict/unresolved, idempotent replay/retry/edit/feedback.
- Templates: clone B does not mutate A, template version stays frozen on an
  existing ticket, new business works without iiko.
- UI/manual: one/many/all grants, forbidden context switch, all-view labels and
  counts, reload/realtime, empty business and business catalog membership save.

## Gates после S1

Historical S2B не блокирует S3: approved alias map и решения по старым строкам
больше не нужны (R190). Релевантные будущие product policies из decision table
остаются PENDING; их нельзя вывести из отказа от тестовых данных. Перед
соответствующим новым path нужны явный contract, changed-file/test plan от
свежего main и negative scope tests. Подготовка чистой схемы/стенда и первый
production запуск — отдельный согласованный runtime scope.

Ниже сохранены исторические source checkpoints; старые требования переноса
dataset не переопределяют решение R190.

### R149: v2 GENERAL producer, nullable SDK и выбранные sessions во всех ботах

- V75 расширяет только CHECK нового questionnaire catalog: GENERAL/USER_SELECTION
  имеет business_id=NULL и business_selection_source=NULL, свой ID/SHA и общий
  catalog SHA. V73/V58 fingerprints остаются immutable; новый trigger также
  фиксирует выбранные owner/kind/source/channel/composite ID. Старые rows не
  переписываются; GENERAL-only и nullable legacy representation сохраняются.
- Writer заново проверяет flags, platform/active channel, полный current catalog,
  membership и selected ID/SHA до root/children. BUSINESS V1 поддерживается без
  active shared GENERAL reservation; с ней требуется v2 catalog, а не каталог,
  в котором GENERAL отсутствует. USER_SELECTION без version proof не превращает
  shared channel в legacy/GENERAL-only. Inbox, root/composite locator и child
  writes атомарны; inbox проверяет повторный payload и сохраняет статус ошибки.
  Exact replay хранит frozen version и не захватывает новый owner.
- V2 choices/selection forStart выдаёт ACK=2 только при strict writer; preview
  не выдаёт ACK. GENERAL требует general resolution. BUSINESS-only v2 start не
  требует включать GENERAL при отсутствии active GENERAL binding. HMAC покрывает
  path/query/forStart. SDK проверяет обе версии, NULL versus fake owner, duplicate
  scope/template, selected bytes SHA и отсутствие global locations/presets;
  новые starts не откатываются на v1 при ошибке/отсутствии ACK=2.
- MAX/VK/Telegram имеют отдельный GENERAL command/button namespace и выбор до
  первого вопроса, JSON/CAS persistence и exact template flow. ScopeKind + nullable
  business ID сопоставляются с сохранённым меню; version/channel/catalog обязаны
  совпасть. Stale nonce не является ответом. Сессия/ответы переживают rebuild,
  final publication key фиксируется до queue; неизвестный итог повторяет ту же
  frozen command, а не перечитывает текущие bindings. Shared GENERAL использует
  выбранную сохраняемую ветку, не legacy GENERAL-only path. Raw support-chat,
  local/global settings, legacy feedback/media fallback не открываются.

Новые starts требуют совместимых backend V75 и SDK/consumers v2; отсутствие ACK=2
закрывает запуск. Старые V1 BUSINESS sessions поддерживаются явно, не получают
GENERAL через fallback. Рабочая migration/flags и provider/Redis/proxy приёмка не
выполнены. Scoped locations/presets/media, feedback/edit, rolling-upgrade и
retention остаются gates; общий статус задачи 🟡.

### R150: изолированные ресурсы выбранного бизнес-вопросника

- Активные business_locations читаются по точному business_id. Global/iiko/
  legacy location tree и dialog_config.preset_definitions не участвуют.
  Business presets хранятся в businesses.settings.questionnaire_presets как
  group → field → список строк. Names/labels не определяют owner. GENERAL
  возвращает только свой вопросник и два пустых объекта ресурсов.
- System-admin GET/PUT /api/business-admin/businesses/{businessId}/questionnaire-presets
  требует PAGE_SETTINGS + ROLE_ADMIN, foundation и CSRF для PUT. READ/ADMIN
  бизнеса или ALL_BUSINESSES не заменяют системные полномочия. expectedSha256
  и row lock защищают от потери параллельного изменения. Другие settings
  сохраняются; отсутствующий/архивный бизнес даёт 404, stale version — 409.
- V3 /internal/api/bot/channels/{channelId}/questionnaire/v3/choices и selection
  используют подпись path/query/forStart и ACK=3 для starts. Каждый choice
  содержит resourcesSha256. Хешируется UTF-8 JSON [locationTree,presetDefinitions]
  с рекурсивной сортировкой object keys и сохранением порядка массивов; общий
  catalog SHA фиксирует все choices и resource versions. ID/SHA самого шаблона
  не подменяется материализованным flow. V1/V2 не содержат нового nullable
  digest в backend JSON и по-прежнему не выдают presets; SDK поддерживает их
  сохранённые сессии, новые starts общего каталога запрашивает только через V3.
- SDK проверяет resource SHA по сохранённому choice, точный owner, shape,
  положительные/уникальные location IDs, допустимые поля, группы и значения.
  Для location_type/city/location_name разрешена одна reference на dimension;
  порядок вопросов произвольный, уже выбранные dimensions взаимно ограничивают
  варианты. Остальные business presets — независимые списки строк. Exclusions
  сохраняются, пустой список не становится свободным вводом и не открывает
  global fallback. Лимиты: 1000 активных локаций, 20 групп × 20 полей, до 100
  строк на поле и до 1000 строк суммарно. Каталог пока до 100 choices.
- MAX/VK сохраняют весь выбранный resource snapshot в Selection; Telegram —
  в уже существующем SelectedQuestionnaire. Restore проверяет menu/owner/hash.
  В V3 answer key — явный binding_key либо ID вопроса; прежняя legacy/preset
  эвристика MAX/VK для старых сессий сохранена. Повтор после неизвестного
  результата queue использует ту же команду/ответы/publication key. GENERAL V3
  переживает JSON/rebuild без синтетического business ID и без чужих ресурсов.
- До нового root/child INSERT writer повторно проверяет полный current catalog,
  шаблон и resource version. V3 preset attributes проверяются по question ID,
  binding key, input type, text/label, допустимым значениям и согласованным
  dimensions; duplicate/unknown attributes и несовпадающие непустые legacy
  projections отклоняются. Exact replay существующего root сохраняет frozen
  proof и не перечитывает новый справочник. Полная server-side проверка всего
  route/required question path этим шагом не объявляется готовой.

Source-схема остаётся V75; runtime rollout/flags/migrations не выполнялись.
UI presets/reference editor, полный route/required validator и все прочие gates
текущей сводки 01-278 остаются открытыми. Статус 🟡.

### R151: бизнес-presets UI и финальный v3 required/forward path

Пункты R150 об отсутствующем UI/validator выше — исторический checkpoint.
В R151 эти source-блоки реализованы; рабочая приёмка остаётся обязательной.

- На /businesses/manage добавлен редактор собственных готовых полей: явный
  активный бизнес, группа/поле, строки вариантов, добавление/удаление и очистка.
  PUT использует SHA из GET; 409 сохраняет черновик, обновление версии выполняется
  явно. Во время записи владелец и поля заблокированы. Задержанный GET другого
  бизнеса не подменяет текущий экран. Значения выводятся через textContent/value.
- В существующем редакторе шаблона доступен явный справочник для просмотра:
  прежний общий, конкретный бизнес или «Без бизнеса». GET .../questionnaire-presets/resources
  требует тех же foundation/PAGE_SETTINGS/ROLE_ADMIN; business ADMIN/ALL_BUSINESSES
  не заменяют эти права. В бизнес-контексте нет global fallback; GENERAL не
  получает бизнес-ресурсы. Выбор здесь — предпросмотр, привязка остаётся отдельной
  операцией в каналах. Ссылки, exclusions и маршруты остальных шаблонов сохраняются
  при общей сериализации; проверка их forward targets использует собственный flow.
- Только v3 catalog/selection применяет QuestionnaireFlowContract: до 200 вопросов,
  уникальные ID и effective keys (binding_key либо ID), стабильная сортировка order,
  boolean required/dashboard, select IDs/labels и однозначные переходы вперёд на
  существующие вопросы. Ключ problem допускается для свободного ответа. Общая
  business-привязка и v1/v2 catalogs сохраняют прежний admission contract; отдельный тест
  доказывает, что несовместимый flow доступен старому каталогу и закрыт для v3.
- До первого нового root/child INSERT проверяется весь финальный v3 путь:
  обязательные ответы на посещённых шагах, отсутствие ответов из пропущенной
  ветки, ID/key/type/text/dashboard metadata, select value ID/label/text, preset
  membership/exclusions/согласованная локация и problem projection. Необязательный
  свободный ответ можно опустить; обязательный вопрос вне выбранной ветки не
  требуется. Порядок transport attributes не определяет маршрут. Exact replay
  существующего root сохраняет прежний frozen proof и не перевалидирует новый flow.
- Telegram теперь использует строку preset-ответа как route value, как MAX/VK;
  JSON restore и отсутствие промежуточного ответа проверены. MAX production code
  не менялся: отдельный сценарий двух возвратов и смены ветки подтвердил очистку
  старых ответов. История provider updates не становится серверным answer ledger.
- Validation: 468 Java tests / 19 XML и 11 Node checks,
  failures/errors/skips=0. Backend 262/12; SDK + Telegram/MAX 206/7.
  PostgreSQL/Spring transaction/inbox/rollback, v1/v2 compatibility, HTTP access/CSRF,
  templates/settings, session restore/retry, быстрые переключения и stale CAS проверены.
  Новые v3 writer fixtures передают canonical question text/dashboard из шаблона.
- Первый серверный прогон: 247 tests, errors=1 — flow guard ошибочно стоял в общем
  bind path и ломал прежний negative fixture с duplicate question. Guard перенесён
  на v3 capture; старые assertions сохранены и усилены v3 assertion. Повторены
  зависимые серверные suites и добавлены четыре compatibility cases. Исходные логи
  сохранены в .tmp/r151-*. Sass outputs исключены из source-пакета.
- Source-схема остаётся V75; новые starts требуют ACK=3, но один ACK не доказывает
  совместимость произвольных старых образов с новым route guard. Рабочий экземпляр
  проекта находится на другой машине и в другой сессии агента. Здесь source и
  isolated tests; runtime revision/DB/flags неизвестны. Deploy/restart/working
  migration/backfill/flags/provider sends и browser/live приёмка не выполнялись.
- Остались >100 choices, scoped media/edit/feedback, live Redis/provider/proxy,
  rolling-upgrade/retention, прочие S3–S5, S2B и S6 gates. Общий статус 🟡.


### R152: v4 каталог до 1000 и pagination сохранённого меню

Предыдущие предел 100/ACK=3 относятся к v1–v3; новые starts используют v4.

- Добавлены signed v4 choices/selection и строгий writer ACK=4. Новые starts
  общего каталога идут только через v4, без downgrade; прежние v1/v2/v3 endpoints
  и сохранённые сессии обслуживаются отдельно, в v4 автоматически не переводятся.
  Подпись включает path/query/forStart; Cache-Control=no-store сохранён.
- V4 допускает до 1000 choices на канал: 1000 BUSINESS либо до 999 BUSINESS и
  отдельный GENERAL. Переполнение закрывает весь каталог, без усечения/скрытия
  GENERAL. Старые версии сохраняют предел 100. Общее число бизнесов в системе
  этим лимитом меню не ограничивается. GENERAL bind учитывает новый v4 budget.
- Каталог фиксируется целиком в сессии и разбит на локальные страницы по 8
  вариантов, до 2 navigation buttons. Переходы не запрашивают новый каталог;
  membership/ID/template/resource hashes проверяются при выборе и перед INSERT.
  Переименование, изменение шаблона либо ресурсов любого, в том числе далёкого
  пункта, меняет общий catalog SHA и требует нового выбора. Labels не являются
  owner identity; GENERAL сохраняет nullable business_id и пустые ресурсы.
- V4 registry делает один общий reservation lookup вместо чтения глобальных
  settings на каждый бизнес. Ресурсы всех участников проверяются/хешируются,
  но runtime payload и ресурсы удерживаются только для выбранного бизнеса;
  копии вопросников/локаций всех участников не создаются. Shared v4 selection
  использует один capture бизнес-каталога и выбранного runtime в своей транзакции.
  Цена capture по-прежнему линейна по числу бизнесов/ресурсов; server paging,
  нагрузочная и live Redis/retention приёмка не объявляются выполненными.
- SDK проверяет v4 capacity, exact ACK и byte/resource proof. MAX/VK/TG сохраняют
  frozen каталог/выбор и собственные ресурсы, включая выбор на последней странице.
  MAX получил страницу в Selection, guarded native navigation и сохранение до
  provider send. Page=0 не добавляется в JSON прежних сессий; отсутствующее поле
  восстанавливается как 0. Telegram допускает page token до страницы 124.
  VK использует прежнюю native pagination с новым валидатором каталога.
- Ingestion узнаёт v4 по проверенному catalog SHA через selectExpanded. До
  первого нового root/child INSERT применяет тот же required/forward path и
  canonical attributes validator, что v3. Frozen replay не перечитывает текущий
  каталог, не переносит владельца и не создаёт новые children. Новая migration
  не нужна: минимальная source-схема остаётся V75.
- Validation: 837 Java tests / 75 XML, failures/errors/skips=0;
  backend 259/7 и полный fleet 578/68. PostgreSQL границы 101/999+
  GENERAL/1000/1001, отсутствие усечения, offscreen resource/name invalidation,
  signed HTTP/ACK, создание/replay/rollback после первой сотни во всех трёх
  платформах, все native buttons до 1000, JSON restore и MAX send retry проверены.
  Node suites отдельно не запускались: JavaScript source не менялся.
- Реализация/валидация — detached sandbox .tmp/01-278-r152, затем guarded source
  apply с exact 27 files/content hashes. Generated Sass CSS исключён. Baseline
  51e89ff2e19df855f397448cc9569fe7a37a27c7, parent
  8cf6d3fb181b2c10616065e2d5e75ce6bfd378ac; R151 actual 21 files проверен.
- Рабочий Docker и проект находятся на другой машине, в другой сессии агента.
  Здесь только source и isolated tests. Рабочие revision/DB/flags неизвестны;
  deploy/restart/рабочие migration/backfill/flags/provider sends не выполнялись.
  Нужны совместимые backend/SDK/боты с ACK=4 и отдельная приёмка на рабочей машине.
- Общий статус задачи 🟡. Далее scoped media/edit/feedback выбранных потоков,
  provider/Redis/proxy, rolling-upgrade/retention, прочие S3–S5, S2B и S6 gates.


### R153: Telegram v4 draft media и server-side bytes admission

- Telegram v4 выбранных BUSINESS/GENERAL вопросников принимает document, photo,
  video, voice, audio, animation, sticker и video_note как свободный ответ до
  регистрации. Caption становится ответом, при его отсутствии используется
  явное описание вложения. Preset/select требуют текстового выбора; media не
  подменяет вариант. До выбора владельца вложение не скачивается/не сохраняется:
  клиенту предлагается отправить его повторно после выбора. Старые v1–v3 остаются
  text-only; их сессии не переводятся в v4.
- До скачивания повторно проверяются текущие selection/catalog и поддержка media
  writer. V4 forStart требует прежний exact ACK=4 и отдельный exact header
  X-Iguana-Questionnaire-Media-Version=1; choices и selection выставляют его только
  после authorization/feature gates. Отсутствие/другая версия закрывает start или
  revalidation без downgrade. Поэтому новые SDK/боты требуют backend R153+;
  одной совместимости с v4 R152 недостаточно. Старые v4 consumers игнорируют
  дополнительный header; payload/catalog schema не менялись.
- Бинарные данные: 1 байт–10 МиБ на файл, до 10 файлов в истории опроса.
  Фактический stream ограничен независимо от Telegram file_size. Ключ
  questionnaires/telegram/{channel}/{business-ID|general}/{user}/{catalogSHA}/
  {providerMessageId}/{bytesSHA}.{extension} связывает файл с canonical owner,
  клиентом и снимком; filename не определяет storage path. Original name очищается
  от пути/control chars и ограничен 255 символами. Выбирается одна rendition photo.
  Повторы/CAS используют тот же content-addressed key; локальный файл публикуется
  после полной записи, S3 использует общий attachments domain/prefix.
- Media history, answers, accepted IDs и frozen publication key сохраняются до
  prompt/provider send. JSON restore проверяет owner/channel/catalog/provider key
  и предел файлов. Duplicate update, CAS retry, prompt/send failure и queue retry
  не меняют выбранного владельца и не добавляют второй history item. Pending
  publication повторяет точный сохранённый command без нового скачивания и
  recapture; /start остаётся явным новым выбором.
- На panel перед первым новым root/child INSERT проверяются v4 version, Telegram,
  canonical channel/business-or-GENERAL/user/catalog/provider identity, допустимый
  тип/имя, уникальность и лимит файлов. Читаются реальные shared storage bytes;
  missing/empty/oversize, чужой path/URL/traversal, изменённый SHA или повтор
  отклоняются. В одной ingestion-транзакции сохраняются chat_history с точным
  ticket_owner_user_id/channel и metadata size/content_hash/storage_key.
  Прежний metadata SQL/opaque ticket-ID contract сохранён. Existing-root frozen
  replay не перечитывает текущий каталог или файлы и не создаёт children повторно.
- BUSINESS/GENERAL scoped attachment APIs теперь читают новый namespace по
  canonical public ticket/history locator, текущему READ и frozen root proof.
  Сверяются channel/user/business-or-GENERAL/catalog/provider и metadata hash/size;
  текущие bindings/каталог не определяют владельца старого обращения. При download
  байты повторно сверяются с SHA/size, затем повторно проверяются READ/reference.
  Старые direct/key/path endpoints закрыты для нового namespace, включая Windows
  slash/case aliases. HTML/SVG и остальные документы выдаются как attachment с
  application/octet-stream, no-store и nosniff через scoped APIs.
- Дополнительные PostgreSQL/read/controller checks покрывают отзыв READ,
  повреждение provider/channel/hash/size metadata, неоднозначные metadata,
  изменённые bytes и попытку обхода через legacy URLs. Старые ticket-directory
  readers и opaque ticket IDs сохранены и проверены отдельно.
- Validation: 845 Java tests / 84 XML, failures/errors/skips=0;
  backend 233/15, полный fleet 612/69. Проверены все 8 типов,
  BUSINESS/GENERAL, restore, duplicate, CAS, failed prompt/download/storage/queue,
  actual-byte limit, signed capability, PostgreSQL owner/metadata/rollback/replay.
  Дополнительно сохранён контракт legacy exact ticket-ID metadata и HTTP READ.
  Начальные 278 backend checks (135 writer cases) прошли; финальный batch
  повторяет 33 новых media write/read cases, остальные 116 writer cases не
  повторялись. Начальный XML сохранён отдельно, в итоговый счётчик не добавляется. Тесты используют
  isolated PostgreSQL, local_fs и direct JSON/CAS; provider downloads/sends mocked.
  Live S3/Redis/provider/browser/proxy acceptance не выполнялась. JavaScript source
  не менялся, Node suites отдельно не запускались.
- Detached sandbox .tmp/01-278-r153; exact 26 files/content guards перед source
  apply. Baseline 554ea0288fbe65fd2e7e8cf9cdf004699c4b4acf, parent
  51e89ff2e19df855f397448cc9569fe7a37a27c7; фактические 27 файлов R152 проверены.
  Generated Sass CSS исключён, source-схема остаётся V75, новой migration нет.
- Read operator v1 остановлен JS parser до transformation; v2 остановлен exact
  anchor guard до изменения reader files. Артефакты сохранены; v3 использует
  проверенные semantic anchors. Это не RED тестов и не source apply в main;
  emitted Java/read SQL проходят полный compile и targeted checks перед apply.
- Первый read batch: 79 checks, одна ошибка прежнего BUSINESS read test с
  минимальной схемой без ticket.channel_id. Новый proof SELECT оказался связан
  с legacy projection. V4 исправления отделяет proof-only SQL, сохраняет общий
  BUSINESS policy predicate и прежний projection для legacy; GENERAL proof
  вынесен в отдельный guarded lookup. Недоступная proof schema не разрешает
  новый namespace. Существующие тесты не ослаблялись; повторяются read batch,
  33 media write/read cases и прежний HTTP READ suite. RED log сохранён отдельно.
- Рабочий Docker/проект находятся на другой машине и в другой сессии агента.
  Рабочие revision/DB/flags неизвестны; deploy/restart, рабочие migrations/backfill,
  flags/config/provider sends здесь не выполнялись. Перед выпуском нужны совместимые
  backend/SDK/боты и один shared storage domain/bucket/prefix либо общий volume.
- Общая задача 🟡. Дальше: VK selected media, Telegram followup media, scoped
  edit/feedback, media deletion/retention/reconciliation (включая orphan objects
  отменённых/конфликтных черновиков), live fleet/provider/Redis/S3/proxy acceptance,
  прочие S3–S5/S2B/S6 gates. Одновременные бизнес-обращения не добавлялись.

### R154: VK media выбранного вопросника, batch identity и scoped bytes

Дата: 2026-10-21. Промт: «продолжай». Связный пакет 20 файлов; общий статус 🟡.

- VK v4 выбранного BUSINESS/GENERAL вопросника принимает photo, document и
  audio_message в свободном ответе до регистрации. Одно сообщение может содержать
  несколько файлов; caption становится ответом, без caption — «Вложений: N».
  Pending choice, preset/select, v1–v3 и media после регистрации закрыты до
  скачивания. Вложения до выбора предлагается отправить заново. GENERAL сохраняет
  business_id=NULL; raw operator relay и feedback не вызываются этим путём.
- Полный batch описывается и проверяется до файлового I/O: неподдерживаемый элемент,
  недоступный URL, declared oversize или превышение общего бюджета не принимают
  часть ответа. 1 байт–10 МиБ на файл, до 10 файлов на всю историю опроса.
  Фактические bytes ограничены независимо от размера в SDK/HTTP headers.
  Поддерживаются только HTTPS URL доменов userapi.com, vkuserphoto.ru,
  vkuserphoto.net, vkuseraudio.net, vkuser.net, vk.com, vk.ru и их subdomains;
  userinfo, fragments, нестандартные порты и redirects не допускаются. CDN/proxy
  совместимость требует live приёмки; новые домены требуют явного расширения.
- Ключ questionnaires/vk/{channel}/{business-ID|general}/{user}/{catalogSHA}/
  {conversationMessageId}/{slot}/{bytesSHA}.{extension}: private peer совпадает
  с user, slot — стабильный 1-based индекс 1–10. Одинаковые bytes в разных slots
  остаются разными вложениями, повтор одного slot не создаёт второй object.
  Сохраняются реальный тип, очищенное original name, provider conversation ID
  и время исходного VK message. URL/токен скачивания не попадают в session/history.
  Старые text history из четырёх полей читаются с nullable дополнительными полями.
- До скачивания RuntimeConfigService повторно проверяет выбранный scope/catalog.
  Только VK v4 дополнительно требует exact
  X-Iguana-Questionnaire-VK-Media-Version=1 на signed choices/selection forStart.
  Backend R154+ обязателен для нового VK consumer; ACK=4 и прежний media-version=1
  сохранены. Telegram/другие consumers не требуют нового VK header. Catalog/payload
  version, лимит 1000 и source-схема V75 не менялись; новой SQL migration нет.
- Весь принятый batch, ответ и accepted-event ledger сохраняются CAS до prompt
  либо публикации. Ошибка загрузки/storage не продвигает опрос; временная ошибка
  освобождает delivery claim для retry. Ошибка размера предлагает новый ответ.
  JSON restore проверяет owner/catalog/provider/slot/budget и accepted event.
  Prompt failure, duplicate, CAS conflict и queue retry не добавляют повторную
  историю. Frozen publication повторяет точный command без recapture/download.
- Panel до первого root/child INSERT проверяет VK v4, canonical owner/channel/
  user/catalog/provider/slot, тип/имя, бюджет, уникальность slot и реальные
  shared-storage size/SHA. Telegram proof и прежний MAX admission сохранены.
  Metadata сохраняет size/content_hash; existing-root replay не читает заново
  каталог/файлы и не создаёт повторные children.
- BUSINESS/GENERAL scoped reads сверяют frozen root proof, provider/slot и
  metadata size/hash; download сверяет реальные bytes и повторно current READ.
  Старые direct/key/path routes закрыты и для questionnaires/vk, включая Windows
  case/slash/absolute aliases. Общая политика legacy ticket-directory readers
  не расширялась. Активные документы выдаются attachment/octet-stream/no-store/
  nosniff; текущие bindings не определяют владельца старого обращения.
- Validation: 788 Java tests / 77 XML, failures/errors/skips=0;
  backend 128/7, полный fleet 660/70. Core 308 и Telegram 117
  взяты из GREEN modules прогона v2; VK/MAX проверены повтором v3 без повторения
  прошедших SDK/Telegram suites. Backend writer class запущен
  только по 8 media write/read methods, включая 33 прежних Telegram cases;
  остальные исторические writer cases в этом пакете не повторялись. Проверены
  BUSINESS/GENERAL batch/replay/rollback/current READ, повреждённые metadata и
  legacy обходы; реальные isolated PostgreSQL/local_fs, direct JSON/CAS;
  provider downloads/sends — doubles. Полный main/test compile backend и fleet.
- После первого backend GREEN 128 cases дополнительно усилены VK negatives:
  чужие channel/business/general/user/catalog/slot keys существуют в storage с
  корректными bytes/SHA; collision одного slot использует два разных корректных
  content hashes. V3 negative также имеет доступный объект. Это доказывает отказ
  по identity/version/slot, а не только из-за отсутствующего/повреждённого файла.
  Повторены все 70 media writer/read cases; исходный GREEN XML архивирован и
  не добавляется повторно в счётчик. Остальные 58 backend cases не повторялись.
- Первый fleet RED: 4 новых SDK cases повторно расширяли один fixture-каталог,
  создавая дубли. Исправление v2 разделило BUSINESS/GENERAL на независимые cases;
  production catalog validation и существующие assertions не ослаблялись.
  Второй fleet RED: 13 новых VK assertions учитывали profile HTTP в счётчике
  файлов либо ожидали exception вместо существующего stale-catalog UX.
  V3 fixture отделяет profile lookup от скачивания и проверяет restart prompt,
  no-download/no-answer и повторный selection call. Assertions защиты не ослаблены.
  RED logs сохранены; финальные fleet/backend GREEN проверены по XML.
- Detached sandbox .tmp/01-278-r154; baseline HEAD/origin/main/actual remote main
  7ca6a046d123c740cc04cef5d3183b13b447b031, parent
  554ea0288fbe65fd2e7e8cf9cdf004699c4b4acf. Проверены subject и реальные 26 файлов
  R153. Exact file/content guards, report hashes и freshness классов предшествуют
  source apply. Staging/commit/push выполняются отдельным operator по разрешению
  только на 2026-10-21 Europe/Moscow, с проверкой remote и без force.
- Рабочий Docker/проект — на другой машине и в другой сессии агента. Рабочие
  revision/DB/flags здесь неизвестны; deploy/restart, рабочие migrations/backfill,
  config/flags/provider sends не выполнялись. Локальный Docker использовался
  только для isolated Testcontainers. Live S3/Redis/VK/CDN/proxy/browser и rolling
  upgrade acceptance не выполнялись; JavaScript не менялся, Node suites не запускались.
- Далее: Telegram/VK selected followup media, scoped edit/feedback, retention/
  reconciliation, в том числе orphan objects частично загруженных, отменённых
  или конфликтных batches; live fleet/provider/Redis/S3/proxy приёмка и остальные
  S3–S5/S2B/S6 gates. Одновременные бизнес-обращения не добавлялись. Перед выпуском
  нужны совместимые backend/SDK/боты и один shared storage volume либо S3
  domain/bucket/prefix; rollout этим source-пакетом не разрешён.
