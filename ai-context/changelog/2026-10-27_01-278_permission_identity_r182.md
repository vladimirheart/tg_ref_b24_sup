# 01-278 R182: точный логин для global admin compatibility

Дата: 2026-10-27, Europe/Moscow. Время фиксации evidence: 2026-10-27T03:57:11.662Z.

Промт пользователя: «продолжай. пуш и коммит подтверждаю автоматически до конца суток».

Baseline R182 (2026-10-27): clean main, HEAD/origin/main/actual remote
ad2b0af6bba77487a563e999732860e09b2b79e4. R181 проверен по parent/subject/exact
8 paths/normalized content SHA. Detached sandbox .tmp/01-278-r182. Рабочие
проект и Docker находятся на другой машине/в другой сессии; здесь только source
и изолированные Maven unit/MVC fixtures. Docker/browser/Node не запускались;
рабочие revision/DB/config/flags/providers неизвестны и не менялись. Нет рабочих
restart/deploy/migration/backfill. Явный grant commit/push действует до конца
27 октября Europe/Moscow: 2026-10-27 21:00 UTC (28 октября 00:00 +03).
Source apply и отдельный expiry-guarded Git publisher разделены.

R182 устраняет подмену account key в PermissionService.isSuperUser: username
сравнивается с прежним admin без trim. Отдельные аккаунты " admin", "admin ",
" admin ", tab/CR/LF variants больше не получают global superuser bypass
через похожий логин. Это распространяется на isSuperUser, hasAuthority и
hasAnyRole. Прежняя case-insensitive совместимость точного unpadded admin,
явные ROLE_ADMIN/ROLE_PORTAL_ADMIN, собственные page authorities, нормализация
role vocabulary и пустой allow-list сохранены. Бизнес-роли/ALL_BUSINESSES tokens
не заменяют системную административную роль. Пользователи/роли/grants в БД,
authentication provider, business membership/SQL, API payloads, UI и flags не
менялись. Удаление самого исторического admin-name bypass и переработка global
role policy не входят в R182. Foreign/shared channel CRUD policy остаётся
отдельным открытым решением; R181 page gate сохраняется. Incident user targets,
остальные legacy/global picker/attribution, S2B и rollout/acceptance открыты.
Общий статус задачи — 🟡.

Проверки: 307 Java tests / 8 свежих XML GREEN, Maven v2 exit 0,
failures/errors/skips=0. Новые 55 cases: PermissionIdentityBoundaryTest (27) и
AdminStorageInventoryIdentityWebMvcTest (28). Второй использует настоящие
SecurityConfig, PermissionService, method security, фильтры и CSRF; inventory
service — mock, скрипт инвентаризации не выполняется. Без admin privileges
12 literal accounts с PAGE_SETTINGS и valid CSRF получают 403 без вызова service;
сохранены 9 allowed combinations (unpadded admin case variants и две роли),
4 отказа без PAGE_SETTINGS, 2 CSRF отказа и anonymous redirect. Unit tests проверяют
все три permission API, собственные права literal account, role/empty-list и
unauthenticated controls. До production edit те же test source SHA на exact
baseline PermissionService blob дали 55 tests / 21 failures / 34 passing controls /
0 errors/skips: 13 unit и 8 HTTP failures, где другой аккаунт допускался к inventory.
Исходные XML/log/test SHA сохранены в ignored r182-baseline-red.json и копиях
report; тесты после RED не менялись. Финально повторены прежние PermissionService
(2), DialogAuthorization (6), SettingsMacroTemplate (1), ManagementController (16),
ChannelManagementAuthority (170) и AuthManagementApiController (57). Некоторые
прежние suites используют permission mocks/отключённые фильтры; новая MVC проверка
отдельно доказывает реальную permission boundary, не live login или DB acceptance.
Java cache R181 использован только после normalized SHA comparison всех Java
sources; оба новых test class скомпилированы в v1, изменённый PermissionService
реально перекомпилирован в v2. XML свежие; source/class/report/log hashes,
неизменность остальных cache inputs, generated CSS baseline и diff --check
проверены. Прежний R53 Node debt не запускался. Рабочей приёмки нет.

Фактический source scope: одна строка PermissionService.java; добавлены
PermissionIdentityBoundaryTest.java и AdminStorageInventoryIdentityWebMvcTest.java.
Обновлены task-details/01-278.md и S0-контракт; эта запись — новый append-only
changelog. Всего 6 project files, без migrations/JS/CSS. Generated CSS не копируется.

Source apply/publisher сохраняют baseline/head/origin_main/sandbox_green/
source_applied/staged/committed/pushed/deployment и exact changed-file list отдельно
в ignored r182-result.json. Запись не подменяет Git publication или рабочую приёмку:
фактические commit/push статусы проверяются publisher/final-check после source apply.
