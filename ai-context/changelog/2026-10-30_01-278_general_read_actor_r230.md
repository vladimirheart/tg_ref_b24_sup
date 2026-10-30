# 01-278 R230 — исходный аккаунт в GENERAL root/child/media reads

Время: 2026-10-30T05:55:19.653Z (UTC; Europe/Moscow +03:00).
Baseline 3a17a798034cd7b9819fcb74ca69320935408f41, parent 586e9d3731ad00b3ad01e780d6312d4eeecdfdea; R229 commit/content/20 paths
и HEAD/origin/main/actual remote проверены.

Промт пользователя:

> давай дальше

GeneralTicketReadService.page/find/history/answers/attachment теперь принимают
исходный Authentication; публичный long-ID fallback удалён. Один immutable
GeneralTicketActor сохраняет numeric ID и exact raw username на время операции,
включая document/questionnaire/legacy fallback. Каждый owning SQL требует эту
пару вместе с прежними CURRENT_ACCOUNT и active GENERAL READ. Cached authorities,
системный admin или business grants не заменяют READ. GeneralTicketReadController
сохраняет раннюю проверку текущего аккаунта и передаёт тот же Authentication,
в том числе в повторную SQL-проверку после чтения hashed media bytes.

Позднее переименование/смена регистра/удаление и reuse имени не дают старой
сессии прочитать данные. Bounds/status/search/cursor/public-ID validation,
page preflight и existing 400/403/404/413, URL/payload/private headers прежние.
Composite child owner, nullable/strict public-ID joins, relation ambiguity,
storage metadata/key/hash/size/provenance и download policy сохранены.
OPERATE не требуется для чтения; GENERAL admin grant policy R229 неизменна.
Обычный legacy attachment сохраняет streaming semantics без нового byte hash;
разрешение относится к SQL snapshot, отзыв после выдачи stream не заявляется.
Новые controlled statement-boundary mutations не доказывают внешний revoke
после захвата locks или непрерывную авторизацию уже выданного ответа.

Изменены GeneralTicketReadService и GeneralTicketReadController;9 API fixtures,
добавлены GeneralReadActorFixture/GeneralReadActorPostgresTest/GeneralReadActorWebMvcTest,
обновлены task/S0 и добавлен этот changelog. Frozen SQL matrix охватывает8 surfaces:
page/card/history/answers/legacy/Telegram/VK/operator-document; raw names,
BIGINT>2^53/Long.MAX, nullable-blocked, read-without-operate, stale/unbound actors.
56 mutations перед owning statement и8 перед конкретным attachment fallback.
HTTP: реальный form login/R189/controller/SQL, поздние account/grant mutations,
3 hashed-media branches после storage read, flags/page authority/private bytes.

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
