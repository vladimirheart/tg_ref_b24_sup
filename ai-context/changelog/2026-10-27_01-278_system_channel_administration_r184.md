# 01-278 R184: каналы управляются только системным администратором с page right

Дата: 2026-10-27, Europe/Moscow. Evidence: 2026-10-27T05:47:15.020Z.

Промт пользователя (ответ): «Только системный администратор с правом страницы».

Контекст вопроса: «Для управления самим каналом (токен, удаление, настройки общего канала)
какую политику принять? Сейчас управление бизнес-привязками уже требует PAGE_SETTINGS + ROLE_ADMIN;
отдельной политики CRUD каналов в задаче пока нет.»

Действующий grant: «продолжай. пуш и коммит подтверждаю автоматически до конца суток»
(2026-10-27 Europe/Moscow); повторное подтверждение не требуется.

Baseline R184 (2026-10-27): clean main, HEAD/origin/main/actual remote
5aa3d5e465ab0fb21b08b9459383fb793d872415. R183 проверен по parent/subject/exact
8 paths/normalized content SHA. Detached sandbox .tmp/01-278-r184. Рабочие
проект и Docker остаются на другой машине/в другой сессии. Здесь source и
изолированные Maven MVC/security/Thymeleaf fixtures; Docker, PostgreSQL, browser
и Node в R184 не запускались. Рабочие revision/DB/config/flags/providers не
проверялись и не менялись; нет restart/deploy/migration/backfill или запуска
рабочих ботов. Grant commit/push от 27 октября сохраняется до 2026-10-27
21:00 UTC (28 октября 00:00 +03); source apply и Git publisher разделены.

Принятое решение владельца: «Только системный администратор с правом страницы».
Канал, его token/credentials и общая конфигурация управляются как глобальный
ресурс: нужен явный ROLE_ADMIN вместе с PAGE_CHANNELS или PAGE_SETTINGS в
Authentication. Business ADMIN, ALL_BUSINESSES, ROLE_PORTAL_ADMIN без ROLE_ADMIN,
одна page authority и исторический username admin сами по себе не подходят.
Новые grants/roles не назначаются; делегирование бизнес-администраторам всех
привязанных бизнесов не реализуется. Общий/чужой business binding не расширяет
право управлять каналом. Этот ранее открытый policy gate закрыт source-only
реализацией, без подтверждения рабочего rollout.

ChannelManagementAccess содержит общий PreAuthorize expression и raw-authority
predicate для навигации. Защищены 14 маршрутов R181 channel CRUD/aliases,
credentials, test-message/bot-info/channel-notifications и дополнительно четыре
BotProcessApiController: start, stop, status, runtime-contract. Все 18 route/verb
combinations требуют роль и одно page right до transport/repository/lifecycle
вызова; write CSRF остаётся обязательным. /channels использует ту же политику.
ROLE_ADMIN + PAGE_CHANNELS без PAGE_SETTINGS остаётся на legacy channels/index;
ROLE_ADMIN + PAGE_SETTINGS открывает /channels как alias в /settings?open=channels.
Redirect больше не использует широкий PermissionService admin bypass вместо
явного PAGE_SETTINGS. Navbar учитывает canManageChannelsInSettings, поэтому
channel-only администратор сохраняет ссылку на доступную страницу. Settings tile,
channelsModal, addChannelModal и channelEditorModal отсутствуют в серверном HTML
для остальных пользователей; сама /settings и другие её разделы сохраняют
прежние page permissions. Внешний th:if wrapper используется до th:replace.

SQL/schema, ChannelTransportService, bot lifecycle orchestration/runner internals,
internal token/HMAC APIs, settings save API и JS не менялись. Не добавляется
публичный read-only channel catalog/status; sidebar без нужных прав сохраняет
catch/hide на 403. Остальные global settings/proxy/template permissions, live
login/session authority refresh/revoke, incident UI/upstream identity, legacy
attribution/global/system policy, S2B и рабочие S3–S6 gates остаются отдельными.
Проверяется authority snapshot текущей Authentication, не новый DB membership
resolver. Общий статус 01-278 — 🟡.

Validation R184: 393 Java tests / 10 свежих XML GREEN, Maven v3 exit 0,
failures/errors/skips=0. ChannelManagementAuthorityWebMvcTest расширен с 170 до
268 cases: 162 отказа для 9 actor/authority combinations × 18 routes,
36 разрешений по ROLE_ADMIN + одному из двух page rights, 52 missing/invalid
CSRF отказа для 13 writes и 18 anonymous redirects. Проверены exact delegation,
response и long ID >2^53; отказы не вызывают transport, repository, runtime role,
runner, process service или SSE publisher. Настоящие SecurityConfig/filters/
method security/CSRF; реальные процессы, provider calls и runner — mocks.
Новый ChannelManagementPageSecurityWebMvcTest — 13 cases с реальными navigation,
PermissionService и Thymeleaf: 5 отказов /channels, 5 settings pages без channel
controls, обе разрешённые entry combinations и отказ роли без page rights.
281 policy cases суммарно; прирост к прежнему suite — 111 cases. До production
edits те же два test source SHA на 9 baseline blobs дали в v2 281 tests /
146 failures / 135 passing controls / 0 errors/skips. V1 сохранил неверное ожидание
redirect вместо прежнего HTTP 403 для page denial; исправлен только новый test
на фактический error contract, без production edit. После v2 оба policy test
source не менялись. RED XML/log/test SHA и baseline blobs сохранены в ignored
r184-baseline-red.json. ManagementController regression сохраняет все assertions;
5 requests получили явно разрешённые policy actors/page authorities.
Остальные финальные suites: ChannelApiController 50, BotProcessApiController 27,
ManagementController 16, SettingsApi/Bridge 5+5, HomeController 2 и UI source
contracts 6+1. Старые functional MVC suites с mocks/без filters не подменяют
отдельную security проверку. Java cache R183 разрешён после normalized SHA
comparison всех Java sources; 8 production classes реально перекомпилированы.
Source/class/XML/log SHA и actual rendered classpath templates сверены. Maven
сохраняет resource timestamps: v2 verifier проверяет exact new-source bytes и
отличие от baseline, вместо неверного требования mtime после начала test run.
Остальные cache inputs, generated CSS baseline и diff --check проверены.
Browser/live DB/provider/runner/rolling-upgrade acceptance отсутствует; прежний
R53 Node debt в R184 не запускался.

Scope: 8 production Java files (5 API controllers, ManagementController,
NavigationService, новый ChannelManagementAccess), 2 Thymeleaf templates,
3 test files (один новый page security class), task details, S0 contract и этот
новый append-only changelog. Всего 16 project files. Исторические changelogs,
DB/settings/flags/JS/CSS не менялись.

Source apply/publisher фиксируют baseline/head/origin_main/sandbox_green/
source_applied/staged/committed/pushed/deployment и exact changed files отдельно
в ignored r184-result.json. Факт commit/push проверяется publisher/final-check
после source apply, не подменяется sandbox GREEN или рабочей приёмкой.
