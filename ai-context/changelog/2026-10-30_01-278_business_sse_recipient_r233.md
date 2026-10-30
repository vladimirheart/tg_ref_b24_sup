# 01-278 / R233 — BUSINESS SSE recipient account binding

Время: 2026-10-30T10:57:39.440Z (Europe/Moscow +03).

Промт пользователя:

> продолжи

Baseline: 5d2ceefc1e5bf4de513f5a0f1ce356a017530e85; parent f1ad1d5ab0f517b80845d47ed6ab4fc54831fb7b.
Subject и18 R232 paths/content проверены; checkout clean, local ahead1.

R233: legacy/BUSINESS SSE connection принимает исходный Authentication и
сохраняет immutable UiEventRecipient(ID, exact raw-name), без String fallback.
UiEventsController не пересобирает identity по имени. Каждый ticket-bound send
повторно проверяет именно этот account в owning SELECT: ID/raw-name, enabled,
strict blockedFALSE, fold uniqueness; READ берётся из прежнего membership/ALL
policy того же account. SQL lower вычисляется от найденного account, а не
Java-folded имени. Два account с переиспользованным именем имеют разные cohorts;
targeted routing использует прежний ROOT-fold key и не является grant. Cleanup
старого cohort не удаляет соединение нового account. Между emitters не кэшируется
READ. Формат fanout/Redis envelope, payloads, producer owner capture/recheck,
composite tuple/public ID/uniqueness/GENERAL boundary и strict compatibility прежние.
Системные invalidations/sidebar/notification refresh/heartbeat сохраняют прежнюю
политику; это account binding ticket events, не новая page/notification policy,
не session-epoch/logout invalidation и не блокировка отзыва после SQL snapshot.

398 tests /9 suites GREEN одним финальным selected run v4.
193 новых:179 SQL/stream и14 real form-login/R189/open servlet SSE HTTP.
Проверены exact raw names/Unicode/edge spaces, BIGINT>2^53/Long.MAX, invalid,
anonymous/unbound/unauthenticated principal, rename/reuse/delete/case/state/
NULL-blocked/duplicate, membership и ALL. 28 controlled SQL boundary mutations,
70 local/captured/broadcast/target/JSON delivery cases, separate replacement
connection/cleanup, per-emitter changes и publication fallback. SQL/script
boundaries не являются доказательством concurrent lock/session lifetime policy.
Frozen baseline193/108 assertion failures/0 errors/85 controls; положительные
raw-name/BIGINT/real-login cases GREEN на неизменённом product. После этого
source/class/case names новых тестов не менялись.
Прежние 5 fixtures и fanout/runtime contract прошли целиком. Полный v2 был RED:
398 tests/3 failures/0 errors, все193 новых GREEN. Legacy fixture ожидал
trim адресата; isolated surefire:test на неизменённых 2260 R232 classes дал
те же6 cases/3 failures (class hashes до/после прежние). Adapter v2 использует
точный canonical target и добавляет отдельный deny для spaced neighbor; старые
assertions не удалялись. Затем весь selection повторён для финального GREEN,
не собран из failed run. Preview product v1 остановлен до записи: tuple WHERE
был также в producer query; v2 сузил anchor к lateral alias. Evidence сохранено.

Scope15:4 main (включая новый package-private recipient),5 old fixtures,
3 new test sources,task/S0/new append-only changelog. R232 cache1384 Java/
2260 classes сверены; не затронутые inputs/classes прежние. Maven wrapper/JDK17,
ignored native R231 supplier с pinned source/class и UTF8/ICU und guards.
Owned PostgreSQL16.15/loopback54167/iguana_r231_unicode, fresh test schemas,
Docker не запускался, owned PG остановлен. UI/schema/migrations/deps/flags прежние;
Sass EOL-only outputs восстановлены exact baseline bytes в sandbox.

Source/test сессия C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на
другой машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup, доступа к нему
не было. Последний документированный runtimeR223: webR222/workerR198,
foundationOFF/readiness200UP/health503DOWN из-за прежнего inboundDLQ1.
Runtime/rollback blocks сохранены. Working deploy/restart/DB/flags/provider/
queue mutations не выполнялись; V83 не повторять. Source R224–R226 и R228–R233
ещё требуют будущего panel-web image, R227 tests/docs отдельного rollout нет.
Baseline HEAD5d2ceefc1 локально содержит R232, origin/actual main при старте
f1ad1d5ab (R231), локальная ветка ahead1. Продолжение разрешено от этого
проверенного local commit; pending push не подменяется утверждением публикации.
Прежний commit grant действует до конца30 октября Europe/Moscow. Push оставлен
владельцу; автоматический push не возобновлять без нового указания.
Фактические apply/Git состояния — ignored r233-result/final JSON.

Далее: оставшиеся BUSINESS edit/feedback/admin account boundaries и page/
notification decisions, PENDING task/project/system/global policies, AI privacy/
context/retention/delivery, fleet/flags/S6 acceptance и DLQ gate. Исторический
S2B снятR190; общая задача остаётся 🟡, deployment не заявляется.
