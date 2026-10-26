# 01-278 — точные получатели уведомлений в редакторе каналов, R180

Дата: 2026-10-26 (Europe/Moscow).

Промт пользователя:

> продолжай

## Baseline и среда

Baseline R180 (2026-10-26): clean main, HEAD/origin/main/actual remote
b92c1aa26eab1c057b6612ee0c837934d66d1132. R179 проверен по parent/subject/exact
11 paths/content SHA. Detached sandbox .tmp/01-278-r180. Рабочие проект и Docker
находятся на другой машине/в другой сессии. Здесь source и изолированные browser/
Node fixtures; Java/Docker в R180 не запускались. Рабочие revision/DB/config/
flags/providers неизвестны и не менялись; restart/deploy/migration/backfill
не выполнялись. Продлённый grant commit/push действует до 2026-10-26 21:00 UTC
(27 октября 00:00 +03); source apply и отдельный date-guarded publisher разделены.

## Изменение

R180 сохраняет точные typed notification usernames от parseQuestionsCfg через
picker до JSON POST и reload. Только channelEditorPanelNotifEmployees и
channelEditorPanelNotifExclude opt-in в data-employee-value-format="username-json".
Их textarea type/id остаются прежними; .value содержит JSON-массив строк,
а backend payload по-прежнему содержит массивы employeeUsernames/excludeUsernames.
Typed values сохраняются без trim при прежнем case folding; case dedup сохраняет
первое вхождение. Старая строковая CSV-грамматика channel parser и прочих полей
picker не менялась. Справочник /api/panel-users/options остаётся прежним global/
legacy source, без нового доступа или расширения native/scoped options.
Явно пустое поле больше не восстанавливает старый список через value || existing;
fallback применяется только при отсутствующем control. defaultValue восстанавливает
загруженный выбор при native reset; ошибка справочника не стирает существующий
список, отсутствующий literal key не подменяется похожим plain account.
Повреждённый JSON/non-string entry блокирует сохранение до POST с сообщением UI.
Непустые строковые ключи, включая >2^53, остаются строками; markup — textContent.
Исправлен и общий observer loop: added node, удалённый до доставки MutationRecord,
больше не считается внешним добавлением и не запускает бесконечный scan/refresh.
SQL/backend predicates, grants, schema/flags, CSV legacy fields и assignment
native selectors не менялись. Это исправление channel UI strings и picker
refresh, без нового authorization scope. Остальные legacy/global picker identity,
ChannelApiController CRUD authority/business policy, incident targets, legacy
attribution, S2B и рабочий rollout/acceptance остаются gates. Статус 01-278 — 🟡.

## Проверки

Проверки R180: 88/88 headless Edge DOM scenarios GREEN (52 notification,
36 assignment regression), 110/110 Node checks из 40 scripts GREEN, 5 node --check.
Реальный channel-editor.html и config/editor/shell/persistence/controls/picker
JS используются с fixture API; GET справочника и JSON POST перехватываются,
внешние URL блокируются. Проверены whitespace variants, comma/semicolon,
string ID >2^53, markup, оба списка, missing literal, directory failure, clear,
reset, disabled/absent control, case folding, malformed typed value без POST,
compatibility shapes, неизменённый legacy CSV, keyboard/Escape/Enter/focus.
Исходный baseline зависал после remote picker open: CDP Runtime.evaluate timeout
180s, Debugger в refresh/scan. После единственного observer fix тот же QA source
SHA дал 52 tests / 47 failures / 5 passing controls / 0 errors в v2. Остальные
четыре production inputs тогда оставались baseline blobs. Final notification
v4 и assignment v1 GREEN; diagnostic test после RED не менялся. Исходные
blobs/test/report SHA и timeout observation сохранены в ignored
r180-baseline-observer.json, r180-baseline-red.json и r180-browser-v2.json.
Полный Node run: 41 scripts / 111 checks, 1 старый R53 notes fixture failure.
Он отдельно воспроизведён на неизменённом main b92c1aa26; все 5 dependencies
совпадают в root/sandbox с baseline. R53 и код заметок не менялись; scoped Node
run повторил остальные 40 scripts без skip/cancel. Весь Node набор не GREEN.
Evidence: r180-node-v1.json/.log, r180-node-baseline.json/.log, r180-node-v2.json/.log.
Source/report/log SHA и diff --check проверены. Java=0, Docker=0; это не MVC/DB/
working-instance acceptance, backend R175/R177 здесь повторно не проверялся.

## Передача

- ai-context/changelog/2026-10-26_01-278_channel_notification_ui_identity_r180.md
- ai-context/rules/03-multibusiness-s0-contract.md
- ai-context/rules/05-monitoring-policy-and-employee-pickers.md
- ai-context/tasks/task-details/01-278.md
- spring-panel/src/main/resources/static/js/employee-picker.js
- spring-panel/src/main/resources/static/js/settings-channel-config-runtime.js
- spring-panel/src/main/resources/static/js/settings-channel-editor-shell-runtime.js
- spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js
- spring-panel/src/main/resources/templates/settings/fragments/channel-editor.html
- tools/channel-notification-identity-qa.cjs

Guarded source apply переносит ровно 10 файлов после targeted GREEN, без
staging/commit/push. Отдельный publisher использует суточный grant и expiry guard.
Фактические source_applied/committed/pushed — ignored .tmp/r180-result.json
и итоговый ответ. Deployment=false; весь Node набор и общая задача не закрываются.
