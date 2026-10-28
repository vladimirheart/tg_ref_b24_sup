# 01-278 R200 — HTTP-фикстуры назначений и ID сессии

Дата: 2026-10-28 (Europe/Moscow).
Baseline: d57d35f022ce96f08b9e2a375696c07d90bd8ab8; parent c3fa2777914095e91b783344072db01e10015682.

Промт пользователя:

> репо обновлён. проверь изменения и давай дальше в рамках задачи.
> комит и пуш разрешаю до конца суток

## Изменение

R199 commit проверен по parent/subject/14 paths/content; R198 сверён по прежнему
validation artifact. Fresh clean HEAD/origin/main/actual remote совпали.
Работа сначала в detached .tmp/01-278-r200, Git publisher отделён от source apply.

BusinessTicketAssignmentHttpPostgresTest теперь создаёт ID-bound PanelUserPrincipal
для точного fixture account и seed DB authorities, которые R189 действительно
обновляет перед контроллером. Никаких trim/lower, mock/disable security filter или
изменений product auth. Старые padded-negative cases получают literal отдельные
аккаунты; прежние 404 продолжают проверять business denial. Все 190 прежних
testcases и assertions сохранены; вне двух fixture setup/helper участков старый
source восстанавливается byte-for-byte после удаления новых test methods.

Добавлены 11 cases: disable/block/delete/rename/reuse после подготовки запроса;
unbound/foreign-ID/case-changed/padded principals; revoke DB PAGE_DIALOGS без
repair helper; реальный login, JDBC SESSION cookie/CSRF, запись take, чтение
current/history R199, revoke страницы и отказ следующей записи. Проверяются
границы вызова controller и event/revision/outbox counts. Старое имя не возрождает
отозванную сессию, новая отдельная сессия replacement account допустима.

Scope 4 paths: 1 existing Java test, task, S0 и этот новый changelog.
Production, UI, SQL/migrations, dependencies, roles/grants/feature flags не
менялись. Исторические changelogs и целые R184/R198 runtime checkpoints сохранены.

## Валидация

Baseline v1: 190 tests, 122 failures / 1 error / 67 controls, production/test
sources неизменны. Это повторяет debt R199 на настоящем PostgreSQL.
Изменённый v2: 400 tests, 0 failures / 1 error; все прежние 190 HTTP cases прошли.
Ошибка нового real-login case — ожидание MockHttpSession при включённом JDBC
session-store. v3 перешёл на SESSION cookie, но передал пустой response-cookie array.
v4 исправил только cookie jar нового case: cookie без нового Set-Cookie сохраняется,
истёкшая удаляется, SESSION/CSRF берутся из HTTP response. Отдельный real-login v4
прошёл с production CookieCsrfTokenRepository/JDBC session. Старые assertions не
ослаблялись. Артефакты v1/v2/v3 и отдельный GREEN v4 XML сохранены.

Final v5: 400 tests / 12 suites GREEN, failures/errors/skips=0. HTTP — 201 cases
(190 прежних + 11 новых). Current/history SQL/MVC/login R199, session R189,
preferences/credentials R195/R196, JPA/Flyway и window/Boolean R197/R198 включены.
15 BUSINESS Node scripts GREEN без изменения source/assertions. Это выбранный
прогон, не весь repository test suite. Case names прежних 190 сопоставлены со
свежим XML; source/class/report hashes и freshness проверены.

SQL исполняется на реальном временном PostgreSQL 16.15 Windows, 127.0.0.1,
отдельная БД и уникальные schemas, data directory .tmp/r200-pg/data. Windows
binary artifact из Maven Central проверен опубликованным SHA1 и зафиксирован
SHA256. Сервис PostgreSQL не установлен. В target test-classes временно собран
ignored JDBC supplier adapter той же API вместо Docker supplier; он проверяет
точный test data directory и версию перед выдачей datasource. Tracked helper,
application/test assertions не заменялись; SQL, JDBC, Spring Security/Session,
MVC, JPA и Flyway реальные. Это не Testcontainers execution: OS/locale и запуск
штатного container runner на другой машине остаются отдельной проверкой.

R198 cache использован после source SHA guards; 5 Java inputs R199 (2 main,
3 test) скомпилированы заново, R200 fixture перекомпилирован. 83 migration sources
неизменны, migrations выполнялись только в disposable test schemas. Working DB
и runtime credentials этому тестовому runner не передавались.

Setup artifacts v1–v4 сохранены: Windows Start-Process -Wait ожидал всё дерево
postgres, первый recovery guard перепутал строки socket/listen в postmaster.pid,
следующий предполагал psql/createdb в минимальном binary bundle. Финальный JDBC
probe проверил directory/version и создал отдельную test DB; остановлен только
собственный ожидающий setup parent. Это ошибки harness, не product changes.

## Среда

Текущая source/test машина C:\Users\sushi\Git_H\tg_ref_b24_sup.
Рабочая машина C:\Users\SinicinVV\git_h\tg_ref_b24_sup — другая сессия.
R198 web/worker/V83 подтверждены её checkpoint; backup/rollback сохранены.
Нового remote runtime probe/deploy/restart/migration здесь не было. R199 не
развёрнут по последнему checkpoint; R200 test-only не требует rebuild или V83.

Инцидент: запуск локального Docker Desktop ради isolated tests автоматически
поднял 14 сохранённых контейнеров проекта, включая старые web/worker/infra.
Docker Desktop остановлен обратно. Compose up/build/migration/config команды
для них не выполнялись. Короткий запуск мог выполнять обычные jobs; их local DB/
provider writes не аудированы, отсутствие writes не утверждается. Это не запуск
или проверка другой рабочей машины. Последующие тесты шли без Docker.

Тестовый PostgreSQL останавливается после проверок; artifacts остаются ignored
в .tmp/r200-*. Штатный повтор на машине с Docker:

    cd spring-panel
    .\mvnw.cmd "-Dtest=BusinessTicketAssignmentHttpPostgresTest,BusinessTicketCurrentAssignmentReadPostgresTest,BusinessTicketCurrentAssignmentReadControllerWebMvcTest,BusinessTicketCurrentAssignmentIdentityWebMvcTest,BusinessTicketAssignmentReadPostgresTest,BusinessTicketAssignmentReadControllerWebMvcTest,SessionAuthorityWebMvcTest" test

Команда не пересобирает runtime image и не применяет рабочую migration.
Остальные scoped writers/children, bot media/feedback, GENERAL, policy/AI/fleet
и первый production S6 открыты; historical S2B снят R190. Статус 01-278 🟡.
Commit/push разрешены до конца 2026-10-28 Europe/Moscow, publisher с expiry guard;
source apply без staging/commit/push/deploy/migration.
