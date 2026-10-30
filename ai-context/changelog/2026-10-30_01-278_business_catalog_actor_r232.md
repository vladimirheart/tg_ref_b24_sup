# 01-278 / R232 — BUSINESS catalog/manual account binding

Время: 2026-10-30T10:24:14.024Z (Europe/Moscow +03).

Промт пользователя:

> делай. пуш я сделаю самостоятельно

Baseline: f1ad1d5ab0f517b80845d47ed6ab4fc54831fb7b; parent56daeb6cb1be2b07de6c17a90381edb6e78c1094,
subject и14 R231 paths/content проверены; root/origin/actual remote совпали.

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
