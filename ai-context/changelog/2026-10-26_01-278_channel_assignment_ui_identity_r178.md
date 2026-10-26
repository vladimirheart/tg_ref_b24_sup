# 01-278 — точные логины в редакторе каналов, R178

Дата: 2026-10-26 (Europe/Moscow).

Промт пользователя:

> давай дальше

## Baseline и среда

Baseline R178 (2026-10-26): clean main, HEAD/origin/main/actual remote
dd41dea3aae55e050395dd472c1c06ea978e20a4 (R177), parent e10cd97066352feb3a7ba1925e0199fc1d3c2f5d.
R177 проверен по parent/subject/exact 12 paths/content SHA; detached sandbox
.tmp/01-278-r178. Рабочие проект и Docker находятся на другой машине/в другой
сессии; здесь только source и изолированный headless browser/Node. Рабочие
revision/DB/config/flags/providers неизвестны и не менялись. Рабочие restart,
deploy/migration/backfill не выполнялись; Docker/Java tests в R178 не запускались.
Продлённый пользователем grant commit/push действует 2026-10-26 Europe/Moscow,
до 2026-10-26 21:00 UTC (2026-10-27 00:00 +03). Source apply и отдельный Git
publisher разделены, с exact baseline/file/content/remote и expiry guards.

## Изменение

R178: typed assignment selectors в редакторе каналов сохраняют точные
непустые username без trim, с прежним case folding. Исправлены parseQuestionsCfg,
assignment catalog, single/pool rendering и сборка JSON payload. Typed arrays
отделены от прежней строковой CSV-грамматики; case dedup сохраняет первое
typed вхождение. Присутствующий пустой single select больше не подставляет
старый configured username; обязательность single/pool по-прежнему проверяет
существующий persistence runtime, без POST для отсутствующего выбора.
Options строятся DOM properties value/textContent вместо HTML-атрибутов:
CR/LF account key не нормализуется HTML parser, labels не исполняются как markup.
defaultSelected сохраняет native reset. Не добавляются отсутствующие в каталоге
accounts; похожее обрезанное имя не выбирается автоматически. Порядок selected
pool остаётся прежним порядком DOM options; нового управления порядком нет.
Общий employee-picker.js, id/name полей, runtime API, CSS/HTML, Java/backend,
SQL/migrations/grants и strategy/cursor semantics не менялись. Native picker
использует только свои options, без расширения глобальным справочником.

Граница R178 — channel assignment native selects. Notification CSV/typed-array
UI round-trip, прочие legacy/global pickers, SLA preview/audit parser и actor
label, ChannelApiController CRUD authority/business policy, incident user
targets и legacy attribution остаются отдельными gates. Refresh unsaved state
и управление порядком пула не переработаны. Общий статус 01-278 остаётся 🟡.

## Проверки

Проверки R178: 36/36 headless Chromium/Edge DOM scenarios GREEN,
95/95 Node checks из 38 regression scripts GREEN, 4 node --check GREEN.
Browser fixture использует актуальный channel-editor.html и реальные config/
shell/editor/persistence/controls/employee-picker runtimes; внешние зависимости
страницы заменены fixture, POST только перехватывается, внешние URL блокируются.
Пять whitespace variants проверены для single/pool restore → picker → captured
JSON POST → parse/reload и для отсутствующего target при наличии plain account.
Также проверены comma/semicolon keys, string ID >2^53, markup label, case dedup,
clear, disabled mode, legacy CSV, keyboard/Enter/Escape/focus и native form reset.
До production edits тот же QA source SHA дал 22 failures / 14 positive passes,
errors=0 в v4; baseline blobs/report/test SHA сохранены в ignored
r178-baseline-red.json и r178-browser-v4.json. Final browser report — v5;
проверки не ослаблялись после baseline, test SHA совпадает.

Полный Node run: 39 scripts/96 checks, 1 failure старого R53 notes fixture
(ожидает «Заметок нет.», получает error state). Сбой воспроизведён отдельно на
неизменённом main dd41dea3a; все 5 dependencies совпадают с baseline/root/sandbox.
Старый DOM stub не реализует replaceChildren текущего renderer. R53 и код
заметок не менялись. Точечный scoped run повторил остальные 38 scripts, без
skip/cancel; весь Node набор не объявляется GREEN. Evidence: r178-node-v1.log,
r178-node-baseline.json/.log и r178-node-v2.json/.log. Этот прежний test debt
нужен отдельным шагом. Source/report SHA, exact scope и diff --check проверены.
Java=0: серверные изменения R177 не менялись и здесь повторно не проверялись.
Это изолированные source-проверки, не приёмка рабочего экземпляра.

## Передача

- ai-context/changelog/2026-10-26_01-278_channel_assignment_ui_identity_r178.md
- ai-context/rules/03-multibusiness-s0-contract.md
- ai-context/tasks/task-details/01-278.md
- spring-panel/src/main/resources/static/js/settings-channel-config-runtime.js
- spring-panel/src/main/resources/static/js/settings-channel-editor-shell-runtime.js
- spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js
- tools/channel-assignment-identity-qa.cjs

Guarded source apply переносит ровно 7 файлов после targeted GREEN и заканчивается
без staging/commit/push. Отдельный publisher использует действующий суточный grant.
Фактические source_applied/committed/pushed — ignored .tmp/r178-result.json и
итоговый ответ. Deployment=false; весь Node набор и общая задача не закрываются.
