# 01-278 — наблюдаемые интервалы работы GENERAL (R138)

Дата: 2026-10-19. Задача: 01-278. Статус: 🟡, source/test increment.

Промт пользователя:

> продолжай

Сохраняется принятое ранее уточнение: рабочий Docker и весь проект находятся
на другой локальной машине и в другой сессии агента. Эта сессия меняет исходники
и использует только изолированные локальные Testcontainers; runtime не проверен.

Baseline: HEAD/origin/main/actual remote main
99063c71ec59171433a58a1eca07a94f02527e43; parent
c76fa5dafac9e080254e0e3a60282d0ef665256a. Actual R137 file set: 12.
Работа выполнена в detached worktree с чистого основного checkout.

Добавлены typed интервалы exact GENERAL owner/public generation, запускаемые
только новым manual pending→open. Следующий ручной переход завершает текущий
интервал при совпадении status/revision и времени. Внешнее ABA/clock regression
исключены из итога. OPEN unique, start/identity и terminal result immutable.
Root CAS, interval, audit и outbox — одна атомарная операция. V68 additive;
readiness закрыт без table/helper и проверяет writer только WHERE 1=0.

READ service проверяет live rights/account/flags/current exact owner до суммы
и LIMIT. Страница работы показывает completed total, active start и последние
10 интервалов; actor labels escaped. Учёт явно частичный: legacy spans/active/
work totals не читаются, не меняются и не backfill-ятся; существующий open root
без нового start не получает длительность. Owner/generation не наследуют историю.
Актуализированы S0 contract и current readiness summary задачи.

Validation: 272/272 GREEN, 9 XML suites без failures/errors/skips:
work SQL 85; HTTP 31; reply SQL 66; realtime 36; reads 7; controllers 10;
readiness 9; outbox scope 16; watcher 12. Compile 681 main / 504 test;
full Flyway до V68, upgrade V67→V68 с сохранением legacy данных, concurrency,
owner/READ isolation, aggregate/limit scope, fractional sum, immutable/unique,
rollback и clock/ABA checks; actual rendered CSRF cycle. Два Node harness и
diff check GREEN. Guarded exact-set/SHA-256 copy — пакет 12 project files;
generated CSS исключён. Report freshness проверена по final source timestamps.

Source apply отделён от ручных staging/commit/push. Рабочие DB migrations,
backfill, flags/config, rebuild/restart/deploy не выполнялись. Local tests не
подтверждают live Redis/proxy/provider E2E и runtime acceptance другой машины.
Legacy activity/span attribution, прочие GENERAL writes/notifications,
reconciliation/retention и остальные блоки current summary ещё PENDING.
