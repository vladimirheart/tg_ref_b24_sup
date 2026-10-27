# 01-278 R198 — доступные аккаунты в общем справочнике сотрудников

Дата: 2026-10-27 (Europe/Moscow).
Baseline: c16cb0bbba608c42d66cfe745b12db91b9240b75 (R197); parent 9283c182176024fc9f10571b8a14c26438d3f44d.

Промт пользователя:

> продолжай

## Изменение

Свежие HEAD/origin/main/actual remote и clean tree сверены; предыдущие R197
subject/parent/13 paths/content attested. Работа в detached .tmp/01-278-r198.

PanelUserOptionsController больше не использует findAll/full user records:
отдельная проекция читает username/full_name, два status flags и window count.
Disabled, blocked и case-fold неоднозначные логины исключаются; count учитывает
все users, даже неактивных соседей. Null flags сохраняют login defaults. Raw value
остаётся строкой без trim/lower/Number, формат value/label и порядок прежние.
Private columns/password/hash не читаются. PanelUser RowMapper исправлен для
настоящего PostgreSQL Boolean.TRUE с сохранением numeric/string 1 semantics.

Сохраняются page union, session refresh/revoke и минимальный response. Read failure
не даёт fallback. Scoped/business pickers не расширены; API не является правом
назначения, новая global/manual/system policy не вводится. Нет миграций, схемы,
UI/dependency/auth/grants изменений; V1–V83 неизменны. Другие repository consumers
сохраняют свои queries; общий mapper теперь точно отражает Boolean blocked.

Scope 10 paths: 2 production Java, 1 existing test fixture, 3 новых test files
(2 выполнены, 1 PostgreSQL только compiled), 4 документа. Исторические changelogs
и полный R184 checkpoint/rollback не переписываются.

## Валидация

38 suites / 974 tests GREEN; failures/errors/skips=0. Frozen 46 cases: baseline v2
8 assertion failures / 38 controls / 0 errors, те же source bytes GREEN.
34 real login/security/session/controller/repository cases с JDBC result adapters
проверяют status/collisions/raw names/precision/private projection, authority/revoke
и error paths. 12 RowMapper cases проверяют JDBC Boolean и numeric/string forms.
Adapters не исполняют PostgreSQL SQL; это не доказательство window function.
Включены R184/R188–R197 regression suites, Management и IncidentPolicyAndUserOptions.
Все прежние assertions/методы последнего сохранены, адаптирован только fixture.

V1 нового harness был некорректен: 1 failure / 33 errors / 12 controls
(всего 46 cases). Singleton JdbcTemplate mock удерживал Answer предыдущего теста
с включённой ошибкой. Исправлен только новый fixture: reset перед stubbing,
артефакты v1 сохранены. Затем production-unchanged baseline v2 выполнен заново.

R197 cache принят после hash-сверки всех baseline Java inputs/attested bytecode.
GREEN перекомпилировал 2 main + 2 test sources, frozen sources ранее собраны из
тех же bytes. Source/class/report SHA/freshness и 83 unchanged migration resources
проверены. PanelUserOptionsPostgresqlTest: 8 cases скомпилированы, НЕ выполнялись:
реальные window counts across all statuses, Boolean mapper, null defaults, raw
keys/large numeric strings, blank names и отсутствие private schema columns.
Нулевые skips выбранных suites не означают выполнение отдельного PostgreSQL suite.

## Среда и остатки

Source/test: C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочий экземпляр/Docker:
C:\Users\SinicinVV\git_h\tg_ref_b24_sup на другой машине/в другой сессии.
По R196 probe здесь daemon недоступен; новых runtime probes не было. Исторические
R184 checkpoint и R195 probe сохранены. R185–R198 не развёртывались отсюда;
рабочая БД/данные/схема, grants/roles/config/providers и rollback не менялись.
V83 здесь НЕ запускалась; deploy, migration и production acceptance не выполнены.

На машине с изолированным Docker до рабочего rollout:

    cd spring-panel
    .\mvnw.cmd "-Dtest=PanelUserOptionsPostgresqlTest,AutomationRunOwnershipPostgresqlTest,EmployeeDiscountAutomationCredentialServiceTest,PreferenceIdentityWebMvcTest,UiPreferenceIdentityPostgresqlTest" test

Команда не разрешает Compose/deploy или рабочую migration. PostgreSQL locks/
rollback R195/R196, V83/JPA/Flyway R197 и window/Boolean R198 остаются открытыми.
V83 нужна перед новым image отдельным разрешённым шагом; mixed writers/fleet не
приняты. Browser/JDBC session-store/live acceptance не выполнялись. Остальные
S3–S6/policy/AI/bots/fleet gates открыты; historical S2B исключён R190, статус 🟡.
Commit/push разрешены до конца 2026-10-27 Europe/Moscow, publisher с expiry guard;
source apply отделён от Git и не выполняет migration/deploy.
