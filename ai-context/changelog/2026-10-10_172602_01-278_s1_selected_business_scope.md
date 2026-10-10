# S1: selected business scope contract для задачи 01-278

- Время: `2026-10-10T17:26:02+03:00` (Europe/Moscow).
- Промт пользователя: `дальше`.
- Тип изменения: source-only S1/S2A foundation. Новый resolver не подключён к
  API/UI/runtime paths. Рабочие Docker-контейнеры, рабочая/production БД,
  migration, backfill, внешние системы, staging, commit, push и rollout не
  выполнялись.

## Изменённые файлы

- `spring-panel/src/main/java/com/example/panel/service/BusinessAccessService.java`
  — типизированные single/all selected contexts и fail-closed resolver.
- `spring-panel/src/test/java/com/example/panel/service/BusinessAccessServicePostgresIntegrationTest.java`
  — PostgreSQL test selected scope.
- `ai-context/rules/03-multibusiness-s0-contract.md` и
  `ai-context/tasks/task-details/01-278.md` — checkpoint access contract.
- `ai-context/changelog/2026-10-10_172602_01-278_s1_selected_business_scope.md`
  — append-only запись данного изменения.

## Что сделано и проверено

- `OneBusiness` разрешается, только если business уже входит в effective
  accessible scope пользователя. Unknown/forbidden/null context отклоняется.
- `AllAccessibleBusinesses` возвращает только union resolved accessible IDs;
  он может быть пустым, не расширяет permissions и не подходит как write owner.
- RED-проверка подтвердила отсутствие метода и типов. После реализации чистый
  `mvnw.cmd clean -Dtest=BusinessAccessServicePostgresIntegrationTest test`
  прошёл GREEN (`3/3`) во временной PostgreSQL Testcontainers-схеме.
- Финальная чистая команда
  `spring-panel\\mvnw.cmd -q clean "-Dtest=BusinessAccessServicePostgresIntegrationTest,MultibusinessFeatureFlagsTest,TicketPublicIdTest,TicketLocatorServicePostgresIntegrationTest,TicketLocatorFlywayMigrationTest,LegacyTicketAssignmentPreviewServicePostgresIntegrationTest" test`
  прошла GREEN: `10/10`. Она применила V1→V52 только во временной PostgreSQL
  Testcontainers-схеме, не включала runtime flags и не делала production
  migration/backfill.
