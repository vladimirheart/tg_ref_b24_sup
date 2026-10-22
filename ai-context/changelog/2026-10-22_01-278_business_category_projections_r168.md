# 01-278 — canonical BUSINESS категории: чтение, поиск, экспорт и сводка, R168

Время: 2026-10-22 18:53:42 +03:00 (Europe/Moscow).

Промт пользователя:

> репо обновлён другими изменениями в рамках задачи. изучи и продолжай. возьми более широкий пакет. напоминаю, что рабочий экземпляр проекта на другой машине.
> коммит и пуш разрешаю сразу после внечения правок без моего подтверждения - разрешение только в течении этих суток

## Baseline и граница среды

HEAD/origin/main/actual remote main 58b5edc86e0f57fd3a25c64f5fdc63dc5bb6ac5b; parent
f96ba725dc3e2fa4b0b2671b927bf2a51a50c474. Проверены R167
12e157512b66ec93b4ffbbbee9b87caf4265ce35 и четыре последующих UI commits.
Main/index исходно чистые. Detached sandbox .tmp/01-278-r168.
Рабочий проект и Docker на другой машине, в другой сессии; записи R163–R167
про «эту машину» не переносятся сюда. Docker Server 29.7.2 здесь используется
только для изолированного PostgreSQL Testcontainers. Рабочий stack не
инспектировался и не менялся; нет restart/rebuild/deploy, рабочих DB migrations,
backfill/config/flags и provider sends. Source minimum V81, без новой migration.
Пользователь дал новый grant commit/push в этой сессии на 2026-10-22 Moscow,
expiry 2026-10-22 21:00 UTC. Publisher отдельно проверяет срок и exact content;
grant не распространяется на рабочий rollout.

## Реализовано

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

## Проверки

413 Java tests / 9 свежих XML, failures/errors/skips=0:
.tmp/r168-panel-v2.log, exit 0. Source/compiled classes/XML и copied resources проверены.
11 BUSINESS Node scripts GREEN, включая новый category UI и прежние
export/root preview/workspace/scope reset/answers/spans/manual/questionnaire cases.
Новые cases покрывают persisted save/close/reopen/clear, все BUSINESS ticket
snapshots, literal category/search/filter intersection и одинаковую page/export
выборку; raw collision/exact opaque ID, old raw category secrecy, owner transfer/
generation, stale grants/business archive и полные counts/1000→1001 refusal.
HTTP покрывает реальный POST с CSRF → canonical read/search/export/summary и
отзыв прав при сохранённой сессии. VM UI проверяет textContent вместо HTML,
late A response после B, exact category URL encoding, empty/403/413 states.

Первый v1 был RED при test-compile: overload Mockito contains(String) выбрался
вместо Hamcrest matcher в двух новых HTTP assertions; указан точный matcher.
Предыдущие assertions не ослаблены. Новый контракт намеренно заменяет только
raw category workspace read: тест ожидает [] canonical projection при наличии
legacy rows и отдельно доказывает их неизменность. Минимальные read/manual
fixtures получили V81 business_categories column; production fallback нет.
RED/GREEN logs и все operator artifacts остаются в ignored .tmp.

## Изменённые области и передача

- BusinessScopedTicketReadService: canonical categories, фильтр/поиск, summary.
- BusinessScopeReadController: category filter и private bounded summary API.
- business-dialogs.js / businesses/dialogs.html: список/карточка/фильтр/сводка,
  сохранено актуальное Neutral Ops оформление.
- PostgreSQL projection suite, реальные BUSINESS HTTP regressions, MVC security,
  read/manual fixtures, четыре BUSINESS Node scripts.
- S0-контракт и текущая сводка 01-278 обновлены, история changelog сохранена.

Source apply допускается только после GREEN и exact baseline/blob/content/file
guards. Generated Sass CSS исключаются только по совпадению filtered blob с
baseline; не являются частью этого пакета. Git staging/commit/push выполняются
отдельно по сегодняшнему разрешению, с проверкой remote и без force push;
фактический commit/push фиксируется в operator result и финальном ответе.

## Осталось

Scoped feedback/client delivery, blacklist policy, producer/child attribution,
прочие legacy writers/read/search/export/background paths, GENERAL media,
global/system/task policies, AI privacy/delivery, owner decisions S2B и рабочая
приёмка/выпуск на другой машине. Остальные global analytics не объявляются
изолированными благодаря category summary. Задача 01-278 остаётся 🟡.
