# 01-278 R201 — автор ручного BUSINESS-назначения по ID сессии

Дата: 2026-10-28 (Europe/Moscow).
Baseline: c2e2ad3443159e97845a6a3fc543fc5eb5d6967b; parent d57d35f022ce96f08b9e2a375696c07d90bd8ab8.

Промт пользователя:

> продолжай работу по задаче

## Проблема и изменение

После R189 refresh и успешного controller read старый change(String identity)
мог выбрать новый аккаунт по повторно использованному имени и записать take/
assign с его actor_user_id. Ожидание raw-ticket advisory lock оставляло такое
же окно. Это реальный перенос авторства, не требование сохранять тестовые данные.

Controller теперь передаёт Authentication. Service принимает только authenticated
bound PanelUserPrincipal с positive ID/exact nonblank username/status. Locking root
SELECT и final conditional write используют ID + exact raw name principal для
actor; нет name fallback и public String-only overload. Take выбирает того же
bound actor; audit ID и stored raw snapshot согласованы. Candidate target ID,
действующие READ/OPERATE/status/unique-name checks и роли не переопределяются.

Tuple/revision/CAS/previous value guards, lock ordering, REQUIRES_NEW, failure
rollback, event/outbox и response/form/CSRF/page contract сохранены. Нет новых
grants, schema/migrations/dependencies/feature flags или serialized-principal
изменений. Read methods и participant/work/AI writers этим шагом не переведены
на Authentication; их ID-bound границы остаются следующими отдельными scopes.
Нет заявления о полной revocation всех запросов после snapshot final SQL.

Fresh HEAD/origin/main/actual remote и clean tree сверены; R200 subject/parent/
4 paths/content проверены. Sandbox .tmp/01-278-r201; source apply отдельно от Git.
Scope 8 paths: 2 main Java, 2 adapted existing test fixtures, 1 new SQL test,
task/S0 и этот changelog. Старые assertions/parameter cases сохранены; тестовые
helpers создают Authentication с известным ID или моделируют initial login со
stored raw name. Это не product lookup fallback.

## Проверки

Baseline v1: 241 tests, 23 assertion failures / 218 controls / 0 errors/skips.
Из них старые 201 HTTP controls сохранены; 40 frozen новых cases дали 23 failures
и 17 controls: 36 SQL и 4 HTTP. Новые tests сначала выполнены до main changes.
Test-only reflection bridge позволяет тем же SQL bytes проверить старую String
API, затем новую Authentication API; отдельный case запрещает legacy overload.

Final v2: 394 tests / 9 suites GREEN, failures/errors/skips=0. HTTP 205 cases,
SQL identity 36, ScopedPrincipal 19, current/history SQL/MVC/login R199 и session
R189 включены. 15 неизменённых BUSINESS Node scripts GREEN. Source bytes SQL
test и нового HTTP block не менялись. При обратном преобразовании только
разрешённых fixture участков обе старые test sources совпали с baseline; case
names старого HTTP/new SQL сопоставлены со свежими XML. Source/class/report
hashes/freshness проверены. 2 main + 2 existing test files перекомпилированы;
новый SQL test ранее собран при RED из тех же bytes, R200 cache проверен по SHA.

Проверены wrong/missing/zero/negative IDs, exact-name mismatch/case/padding,
rename/delete с replacement account, unbound/anonymous/disabled/unauthenticated,
Long.MAX_VALUE/>2^53, Unicode/raw audit, текущие grants/status. HTTP mutation
выполнена после настоящего session refresh и controller read. Отдельный real SQL
case подтвердил writer wait через pg_stat_activity при удерживаемом advisory
lock, изменил имя/создал replacement, затем отпустил lock: новая запись/audit/
revision/outbox не появились. Rollback/concurrent writer/eligibility/legacy
boundary assertions R159–R200 не удалялись и не ослаблялись.

Backend тестов — native PostgreSQL 16.15 на 127.0.0.1:54167, свежие schemas в
owned .tmp/r200-pg/data. Явный ignored JDBC supplier bytecode adapter R200
сохранён; tracked PostgresqlJdbcTestSupport не менялся. Новых packages/dependencies
не скачивалось. Docker Desktop не запускался. Первый startup guard неверно
прочёл ExitCode property завершившегося pg_ctl; exact directory/PID и status
подтвердили единственный уже готовый test server без повторного старта.
Это реальный SQL/transactions/locks, но не Testcontainers/working acceptance.
83 migrations не менялись, V77/V78/Flyway использовались только в test schemas.

## Среда и следующий шаг

Source/test: C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочий экземпляр:
C:\Users\SinicinVV\git_h\tg_ref_b24_sup — другая машина/сессия.
Её R198 web/worker/V83, backup и rollback checkpoints сохранены точно. R200
инцидент старых local Docker containers сохранён как исторический; новых
Docker/Compose/up/build/remote restart/migration/provider commands не было.
Тестовый native PostgreSQL останавливается после проверок, evidence сохраняется.

Для использования изменения нужен новый panel-web image с согласованными
controller/service callers. Новых migrations нет, повтор V83 не нужен. R199
не развёрнут по последнему checkpoint, его UI попадёт в ту же новую сборку.
Рабочий rollout требует отдельной сессии и свежей проверки Compose/overrides/
реплик/rollback; разрешение на Git не объявляется разрешением на deploy.
Штатный Docker test runner на другой машине:

    cd spring-panel
    .\mvnw.cmd "-Dtest=BusinessAssignmentActorIdentityPostgresTest,BusinessTicketAssignmentHttpPostgresTest,ScopedPrincipalIdentityPostgresTest,BusinessTicketCurrentAssignmentReadPostgresTest,BusinessTicketCurrentAssignmentReadControllerWebMvcTest,BusinessTicketCurrentAssignmentIdentityWebMvcTest,BusinessTicketAssignmentReadPostgresTest,BusinessTicketAssignmentReadControllerWebMvcTest,SessionAuthorityWebMvcTest" test

Ближайшие исходные границы — оставшиеся name-based read/participant/work paths,
затем scoped children, bot media/feedback, GENERAL, policy/AI/fleet и первый
production S6. Historical S2B исключён R190. Общий статус 01-278 🟡.
Commit/push действуют до конца 2026-10-28 Europe/Moscow с expiry guard;
source apply не выполняет staging/commit/push/deploy/working migrations.
