# 01-278 — BUSINESS roster/history READ и панель участников, R169

Время: 2026-10-22 19:24:59 +03:00 (Europe/Moscow).

Промт пользователя:

> продолжай

Действует контекст предыдущего запроса этой сессии:

> репо обновлён другими изменениями в рамках задачи. изучи и продолжай. возьми более широкий пакет. напоминаю, что рабочий экземпляр проекта на другой машине.
> коммит и пуш разрешаю сразу после внечения правок без моего подтверждения - разрешение только в течении этих суток

## Baseline и среда

HEAD/origin/main/actual remote main f8a32d01d0219f27323a5fd7341d899cef6c67dc;
parent 58b5edc86e0f57fd3a25c64f5fdc63dc5bb6ac5b. Subject и фактические
16 файлов R168 подтверждены; main/index исходно чистые. Detached sandbox
.tmp/01-278-r169. Рабочий экземпляр на другой машине, в другой сессии;
исторические наблюдения runtime-сессии не переносятся сюда. SQL только в
изолированном PostgreSQL Testcontainers. Рабочие stack/revision/DB/config/
flags/providers не инспектировались и не менялись. Нет рабочего restart,
rebuild/deploy/migration/backfill. Source minimum V81 без новой migration.
Grant commit/push R168 действует в этой же сессии только 2026-10-22 Moscow,
expiry 2026-10-22 21:00 UTC; publisher отделён от source apply и проверяет срок,
baseline, actual remote и exact files/content; разрешение не включает rollout.

## Реализовано

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

## Проверки

309 Java tests / 9 XML GREEN суммарно: восемь неизменных
GREEN suites v1 и MVC rerun v2 (14 tests, exit 0), failures/errors/skips=0.
Evidence: .tmp/r169-panel-v1.log (8 preserved GREEN suites) + .tmp/r169-panel-v2.log (14 GREEN MVC tests). Верифицированы source/compiled/XML и copied resources.
12 BUSINESS Node scripts GREEN: новая panel/pagination и прежние
scope reset/root preview/workspace/categories/export/questionnaire paths.
Новые PostgreSQL cases проверяют persisted V79 add/remove и исторические
snapshots, raw/public collisions, closed/resolved, отсутствие legacy table,
чужой/GENERAL/unresolved scope и полную замену user/raw/public/business/generation;
current READ/roles/account state и numeric member identity при rename/delete/
reuse имени. History strict cursor/newest-first/hasMore/empty и default page
на Long.MAX_VALUE; JSON revisions/member IDs strings. Roster ровно 200 либо
явный 413 на 201, schema unavailable — fail closed. Реальные HTTP cases:
CSRF add/remove → read-only roster/history, cursor, old raw projection=[],
scope transfer и отзыв доступа. VM UI проверяет textContent, late context,
owner/revision mismatch, строковый BIGINT cursor, double-click и denial/empty.
Legacy workspace test намеренно заменяет только raw participants read и лимит:
теперь проверяется [], сохранность старых rows и отсутствие их влияния на
workspace при >100 entries. Иные protective/golden assertions не ослаблены.
Первый v1 RED только на настройке Mockito нового MVC-test: replacing when()
повторно вызывал прежнюю throwing stub. Последовательность empty/413/503 задана
одним stubbing chain; assertions/production unchanged. Перед v2 сохранены
exact SHA-256 восьми GREEN reports/sources/compiled classes и production/
resources; после v2 совпадение подтверждено. Новый MVC XML свежий, итог без
двойного счёта tests. RED/GREEN logs сохранены в ignored .tmp.
Browser/live providers/Redis/proxy и рабочая приёмка не выполнялись.

## Изменённые области и передача

- Новый BusinessTicketParticipantReadService и private READ controller.
- BusinessScopedTicketReadService: удалён raw participants query, compatibility [].
- business-dialogs.js / businesses/dialogs.html: roster/history panel с пагинацией;
  существующее Neutral Ops оформление сохранено.
- PostgreSQL/MVC/real HTTP и Node проверки, прежний read fixture/UI contract.
- Актуальная сводка 01-278 и S0-контракт; старые changelog не переписывались.

Перед source apply проверяются exact baseline, blobs/content, actual remote,
clean main/index и полный список файлов; CSS build artifacts исключаются лишь
по exact filtered baseline hash. Источники переносятся только после GREEN;
Git publisher выполняется отдельно по сегодняшнему разрешению, без force push.
Фактические source/commit/push/deployment состояния — в ignored operator result
и финальном ответе. Общая задача остаётся 🟡: остались legacy responsible/active/
spans attribution, global/recipient policy, feedback/client delivery, S2B и live
acceptance/rollout на рабочей машине. Эти gates данным READ-пакетом не закрыты.
