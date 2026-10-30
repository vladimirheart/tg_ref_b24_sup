# 01-278 R231 — исходный аккаунт в BUSINESS card/child/media reads

Время: 2026-10-30T07:18:39.630Z (UTC; Europe/Moscow +03:00).
Baseline 56daeb6cb1be2b07de6c17a90381edb6e78c1094, parent 3a17a798034cd7b9819fcb74ca69320935408f41; R23017 paths/content
и HEAD/origin/main/actual remote проверены.

Промт пользователя:

> давай дальше

Действующее разрешение владельца на Git: «Разрешаю до конца 30 октября».

BusinessScopedTicketReadService: findByPublicId/findHistoryByPublicId/
findAnswersByPublicId/findWorkspaceByPublicId/findSpansByPublicId/
findAttachmentByPublicId принимают исходный Authentication, без public Long-ID
fallback для этих6 methods. Private immutable ReadActor сохраняет numeric ID и
exact raw username, проверяет bound PanelUserPrincipal/authenticated/principal
state; его toString не выводит identity. Каждый owning SELECT повторяет current
ID/raw name/enabled/strict is_blocked=FALSE/fold uniqueness вместе с прежними
business.active и membership OR ALL_BUSINESSES. NULL blocked по-прежнему не
разрешён BUSINESS entry policy; GENERAL COALESCE-policy не переносилась.
SQL ID передаётся как BIGINT, имя как VARCHAR, без Java fold/trim/name lookup.

Controller сохраняет early R224 identity guard/PAGE_DIALOGS/flags/400/403/404/413,
передаёт тот же Authentication, включая recheck после hashed Telegram/VK bytes.
Attachment proof/legacy fallback используют одну пару; сохранены composite child
owner, ambiguity/limits/categories/metadata/provenance/hash/size/private headers.
Legacy attachment streaming semantics прежние; отзыв после выдачи stream не
заявляется. Controlled fixture mutations до statement не являются доказательством
внешнего revoke после snapshot/locks. BusinessAccessService и роли/правила READ
не менялись: page right остаётся отдельным gate, business scope проверяется в SQL.

availableBusinesses/listTickets/pageTickets/exportTickets/summarizeTickets/
summarizeCategories и ManualBusinessTicketCreationService остаются прежними ID
API. Их late actor boundary — явный следующий пакет. Current R224 entry SQL,
controller catalog/list/summary prefix, весь GENERAL product R228–R230 прежние.

Изменены BusinessScopedTicketReadService/BusinessScopeReadController;6 existing
fixtures. Добавлены BusinessChildActorFixture/BusinessChildActorPostgresTest/
BusinessChildActorWebMvcTest, task/S0 и этот append-only changelog.
Fixture переиспользует неизменённые dedicated GENERAL fixture tables/media bytes
только как основу test schema; business scope и private payload заданы отдельно.

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
