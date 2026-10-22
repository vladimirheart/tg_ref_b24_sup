# 01-281 — правки после визуальной приёмки (R8)

## Результат

- Раскрытие дерева локаций и меню действий строки диалога: 24×24 px для мыши,
  44×44 px на touch. Исправлен конфликт компактного размера с общей min-height.
- Переписка: компактная шапка, плоский контекст, входящие слева, исходящие справа,
  системные события по центру, различимые reply previews, нижняя строка инструментов
  с отдельным местом в textarea. На узких экранах textarea занимает всю ширину.
- Отчёты: новый двухлинейный график с лёгкой заливкой; остальные диаграммы без
  тяжёлых градиентов, с более тонкими столбцами и аккуратными легендами.

Исходное превью: `C:/Users/SinicinVV/Desktop/Iguana-Neutral-Ops-Preview.html`.
Серии нового графика честно проецируют существующую `activity_stats.matrix`:
сумма всех обращений и сумма обращений в рабочие часы по каждому дню недели за
выбранный период. Это не календарный ряд и не статистика закрытия обращений.
Новые источники, API, серверные формулы, права или фильтры не добавлялись.

## Проверка

Baseline `24e95ff6f8402dd63fe30e3cc0991fc1cf7e5ac3`, чистый `HEAD=origin/main`.
Detached worktree `C:/Users/SinicinVV/AppData/Local/Temp/iguana-ui-r8`.
11 source/test/tool files после переноса совпали с проверенной копией; app.css
сгенерирован штатным Maven/Sass pipeline, не редактировался вручную.

- 145 MVC/UI-contract tests: 0 failures, 0 errors, 0 skipped; package успешен.
- 9 JS tests: агрегация/empty/re-render/ночные часы нового графика, существующие
  query contracts диалога/истории, категории и участники бизнеса.
- 10 комбинаций light/dark × 320/390/768/1440/1920 px, на каждой — заполненные
  отчёты, дерево локаций и переписка; отдельно проверен пустой ответ графика.
- 30 снимков; проверены суммы графика, 24/44 px, реальное раскрытие ветки,
  отсутствие выхода истории/модалки за экран, выравнивание исходящих по правому
  краю и резерв под инструменты composer.
- 72 дополнительных проверки трёх палитр, light/dark/auto и touch; 63 открытия
  модалок с удержанием клавиатурного фокуса. `git diff --check` успешен.

Начальный RED разобран, тесты не ослаблялись: обновлено точное CSS-version
assertion; два новых native details в business-странице получили уже принятый
`ui-disclosure-native`; Java в sandbox нормализована по LF для старого
строкового контракта оборудования. Production Java не менялась.

Снимки и `report.json`: `spring-panel/target/neutral-ops-feedback/`.
Наполнение явно тестовое, сообщения сформированы настоящим history renderer,
дерево — настоящим locations runtime. Внешние запросы и реальные записи
заблокированы; POST read-API dashboard получает fixture response. Это проверка
представления и локальных взаимодействий, не отправка сообщений/сохранение
данных в рабочей системе и не authenticated business acceptance.

Воспроизведение (после MVC capture по инструкции `neutral-ops-qa.md`):

```powershell
node --test spring-panel/src/test/js/dashboard-activity-trend.test.cjs
node tools/neutral-ops-feedback-qa.cjs
```

Для Node требуется доступный `playwright` или путь к установленному драйверу в
`PLAYWRIGHT_MODULE`. Артефакты `target` не коммитятся.

## Развёртывание

Обновлён только локальный `panel-web`, состояние `healthy`:

- image `iguana-panel:neutral-ops-r8-20261022`;
- image ID `sha256:3936d8495f132a9a73b2a75cc0ccd1e1cdeb0128a7dfedb8fbb25c26a5b445a0`;
- JAR SHA-256 `4cd6e2f939cbe5fabebcfd82a88bbde1d812fd8f3a9507665394c6c7787a504a`;
- непосредственный rollback `iguana-panel:rollback-neutral-ops-r8-20261022`,
  image ID `sha256:2d4d06151468fbe62cb5fcb310f1e9f681cf4fc0fc4bc7399edb0d775df35ca7`.

Взята копия JAR реально работавшего R7; заменены ровно пять UI entries:
`static/css/app.css`, `static/js/dashboard-activity-trend.js`,
`templates/{dashboard,dialogs,settings}/index.html` внутри `BOOT-INF/classes/`.
SHA каждого другого entry сохранён, Java и библиотеки не менялись.
Пакетирующий скрипт, JAR и Dockerfile — в `spring-panel/target/neutral-ops-r8-image/`.
Это UI-only overlay, а не полная сборка текущего HEAD; новые backend commit
мультибизнеса не развёртывались. Их два canonical-class исправления находятся
только в source и попадут в следующую штатную сборку.

Перед пересозданием сверены все Compose environment values и bind mounts.
После — HTTP 200 и совпадение SHA отдаваемых CSS/JS с source, `/login` HTTP 200.
Alias `iguana-panel:local` обновлён. Workers не перезапускались, остались на
`sha256:97ccb6fc7a34ae77af2eb7f264c49d8665393571060ad0fa46664ea528a9ce62`.
Flyway по-прежнему `81`; migrations, backfill и внешние бизнес-записи не выполнялись.
Оба прежних rollback сохранены; откат не исполнялся. Задача остаётся `🟣` до
повторной ручной проверки владельца.

## Файлы и фиксация

Staging, commit и push не выполнялись. Перед фиксацией убедитесь, что index не
содержит чужих изменений. Ожидаемый scope — следующие 14 файлов:

```powershell
$uiFiles = @(
  'spring-panel/src/main/resources/scss/app.scss'
  'spring-panel/src/main/resources/scss/app/_neutral-ops-feedback.scss'
  'spring-panel/src/main/resources/static/css/app.css'
  'spring-panel/src/main/resources/static/js/dashboard-activity-trend.js'
  'spring-panel/src/main/resources/templates/businesses/dialogs.html'
  'spring-panel/src/main/resources/templates/dashboard/index.html'
  'spring-panel/src/main/resources/templates/dialogs/index.html'
  'spring-panel/src/main/resources/templates/settings/index.html'
  'spring-panel/src/test/java/com/example/panel/runtime/DialogsColumnWidthsUiSourceContractTest.java'
  'spring-panel/src/test/js/dashboard-activity-trend.test.cjs'
  'tools/neutral-ops-feedback-qa.cjs'
  'docs/ui/neutral-ops-feedback.md'
  'ai-context/tasks/task-details/01-281.md'
  'ai-context/changelog/2026-10-22_210827_neutral-ops-acceptance-feedback.md'
)
git status --short
git add -- $uiFiles
git diff --cached --check
git diff --cached --name-only
# Только если staged paths точно совпадают со списком выше:
git commit -m 'fix(ui): доработать Neutral Ops по замечаниям приёмки #ai'
git push
```
