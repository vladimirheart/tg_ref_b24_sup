# 01-278 R197 — владелец истории запусков автоматизации по ID

Дата: 2026-10-27 (Europe/Moscow).
Baseline: 9283c182176024fc9f10571b8a14c26438d3f44d.

Промт пользователя:

> продолжи

## Изменение

Свежие clean HEAD/origin/main/actual remote совпали. Проверены R196 subject,
parent 181e04ba6b65cbae6d66721d5cbe91e2616ba38e, точные 23 paths/content по
прежнему validation artifact. Работа в detached .tmp/01-278-r197.

- AutomationRun получает actorUserId, explicit actor_user_id column. Run пишет ID
  из bound Authentication до initial audit/discovery и сохраняет его во всех
  success/error/dry-run/execute saves. Raw actor остаётся display snapshot.
- Controller/service list/detail получают Authentication. Repository queries
  используют automation key + owner ID (+ run ID для detail), без trim/IgnoreCase
  fallback. Top 20 имеет stable started_at DESC/id DESC. Foreign/NULL legacy/other
  automation/missing parent даёт 404 до child query. JSON/UI shape не меняется.
- Source-only V83 добавляет nullable positive BIGINT и composite index. Нет FK
  или cascade на users, backfill, reset/delete или преобразования имён в ID.
  V1–V82 и старый actor index сохранены. Новая модель соответствует решению R190.
- PAGE_DIALOGS, R189 session refresh/revoke, CSRF, интеграции/credentials R196,
  execution selection и mutation ordering сохранены. Нет новой global/business
  policy, grants, dependencies, serialized-principal или feature-flag изменений.

Scope: 4 production Java, V83 SQL, 1 existing test fixture, 3 новых test files
(2 выполнены, PostgreSQL suite только compiled), 4 документа = 13 paths.
Existing assertions/test methods сохранены; name fixtures заменены bound IDs,
включая сохранённый отказ для чужой истории. Исторические changelogs и полный
checkpoint/rollback R184 не переписываются.

## Валидация

34 suites / 909 tests GREEN; failures/errors/skips=0. 43 frozen new cases:
baseline v2 32 assertion failures / 11 controls / 0 errors/skips; те же source
bytes GREEN. Это 40 real login/security/session/CSRF MVC cases со stateful JDBC/
repository adapters и 3 Spring Data PartTree/JPA annotation cases. Collision,
raw/padded/Unicode/anonymousUser, rename/reuse, owner Long precision, child read
guard, top-20 tie-break, invalid principals, revoke/page/CSRF, initial/final audit
saves и failure paths проверены. R184/R188–R196 suites и FlywayConfigTest включены.

R196 cache принят только после hash-сверки всех baseline Java inputs и attested
bytecode; 4 main + 2 test sources перекомпилированы. Source/class/report SHA и
freshness проверены; новый SQL resource в target совпал, 82 прежних SQL неизменны.
V1 нового harness не является корректным RED: 31 failure / 1 error / 11 controls,
один nullable fixture Long был неявно unboxed. Исправлен только новый fixture,
после чего baseline v2 выполнен снова до production transformation. Артефакты сохранены.

AutomationRunOwnershipPostgresqlTest: 8 cases скомпилированы, НЕ выполнялись.
Они предназначены для real PostgreSQL constraint/index/NULL legacy tests,
настоящего Hibernate/Spring Data JPA round-trip и derived queries, Long IDs,
top-20 и полного V82→V83 Flyway upgrade/repeat. Нулевые skips у выбранных 34 suites
не означают, что этот отдельный PostgreSQL suite прошёл. Миграция не запускалась.
Working PostgreSQL, browser, JDBC session-store, fleet/live providers не проверялись.

## Среда и дальнейшая проверка

Текущая source/test сессия: C:\Users\sushi\Git_H\tg_ref_b24_sup.
Рабочий экземпляр/Docker: C:\Users\SinicinVV\git_h\tg_ref_b24_sup, другая
машина/сессия. Здесь daemon недоступен по R196 probe; runtime status отсюда не
подтверждался. R195 probe R184 сохранён как исторический факт другой машины.
R185–R197 не развёртывались отсюда; рабочая БД/данные/схема, grants/roles,
flags/config/providers и rollback tag/override не менялись.

На машине с изолированным Docker выполнить до рабочего rollout:

    cd spring-panel
    .\mvnw.cmd "-Dtest=AutomationRunOwnershipPostgresqlTest,EmployeeDiscountAutomationCredentialServiceTest,PreferenceIdentityWebMvcTest,UiPreferenceIdentityPostgresqlTest" test

V83 требует отдельного разрешённого migration step перед новым image; без
колонки нет name/schema fallback. Старые writers могут оставлять NULL, поэтому
согласование readers/writers/fleet обязательно. Это additive source migration,
не historic S2B и не разрешение на rollout. Прочие S3–S6/policy/AI/bots gates
открыты, статус 01-278 🟡. Source apply и Git publisher разделены; разрешение
на commit/push до конца 2026-10-27 Europe/Moscow проверяется expiry guard.
