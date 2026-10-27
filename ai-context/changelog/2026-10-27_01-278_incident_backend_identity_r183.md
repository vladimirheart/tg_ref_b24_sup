# 01-278 R183: точные логины в backend инцидентов и доставке

Дата: 2026-10-27, Europe/Moscow. Время фиксации evidence: 2026-10-27T04:08:38.707Z.

Промт пользователя: «продолжи».

Действующий предыдущий grant: «продолжай. пуш и коммит подтверждаю автоматически до конца суток»
(2026-10-27 Europe/Moscow); повторное подтверждение не запрашивается.

Baseline R183 (2026-10-27): clean main, HEAD/origin/main/actual remote
ae325d000f43d367f2e0c6ad344e1e30c34986b7. R182 проверен по parent/subject/exact
6 paths/normalized content SHA. Detached sandbox .tmp/01-278-r183. Рабочие
проект и Docker находятся на другой машине/в другой сессии. Здесь source и
изолированные Maven service/MVC fixtures. Read-only docker version probe показал
недоступный local daemon; Docker не запускался, PostgreSQL/Testcontainers здесь
не выполнялись. Рабочие revision/DB/config/flags/providers неизвестны и не менялись;
restart/deploy/migration/backfill отсутствуют. Grant пользователя на commit/push
от 27 октября остаётся до 2026-10-27 21:00 UTC (28 октября 00:00 +03).
Source apply и отдельный expiry-guarded Git publisher разделены.

R183 сохраняет точные непустые account keys в backend инцидентов. IncidentService
не обрезает owner, watcher_identity, created_by/added_by/event actor; прежние
case folding, порядок/dedup и empty-input validation сохранены. Загруженные owner
и watchers передаются в NotificationRoutingService без trim. Add/remove watcher
различают literal " lead " и отдельный "lead". Исторические строки не переписываются.
Singular route_type=user сохраняет scalar target в inline routes при создании,
add/update route, JDBC binding outbox, JSON envelope, row mapping и вызове
NotificationService.notifyUser. В outbox также сохраняются exact owner и
requested_by; прежний регистр этих metadata не меняется. Нормализация остальных
route types и строковая users CSV-грамматика с split/trim/case folding/dedup
сохранены; department и all_operators используют прежние API. Webhook targets,
display text, route retries/claim/lease/monitoring policy и SQL predicates не менялись.
Это исправление identity strings, не новый business scope, recipient eligibility,
правила глобальных incidents или выдача grants. UI/JS по-прежнему имеют отдельный
trim/CSV debt: полный browser→API round-trip R183 не закрывает. Старая уже обрезанная
outbox/history metadata не восстанавливается по догадке. Foreign/shared channel
CRUD policy, остальные upstream/global picker/attribution, S2B и рабочие S3–S6
rollout/acceptance остаются открытыми. Общий статус задачи — 🟡.

Проверки R183: 88 Java tests / 7 свежих XML GREEN, Maven v3 exit 0,
failures/errors/skips=0. Новые IncidentIdentityBoundaryTest — 50 cases и
IncidentRouteIdentityBoundaryTest — 19. Проверены create/update/reload projection,
owner/watchers/audit/notification delegation, add/remove literal watcher,
inline/add/update singular user route, enqueue bindings + JSON → реальный outbox
row mapper/dispatch → реальный delivery service → captured NotificationService,
long string >2^53, whitespace/CRLF/tab, padded null, delimiter/markup keys,
blank/case/order/dedup и CSV/department/operator controls. Repository/notification
зависимости — mocks; CapturingJdbc сохраняет параметры и возвращает их реальному
row mapper, но не исполняет SQL. DB locking/transactions/claims, live authentication,
сохранение SQL/фактическая доставка или browser/provider acceptance не заявляются.
До production edit те же test source SHA на трёх exact baseline blobs дали
69 tests / 48 failures / 21 passing controls / 0 errors/skips в v2. Первый v1
выявил missing findByIdForUpdate stub нового fixture; добавлен только lock lookup,
assertions не ослаблялись. V1 log сохранён; final test source заморожен после v2.
RED XML/log/test SHA и baseline blobs записаны в ignored r183-baseline-red.json.
Финально повторены существующие IncidentService (3), IncidentApiController (3),
route delivery diagnostics (1), monitoring policy (8), ops escalation (4).
Java cache R182 использован только после normalized SHA comparison всех Java
sources; новые tests скомпилированы в baseline runs, все три production class
реально перекомпилированы в v3. XML свежие; source/class/report/log SHA, unchanged
cache inputs, generated CSS baseline и diff --check проверены. Node/browser не
запускались; прежний R53 Node debt и PostgreSQL suites здесь не проверялись.
Рабочей приёмки нет.

Фактические production files: IncidentService.java,
IncidentRouteDeliveryOutboxService.java и IncidentRouteDeliveryService.java.
Добавлены два identity test class; обновлены task-details/01-278.md и S0-контракт.
Эта запись — новый append-only changelog. Итого 8 project files; migrations,
UI/JS/CSS, permissions/schema/flags и SQL statements не менялись.

Source apply/publisher сохраняют baseline/head/origin_main/sandbox_green/
source_applied/staged/committed/pushed/deployment и exact changed-file list отдельно
в ignored r183-result.json. Changelog не подменяет факт Git publication или
рабочей приёмки; commit/push проверяются после source apply.
