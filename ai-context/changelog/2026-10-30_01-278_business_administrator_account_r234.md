# 01-278 / R234 — BUSINESS administration account/privileges

Время: 2026-10-30T12:13:17.168Z (Europe/Moscow +03).

Промт пользователя:

> продолжай

Baseline: 90902715bcf6cf5459aed858795f442aa8d1a424; parent 5d2ceefc1e5bf4de513f5a0f1ce356a017530e85.

R234: все14 API операций BusinessAdministrationService принимают исходный
Authentication; unbound overloads удалены. Controller передаёт тот же principal
для overview/channel inventory/binding preview, business create/rename/status,
membership/ALL grant/revoke, location create/status и channel binding save/disable.
Package-private BusinessAdministratorSql проверяет исходные BIGINT ID и exact
raw-name, enabled/COALESCE(blocked,FALSE), отсутствие folded-дубликата и текущие
ROLE_ADMIN + PAGE_SETTINGS в каждом owning SQL. Предварительное чтение assigned
role — только snapshot: raw id/name/permissions повторно сверяются в CTE с locks;
direct authorities также читаются/блокируются в том же statement. Семантика role
name/permissions совпадает с login/GeneralTicketAccessService через существующий
PanelRolePermissionSnapshot. Session authorities не заменяют текущие DB grants.
Каждый DML зависит от administrator до изменения данных, а outer result отдельно
различает отказ403 и допустимый пустой результат/отсутствующую цель. Overview
проверяет права в каждом из5 SELECT; отказ не возвращает частичный catalog.
Сериализация channel edits и прежние validation/status/identity/grant policies
сохранены. Это statement-level account/rights proof, не новая lifetime/session
epoch или serializable policy против изменения после snapshot.

615 tests /5 suites GREEN одним selected run v5.
589 новых:505 SQL/service и84 HTTP с реальным security chain/R189/controller/SQL.
Проверены все14 operations, rename/delete reuse, case/state/duplicates, revoked
direct rights, invalid/unbound/anonymous/unauthenticated auth; no-write snapshots.
Положительные raw edge spaces/Unicode, BIGINT>2^53/Long.MAX, NULL-blocked,
direct/mixed/assigned role sources и13 login-compatible role/permissions forms.
56 controlled mutations между role snapshot и owning statement; это scripted
boundary tests, не concurrent MVCC proof. HTTP early revocations сохраняют
прежний R189 redirect/403; valid HTTP path использует реальные service queries.
Frozen baseline: 589 tests/323 assertion failures/0 errors/
266 controls на неизменённом product; source/class/case names
новых тестов после baseline не менялись. 3 прежних fixtures адаптированы только
для Authentication: отдельный administrator без business grants и Mockito args;
старые assertions сохранены и suites выполнены целиком.

Scope11:3 main (service/controller/new SQL helper),3 old fixtures,2 new tests,
task/S0/new append-only changelog. Prior R23390902715b, parent5d2ceefc1,
subject/15 paths/content проверены. HEAD/origin/actual remote совпадали: R232/R233
опубликовал владелец. Cache1388 Java/2265 classes сверены; untouched inputs/classes
прежние. Изолированный detached worktree, Maven wrapper/JDK17, pinned ignored
native supplierR231 с UTF8/ICU und guards, PostgreSQL16.15/loopback54167,
iguana_r231_unicode/fresh schemas. Docker не использовался; owned PG остановлен.
UI/schema/migrations/deps/flags прежние; Sass EOL-only outputs восстановлены.
Первый unfrozen harness остановлен до evidence для reuse одной owned connection
на case. Следующий fixture run выявил GENERATED ALWAYS setup error и неверное
ожидание403 вместо прежнего R189 login redirect; fixtures исправлены до product,
предыдущие sources/logs сохранены, финальная RED/GREEN пара отдельная. Product
operator previewv1 остановлен до записи из-за неоднозначного overview() anchor;
v2 использует declaration anchor и WHERE TRUE перед INSERT SELECT ON CONFLICT.
При первом сохранении RED metadata source snapshot не успел завершиться перед
адаптацией fixtures; основной checkout не затронут. Premature v4 дал только
compile failure без test execution. Все sessions завершены; fixtures восстановлены
exact baseline bytes, product source/classes сверены прежними, frozen v3 reports
и test classes сохранены повторно перед product edit. Evidence sequence recovery
сохранено; эта ошибка orchestration не подменяется product/test failure.

Source/test сессия C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на
другой машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup; доступа к нему не
было. Последний документированный runtimeR223: webR222/workerR198,
foundationOFF/readiness200UP/health503DOWN из-за прежнего inboundDLQ1.
Runtime/rollback blocks сохранены. Working deploy/restart/DB/flags/provider/queue
mutations не выполнялись; V83 не повторять. SourceR224–R226 и R228–R234 ещё требуют
будущего panel-web image, R227 tests/docs отдельного rollout нет. Commit grant
до конца30 октября Europe/Moscow; push оставлен владельцу по его последнему
указанию, автоматический push не возобновлять. Apply/Git факты — ignored
r234-result/final JSON; source validation не является deployment/acceptance.

Далее: оставшиеся BUSINESS edit/feedback account boundaries и page/notification
решения; task/project/system/global PENDING, AI privacy/context/retention/delivery,
fleet/flags/S6 acceptance и DLQ gate. S2B снятR190; общая задача остаётся 🟡.

Изменённые файлы:

- `ai-context/changelog/2026-10-30_01-278_business_administrator_account_r234.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `spring-panel/src/main/java/com/example/panel/controller/BusinessAdministrationApiController.java`
- `spring-panel/src/main/java/com/example/panel/service/BusinessAdministrationService.java`
- `spring-panel/src/main/java/com/example/panel/service/BusinessAdministratorSql.java`
- `spring-panel/src/test/java/com/example/panel/controller/BusinessAdministrationApiControllerWebMvcTest.java`
- `spring-panel/src/test/java/com/example/panel/security/BusinessAdministratorAccountWebMvcTest.java`
- `spring-panel/src/test/java/com/example/panel/service/BusinessAdministrationServicePostgresIntegrationTest.java`
- `spring-panel/src/test/java/com/example/panel/service/BusinessAdministratorAccountPostgresTest.java`
- `spring-panel/src/test/java/com/example/panel/service/BusinessScopedTicketReadServicePostgresIntegrationTest.java`
