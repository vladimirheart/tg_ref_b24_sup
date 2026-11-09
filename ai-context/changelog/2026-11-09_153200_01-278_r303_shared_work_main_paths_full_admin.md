# 01-278 / R303 — основные пути общих работ и полный доступ системного администратора

Дата: 2026-11-09, Europe/Moscow. Общая задача🟡, без заявления общей S6 приёмки.

## Промты пользователя:

«ну по доступу задач, тут важное отступление, что системный администратор должен
иметь право доступа на абсолютно любое создание задачи или проекта или стадии
канбан. в целом эта должность обязана иметь фул права.
делай дальше и ускорься»

«продолжи»

Решение R301 об общих межбизнесовых задачах/проектах с отдельными правами и
участниками сохранено. Уточнение R303 меняет полномочия системного администратора.
Рабочий экземпляр находится здесь; дополнительных ресурсов пользователь
предоставить не может. Команды ручной фиксации не выдаются; прежнее однодневное
разрешение commit/push истекло.

## Baseline и фактическое состояние

HEAD/origin/main/actual remote до применения:
0207e5800e7959df64e3a9097310583f9b9a5dd5 — R302; parent
99cfefde03f6ebd067a7495af3ddcfb98ca5aae0 — R301.
Primary checkout был чистым. Работа и проверки выполнены в detached sandbox от
exact baseline. Перед переносом проверены19 исходных текстов,20 final Java inputs
и их SHA256 из validation-v7. Четыре generated CSS имеют filtered blobs baseline
и не перенесены. Метаданные проверены по HEAD blobs; старые changelog не менялись.

baseline/head/origin_main=0207e5800e7959df64e3a9097310583f9b9a5dd5;
sandbox_green=true; source_applied=true; staged=false; committed=false;
pushed=false; deployment=false. Изменены26 project files:

- spring-panel/src/main/java/com/example/panel/service/SharedWorkAccessService.java
- spring-panel/src/main/java/com/example/panel/controller/SharedWorkAccessController.java
- spring-panel/src/main/java/com/example/panel/controller/TaskApiController.java
- spring-panel/src/main/java/com/example/panel/service/TaskQueryService.java
- spring-panel/src/main/java/com/example/panel/service/ProjectService.java
- spring-panel/src/main/java/com/example/panel/controller/ProjectApiController.java
- spring-panel/src/main/java/com/example/panel/service/TaskDomainFoundationService.java
- spring-panel/src/main/java/com/example/panel/service/TaskBoardService.java
- spring-panel/src/main/java/com/example/panel/controller/TaskBoardApiController.java
- spring-panel/src/main/java/com/example/panel/controller/ManagementController.java
- spring-panel/src/main/java/com/example/panel/settings/SettingsPageController.java
- spring-panel/src/main/java/com/example/panel/service/TaskAnalyticsService.java
- spring-panel/src/main/java/com/example/panel/controller/TaskAnalyticsApiController.java
- spring-panel/src/test/java/com/example/panel/task/ProjectServiceTest.java
- spring-panel/src/test/java/com/example/panel/task/TaskDomainFoundationServiceTest.java
- spring-panel/src/test/java/com/example/panel/task/TaskBoardFoundationSourceContractTest.java
- spring-panel/src/test/java/com/example/panel/service/SharedWorkAccessPostgresTest.java
- spring-panel/src/main/java/com/example/panel/security/PanelRolePermissionSnapshot.java
- spring-panel/src/test/java/com/example/panel/task/TaskAnalyticsSourceContractTest.java
- spring-panel/src/test/java/com/example/panel/service/SharedWorkMainHttpPostgresTest.java
- README.md
- ai-context/tasks/task-details/01-278.md
- ai-context/rules/03-multibusiness-s0-contract.md
- docs/BOT_RUNTIME_CONTRACT.md
- docs/runbooks/shared-work-access.md
- ai-context/changelog/2026-11-09_153200_01-278_r303_shared_work_main_paths_full_admin.md

## Реализовано

Текущий ROLE_ADMIN имеет полные права общих задач, проектов, Kanban и управления
допусками/участниками/владельцами. PAGE_TASKS/PAGE_SETTINGS, shared grants или
членство ему не нужны. Повторно проверяются numeric users.id/raw username,
enabled/blocked/ambiguity, текущие прямые/assigned права; отозванная сессионная
роль не сохраняет доступ. Business ADMIN/ALL_BUSINESSES/portal role полного
доступа не получают. CSRF и целостность данных обязательны.

Обычный пользователь требует current PAGE_TASKS +shared can_read +membership;
VIEWER читает, EDITOR пишет, OWNER управляет составом. Отдельные create grants
сохранены. Trusted task/project create записывает numeric owner и OWNER в одной
транзакции с root, не доверяет клиентскому creator; comment author берётся из
principal. Отсутствующий update ID не создаёт новую запись. task_seq атомарен.

Подключены TaskApi/TaskQuery/ProjectService и controllers: CRUD, комментарии,
SQL visible IDs до count/pagination, ManagementController server model,
project options в settings, task project links/events, Kanban и task analytics/
CSV. Проект не выдаёт READ его задач. Скрытые links/placements сохраняются при
постороннем редактировании, count/cards/events фильтруются. Для перемещения
карточки нужны права на проект и задачу. Personal board привязан к
PERSONAL_USER:<users.id>; администратор управляет также чужой доской.
Аудит task/project/board сохраняет точного raw actor.

Новый owner transfer route атомарно меняет root FK, прежнего OWNER на EDITOR и
нового eligible numeric участника на OWNER. OWNER передаёт свой объект;
администратор восстанавливает также legacy unowned объект без ownership по
creator/lead. Member API по-прежнему не удаляет/не понижает OWNER.

Cross-object writes используют общий transaction advisory gate до account/root
locks; project/personal board projections также проходят gate и board scope lock.
READ блокирует membership, без блокировки всех unrelated roots. Создаваемые
уведомления ограничены текущими readable recipients до записи.

## Техническая проверка

Один финальный validation-v7:369 tests/10 suites GREEN, failures/errors/skips0:

- GeneralTicketAdminAuthorizationPostgresTest —246;
- SharedWorkAccessPostgresTest —98;
- SharedWorkMainHttpPostgresTest —15 новых;
- SharedWorkMigrationPostgresTest —2;
- ProjectServiceTest —2;
- TaskDomainFoundationServiceTest —1;
- TaskBoardFoundationSourceContractTest —1;
- TaskProjectFoundationSourceContractTest —1;
- TaskAnalyticsSourceContractTest —1;
- TaskAnalyticsPersistenceSourceContractTest —2.

Новый fixture использует реальные HTTP controllers, Spring Security/method
security/CSRF, JPA repositories, JpaTransactionManager и PostgreSQL, Flyway V88,
13 managed entities с реальным naming strategy. Внешние collaborators заменены
mock singleton, без lifecycle/autowire mocks. Проверены admin без pages/grants,
trusted owner/creator/author, missing update, VIEWER/EDITOR, скрытые lists/totals/
pagination/count/cards/links, analytics/CSV, server page model, чужая personal
board, живой revoke, CSRF, exact recipients/raw actor и параллельные project/task
writes. V88 clean/upgrade V87→V88 выполнялись только в disposable PostgreSQL.

Ранние RED сохранены: v1 — package-private parser; v2 — устаревшие admin ожидания,
fixture naming/managed entities и source contract; v3/v4 — test compile JSON/Tag;
v5 — пустой trailing classpath entry; v6 — lifecycle injection полей mock.
Исправлены код/fixtures/operators до финального GREEN; проверки не отключались.

V2 выполнил полный Maven main/test compile. V7 заново скомпилировал все14 изменённых
production и6 test sources portable JDK17 javac --release17 -parameters с bounded
heap256MiB; остальные classes сверены по SHA. Затем repository Maven wrapper
surefire:test, sequential bounded JVM384MiB. Это targeted compile поверх frozen
compiled baseline, не новый полный Maven/image rebuild. Тестовый PostgreSQL128MiB,
tmpfs128MiB, случайный localhost port, dummy DB и owner/nonce/image guard.
Рабочая БД не использовалась. XML и source hashes сохранены, тестовый контейнер
удалён после guard. Все20 existing containers full snapshots exact до/после.

README, task status/current remainder, S0 policy/decision table, runtime source
checkpoint и runbook обновлены после технического GREEN. Исторический R302
сохранён с указанием, что его admin restriction/wiring plan заменён R303.

## Рабочий экземпляр и следующий пакет

Рабочие panel-web/ops-worker по-прежнему backend R299/e661588b5,
image348066da11f5226c78a1e490a50f5328f0ebe32199606f63a519effc31033ab7.
Bot-runner — R295/6bc6ae23a, image29f36986a0dbfd615c1444851567517602ba11bf2729d095a918850b03054d89.
Compose R300, DB V87, flags/principals прежние. Новых migrations нет;
V88 в рабочей БД не запускалась. Rebuild/restart/deploy/provider sends отсутствуют.
Нужна отдельная подготовка выпуска после оставшегося wiring и проверок;
повторять V86/V87/provision не нужно.

Остаются UI grants/members/owner и save/reload/revoke, аудит изменений доступа,
повторный current READ при поздней доставке/SSE, остальные related-object/search/
report projections и trusted background/auto-close producers без authenticated
principal. Incident/system notification policy и прочая global/system matrix
остаются отдельными решениями; AI/privacy и S6 — в общей задаче. Historical S2B
снятR190. Полная изоляция всех путей и browser/provider acceptance не объявляются.

Evidence:
../iguana-runtime/tg_ref_b24_sup/releases/r303-work-wiring-20261109/,
validate-v1.ps1…validate-v7.ps1, validation-v7/proof.json, source-inputs.json,
frozen-other-classes.json, final-suites.json, source-applied.json, final-runtime.json
и архив project-files с SHA256. Без staging/commit/push.
