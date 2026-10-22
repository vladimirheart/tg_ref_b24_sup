# Neutral Ops — повторная приёмка R9, 22.10.2026

Статус: `🟣`, ожидается проверка владельцем. Этот отчёт дополняет, а не
переписывает исторические результаты R7/R8.

## Результат

- Открытие диалога и действия: одинаковые 24×24 px, рамка, фон и радиус;
  на touch — 44×44 px. Удалены два конфликтующих legacy `!important`, которые
  делали контур ссылки визуально меньше.
- Входящие, ответы оператора и системные события находятся у одного левого
  края. Сохранены avatars, reply preview, история, composer и DOM hooks.
- Все пять столбчатых диаграмм используют общий плагин подписей:
  вертикальные — над столбцами, горизонтальные — после конца столбцов.
  Под текст зарезервировано место; цвет берётся из темы, а не из заливки.
  Белая колонка в палитре устранена. Ресторанов действительно до десяти.
  На узких экранах длинные названия сокращаются только на оси; полные названия
  сохранены в данных/подсказке.
- Для сотрудников исправлено чтение PostgreSQL `timestamptz`:
  пробел, микросекунды и offsets `+HH`/`+HHMM` нормализуются перед прежним
  разбором с переводом в системную зону. Старые ISO/локальные форматы и fallback
  date/time сохранены. SQL, фильтры и формулы длительностей не изменялись.
  Одна точка видима; настоящее отсутствие подходящих дат объясняется текстом,
  а не подменяется демонстрационными значениями.

Примеры тестового наполнения:
[диалог](../../spring-panel/target/neutral-ops-r9-feedback/dialog-dark-1440.png),
[каналы](../../spring-panel/target/neutral-ops-r9-feedback/channels-dark-1440.png),
[рестораны](../../spring-panel/target/neutral-ops-r9-feedback/restaurants-dark-1440.png),
[сотрудники](../../spring-panel/target/neutral-ops-r9-feedback/staff-dark-1440.png).

## Аудит оформления всего проекта

Инвентаризированы 73 шаблона: 46 самостоятельных страниц и 27 фрагментов.
Все страницы используют общие theme/app styles и версию app.css R9.

Найдены и нейтрализованы Bootstrap `shadow*`, отдельный фон toolbar диалогов,
blur/тень редактора знаний, остаточные оформления doughnut-легенды и карточек
сотрудников. CSS EditGuard перенесён из runtime-injection в общий SCSS и
переведён на theme tokens. В UI Kit literal-цвета заменены семантическими токенами.

В 460 проверенных состояниях страниц и 72 дополнительных состояниях не
осталось найденных аудитом декоративных gradient/blur/shadow у проверяемых
видимых карточек/панелей/toolbar. Тени overlay и клавиатурный focus ring —
намеренные исключения. Отдельных inline-тем и внедряемых JS stylesheets нет;
общий first-paint style, структурные/data-dependent inline styles, CSS variables,
цвета статусов и компонентные layout-стили оставлены. Это не заявление об
удалении всех legacy-селекторов: различия компоновки компонентов необходимы,
а проверка не охватывает все возможные business/permission/error состояния.

## Доказательства и воспроизведение

Baseline: `33a58565ae048cf02d01f2ecb3e57bfc1ddcd9ab`, исходно чистый
`HEAD=origin/main`. Detached worktree:
`C:/Users/SinicinVV/AppData/Local/Temp/iguana-ui-r9`.

- Sass generation, package и 160 выбранных Java tests: 0 failures/errors/skipped.
  Включены 145 прежних UI/MVC contracts и 15 новых timestamp cases.
- До изменения парсера: 15 timestamp cases, 6 failures, 0 errors.
  После исправления все 15 успешны; проверены offsets, fractions, смена даты
  в системной зоне, прежние форматы, fallback, нулевая/некорректная/отрицательная
  длительность. Тесты не отключались и не ослаблялись.
- 13 JS checks: существующие query/category/participant contracts и тесты
  агрегации/геометрии диаграмм.
- 460 снимков: 46 MVC-rendered pages × light/dark × 320/390/768/1440/1920 px,
  24 открытия модалок. Нет найденных overflow, пропавших tokens,
  нарушений проверяемого контраста или декоративных overrides.
- 72 palette/theme/touch states и 63 modal focus checks — успешны.
- Дополнительно 10 заполненных сценариев, 70 снимков: точные значения из
  замечаний владельца, геометрия labels вне bars без обрезания/пересечений,
  staff single/empty, настоящие history/tree runtimes, одинаковый контур
  кнопок и левый край сообщений, EditGuard lock/dirty/save.
- Все 64 source/test/tool files совпали с validated sandbox после LF
  normalization. app.css повторно сгенерирован штатно в основном checkout.
  `git diff --check` успешен. Нормализация Java в sandbox для старых текстовых
  contracts не переносилась массово в source.

Браузерные проверки используют тестовое наполнение. Записи и внешние запросы
заблокированы; dashboard read-POST получает fixture response. Они не равны
аутентифицированной приёмке рабочих данных/прав, отправке сообщений или сохранению
форм в рабочей системе.

Артефакты (не коммитятся):

- `spring-panel/target/neutral-ops-r9-feedback/`: 70 PNG и report.json;
- `spring-panel/target/neutral-ops-r9-qa/`: 460 PNG, modal PNG, browser-report.json;
- `spring-panel/target/neutral-ops-r9-states/`: 72 состояния и modal focus;
- `spring-panel/target/neutral-ops-r9-style-audit/source-report.json`.

Команды browser QA после MVC capture по методике `neutral-ops-qa.md`:

```powershell
node tools/neutral-ops-source-audit.cjs
node --test spring-panel/src/test/js/dashboard-activity-trend.test.cjs spring-panel/src/test/js/dashboard-chart-presentation.test.cjs
node tools/neutral-ops-feedback-qa.cjs
node tools/neutral-ops-qa.cjs
node tools/neutral-ops-qa.cjs --states
```

Нужен Playwright или `PLAYWRIGHT_MODULE` с путём к установленному драйверу.
Java regression: repository wrapper, `-f spring-panel/pom.xml`,
`-Dtest=DashboardAnalyticsTimestampTest`, goal `test`.

## Локальное развёртывание

После разрешения владельца обновлён только `panel-web`; `healthy`.
Environment и четыре bind mounts совпали с прежним контейнером.

- image: `iguana-panel:neutral-ops-r9-20261022-v2`;
- image ID: `sha256:445d26e30d138b6e6132f7697654b9a9b77afffbc9b23a6058d6201aa46127c3`;
- JAR SHA-256: `9ba7310a6dad58754ab0b551696f09749f33d6d88fd5a893042d3e4cdd9ea9d6`;
- started: `2026-10-22T19:17:08.59843781Z`;
- rollback: `iguana-panel:rollback-neutral-ops-r9-20261022`;
- rollback image ID: `sha256:3936d8495f132a9a73b2a75cc0ccd1e1cdeb0128a7dfedb8fbb25c26a5b445a0`.

В прежнем работающем JAR изменены ровно 49 UI resources и один
`DashboardAnalyticsService.class`; hashes остальных Java/classes/libraries
и entries сохранены (2018 entries). Исходник этого сервиса в R7 runtime-baseline
и текущем HEAD до узкого исправления одинаков. Остальные backend commit
мультибизнеса не развёртывались. Для business dialogs сохранён прежний runtime
markup с новой CSS-version; новая source-версия попадёт в штатную полную сборку.
Это точечный overlay, не полная сборка HEAD. Предварительный R9 UI-only image
не развёртывался; final v2 содержит также разрешённый timestamp fix.

Проверены HTTP 200 и SHA фактически отдаваемых app.css/chart JS/EditGuard JS;
login содержит app.css R9. Копия JAR из нового контейнера совпала по SHA.
Из неё извлечён parser и в read-only harness прочитаны все 47 пар дат из
PostgreSQL: null parse нет, все длительности совпали с SQL-расчётом.
Идентификаторы/содержимое обращений для этой проверки не извлекались.

`ops-worker` и `bot-runner` не перезапускались: прежний digest
`sha256:97ccb6fc7a34ae77af2eb7f264c49d8665393571060ad0fa46664ea528a9ce62`,
started `2026-10-22T12:43:42Z`. Инфраструктура и db-migrate не запускались.
Flyway до/после — `81`; migrations/backfill/внешние бизнес-записи не выполнялись.
Alias `iguana-panel:local` обновлён; rollback tags сохранены, откат не выполнялся.

Пакетирующий/deploy script и read-only harness находятся в игнорируемом
`spring-panel/target/neutral-ops-r9-image/`; секреты в отчёт не сохранялись.
Остаётся ручная проверка владельцем: Ctrl+F5, диалоги и отчёты с нужным периодом,
обе темы. Принятие результата не подразумевается.

## Точный scope и фиксация

Staging/commit/push не выполнялись. Ниже все 67 файлов, включая этот отчёт,
task checkpoint и новый append-only changelog. Не использовать `git add .`.

```powershell
$uiFiles = @(
  'ai-context/changelog/2026-10-22_222021_neutral-ops-consistency-r9.md'
  'ai-context/tasks/task-details/01-281.md'
  'docs/ui/neutral-ops-consistency-r9.md'
  'spring-panel/src/main/java/com/example/panel/service/DashboardAnalyticsService.java'
  'spring-panel/src/main/resources/scss/app.scss'
  'spring-panel/src/main/resources/scss/app/_neutral-ops-audit.scss'
  'spring-panel/src/main/resources/scss/app/_neutral-ops-feedback.scss'
  'spring-panel/src/main/resources/scss/app/dialogs/_list-layout.scss'
  'spring-panel/src/main/resources/static/css/app.css'
  'spring-panel/src/main/resources/static/js/dashboard-chart-presentation.js'
  'spring-panel/src/main/resources/static/js/edit-guard.js'
  'spring-panel/src/main/resources/templates/analytics/backup-readiness.html'
  'spring-panel/src/main/resources/templates/analytics/certificates.html'
  'spring-panel/src/main/resources/templates/analytics/credential-rotation.html'
  'spring-panel/src/main/resources/templates/analytics/iiko-api-monitoring.html'
  'spring-panel/src/main/resources/templates/analytics/index.html'
  'spring-panel/src/main/resources/templates/analytics/provider-delivery.html'
  'spring-panel/src/main/resources/templates/analytics/provider-health.html'
  'spring-panel/src/main/resources/templates/analytics/public-ingress.html'
  'spring-panel/src/main/resources/templates/analytics/rms-control.html'
  'spring-panel/src/main/resources/templates/analytics/smtp-notifications.html'
  'spring-panel/src/main/resources/templates/auth/login.html'
  'spring-panel/src/main/resources/templates/businesses/ai-source.html'
  'spring-panel/src/main/resources/templates/businesses/ai-sources.html'
  'spring-panel/src/main/resources/templates/businesses/dialog-ai.html'
  'spring-panel/src/main/resources/templates/businesses/dialog-assignment.html'
  'spring-panel/src/main/resources/templates/businesses/dialogs.html'
  'spring-panel/src/main/resources/templates/businesses/manage.html'
  'spring-panel/src/main/resources/templates/channels/index.html'
  'spring-panel/src/main/resources/templates/clients/index.html'
  'spring-panel/src/main/resources/templates/clients/profile.html'
  'spring-panel/src/main/resources/templates/clients/unblock_requests.html'
  'spring-panel/src/main/resources/templates/dashboard/index.html'
  'spring-panel/src/main/resources/templates/dialogs/ai-ops.html'
  'spring-panel/src/main/resources/templates/dialogs/index.html'
  'spring-panel/src/main/resources/templates/error.html'
  'spring-panel/src/main/resources/templates/error/403.html'
  'spring-panel/src/main/resources/templates/error/404.html'
  'spring-panel/src/main/resources/templates/error/500.html'
  'spring-panel/src/main/resources/templates/general/access.html'
  'spring-panel/src/main/resources/templates/general/dialogs.html'
  'spring-panel/src/main/resources/templates/general/new.html'
  'spring-panel/src/main/resources/templates/general/work.html'
  'spring-panel/src/main/resources/templates/incidents/index.html'
  'spring-panel/src/main/resources/templates/knowledge/editor.html'
  'spring-panel/src/main/resources/templates/knowledge/list.html'
  'spring-panel/src/main/resources/templates/knowledge/note-editor.html'
  'spring-panel/src/main/resources/templates/knowledge/notes.html'
  'spring-panel/src/main/resources/templates/passports/detail.html'
  'spring-panel/src/main/resources/templates/passports/list.html'
  'spring-panel/src/main/resources/templates/projects/index.html'
  'spring-panel/src/main/resources/templates/settings/index.html'
  'spring-panel/src/main/resources/templates/tasks/index.html'
  'spring-panel/src/main/resources/templates/ui-kit/index.html'
  'spring-panel/src/main/resources/templates/users/detail.html'
  'spring-panel/src/main/resources/templates/users/index.html'
  'spring-panel/src/main/resources/templates/workforce/check-in.html'
  'spring-panel/src/test/java/com/example/panel/controller/AnalyticsControllerWebMvcTest.java'
  'spring-panel/src/test/java/com/example/panel/passports/ObjectPassportEquipmentEditorUiSourceContractTest.java'
  'spring-panel/src/test/java/com/example/panel/passports/ObjectPassportsListUiSourceContractTest.java'
  'spring-panel/src/test/java/com/example/panel/runtime/DialogsColumnWidthsUiSourceContractTest.java'
  'spring-panel/src/test/java/com/example/panel/service/DashboardAnalyticsTimestampTest.java'
  'spring-panel/src/test/java/com/example/panel/ui/NeutralOpsPageFamiliesWebMvcTest.java'
  'spring-panel/src/test/js/dashboard-chart-presentation.test.cjs'
  'tools/neutral-ops-feedback-qa.cjs'
  'tools/neutral-ops-qa.cjs'
  'tools/neutral-ops-source-audit.cjs'
)
git status --short
$existingStaged = @(git diff --cached --name-only)
if ($existingStaged.Count -ne 0) { throw 'Сначала проверьте уже staged изменения' }
git add -- $uiFiles
if ($LASTEXITCODE -ne 0) { throw 'git add failed' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'staged diff check failed' }
git diff --cached --name-only
$actual = @(git -c core.quotePath=false diff --cached --name-only)
if ($actual.Count -ne $uiFiles.Count -or @(Compare-Object $actual $uiFiles).Count -ne 0) {
  throw 'Staged paths отличаются от ожидаемого scope'
}
git commit -m 'fix(ui): согласовать Neutral Ops и восстановить график сотрудников #ai'
# Только после успешного commit:
git push
```
