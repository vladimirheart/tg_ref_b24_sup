# 01-278 — ручной BUSINESS lifecycle и категории, R167

Время: 2026-10-22 14:12:17 +03:00 (Europe/Moscow).

Промт пользователя:

> делай

Контекст: продолжение предложенного после R166 scoped BUSINESS закрытия,
повторного открытия и категорий. Рабочий экземпляр находится на этой машине;
пользователь разрешил restart Docker/контейнеров, но не рабочие migration/
backfill/flags/rollout. Ручные команды фиксации не предоставляются.

## Baseline и среда

HEAD/origin/main/actual remote main:
248b5db16ffe69b4c70ecc937f032592cff003ae; parent
c70d934af3ea57c1d96e4e41dd915db11f8a4ac7.
Проверены subject и 17 фактических файлов R166; main/index перед работой чистые.
Detached sandbox: .tmp/01-278-r167-business-lifecycle.
Docker Server 29.7.2; web/bot-runner/ops-worker/panel-direct/PostgreSQL/
RabbitMQ/Redis healthy при read-only проверке. Restart/rebuild не понадобились.
Рабочие revision/DB migrations/flags и functional acceptance не подтверждены.
Все тестовые SQL migrations выполняются только в изолированном Testcontainers.
Grant commit/push R160 из другой сессии не наследуется; Git publisher отсутствует.

## Реализовано

- V81 добавляет business_work_revision и business_categories в canonical root.
  Default 0/[] не импортирует ticket_categories и не классифицирует старые
  BUSINESS/GENERAL/UNRESOLVED rows. Новый append-only business_ticket_work_events
  хранит полный user/raw/public/business, revision, action, статусы, обе category
  snapshots и numeric actor со snapshot имени. Нет FK cascade потери истории.
- BUSINESS close из pending/open переводит root в resolved с 1–50 категориями.
  Reopen из closed/resolved переводит в pending, сохраняя категории.
  Сохранить/очистить категории можно только в pending/open; no-op отвергается.
  Trim/dedup, case-sensitive, 160 Unicode code points/категорию; null/control/
  oversize/>50 входных строк отвергаются. Counters/timestamps меняются только
  соответствующей командой. Reopen не назначает ответственного автоматически.
- Отдельный root trigger отслеживает статус, категории, полный owner и channel,
  включая внешние SQL writers и ABA. Прямой revision override запрещён.
  Новый полный owner начинает с пустой category projection, не наследует
  категории другого scope. Workflow-written значения остаются в immutable
  истории исходного owner; direct SQL обязан обеспечивать собственный audit.
  Legacy/raw и GENERAL категории не переписываются.
- BusinessTicketWorkService использует PAGE boundary контроллера + current
  unique enabled/unblocked/non-AI account, READ и business OPERATE. ROLE_ADMIN,
  VIEWER и GENERAL grants не заменяют OPERATE; ALL_BUSINESSES OPERATE допускается.
  Principal не trim-ится. Shared raw advisory → root → account/business →
  qualifying grant locks → final owner/unique/raw/status/revision/rights/
  categories/channel proof. Root, audit и outbox атомарны; final confirmation
  и flags проверяются перед commit. Ошибка/поздний отказ откатывают всю запись.
- Существующая страница business-dialog-assignment получила статус, категории,
  формы и последние 10 событий. Posted owner/CAS/status сверяются с public
  lookup; GET не смешивает owners двух последовательных lookups. CSRF и
  no-store сохранены. HTML CSRF отказ идёт на /error/403, page/OPERATE — 403.
  Категории и история экранируются. Карточка ведёт на расширенный экран.
- Frozen business_work_changed outbox проходит только metadata invalidation
  через existing current READ SSE/fanout. Нет bell, queue, AI, client/support
  provider, activity/spans, feedback, assignment или participant callbacks.
  Старые lifecycle entrypoints R166 остаются закрыты для BUSINESS/GENERAL.
- Readiness проверяет новые root поля и events; source minimum V81.
  S0 и актуальная сводка/семь блоков/очередь задачи обновлены. Общий статус 🟡.

## Проверки

Итог: 979 Java cases / 8 свежих XML GREEN, failures/errors/skips=0:
810 cases из семи GREEN suites r167-final-v3.log и все 169 cases
BusinessTicketAssignmentHttpPostgresTest из r167-http-final-v4.log (exit 0).
V3 целиком был RED только на трёх page-denial assertions; после уточнения
ожидания до фактического 403 повторён весь HTTP-класс. Остальные семь reports
и их compiled classes имеют прежние exact SHA-256 после v4 compile;
source/class/report freshness сверены.
Новых cases — 190: work service 150, BUSINESS HTTP 31, frozen metadata 7,
readiness 2. GENERAL HTTP — 132, GENERAL work — 85, GENERAL assignment — 48,
legacy lifecycle — 260, frozen metadata — 105, readiness — 30.
Полный backend main/test compile выполнен.

Проверены позитивный save→close→reopen→reload, CSRF/PAGE/OPERATE/foreign/system
admin, current rights, forged/старые формы, root/status/category ABA, полный
owner/channel/generation и unique raw ID, opaque trim-сосед, Unicode/XSS,
лимиты, immutable history, metadata-only outbox, concurrent winner и rollback.
Пять реальных PostgreSQL lock-wait cases: membership, ALL grant, account,
business и root. Final SQL injections повторяют права/owner/CAS после locked
snapshot. Canonical category projection не наследуется новым полным owner.
V80→V81 сохраняет все прежние поля/legacy children и новые defaults 0/[];
повторный Flyway не применяет миграции снова и не меняет rows. В прежних
upgrade tests сохранены exact V66→V67/V67→V68/V68→V69/V77→V78/V78→V79/V79→V80
goldens; затем отдельно проверяются V81 old fields/new defaults, roster,
grants/intervals/history без invented rows.

Промежуточные evidence не суммируются повторно:
v1 — 161 GREEN (131 первых work cases + 30 readiness).
v2 — 969 cases, 5 failures: три новых CSRF tests ожидали прямой 403 вместо
существующего HTML redirect, два старых upgrade SELECT * захватывали V81.
v3 уточнил версии миграций и CSRF redirect; три оставшихся page-denial
assertions отдельно уточнены до фактического 403. Общие security handlers
и защитные predicates не менялись. Assertions denial/no-write и точные
upgrade snapshots сохранены; RED logs не удаляются.
JavaScript не менялся; Node/browser/live provider/Redis acceptance не запускались.

## Передача и границы

Manifest — 18 project files. Guarded apply только после sandbox GREEN:
fresh HEAD/origin/actual remote, clean main/index, exact Git blobs и отсутствие
новых targets; затем exact file set и normalized SHA-256 sandbox/main.
Проверены compiled classes и source/target copies трёх изменённых resources.
Четыре Maven CSS EOL-only artifacts исключаются только после совпадения filtered
Git hash с baseline. Ignored operator artifact: spring-panel/target/r167-operator-v4.json.
Source apply и Git publisher разделены; staging/commit/push/deploy не выполняются.

Это source-only локальный ручной workflow, не законченный client lifecycle.
AI, feedback, activity/spans, old children attribution и provider delivery не
переведены на новый workflow. Canonical категории пока управляются на новом
workbench; остальные read/analytics/export projections требуют отдельного
подключения без raw fallback. Business transfer policy, retention/reconciliation,
остальные producers/writers, global blacklist/system/task policy, AI privacy,
владелец-решения S2B и live/release acceptance остаются gates.
In-memory flags проверяются, но не сериализованы до commit. Новых grants,
массового backfill, рабочего deploy/restart/migration/config/flags/provider sends
нет; пользовательские данные и старые rollback paths/worktrees не удалялись.
