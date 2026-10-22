# 01-278 — история статуса и категорий в карточке, R171

Время: 2026-10-22 23:31:37 +03:00 (Europe/Moscow).

Промт пользователя:

> продолжай

Продолжает действовать контекст предыдущего запроса этой сессии:

> репо обновлён другими изменениями в рамках задачи. изучи и продолжай. возьми более широкий пакет. напоминаю, что рабочий экземпляр проекта на другой машине.
> коммит и пуш разрешаю сразу после внечения правок без моего подтверждения - разрешение только в течении этих суток

## Baseline и среда

Baseline R171: HEAD/origin/main/actual remote main
d13b428b65acc969f9be458302eec63820271ac3; parent
483cbddd20fae95687698fac69e7a931bdedcdff. R170 subject и фактические
12 файлов (713 additions/7 deletions) проверены; main/index исходно чистые.
Detached sandbox .tmp/01-278-r171. Рабочий экземпляр и Docker — на другой
машине и в другой сессии; здесь source и изолированные PostgreSQL Testcontainers.
Рабочие revision/DB/config/flags/providers не проверялись и не менялись;
нет рабочего restart/rebuild/deploy/migration/backfill. Продолжает действовать
grant commit/push этой сессии только до 2026-10-23 00:00 +03 (2026-10-22 21:00 UTC).
Publisher отделён от source apply и проверяет expiry/exact baseline/files/content/
actual remote, без force; разрешение не включает рабочий rollout.

## Реализовано

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

## Проверки

Проверки R171: 258 Java tests / 6 свежих XML GREEN,
failures/errors/skips=0, Maven exit 0; 14 BUSINESS Node scripts GREEN.
Evidence: .tmp/r171-panel-v2.log. Проверены все три действия и ordered before/after
snapshots, current READ/account/grants, raw/public collisions, закрытый root,
полная смена owner/generation, отсутствие raw table и canonical schema,
strict cursor/empty pages/Long.MAX_VALUE/string BIGINT и frozen actor после
rename/delete/reuse имени. Invalid array/object/number/JSON null snapshots — 503
без строкового coercion. Real HTTP CSRF save→close→reopen→clear→paged history,
сохранность старых rows, revoke и business transfer. UI VM: full owner/две
версии, late response, double-click, textContent и точный string cursor;
прошли также прежние assignment/participants/categories/reset/export checks.
Protective assertions не ослаблены; root-preview fixture получил только stub
нового независимого loader. Freshness source/class/XML/copied resources сверена;
generated CSS исключены по exact filtered baseline blob proof. UI helper v1
остановлен self-guard до записи файлов (порядок переименования function names),
исправлен v2. Первый Java v1 остановился на test-compile: contains(String)
выбрал Mockito вместо Hamcrest в новом HTTP assertion; matcher уточнён явно,
полный targeted набор повторён v2. Assertions и production predicates не ослаблены.
Browser/live Redis/proxy/provider и рабочая приёмка не проводились. Статус 🟡.

## Изменённые области и передача

- Новый BusinessTicketWorkReadService и private READ controller.
- business-dialogs.js / businesses/dialogs.html: status/category history/pagination.
- PostgreSQL/MVC/real HTTP/Node; прежний root-preview fixture.
- Актуальная сводка 01-278 и S0-контракт; старые changelog не переписаны.

Source apply только после sandbox GREEN, clean main/index, exact baseline/actual
remote/source content и полного списка 12 файлов. Git publisher — отдельно по
сегодняшнему разрешению. Фактические source/commit/push/deployment состояния
фиксируются в ignored operator result и финальном ответе. Этот source READ-пакет
не является рабочей приёмкой; следующий runtime-этап выполняется в другой сессии.
