# 01-278 — история ручных и SLA-назначений в карточке, R170

Время: 2026-10-22 23:12:58 +03:00 (Europe/Moscow).

Промт пользователя:

> продолжай

Продолжает действовать контекст предыдущего запроса этой сессии:

> репо обновлён другими изменениями в рамках задачи. изучи и продолжай. возьми более широкий пакет. напоминаю, что рабочий экземпляр проекта на другой машине.
> коммит и пуш разрешаю сразу после внечения правок без моего подтверждения - разрешение только в течении этих суток

## Baseline и среда

Baseline R170: HEAD/origin/main/actual remote main
483cbddd20fae95687698fac69e7a931bdedcdff; parent
33a58565ae048cf02d01f2ecb3e57bfc1ddcd9ab. Оба новых Neutral Ops UI commits
33a58565a (14 файлов) и 483cbddd2 (67 файлов) изучены; оформление сохранено.
Main/index исходно чистые; detached sandbox .tmp/01-278-r170.
Рабочий экземпляр и Docker находятся на другой машине и в другой сессии.
Здесь только source и изолированные PostgreSQL Testcontainers: рабочие
revision/DB/config/flags/providers не проверялись и не менялись. Нет рабочего
restart/rebuild/deploy/migration/backfill. Сегодняшний grant commit/push
этой сессии действует до 2026-10-23 00:00 +03 (2026-10-22 21:00 UTC), проверяется
отдельным publisher по exact baseline/files/content/actual remote, без force.
Разрешение не включает рабочий rollout и не переносится на следующие сутки.

## Реализовано

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

## Проверки

Проверки R170: 288 Java tests / 7 свежих XML GREEN,
failures/errors/skips=0, Maven exit 0; 13 BUSINESS Node scripts GREEN.
Evidence: .tmp/r170-panel-v1.log. Проверены mixed manual/SLA pagination и empty pages,
current account/grants, VIEWER/ALL read, exact opaque ID, raw/public collisions,
full-owner/generation replacement, closed/resolved, отсутствие raw table,
Long.MAX_VALUE/string BIGINT и snapshots после delete/reuse имени. Schema
unavailable и duplicate revision — fail closed. Real HTTP: CSRF manual write
и настоящий SLA writer → общий private READ/cursor, отсутствие source/route/
previous responsible, revoke и owner transfer. UI VM: markup, versions/full
owner, late response, double-click, exact string cursor и empty/denied states.
Прежние protective assertions сохранены; root-preview fixture получил только
stub нового независимого loader. Source/class/XML/resources freshness сверены;
generated CSS исключены по exact filtered baseline blob proof. Browser/live
Redis/proxy/provider acceptance не проводилась. Общий статус 01-278 — 🟡.

## Изменённые области и передача

- Новый BusinessTicketAssignmentReadService и private READ controller.
- business-dialogs.js / businesses/dialogs.html: отдельная история и pagination.
- PostgreSQL/MVC/real HTTP/Node, прежний root-preview fixture.
- Актуальная сводка 01-278 и S0-контракт. Исторические changelog не переписаны.

Source apply только после sandbox GREEN и проверки exact baseline, actual remote,
clean main/index, test/source content и полного списка 12 файлов. Git publisher
отделён от source apply. Фактические source/commit/push/deployment состояния
фиксируются в ignored operator result и финальном ответе. Рабочий агент другой
машины выполняет live приёмку отдельно; задача этим READ-пакетом не закрывается.
