# Поиск связанного объекта и общий справочник внешних объектов — R19

Дата:26.10.2026,13:38:06+03:00. Задача01-281, 🟣.

## Пользовательский промпт

> при создании инфидента вручную, при выборе "связанный объект", в поле "Номер связанного объекта" нужен поиск существующих записей.
> более того: связанный объект вообзе может не находиться в панели, есои я его создам отдельно - кстати, нужна такая возможность но только у админов портала

Значимые уточнения пользователя:
- «Отдельный справочник объектов (рекомендую)» — отдельная повторно используемая
  запись с названием, внешним номером/ссылкой, описанием.
- «Общий справочник портала» — без разделения записей по бизнесам.
- «Также обновить рабочую панель» — после тестов выполнить миграцию и update,
  с учётом предупреждения о другой Docker-сессии в свежем контексте.

## Фактическое изменение

Lookup диалогов/задач/паспортов/внешних объектов, combobox вместо ручного ID,
keyboard/late-response/reset guards, переход в справочник с сохранением черновика.
Глобальный admin создаёт внешний объект отдельно от инцидента, остальные пользователи
модуля выбирают и используют его повторно. Новый PostgreSQL справочник V82,
controller/service global admin/page/CSRF guards, native page и business READ
границы, GENERAL/collisions excluded, exact opaque IDs, canonical external labels,
http(s)-валидация без server fetch. Общие SCSS/tokens, cache versions updated.

184Java/44suites,46JS+42exact-ID assertions,170browser combinations/190PNG GREEN;
дополнительно6PG tests с lookup на полнойV1–82schema. Audit74templates/47pages GREEN.
Новые проверки не ослабляли прежние exact-ID/CSRF/style/layout контракты.

Guarded source application22paths против clean3629b7220=origin=remote,
после closeout итог24files. Полный main не развёрнут: R18 runtime +9entries R19,
4new,2171unchanged. Candidate Java17, strict JAR guard GREEN.
Flyway-only preflight и migration выполнены без repair/normalization/backfill:
толькоV82, старая история совпадает. Backup сохранён и проверен pg_restore --list.

Web healthy на incidents-r19-20261026,
image6b1884e0734188bc59488e5f9eba2d078f18dcfc2a4662168cd34b23d9dbc8c6,
JAR3b2d5197a1dda652dc1cc66d111b33398512d9a920f8c7c40a1544c3c2a135ca.
Остальные17контейнеров/4bot JVM/env/mounts/policy/failed records/cursors сохранены.
Worker не перезапускался. Rollback tag R19web→R18 сохранён, откат не выполнялся.
Авторизованная production acceptance остаётся владельцу: bootstrap credentials
отсутствуют, аккаунты/права не менялись. Catalog/incident writes проверены в
изолированной БД; рабочий каталог пуст, ручных бизнес-записей агент не создавал.

## Затронутые файлы

- `ai-context/changelog/2026-10-26_133806_incident-related-objects-r19.md`
- `ai-context/rules/05-monitoring-policy-and-employee-pickers.md`
- `ai-context/tasks/task-details/01-281.md`
- `docs/ui/incident-related-objects-r19.md`
- `spring-panel/src/main/java/com/example/panel/controller/IncidentRelatedObjectController.java`
- `spring-panel/src/main/java/com/example/panel/service/IncidentRelatedObjectService.java`
- `spring-panel/src/main/java/com/example/panel/service/IncidentService.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V82__incident_external_objects.sql`
- `spring-panel/src/main/resources/scss/app/_incident-workbench.scss`
- `spring-panel/src/main/resources/static/css/app.css`
- `spring-panel/src/main/resources/static/js/incident-relation-picker.js`
- `spring-panel/src/main/resources/static/js/incidents-workbench.js`
- `spring-panel/src/main/resources/templates/incidents/index.html`
- `spring-panel/src/test/java/com/example/panel/controller/IncidentRelatedObjectControllerWebMvcTest.java`
- `spring-panel/src/test/java/com/example/panel/service/IncidentClosurePostgresqlTest.java`
- `spring-panel/src/test/java/com/example/panel/service/IncidentPriorityPostgresqlTest.java`
- `spring-panel/src/test/java/com/example/panel/service/IncidentRelatedObjectPostgresqlTest.java`
- `spring-panel/src/test/java/com/example/panel/service/IncidentServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/ui/NeutralOpsPageFamiliesWebMvcTest.java`
- `spring-panel/src/test/js/incident-descriptions-r13.test.js`
- `spring-panel/src/test/js/incidents-create-exact-ticket-id-r35.test.js`
- `tools/incident-related-objects-qa.cjs`
- `tools/incident-workbench-qa.cjs`
- `tools/neutral-ops-source-audit.cjs`

Отчёт/точные команды staging/commit/push: docs/ui/incident-related-objects-r19.md.
source_applied=true; deployment=true; migration=V82;
staged/committed/pushed=false; acceptance owner pending, 🟣.
